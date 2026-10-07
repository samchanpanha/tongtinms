# STATUS

CURRENT_STEP: 19
CURRENT_STEP_TITLE: Subscriptions & ABA PayWay Gateway
PHASE: completed
LAST_UPDATED: 2026-10-07

## Done

- [x] Step 00 Planning artifacts
- [x] Planning docs (domain, architecture, roadmap, workflow, owner-register, governance, multi-currency, formulas, skill, slides, plan.md)
- [x] Step 01 Monorepo skeleton
- [x] Step 02 Database and Flyway (V1 identity)
- [x] Step 03 Identity and owner self-register (V2 owner_accounts)
- [x] Step 04 Member directory (V2.1 member_profiles)
- [x] Step 05 Group draft (V3 currencies + groups)
- [x] Step 06 Shares and READY (V4 group_shares)
- [x] Step 07 Formula engine (pure Java, no HTTP)
- [x] Step 08 Cycle open (V5 cycles)
- [x] Step 09 Sealed bidding (V6 bids)
- [x] Step 10 Close and calculate (V7 ledger_entries)
- [x] Step 11 Payments (V8 payments + payment_allocations)
- [x] Step 12 Host dashboard (GET /host/dashboard)
- [x] Step 13 Member portal (host-set login + GET /me/groups, /me/groups/{id},
      /me/groups/{id}/balance, POST /me/cycles/{id}/bids)
- [x] Step 14 In-app notifications (V9, `com.tongtin.notify`)
- [x] Step 15 Reports (ledger, profit, member statement, public cycle summary)
- [x] Step 16 Hardening (audit coverage, payment idempotency, rounding, authz sweep, demo seeder)
- [x] Step 17 Preview polish (Vietnamese UI copy first, formatMoney 1.000.000 đ, dark mode, host & member portal routes, preview allowedOrigins, start.sh, npm run build & lint clean, 119 tests pass)
- [x] Step 18 Final delivery & wrap-up (All 10 e2e checks passed, audit verification, comprehensive README, DB clean, 18/18 steps 100% complete)
- [x] Step 19 SaaS Subscriptions & ABA PayWay Gateway (Host registration 1-month free trial, ABA PayWay HMAC-SHA512 checkout & KHQR integration per developer.payway.com.kh, admin control panel for gateway configuration and subscription plans, Flyway V11, multilingual support)

## Step 08 acceptance (2026-10-06)

- [x] Doc gate: GET /groups/{id}/cycles added to 02-ARCHITECTURE §5
- [x] V5 cycles: id, group_id, cycle_no (UNIQUE per group), status, currency, open_at, bid_close_at, due_at, winner_share_id, winning_bid, gross_pot, host_fee, net_payout, calculated_at; plus group_shares.won_cycle_id FK now cycles exist
- [x] POST /groups/{id}/cycles/open: first cycle requires group READY -> RUNNING; later cycles require previous SETTLED
- [x] BIDDING groups -> cycle status BIDDING; FIXED and last cycle -> OPEN (bidding skipped)
- [x] 	 guard: cannot open beyond cycleCount; only previous-SETTLED progression
- [x] Schedule (MVP): bid_close_at = open + bidCloseOffset days (default 0); due_at = open + 1/7/30 days by cycleUnit
- [x] GET /groups/{id}/cycles (list), GET /cycles/{id} (owner-scoped)
- [x] Live: open -> cycleNo 1 BIDDING VND, group RUNNING, re-open 400, Flyway V5 applied
- [x] mvn test: 47 tests, 0 failures

## Step 09 acceptance (2026-10-06)

- [x] Doc gate: POST /cycles/{id}/bids and GET /cycles/{id}/summary already in 02-ARCHITECTURE §5
- [x] V6 bids: id, cycle_id, share_id, amount_minor, currency, submitted_at, is_latest;
      partial unique index on (cycle_id, share_id) WHERE is_latest -> one live bid per share, history kept
- [x] POST /cycles/{id}/bids {shareId, amountMinor}: BIDDING cycle only, window open, share ALIVE,
      minBid <= B <= maxBid, B % bidStep == 0; update = new row, old marked is_latest=false (latest wins)
- [x] Sealed: GET /cycles/{id}/summary hides amountMinor + submittedAt before bid_close_at (sealed=true),
      reveals after close (sealed=false)
- [x] Foreign share in another cycle's group -> 403 (ForbiddenException); cross-owner cycle -> 404
- [x] **Decision (scaled back):** host enters bids on behalf of members; per-member write auth
      arrives with the member portal (Step 13) per 05-OWNER-SELF-REGISTER.md
- [x] Live: bid 200000/150000, update 250000 latest wins, sealed hides amounts, foreign 403,
      step/range 400, reveal after close; Flyway V6 applied; DB left clean (0 users/bids/cycles)
- [x] mvn test: 56 tests, 0 failures

## Step 10 acceptance (2026-10-06)

- [x] Doc gate: POST /cycles/{id}/close-and-calculate and POST /cycles/{id}/confirm-payout
      already in 02-ARCHITECTURE §5 (lines 158-159) — no doc change needed
- [x] V7 ledger_entries: id, group_id, cycle_id, share_id nullable, member_profile_id nullable,
      type, direction, amount_minor BIGINT > 0, currency, status, due_at, created_at;
      partial unique (cycle_id, share_id, type) WHERE type IN ('CONTRIBUTION','PAYOUT')
      and (cycle_id) WHERE type='HOST_FEE' as duplicate-close insurance
- [x] POST /cycles/{id}/close-and-calculate: status BIDDING|OPEN only (else 400), pessimistic
      lock on cycle, transient CLOSED_FOR_CALC in-tx then PAYOUT_PENDING, FormulaException -> 400
- [x] Winner: BIDDING -> highest latest B among ALIVE shares; tie per group tieBreak
      (EARLIEST_BID earliest submittedAt, LOWEST_MEMBER_CODE lowest share_no,
      HOST_DECISION requires body {winnerShareId} among tied else 400); no eligible bids -> 400.
      OPEN (FIXED / last cycle) -> first ALIVE share by share_no, B = 0
- [x] Ledger per cycle: CONTRIBUTION IN per paying share (DEAD/DEFAULTED pay C, alive
      non-winner pay C-B, winner skips), HOST_FEE OUT if T > 0, PAYOUT OUT if netPayout > 0;
      all start UNPAID with cycle due_at; invariant SUM(IN) = SUM(OUT) = grossPot
- [x] Winner share ALIVE -> DEAD + won_cycle_id; summary sealed = status BIDDING AND
      now < bidCloseAt, reveals amounts + winner/result fields after close
- [x] POST /cycles/{id}/confirm-payout: PAYOUT_PENDING -> SETTLED (else 400); marks
      PAYOUT + HOST_FEE entries PAID (contributions stay UNPAID until Step 11);
      last cycle also sets group -> COMPLETED
- [x] Decisions: member_profile_id nullable (HOST_FEE has no member); FIXED winner = lowest
      share_no still ALIVE each cycle; LOWEST_MEMBER_CODE compares share_no (no member code
      column); engine D = N - count(ALIVE) so DEFAULTED pays full C and cannot win;
      netPayout = 0 skips the PAYOUT row; no GET /groups/{id}/ledger endpoint in Step 10
- [x] Live: fixture A cycle 1 gross 7_200_000 / fee 100_000 / net 7_100_000, ledger
      9x800000 IN UNPAID + HOST_FEE + PAYOUT (PAID after confirm), SUM(IN)=SUM(OUT)=7_200_000,
      sealed->reveal, repeat close 400, cycle 2 net 7_700_000, Flyway V7 applied, DB left clean
- [x] mvn test: 69 tests, 0 failures (CloseCalculateTests 13 new)

## Step 11 acceptance (2026-10-07)

- [x] Doc gate: POST /groups/{id}/payments and GET /groups/{id}/debts already in
      02-ARCHITECTURE §5 (lines 164-165) — no doc change needed
- [x] V8 payments: payments (amount_minor > 0, method CHECK CASH/BANK_TRANSFER/OTHER
      default CASH, paid_at, note, received_by_host_id FK owner_accounts, currency FK)
      + payment_allocations (amount_minor > 0, unique (payment_id, ledger_entry_id));
      LATE_FEE deferred — no late_fee_type/late_fee_value columns yet
- [x] POST /groups/{id}/payments: HOST only, 404 cross-owner group, currency must equal
      group currency else 409 ConflictException (07-MULTI-CURRENCY §4.5), allocations
      must sum EXACTLY to amountMinor else 400, duplicate entryId 400, entry not in
      group 404, pessimistic lock on entries sorted ascending id (deadlock-safe),
      allocate to non-UNPAID/PARTIAL entry 400, over-allocation 400;
      entry UNPAID/PARTIAL -> PARTIAL (allocated < amount) or PAID (fully covered)
- [x] GET /groups/{id}/debts: HOST only, overdue = due_at < now AND status UNPAID/PARTIAL,
      response {ledgerEntryId, cycleId, cycleNo, shareId, memberProfileId, type,
      direction, amountMinor, allocatedMinor, remainingMinor, currency, status,
      dueAt, overdueDays}; returns [] when nothing overdue
- [x] Decisions (user 2026-10-06): late fees DEFERRED (Step 11 = payments + debts only;
      lateFeeType/lateFeeValue stay unused); sum(allocations) == amountMinor exactly
      (every dong recorded is allocated); method default CASH; paidAt optional ISO
      Instant defaults to now; createdAt set app-side so response is non-null
- [x] Payments are allowed any time (not only when overdue); debts list is read-only
      (payment while overdue is how a debt clears); no idempotency key yet — covered by
      cross-cutting release gate before ship (Step 14-16 territory)
- [x] Live: register -> 3-share group -> cycle settled -> partial 300000 -> PARTIAL ->
      rest 500000 -> PAID (201 both), over-allocate 400, allocate to PAID 400,
      USD 409, debts [] after full payment; backdated due_at 3d shows one debt
      (overdueDays 3, cycleNo 1, allocated/remaining correct, PARTIAL after partial
      payment); Flyway V8 applied (constraints verified), app stopped, DB left clean
- [x] mvn test: 81 tests, 0 failures (PaymentTests 12 new)

## Step 12 acceptance (2026-10-07)

- [x] Doc gate: GET /host/dashboard added to 02-ARCHITECTURE §5 (Host dashboard section)
      BEFORE coding — includes profit definition + money shape + no-cross-currency note
- [x] `com.tongtin.dashboard`: GET /api/v1/host/dashboard (HOST only, owner-scoped)
- [x] Response: `groups[]` = my groups (id, code, name, type, status, currency,
      shareCount, cycleCount, currentCycleNo, currentCycleStatus, nextDueAt,
      unpaidCount, overdueCount, hostProfitMinor) ordered newest-created first
      (reuses GET /groups ordering); `currencies[]` = per-currency aggregate profit
- [x] nextDueAt = earliest due_at among UNPAID/PARTIAL obligations (overdue surfaces
      first — its due_at is earliest); unpaidCount = UNPAID+PARTIAL count (any due
      date), overdueCount = UNPAID/PARTIAL with due_at < now
- [x] Profit DEFINITION (documented §5): hostProfit = SUM(cycles.host_fee) over
      owner's SETTLED cycles only — PAYOUT_PENDING NOT counted (verified live:
      close-but-not-confirm shows 0, after confirm shows fee). Per-group profit =
      that group's settled cycles; per-currency aggregate = money shape
      {currency, amountMinor, exponent, symbol} from `currencies` lookup
      (VND exponent 0 / symbol d, USD exponent 2 / symbol $) — rows kept per
      currency, NEVER summed across currencies (07-MULTI-CURRENCY §3/§4.7)
- [x] Decisions: no cross-currency total field exists at all (DTO has only
      currencies[] + groups[]); zero-profit currencies of owned groups are included;
      N+1 small indexed queries per group (fine at owner scale, noted for later);
      no new migration (reads only); GET /groups/{id}/ledger + /profit untouched
- [x] Live: empty dashboard []; VND FIXED group fee 100000 -> currencies
      [VND 100000 / d], group row currentCycleNo 1 SETTLED, unpaid 2 overdue 1
      nextDue backdated, hostProfit 100000; anonymous 401; payment clears debt ->
      unpaid 1 overdue 0; app stopped, DB left clean
- [x] mvn test: 87 tests, 0 failures (DashboardTests 6 new)

## Step 13 acceptance (2026-10-07)

- [x] Doc gate: §5 updated with "Member login" (POST /members/{id}/set-login +
      shared /auth/login), "Member identity model" (phone -> member_profiles across
      hosts -> group_shares), member balance definition; 05-OWNER-SELF-REGISTER §14
      decision log entry added
- [x] **Decision (user 2026-10-07): member auth = HOST-SET PASSWORD.** Host calls
      POST /members/{id}/set-login {password} to create (or reset) the member's
      `users` row (phone + BCrypt + MEMBER role); no SMS/OTP, no public member
      registration. Members log in through the shared POST /auth/login.
- [x] AuthService generalized: login/refresh/me detect roles via user_roles
      (new UserRoleRepository.findRolesByUserId), ownerId nullable; /me returns
      {user, roles} and omits owner/onboarding for non-owners. Register flow,
      owner/onboarding routes unchanged (93 tests green before portal suite).
- [x] set-login guards: 404 unknown/cross-owner member; 400 password <8 or == phone;
      409 phone already belongs to a HOST account; 409 second attempt with same phone
      when it could join another member (hosts are unique, MEMBER can be re-set);
      audit events MEMBER_LOGIN_CREATED / MEMBER_LOGIN_RESET
- [x] Member identity: member sees a group iff some member_profiles(phone) holds a
      share there; may hold shares in several hosts' groups. GET /me/groups[] (id,
      code, name, type, status, currency, shareCount, cycleCount, currentCycleNo,
      currentCycleStatus, myShareCount, myShares[{id, shareNo, status}]); unknown or
      non-member group -> 404
- [x] GET /me/groups/{id}/balance (01-DOMAIN §9.5): contributed = SUM allocations
      toward CONTRIBUTION entries; received = SUM PAID PAYOUT entries; feesPaid =
      SUM allocations toward LATE_FEE/OTHER_FEE; netPosition = received - contributed
      - feesPaid; share-level breakdown; currency on the row (no cross-currency math)
- [x] POST /me/cycles/{id}/bids: MEMBER role, MUST own the share else 403, reuses
      BIDDING-cycle rules (min/max/step, latest-wins) via BidService.placeBid
- [x] New repo queries: GroupShareRepository.findByGroupIdAndMemberProfileIdIn /
      findByMemberProfileIdIn, LedgerEntryRepository.sumAmountByGroupAndShareInAndTypeInAndStatus,
      PaymentAllocationRepository.sumAllocatedByGroupAndShareInAndTypeIn
- [x] Live: set-login 200 (loginEnabled), member login roles=[MEMBER] owner=null,
      /me without owner/onboarding, me/groups 1 group RUNNING/BIDDING myShareCount 1,
      balance zeros, bid own 201, bid other 403, below-min 400, dup host-phone 409,
      member -> host dashboard 403, anonymous /me/groups 401; app stopped, DB clean
- [x] mvn test: 93 tests, 0 failures (MemberPortalTests 6 new)

## Step 14 acceptance (2026-10-07)

- [x] Doc gate: 02-ARCHITECTURE §5 "Notifications (Step 14)" section — table shape,
      `com.tongtin.notify` module, recipients, event list, endpoints, failure invariant
- [x] **Decision (user 2026-10-07): in-app feed only.** New `notifications` table
      (V9) per 02-ARCHITECTURE §4 — no push/webhook, no scheduler.
- [x] **Decision (user 2026-10-07): sync transitions only.** No @Scheduled reminders
      (T-24h/T-2h/overdue) yet; only events fired on host actions.
- [x] V9 notifications: id, user_id FK users, type VARCHAR(32), title VARCHAR(120),
      body VARCHAR(500), read_at, created_at DEFAULT now(); index (user_id,
      created_at DESC)
- [x] Recipients = USERS with logins only: member_profile.phone -> users.phone,
      deduped per user; paper profiles (no users row) skipped silently; host is
      never notified (acts offline)
- [x] Invariant: notification failure never mutates financial state —
      NotificationService.create swallows+logs per row; events are written inside
      the same business transaction as the financial change (commit/roll back together)
- [x] Events (Vietnamese copy, same tx as the action): CYCLE_OPENED (all members in
      group on open), WINNER_PUBLISHED (all members in group on close-and-calculate),
      PAYOUT_READY (winner on confirm-payout), PAYMENT_CONFIRMED (payer members who
      paid, on POST /payments), GROUP_COMPLETED (all members when last cycle confirmed
      -> COMPLETED), MEMBER_LOGIN_NOTICE (member on set-login create or reset)
- [x] Endpoints (any authenticated user, own feed only): GET /notifications ->
      {notifications[], unreadCount} newest first cap 100; GET /notifications/unread-count;
      POST /notifications/{id}/read (404 if not own); POST /notifications/read-all ->
      {updatedCount}
- [x] Wiring: CycleService.open, CycleCloseService (close+confirmPayout), PaymentService.record,
      MemberService.setLogin (+ helper of() in CycleService)
- [x] Live: set-login -> MEMBER_LOGIN_NOTICE, open -> CYCLE_OPENED, close ->
      WINNER_PUBLISHED, confirm -> PAYOUT_READY (winner only), payment ->
      PAYMENT_CONFIRMED (payer only), late-provisioned member gets NO retroactive
      rows, host feed empty, cross-member read 404, read-all + unread-count, anonymous
      401; app stopped, DB clean
- [x] mvn test: 99 tests, 0 failures (NotificationTests 6 new; MemberPortalTests
      cleanup updated for notifications FK)

## Step 15 acceptance (2026-10-07)

- [x] Doc gate: 02-ARCHITECTURE §5 "Reports (Step 15)" — scope confirmed with user
      via Questions (all three reports; host ledger + profit, member statement,
      public cycle summary); JSON only, CSV/Excel parked P2
- [x] **Decision (user 2026-10-07): every monetary field uses the money shape**
      `{currency, amountMinor, exponent, symbol}` from 07-MULTI-CURRENCY §5 — no
      bare amounts; pure counters and dates stay scalars
- [x] `com.tongtin.common.money.Money` (+ `MoneyLookup` caching `currencies` rows
      at boot); `com.tongtin.reports` module with ReportService (host),
      MemberReportService (member identity = phone across hosts), ReportController,
      MemberPortalController additions
- [x] HOST GET /groups/{id}/ledger -> {groupId, groupName, currency, entries[],
      totalIn, totalOut (money)}; entries sorted (cycleNo, shareNo nulls-last,
      entryId): PAYOUT, CONTRIBUTION (per paying share), HOST_FEE; allocated =
      SUM payment_allocations, remaining = amount - allocated (floor 0);
      HOST_FEE carries shareNo/memberProfileId/memberName NULL
- [x] HOST GET /groups/{id}/profit -> {groupId, groupName, currency,
      formulaVersion (= FormulaEngine.ENGINE_VERSION, no V10 migration needed),
      cycles[], totals{grossPot, hostFee, netPayout, settledHostFee}}; null
      amounts until calculated; settledHostFee counts status=SETTLED only
- [x] MEMBER GET /me/groups/{id}/statement -> own shares only (404 if not a
      member): per-share entries + runningBalance + totals{contributed, received,
      feesPaid, netPosition} per 01-DOMAIN §9.5 (runningBalance: OUT adds full
      amount, IN subtracts allocated)
- [x] MEMBER GET /me/groups/{id}/cycles -> PUBLIC cycle summaries (raw array):
      cycleNo, status, openAt, bidCloseAt, dueAt, winner{shareNo, memberName},
      winningBid, grossPot, netPayout — **hostFee and host profit (incl. any
      totals node) never appear** (01-DOMAIN §14), verified by negative test
- [x] Formula-verified fixture: N=3 FIXED_PER_CYCLE 50k, B=200000 ->
      grossPot 1.6M, hostFee 50k, netPayout 1.55M; ledger
      PAYOUT 1.55M PAID / CONTRIBUTION 800k PAID after 800k payment /
      CONTRIBUTION 800k UNPAID / HOST_FEE 50k; totalIn == totalOut == 1.6M;
      member1 netPosition 1.55M, member2 -800k
- [x] Authz: cross-tenant host 404, anonymous 401, member on host report 403,
      host on member statement 403, member on non-member group 404 (all statuses
      verified live)
- [x] Live e2e: register 2 hosts -> 3-member share group -> open/bid/close/confirm
      -> payment -> ledger (4 rows, money shapes), profit (formulaVersion 1,
      totals + settledHostFee), member1 statement net 1.55M, member2 net -800k,
      public cycles clean of hostFee/hostProfit/totals, 7 authz status checks;
      app stopped, DB left clean (0 users)
- [x] mvn test: 107 tests, 0 failures (ReportTests 5 + MemberReportTests 3 new)

## Step 16 acceptance (2026-10-07)

- [x] Doc gate: 02-ARCHITECTURE §5 "Hardening (Step 16)" section (audit events, payment
      idempotency key, rounding tests, cross-tenant authz sweep, demo seeder).
- [x] Audit trail: `AuditService` + all 5 services wired to thread authenticated `actorUserId`
      (host or member). Events: CYCLE_OPENED, BID_SUBMITTED (host + member portal),
      CYCLE_SETTLED, PAYOUT_CONFIRMED, PAYMENT_RECORDED. Invariant: failure never mutates
      financial state (same tx commit/rollback, serialization degradation to `{}`).
- [x] Payment idempotency key (release gate "retrying never duplicates money"): Flyway V10
      `payments.idempotency_key` with partial unique index `(group_id, idempotency_key)`.
      Optional `Idempotency-Key` header on `POST /groups/{id}/payments`. Same key + same payload
      replays 200 OK returning existing payment without writing new rows; same key + different
      payload returns 409 Conflict; concurrent submit unique constraint violation returns 409.
- [x] Money rounding boundary tests: `RoundingTests` testing `roundBps` with explicit HALF_UP
      boundaries (ties round away from zero, integer minor units, exact bps math).
- [x] Cross-tenant authz sweep tests: `AuthzSweepTests` matrix across all money, cycle, bid,
      ledger, report endpoints verifying other host (404), member role (403), anonymous (401).
- [x] Demo seeder (`DemoSeeder`, `--app.seed-demo=true`): reproduces N=10 BIDDING fixture A
      (C=1_000_000, T=100_000) through real domain services playing 10 cycles to COMPLETED:
      cycle 1 B=200k -> 7.1M; cycle 2 B=150k -> 7.7M; cycles 3-9 B=100k; cycle 10 B=0 -> 8.9M.
      Idempotent: skips if demo host already exists. Normal DB clean removes all demo rows.
- [x] Live e2e: full live verification (idempotency key create 201, replay 200, mismatch 409,
      no duplicate DB rows; audit rows present for host & member portal; seeder reproduces
      C1 7.1M, C2 7.7M, C10 8.9M, 1M host profit; cross-tenant 404 / 403 / 401).
      App stopped, DB left clean (0 rows).
- [x] mvn test: 119 tests, 0 failures (12 new tests: PaymentIdempotencyTests 5,
      AuditTrailTests 3, RoundingTests 1, AuthzSweepTests 3).

## Step 17 acceptance (2026-10-07)

- [x] Doc gate: 02-ARCHITECTURE §5 Step 17 (Preview polish) documented: Vietnamese UI copy first,
      money formatting, theme support, full host & member page map, preview origins, start script.
- [x] Vietnamese UI copy & formatting (`lib/format.ts`):
      `formatMoney` renders VND as `1.000.000 đ`, USD as `$1,000.00`, KHR as `1,000,000 ៛`;
      natural Vietnamese labels for all domain terms (Hụi / Thảo / Bỏ thăm / Quyết toán);
      status badges for groups, cycles, shares.
- [x] Modern web aesthetics: rich dark mode support, glassmorphism headers, Tailwind CSS v4,
      reusable `StatCard`, `StatusBadge`, `EmptyState`, loading states and interactive modals.
- [x] Full Frontend Routes in `apps/web` (Next.js 16 App Router):
      - `/`: Landing page with feature highlights and role portals
      - `/login`: Unified login with one-click demo credentials for Host & Member
      - `/register/owner`: Chủ Hụi (Host) self-registration wizard
      - `/host`: Chủ Hụi dashboard (KPIs, profit per currency, group management)
      - `/host/members`: Member directory (search, create member profile, set login password)
      - `/host/groups/new`: Group creation wizard with live pot calculation preview
      - `/host/groups/[id]`: Group lifecycle console (shares assignment, bidding, manual payments)
      - `/host/groups/[id]/ledger`: Balanced double-sided ledger (IN/OUT exact match)
      - `/app`: Cổng Hội Viên (Member portal) showing joined groups & active statuses
      - `/app/groups/[id]`: Sealed bidding submission & personal financial statement
      - `/notifications`: In-app notification center with read/unread tracking
- [x] Preview environment support: `next.config.ts` configured with `allowedOrigins: ["*.monkeycode-ai.live", "localhost:3000"]`.
- [x] All-in-one startup script: executable `start.sh` at repo root with PostgreSQL check,
      Spring Boot API launch, Next.js Web launch, `--seed` option, and clean Ctrl+C shutdown.
- [x] Build & lint verification: `npm run lint` 0 errors/warnings, `npm run build` compiled 11/11 routes.
- [x] Backend test suite: `mvn test` 119/119 tests passing (0 failures).
- [x] Database state: left completely clean (0 users, 0 groups, 0 audit events).

## Step 18 acceptance (2026-10-07)

- [x] Doc gate: 02-ARCHITECTURE §5 Step 18 (Final delivery & wrap-up) documented.
- [x] End-to-end verification (`test_e2e_delivery.py`):
      1. Health check: API UP
      2. Host login (`0900111001` / `demo1234`): JWT issued, role HOST
      3. Member login (`0900100001` / `demo1234`): JWT issued, role MEMBER
      4. Host dashboard: active groups, KPIs, next due dates
      5. Double-sided ledger: strictly balanced (`totalIn == totalOut == 84_200_000 VND`)
      6. Host profit: formula v1, settled host fee `1_000_000 VND`
      7. Member portal: joined groups, share statuses
      8. Member statement: net position `7_100_000 VND`, received `7_100_000 VND`
      9. In-app notifications: 23 notifications tracked for cycles, winners, and payouts
      10. Confidentiality invariant: member public cycle summary verified clean (zero host fee/profit leaks)
      All 10/10 acceptance tests passed!
- [x] Audit trail verification: `audit_events` table confirmed tracking 41 events
      (CYCLE_SETTLED, CYCLE_OPENED, PAYOUT_CONFIRMED, BID_SUBMITTED, OWNER_REGISTERED, MEMBER_LOGIN_CREATED).
- [x] Comprehensive documentation: `README.md` updated with full system architecture,
      core mathematical invariants, quickstart guide, demo credentials, routes map, and verification logs.
- [x] Database state: left completely clean (0 users, 0 groups, 0 audit events).
- [x] Build status: 18/18 steps (Step 00 through Step 18) 100% COMPLETE.

## Post-delivery audit & SaaS hardening (2026-10-07)

- [x] Full audit: backend `mvn test` green, frontend lint/build clean, then a manual
      security review of the Step 19 subscription/PayWay layer
- [x] **Critical: payment bypasses closed** — `simulate-complete` returns 403 outside sandbox
      mode; public webhook callback activates ONLY after ABA check-transaction confirms
      APPROVED (never from URL params); `POST /subscription/verify/{tranId}` enforces order
      ownership (foreign host 403); `tran_id` now `TT_<ownerId>_<uuid10>` (unguessable);
      checkout blocked (400) when `payway_enabled = false`
- [x] **Policy fixes** — new `SubscriptionGuard` (single policy point): `enforce_subscription`
      switch, `grace_period_days` setting (was hardcoded 3 days), plan `max_groups` limit
      (was never enforced); `GroupService.create` delegates to it
- [x] **Correctness/perf** — `PayWayService.formatAmount` uses BigDecimal (zero-float
      invariant restored on the payment path); CORS allows `Idempotency-Key` (Step 16
      replays work from the Next.js origin); `GroupRepository.countByOwnerId` replaces
      findAll scans in status/admin queries
- [x] **Improvement implemented** — `SubscriptionLifecycleJob` + `@EnableScheduling`:
      runs daily 08:00; skipped when enforcement is off; SUBSCRIPTION_EXPIRING reminders
      at 7/3/1 days remaining; auto-expires owners past `subscription_ends_at + grace`
      (status → EXPIRED + SUBSCRIPTION_EXPIRED notification, never re-notified);
      host-billing scope only (member group events keep the Step 14 sync-only decision)
- [x] Tests: +5 security + 1 lifecycle in `SubscriptionAndPayWayTests` (now 13 in class);
      full suite **132 tests, 0 failures**; frontend untouched
- [x] `plan.md` §7 records the fixes + ranked roadmap of analyzed ideas (real gateway
      return-flow wiring, `payment_events` forensics, checkout idempotency, index, ...)

## Next action

All 19 steps delivered AND the post-delivery audit is complete (2026-10-07, `plan.md` §7).
To run the full system with pre-seeded demo data anytime:
```bash
./start.sh --seed
```
Next improvement candidates (user picks — see `plan.md` §7.5): wire real PayWay return
flow to `POST /subscription/verify/{tranId}`, `payment_events` forensics, checkout
idempotency, `owner_accounts(subscription_ends_at, subscription_status)` index.

## Blockers

None.

## Demo fixture (matches 08-FORMULA-ENGINE fixture A)

N=10, C=1000000, T=100000, BIDDING
Cycle1 B=200000 netPayout=7100000
Cycle2 B=150000 netPayout=7700000
Last B=0 netPayout=8900000