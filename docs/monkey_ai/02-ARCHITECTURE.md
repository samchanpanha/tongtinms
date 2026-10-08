# Architecture

## 1. Decision for MVP

Use a modular monolith, not many microservices on day one.

Why:
- one preview port in this environment
- financial formulas must stay in one transaction
- faster one-by-one delivery

Layout:
- `apps/web` Next.js frontend
- `apps/api` Spring Boot backend
- `infra` Docker Compose for Postgres + api + web
- frontend reverse-proxies `/api` to backend

Later extract: notification service, identity service.

## 2. Stack

| Layer | Choice |
|---|---|
| Web | Next.js App Router, TypeScript, Tailwind |
| API | Java 21, Spring Boot 3, Spring Web, Validation, Security, Data JPA |
| DB | PostgreSQL 16 |
| Auth | JWT access + refresh, roles HOST and MEMBER, owner self-register |
| Migrate | Flyway |
| API docs | springdoc-openapi |
| Tests | JUnit + AssertJ for formula engine |
| Package | Docker Compose |
| Money | Java `long` minor units + CHAR(3) currency, never `double` |
| Export | Apache POI (XLSX) + RFC 4180 CSV (UTF-8 BOM) |

Frontend talks only to `/api`.
Backend listens internally (example 8080).
Preview exposes Next.js port only.

## 3. Modules inside API

```
com.tongtin
  common        id, money (minor-unit long), time, errors, security (JWT), audit events, health
  identity      users, roles, JWT, owner self-register
  members       member profiles
  groups        groups, shares, invites
  cycles        cycle state machine + sealed bids (cycles/bids) + close-and-calculate
  ledger        formulas, obligations, payments (ledger/payments), late fees
  dashboard     host KPI dashboard
  reports       ledger / host profit / member statement + CSV & XLSX export (reports/export)
  notify        in-app notifications
  memberportal  member portal (member-facing `/api/v1/me/...`)
  subscription  plans, orders, ABA PayWay, lifecycle job
  settings      system_settings entity, catalog registry, typed reads
  demo          demo fixture seeder
```

As built: sealed bidding lives in `cycles/bids` (part of the cycle state machine),
payments in `ledger/payments`, and audit events in `common/audit`. `reports` gained
the `export` subpackage in Step 30 (`CsvRenderer`, `XlsxRenderer` via Apache POI,
`ExportService`, `ExportTables`). `memberportal` is the member-only `/api/v1/me` surface.

`settings` (Step 20) owns the `system_settings` table and the
`SettingsCatalog` registry — the single source of truth for every runtime
knob. Other modules read typed values through `SettingsService` and never
touch the repository directly. `subscription` reads/writes PayWay +
subscription keys through `SettingsService` too.

Formula engine lives in `ledger` and is pure Java with no Spring in the calculator class.
This is the most important code. Tests must match `docs/01-DOMAIN.md` examples.

## 4. Data model (PostgreSQL)

```
users
  id, email unique nullable, phone unique, password_hash, full_name, status, created_at

user_roles
  user_id, role

currencies
  code CHAR(3) PK, name, symbol, exponent, rounding, active

owner_accounts
  id, user_id unique, display_name, status, default_currency CHAR(3) default VND,
  cccd, bank_name, bank_account, account_holder, zalo, city, created_at

member_profiles
  id, user_id nullable, owner_id, full_name, phone, note, created_at

groups
  id, owner_id, code, name, type, base_amount, share_count,
  cycle_unit, cycle_count, start_at, status,
  host_fee_type, host_fee_minor, host_fee_bps,
  min_bid, max_bid, bid_step, tie_break,
  late_fee_type, late_fee_value,
  bid_open_offset, bid_close_offset, allow_multi_share,
  currency CHAR(3) FK currencies, rules_frozen, created_at

group_shares
  id, group_id, member_profile_id, share_no, status,
  won_cycle_id nullable

cycles
  id, group_id, cycle_no, status, currency,
  open_at, bid_close_at, due_at,
  winner_share_id, winning_bid, gross_pot, host_fee, net_payout,
  calculated_at

bids
  id, cycle_id, share_id, amount_minor, currency, submitted_at, is_latest

ledger_entries
  id, group_id, cycle_id, share_id nullable, member_profile_id,
  type, direction, amount_minor, currency, status, due_at, created_at

payments
  id, group_id, received_by_host_id, amount_minor, currency, method, paid_at, note

payment_allocations
  id, payment_id, ledger_entry_id, amount_minor

notifications
  id, user_id, type, title, body, read_at, created_at

audit_events
  id, actor_user_id, entity_type, entity_id, action, payload_json, created_at

system_settings (V11, owned by settings module)
  key VARCHAR(100) PK, value TEXT, description TEXT, updated_at TIMESTAMPTZ

subscription_plans (V11)
  id, code unique, name, price_minor, currency CHAR(3), billing_cycle,
  max_groups, active, created_at

subscription_orders (V11)
  id, owner_id, plan_id, tran_id unique, amount_minor, currency,
  status, expires_at, created_at, updated_at

payment_events (V12, Step 25 — append-only payment forensics)
  id, tran_id nullable, source (CALLBACK | CHECK), outcome
  (RATE_LIMITED / MISSING_TRAN_ID / ORDER_* / ERROR for CALLBACK;
  APPROVED | NOT_APPROVED for CHECK), payload TEXT (raw JSON,
  truncated 8000 chars), remote_ip nullable, created_at.
  Insert-only by design (repository exposes no update/delete);
  survives callback failures because each record() commits outside
  the verify transaction when no transaction is active.
```

Indexes:
- unique (group_id, cycle_no)
- unique latest bid per (cycle_id, share_id) via partial unique or version
- ledger by group_id + share_id
- users.phone unique
- users.email unique where not null
- groups.code unique per owner_id
- member_profiles.phone unique per owner_id
- money columns are BIGINT minor units; reports must select currency + exponent
- subscription_orders.tran_id unique; idx_sub_orders_owner; idx_sub_orders_tran_id
- payment_events (tran_id, created_at DESC)
- owner_accounts (subscription_ends_at, subscription_status) — V13, Step 27;
  serves the daily lifecycle-job scan
  (`findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn`)
- ledger_entries (cycle_id, share_id) WHERE type = 'LATE_FEE' — V14, Step 29;
  speeds up the "already charged" delta sum during late-fee assessment
  (groups.late_fee_type / late_fee_value themselves existed since V3)

## 5. API outline

Prefix: `/api/v1`

Auth:
- POST `/auth/register-owner`  (public, creates User HOST + OwnerAccount)
- POST `/auth/login`           (phone + password)
- POST `/auth/refresh`
- GET  `/me`
- PATCH `/me/owner-profile`    (HOST onboarding fields)
- no public member register in MVP

Host members (added Step 04):
- POST `/members`            create member profile (name, phone, note)
- GET  `/members`            list own members (?q= search)
- GET  `/members/{id}`
- PATCH `/members/{id}`      update name/phone/note/status
- DELETE `/members/{id}`     soft deactivate (status INACTIVE; history kept)

Host groups:
- POST `/groups`
- GET `/groups`
- GET `/groups/{id}`
- POST `/groups/{id}/shares`   assign share(s) to a member (count >= 1, multi-share allowed)
- GET  `/groups/{id}/shares`   list assigned shares
- DELETE `/groups/{id}/shares/{shareId}`  unassign (only before start)
- POST `/groups/{id}/start`    requires sum(shares) == share_count; freezes rules (READY)
- GET `/groups/{id}/ledger`
- GET `/groups/{id}/profit`

Cycles:
- POST `/groups/{id}/cycles/open`
- GET  `/groups/{id}/cycles`        list cycles of my group
- POST `/cycles/{id}/bids`
- POST `/cycles/{id}/close-and-calculate`
- POST `/cycles/{id}/confirm-payout`
- GET `/cycles/{id}`
- GET `/cycles/{id}/summary`

Payments:
- POST `/groups/{id}/payments`
- GET `/groups/{id}/debts`
- POST `/groups/{id}/late-fees/assess`  (HOST, Step 29) scan overdue CONTRIBUTION
  obligations of my group and append LATE_FEE delta rows per 01 §9.6; returns
  `{assessed, created, entries[]}` (entry = ledgerEntryId, cycleId, shareId,
  amountMinor, currency, dueAt). Idempotent: same-day re-run creates nothing;
  `lateFeeType = NONE` → `{0, 0, []}`. GET stays read-only (assessment is explicit).

Host dashboard (Step 12):
- GET `/host/dashboard`   owner-scoped summary: my groups (id, code, name, type, status,
  currency, shareCount, cycleCount, currentCycleNo + currentCycleStatus, nextDueAt,
  unpaidCount, overdueCount, hostProfitMinor) plus aggregate host profit per currency
  (`currencies[]` = money shape per §5: currency, amountMinor, exponent, symbol).
  hostProfit = SUM(cycles.host_fee) of my SETTLED cycles; per-group profit is settled
  cycles of that group. nextDueAt = earliest due_at among UNPAID/PARTIAL obligations.
  Profits are grouped by currency and NEVER summed across currencies (07 §3/§4.7).

Member login (Step 13):
- POST `/members/{id}/set-login`   HOST creates/resets the member's login
  (body {password}). Creates the member's `users` row (phone from profile, BCrypt,
  MEMBER role) or resets its password if already present. 409 if that phone is
  already claimed by ANOTHER user account. Member then logs in via `POST /auth/login`
  (shared endpoint — now role-agnostic: roles from `user_roles`, ownerId absent for
  members). Decision: host-set password (no SMS/OTP in MVP, offline cash-group
  reality, 05-OWNER-SELF-REGISTER §9a).

Member portal:
- GET `/me/groups`
- GET `/me/groups/{id}`
- GET `/me/groups/{id}/balance`
- POST `/me/cycles/{id}/bids`

All write APIs require auth.
Member bid API only for owner of the share.

Member identity model (Step 13): member logs in with the phone on their
member profile + host-set password. The member's `users` row resolves to every
`member_profiles(phone)` across any owner -> the member sees only groups/shares
assigned to those profiles. A member may belong to several hosts' hội at once.
Balance (01-DOMAIN §9.5): contributed = sum allocated toward CONTRIBUTION entries
of own shares, received = sum of PAID PAYOUT entries, feesPaid = sum allocated
toward LATE_FEE/OTHER_FEE, netPosition = received - contributed - feesPaid.
Money is long minor units with the group currency on the same row.

Notifications (Step 14) — decision 2026-10-07 (user): in-app feed table, sync
transitions only (no scheduled reminders yet). V9 creates `notifications`
(id, user_id FK users, type, title, body, read_at, created_at, index
(user_id, created_at DESC)). Module `com.tongtin.notify`.
- Recipients: USERS with logins only (paper profiles without a `users` row get
  nothing). Notifications go to the affected member's user(s),
  resolved by member_profile.phone -> users.phone; host acts OFFLINE
  (no inbox spam for their own actions).
- Events fired synchronously inside the SAME transaction as the business op:
  - CYCLE_OPENED      all members of group     "Ky {no} mo" / bidding deadline
  - WINNER_PUBLISHED  all members of group     on close-and-calculate (public)
  - PAYOUT_READY      winning member           on confirm-payout (net payout)
  - PAYMENT_CONFIRMED payer member(s)          on POST /payments (allocated rows)
  - GROUP_COMPLETED   all members of group     on confirm-payout of last cycle
  - MEMBER_LOGIN_NOTICE the member             on POST /members/{id}/set-login
- Notification FAILURE MUST NOT MUTATE FINANCIAL STATE: NotificationService.create
  swallows+logs exceptions per row so a notification write can never roll back the
  surrounding financial transaction.
- Endpoints (any authenticated user, own feed only):
  - GET  /notifications              -> { notifications[], unreadCount } newest
    first, capped at 100
  - POST /notifications/{id}/read    -> marks one read (404 if not own)
  - POST /notifications/read-all     -> { updatedCount }

Reports (Step 15) — decision 2026-10-07 (user): build all three reports in JSON
only (CSV/Excel export stays parked P2), and every monetary field uses the money
shape {currency, amountMinor, exponent, symbol} from 07 §5 (no bare amounts; pure
counters and dates stay scalars). New module `com.tongtin.reports` (+ reusable
`Money` record in `com.tongtin.common.money`, currency rows cached at boot).
- HOST GET /groups/{id}/ledger  -> { groupId, groupName, currency,
  entries[] } each entry: entryId, cycleNo, type, direction, shareNo,
  memberProfileId, memberName, amount (money), status, dueAt, allocated (money),
  remaining (money); entries sorted by (cycleNo, shareNo nulls-last, entryId);
  totals totalIn / totalOut (money) across the group currency. Ledger is
  append-only; allocated = SUM of that entry's payment_allocations, remaining =
  amount - allocated (floor 0).
- HOST GET /groups/{id}/profit  -> { groupId, groupName, currency,
  formulaVersion, cycles[] } per cycle: cycleNo, status, openedAt, closedAt
  (calculatedAt), winner { shareId, shareNo, memberName }, winningBid, grossPot,
  hostFee, netPayout (all money; null until calculated); totals: grossPot,
  hostFee, netPayout (across calculated cycles) + settledHostFee (only
  status=SETTLED). formulaVersion = FormulaEngine.ENGINE_VERSION (same for all
  groups today; presets BIDDING_CLASSIC/FIXED_EQUAL frozen).
- MEMBER GET /me/groups/{id}/statement -> per-share transaction history (own
  shares only, 404 if not a member): { groupId, groupName, currency, shares[],
  totals }. Each share: shareId, shareNo, status, entries[] (entryId, cycleNo,
  type, direction, shareNo, amount, status, dueAt, allocated, remaining,
  runningBalance — money) sorted (cycleNo, entryId) with runningPosition added
  after each line, and totals {contributed, received, feesPaid, netPosition}
  using the 01-DOMAIN §9.5 definitions; totals across the member's shares as
  well. runningBalance: money actually moved — contributions/fees subtract
  allocated, payouts add full amount.
- MEMBER GET /me/groups/{id}/cycles -> public cycle summaries (01-DOMAIN §14;
  host fee and host profit are HOST-ONLY and must NOT appear): each cycle {
  cycleNo, status, openAt, bidCloseAt, dueAt, winner { shareNo, memberName },
  winningBid, grossPot, netPayout (money; null until calculated) }.
- All amounts are long minor units of the group currency; no cross-currency
  math; unknown/cross-tenant/cross-member -> 404.

Hardening (Step 16) — decision 2026-10-07 (user): financial-hardening core +
demo seeder only (no paging, no error-body polish, no JSON logging this step).
1. **Audit trail (release gate "corrections create reversal trails"):** every
   money/winner action writes an `audit_events` row IN THE SAME tx as the state
   change via a shared `AuditService` (actor = the authenticated principal's
   user id; JSON payload, serialization failure degrades to `{}` and never
   breaks the financial write).
   - CYCLE_OPENED  (Cycle)     {cycleNo, status, bidCloseAt, dueAt}
   - BID_SUBMITTED (Bid)       {cycleId, shareId, amountMinor, currency} — actor
     = whoever submits (host or member portal)
   - CYCLE_SETTLED (Cycle)     {cycleNo, winnerShareId, winningBid, grossPot,
     hostFee, netPayout, formulaVersion}
   - PAYOUT_CONFIRMED (Cycle)  {cycleNo, netPayout}
   - PAYMENT_RECORDED (Payment) {paymentId, amountMinor, currency, method,
     paidAt, allocations:[{ledgerEntryId, amountMinor}]}
2. **Payment idempotency key (release gate "retrying never duplicates
   money"):** POST /groups/{id}/payments accepts optional `Idempotency-Key`
   header (≤64 chars). `payments.idempotency_key` added (V10) with partial
   unique index (group_id, idempotency_key). Replay of the SAME key with an
   IDENTICAL payload returns the original payment (200, no new rows); same key
   with a DIFFERENT payload -> 409; concurrent duplicate submit is caught by
   the unique index -> 409.
3. **Money rounding:** currency-agnostic minor-unit math; explicit HALF_UP
   boundary tests for `roundBps` (ties round away from zero, integer-only).
4. **Cross-tenant authz sweep:** one test class hitting every money/winner +
   owner-scoped endpoint with (a) another host -> 404, (b) anonymous -> 401,
   (c) member role -> 403 (member-only endpoints: host -> 403).
5. **Demo seeder (fixture from STATUS.md):** optional `CommandLineRunner`
   enabled by `app.seed-demo=true` (default OFF). Registers demo host phone
   0900111001, builds the N=10 BIDDING fixture (C=1_000_000,
   hostFeeType FIXED_PER_CYCLE 100_000) and plays all 10 cycles to COMPLETED:
   cycle1 B=200_000 -> net 7_100_000; cycle2 B=150_000 -> net 7_700_000;
   cycles 3-9 B=100_000; last cycle OPEN B=0 -> net 8_900_000. Idempotent:
   re-run on an already-seeded host logs and exits. It reuses the real
   services (AuthService, MemberService, GroupService, ShareService,
   CycleService, BidService, CycleCloseService) so demo data passes through
   production invariants. All demo rows are gated to that host, so a normal
   DB clean removes them.

Preview polish (Step 17) — decision 2026-10-07:
1. **Frontend UI Architecture**:
   - Next.js App Router (TypeScript + Tailwind CSS v4), dark-mode compatible, responsive layouts, Vietnamese copy first.
   - Universal currency formatter: `formatMoney(amountMinor, currency)` formatting `1.000.000 đ` (VND) and `$100.00` (USD).
   - Rich aesthetics: clean status pills (`DRAFT`, `RECRUITING`, `READY`, `RUNNING`, `COMPLETED`), stat badges, cycle countdowns.
   - Empty states with explanatory messaging and quick call-to-action buttons across all directories and lists.
2. **Host & Member Workflows**:
   - `/`: Landing page with live API health badge, quick-switch between Host and Member portals.
   - `/login`: Unified phone + password login supporting both `HOST` and `MEMBER` roles with automatic redirection.
   - `/register/owner`: Chu Hoi self-registration with full validation, terms acceptance, and instant dashboard entry.
   - `/host`: Comprehensive Host Dashboard with per-currency profit, active groups, next due dates, unpaid/overdue badges.
   - `/host/members`: Member directory with search filter, new member profile creation, and set-login credential management.
   - `/host/groups/new`: Group setup wizard (BIDDING / FIXED_EQUAL), base amounts, share counts, fee presets.
   - `/host/groups/[id]`: Group command center — share assignment, cycle control (open, enter bids, close & calculate, confirm payout), manual payment recording with idempotency.
   - `/host/groups/[id]/ledger`: Full group ledger breakdown showing contributions, payouts, and host fees with allocated/remaining balances.
   - `/app`: Member portal listing personal groups, share counts, and notifications.
   - `/app/groups/[id]`: Member cycle view, sealed bid placement, public cycle summaries (host fee hidden), and personal statement breakdown.
3. **Environment & Start Infrastructure**:
   - `next.config.ts`: `allowedOrigins` configured with `*.monkeycode-ai.live` and `localhost:3000`.
   - Start script: `start.sh` at repository root orchestrating docker-compose PostgreSQL, API build/run, and Next.js frontend with health checks.

Final delivery & wrap-up (Step 18) — decision 2026-10-07:
1. **Complete Monorepo Delivery**:
   - Backend: Spring Boot 3 + Java 21 modular monolith with 119 passing tests (100% success rate).
   - Frontend: Next.js 16 App Router + Tailwind CSS v4 with static/dynamic compilation across all 11 routes and 0 lint warnings/errors.
   - Database: PostgreSQL 16 managed via forward-only Flyway migrations (V1 to V10) with multi-tenant data isolation and audit logging.
2. **Financial Engine Guarantees**:
   - Exact integer minor unit math (`long` minor units, exact bps calculation with HALF_UP rounding, zero floating-point arithmetic).
   - Double-sided balanced ledger invariant: `SUM(IN) == SUM(OUT)` strictly maintained for every closed cycle.
   - Payment idempotency: `Idempotency-Key` header prevents duplicate money allocation on retries.
3. **Turnkey Operations**:
   - All-in-one root startup script (`start.sh`) managing PostgreSQL, backend API, Next.js frontend, and `--seed` demo preloading with graceful shutdown.

Subscriptions & ABA PayWay (Step 19) — decision 2026-10-07 (user): host
subscription billing via ABA PayWay checkout, admin-managed plans and
settings. Module `com.tongtin.subscription`.

Public / host:
- GET  `/subscription/plans`            active plans (public)
- GET  `/subscription/my-status`        own subscription status + trial/grace info
- POST `/subscription/checkout/payway`  create order -> PayWay purchase URL
  (tran_id `TT_<ownerId>_<uuid10>`); returns paywayUrl + fields
  Return-flow contract (Step 24, decision 2026-10-08): the gateway needs
  ABSOLUTE return URLs, so the web client passes its own origin
  (`returnUrl` = `<origin>/host/subscription`) and the server composes
  `return_url` = base + `?status=success&tran_id=...`,
  `continue_success_url` = base + `?status=success&tran_id=...`,
  `cancel_url` = base + `?status=cancelled&tran_id=...`.
  Server validates any client-supplied absolute URL against the request
  `Origin` header (scheme http/https, host+port must match; path-only
  URLs resolve against Origin); rejects `status=`/`tran_id=` already in
  the query. Without Origin (tests/curl) relative defaults are kept.
  Landing on `/host/subscription?tran_id=...` makes the page call
  `POST /subscription/verify/{tranId}` — activation trusts ONLY the
  gateway checkTransaction result, never the query params.
  Checkout idempotency (Step 26, decision 2026-10-08): a repeated
  checkout for the same (owner, plan) REUSES the most recent PENDING
  order created within `checkout_pending_reuse_minutes` (SUBSCRIPTION
  catalog, default 10, 0 disables) instead of inserting a duplicate —
  same `tran_id`/`req_time` are kept, the response (hash, form fields,
  return URLs) is rebuilt from the current request and the order row's
  `payway_hash` updated. PAID/FAILED orders and orders older than the
  window are never reused.
- POST `/subscription/verify/{tranId}`  authoritative checkTransaction
  (status.code 0/00 + payment_status APPROVED), activates/extend on success
- POST `/payments/payway/callback`      provider callback (public), same
  authoritative verification, idempotent per order status.
  Step 25 abuse control + forensics: per-IP sliding-window rate limit
  (`rate_limit_payway_callback_per_minute`, SECURITY catalog, default
  60/min,   429 + `RATE_LIMITED` event when exceeded) and every invocation is
  journaled to the append-only `payment_events` table — exactly one
  source `CALLBACK` row per request with its final outcome
  (RATE_LIMITED / MISSING_TRAN_ID / ORDER_<status> / ERROR) plus
  source `CHECK` rows
  written by `verifyAndActivateViaGateway` carrying the raw
  check-transaction response (APPROVED / NOT_APPROVED)
- POST `/payments/payway/simulate-complete` sandbox/dev helper to complete an
  order without real bank (gated by `payway_sandbox_mode`)

Admin (`/api/v1/admin`, `@PreAuthorize("hasRole('ADMIN')")`):
- GET|POST|PUT|DELETE `/plans`          plan CRUD
- GET  `/hosts`                         host accounts with subscription state
- POST `/hosts/{id}/extend`             manual subscription extension
- GET  `/orders`                        recent orders
- GET  `/insights`                      revenue per plan (PAID orders grouped
  by plan + per-currency totals) and registration cohort/churn counts
  (owner_accounts grouped by created month: registered / active_now /
  churned, churned = subscription_ends_at in the past) — Step 27
- GET|PUT `/settings/payway`            typed PayWay settings (legacy shape;
  delegates to the settings module)

Lifecycle: `SubscriptionLifecycleJob` runs daily 08:00 — sends expiry
reminders at `subscription_reminder_days` (default 7/3/1), then auto-expires
hosts past `grace_period_days` after `subscription_ends_at` (no-op when
`enforce_subscription` is off).

Settings module (Step 20) — decision 2026-10-07 (user): one centralized
settings surface so every module's runtime configuration can be viewed and
controlled from the admin UI. Package `com.tongtin.settings`, backed by the
`system_settings` key/value table (V11).

- `SettingsCatalog` = static registry of every known key with metadata:
  category, label, description, type (STRING | SECRET | URL | INT | BOOLEAN |
  INT_LIST), default value, min/max. Categories: PAYMENT, SUBSCRIPTION,
  GROUPS, SECURITY. Adding a knob = one catalog entry, no migration (rows are
  upserted on save; defaults live in code).
- `SettingsService` = the only read/write path: typed getters
  (getBoolean/getInt/getLongList/getString) that fall back to catalog
  defaults on missing row OR malformed value (fail-safe: bad config can never
  crash a request), grouped read view, bulk update, one audit event per save.
- API:
  - GET `/api/v1/admin/settings` -> grouped view
    `{ categories:[{ code, label, settings:[{ key, label, description, type,
    value, configured, defaultValue, min, max, updatedAt }] }] }`.
    SECRET values are NEVER returned: `value=null` + `configured` bool.
  - PUT `/api/v1/admin/settings` body `{ values: { key: value } }` -> upserts
    known keys only (unknown key 400; INT out of range 400; non-bool 400;
    URL must start http(s); INT_LIST normalized `"7, 3, 1"` -> `"7,3,1"`;
    blank SECRET = keep existing). Writes audit event `ADMIN_UPDATE_SETTINGS`
    with `changedKeys`, returns the fresh grouped view.
- Runtime effect (values are read at use time, no restart):
  - PAYMENT: PayWay merchant/api key/urls/sandbox/enabled
  - SUBSCRIPTION: `enforce_subscription`, `free_trial_days` (trial length at
    owner registration), `grace_period_days`, `subscription_reminder_days`,
    `checkout_pending_reuse_minutes` (Step 26 order-reuse window)
  - GROUPS: `default_bid_close_offset_days` (group create default)
  - SECURITY: `access_token_ttl_minutes`, `refresh_token_ttl_days` (per token
    issuance), `rate_limit_register_per_hour`, `rate_limit_login_per_15min`,
    `rate_limit_payway_callback_per_minute` (Step 25)
    (per request). `app.jwt.secret` intentionally stays in env/yml only —
    rotating a signing key is a deployment action, not an admin-UI action.
- Admin UI: `/admin` settings tab is catalog-driven — one card per category,
  inputs rendered per type (toggle/number/password/text), SECRET shows a
  "configured" badge instead of the value, per-category save.

Export (Step 30) — decision 2026-10-08 (user: CSV + real .xlsx via Apache POI):
file downloads that REUSE the Step 15 JSON reports as the data source — same
services, same permissions, same 404s; export adds no new queries, no new math.
New package `com.tongtin.reports.export` (Table builder + CsvRenderer +
XlsxRenderer + ExportService); `org.apache.poi:poi-ooxml` added to pom.xml.
- HOST GET /groups/{id}/export/ledger?format=csv|xlsx
- HOST GET /groups/{id}/export/profit?format=csv|xlsx
- MEMBER GET /me/groups/{id}/export/statement?format=csv|xlsx
  `format` defaults to `csv`; unknown value -> 400. Response is an attachment:
  Content-Type `text/csv;charset=UTF-8` or
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
  filename `tongtin-<kind>-g<groupId>.<ext>`.
- Money is rendered as an exact major-unit decimal string via
  BigDecimal.valueOf(amountMinor, exponent).toPlainString() — integer math
  only (VND exponent 0 yields the plain integer); dates are ISO-8601 UTC text.
  All report JSON money shapes stay as-is; decimal rendering is export-only.
- Columns (header row, then data rows):
  - ledger: groupName, currency, entryId, cycleNo, shareNo, type, direction,
    memberProfileId, memberName, amount, status, dueAt, allocated, remaining;
    trailing rows `type=TOTAL_IN` / `type=TOTAL_OUT` put the report's
    totalIn/totalOut into `amount`.
  - profit: groupName, currency, formulaVersion, cycleNo, status, openedAt,
    closedAt, winnerShareNo, winnerName, winningBid, grossPot, hostFee,
    netPayout, settledHostFee; trailing row `cycleNo=TOTAL` fills grossPot,
    hostFee, netPayout, settledHostFee from report totals (per-cycle
    settledHostFee cell stays empty).
  - statement: groupName, currency, shareNo, shareStatus, entryId, cycleNo,
    type, direction, amount, status, dueAt, allocated, remaining,
    runningBalance; trailing rows `type=TOTAL_CONTRIBUTED | TOTAL_RECEIVED |
    TOTAL_FEES_PAID | TOTAL_NET_POSITION` put the totals into `amount`.
    Per-share totals stay JSON-only; exports carry group totals.
- CSV rules: UTF-8 BOM as first byte (Excel auto-detect), CRLF row endings,
  RFC 4180 quoting (embedded `"` doubled); a NON-NUMERIC cell starting with
  `=`, `+`, `@` or `-` is prefixed with `'` (CSV formula-injection guard,
  OWASP). Numeric cells — which include negative money like `-5000.00` — are
  emitted raw so Excel parses them as numbers.
- XLSX rules: one sheet named after the kind (ledger/profit/statement),
  header row + data rows; a cell whose FULL content parses as a number is
  written as a numeric cell (summable in Excel), everything else as a text
  cell — text cells never evaluate as formulas, so no guard is needed there.
- Statement export is member-scoped: own shares only, and it can never carry
  hostFee/host profit because MemberReportService (the JSON source) already
  excludes them.
- Web: CSV/XLSX buttons on /host/groups/[id]/ledger (ledger), host group
  cycles tab (profit), and the member statement tab; downloads use
  fetch-with-token -> Blob -> anchor click (the API requires a Bearer token,
  so plain links cannot work).

## 6. Frontend pages

Host:
- `/login` `/register/owner`
- `/host` dashboard
- `/host/onboarding`
- `/host/members`
- `/host/groups/new`
- `/host/groups/[id]`
- `/host/groups/[id]/cycle/[cycleId]`
- `/host/groups/[id]/ledger`

Member:
- `/app` my groups
- `/app/groups/[id]` tracker
- `/app/groups/[id]/bid`
- `/app/groups/[id]/statement`

UI language: Vietnamese first, English keys in code.
Currency format: `1.000.000 d`

## 7. Reverse proxy

Next.js rewrites `/api/:path*` to `http://localhost:8080/api/:path*`.
Vite/Next allowedHosts must include `.monkeycode-ai.live` when using the preview env.

## 8. Security

- passwords hashed with BCrypt
- JWT in memory/local storage later moved to httpOnly cookie if needed
- host can only access own tenant (`owner_id`)
- host can only access own groups
- member can only bid own shares
- bids hidden from other members until close
- no formula executed in the browser as source of truth
- audit every money and winner action

## 9. What not to build in MVP

- Zalo/SMS/Telegram gateways
- real bank settlement
- Flutter app
- split microservices
- multi-currency FX
- FIRST_CYCLE_TO_HOST fee type
- RANDOM group type
- AI bidding assistant
- free-form custom formula editor
- global member blacklist
- visual theme marketplace
