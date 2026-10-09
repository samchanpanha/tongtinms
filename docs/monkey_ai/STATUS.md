# STATUS

CURRENT_STEP: DONE — Steps 00–42 complete
CURRENT_STEP_TITLE: Phase 3 complete — Invoice, Member Bids, Random Equal-Max Winner, Logged-in Redirect, Report Pagination & Filtering
PHASE: complete
LAST_UPDATED: 2026-10-09

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
- [x] Step 20 Centralized Settings Module (catalog-driven `com.tongtin.settings`: 15 keys in 4 categories over `system_settings`, ADMIN-only `/api/v1/admin/settings` GET/PUT with per-type validation + SECRET masking + audit, admin UI tab rewritten from the catalog, 8 consumers rewired to read settings live, `AdminSettingsTests` 9 new → 141 tests pass)
- [x] Step 21 Full-stack Docker deployment (multi-stage `apps/api/Dockerfile` + `apps/web/Dockerfile`, Compose now runs postgres + api + web with healthcheck-gated startup order, Next.js standalone build, `API_INTERNAL_URL` build arg, `.env.example`, opt-in `APP_SEED_DEMO`; 3 containers healthy, health UP direct + proxy, login e2e 200)
- [x] Step 22 Release-gate audit (plan.md §3 all 10 cross-cutting gates now ticked with test evidence; new `ReleaseGateTests` 4 tests; suite 145 pass)
- [x] Step 24 Real ABA PayWay return flow (absolute same-origin return URLs composed server-side with tran_id, gateway-return handling on /host/subscription with authoritative re-verify; suite 150 pass)
- [x] Step 25 payment_events forensics + callback rate limit (V12 append-only payment_events journaling CALLBACK/CHECK exchanges, per-IP sliding-window 429 on /payments/payway/callback via rate_limit_payway_callback_per_minute setting; suite 156 pass)
- [x] Step 26 checkout idempotency (repeated checkout for same owner+plan reuses the most recent PENDING order within checkout_pending_reuse_minutes — same tran_id/req_time, hash rebuilt; PAID/expired/other-plan orders never reused; suite 161 pass)
- [x] Step 27 subscription index + admin insights (V13 idx_owner_sub_ends_status for the lifecycle-job scan; GET /admin/insights = PAID revenue per plan + per-currency totals + registration cohort/churn counts; new Insights admin tab; suite 165 pass)
- [x] Step 28 Testcontainers hermetic suite (tests no longer hit the dev DB — a singleton postgres:16 container per JVM is started by a spring.factories ApplicationContextInitializer and Flyway migrates from scratch; suite 165 pass WITH dev postgres stopped)
- [x] Step 29 Late fees (V14 idx_ledger_late_fee_cycle_share + LATE_FEE ledger rows — host-triggered POST /groups/{id}/late-fees/assess, cumulative delta idempotency, CONTRIBUTION obligations only; suite 179 pass)
- [x] Step 30 CSV/Excel export (poi-ooxml 5.5.1; CSV with BOM + RFC 4180 quoting + formula-injection guard, real .xlsx via Apache POI; ledger + profit + member statement; suite 184 pass)
- [x] Step 31 Wrap-up & cleanup (stale docs refreshed: README badges 3.5.5/184 tests + module tree + 14 migrations + roadmap rows 19-31, 00-INDEX rewritten, 02-ARCH §3 module tree as-built, 03-BUILD-ROADMAP post-MVP track appended; Docker compose rebuilt + clicked through live; final suite 184 + `npm run build` 13/13) — see acceptance below
- [x] Step 32 Attachments (Phase 2 #1) — BYTEA receipts on payments + member docs (V15), HOST upload/stream/delete, `GET /groups/{id}/payments` history, SettingsCatalog storage limits, host + member UI; suite **191 pass** — see acceptance below
- [x] Step 33 Blacklist + enforced member status (Phase 2 #2) — `member_blacklists` (V16, owner-local phone list, soft unlist), HOST Blacklist manager API + UI, BLOCKED → no login / INACTIVE·BLOCKED → no bid, blacklisted phone rejected on add-member/add-share; suite **196 pass** — see acceptance below
- [x] Step 34 KHQR per obligation (Phase 2 #3) — real EMVCo CRC16-CCITT merchant-presented QR per unpaid obligation (`TONGTIN <code>-O<entryId>`), `khqr` on debts + statement rows, `payments_khqr_enabled` setting, PNG render endpoint (host own group / member own share, image/png), QR modal in host debts + member statement; suite **206 pass** — see acceptance below
- [x] Step 35 Telegram per-host chat + cycle events + daily due-digest (Phase 2 #4) — `V17 owner_accounts.telegram_chat_id`, TELEGRAM settings category (bot token / events / digest toggle + time), host-only chat-id link + test-send, six event hooks (CYCLE_OPENED, WINNER_PUBLISHED, PAYOUT_CONFIRMED, GROUP_COMPLETED, PAYMENT_RECORDED, LATE_FEE_ASSESSED), every-minute gated digest job, hermetic suite (mock client, zero network); suite **213 pass** — see acceptance below
- [x] Step 36 Quick-pay single-step settlement (Phase 2 #5) — `POST /groups/{id}/quick-pay`: host types a member + total, backend auto-allocates oldest-due-first (then entry id) across the member's UNPAID/PARTIAL direction-IN obligations incl. LATE_FEE (opt-out flag), runs the SHARED core of `PaymentService.record()` (`recordCore`, audit `autoAllocated`), normal `payments` row + idempotency replay; host UI modal with member totals + optional receipt attach; suite **224 pass** — see acceptance below
- [x] Step 37 Wrap-up & close-out (Phase 2 #6) — full docs pass, final `mvn package` BUILD SUCCESS (suite 224/0), `npm run build` 14/14, Docker compose verified.
- [x] Step 38 Payment Invoice — `GET /groups/{id}/payments/{paymentId}/invoice` JSON endpoint with issuer host bank info, payer member info, itemized obligation allocation lines; printable HTML invoice page at `/host/groups/[id]/payments/[pid]/invoice` with print CSS and browser PDF export; "Hoá đơn" link button in group payments tab.
- [x] Step 39 Member Bids UI Polish — Sealed bidding UI in member portal (`/app/groups/[id]`) with deadline pill (`bidCloseAt`), confidentiality security badge, input validation, and clear submission confirmation.
- [x] Step 40 Random / Equal-Max Winner Selection — `CloseCalculateRequest.winnerSelectionMode = "RANDOM_EQUAL_MAX"`: host selects lottery mode in close cycle modal; backend picks uniformly at random among all ALIVE shares, treats winning bid as group `maxBid`, and executes ledger formulas accordingly.
- [x] Step 41 Disable Landing Page for Logged-In Users — Landing page (`/`) checks auth token & roles on mount; automatically redirects logged-in HOST to `/host`, MEMBER to `/app`, and ADMIN to `/admin` without flashing public marketing content.
- [x] Step 42 Pagination & Filtering for Reports — Paginated + filtered `GET /groups/{id}/ledger` with optional query params (`cycleId`, `type`, `status`, `page`, `size`) and `PageMeta` envelope; responsive filter toolbar and pagination controls in host ledger page (`/host/groups/[id]/ledger`); full backward compatibility.
- [x] New comprehensive tests in `Phase3FeatureTests.java` covering Steps 38, 40, and 42; release-gate zero-float invariant maintained; suite **233 pass**.

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

## Step 20 acceptance (2026-10-07)

- [x] Doc gate: `02-ARCHITECTURE.md` §4/§5 updated with the `settings` module + `GET/PUT /api/v1/admin/settings` before code
- [x] Config audit first: every configuration surface mapped (PayWay credentials, subscription policy, lifecycle reminders, trial length, token TTLs, rate limits, group default offset) — `plan.md` §8.1
- [x] Catalog-driven design: `SettingsCatalog` = single place a key is declared (15 keys / 4 categories: PAYMENT 6, SUBSCRIPTION 4, GROUPS 1, SECURITY 4); rows created on first save — no migration per new key
- [x] `SettingsService`: fail-safe typed reads (`getString/getBoolean/getInt/getIntList`, DB/corrupt value → catalog fallback); `update` normalizes per type (bool, int ± range, URL scheme, int-list dedupe DESC), 400 on unknown key / empty payload / invalid value; blank SECRET = keep stored; audit `ADMIN_UPDATE_SETTINGS` with changed keys
- [x] SECRET never exposed: GET masks value + defaultValue, exposes `configured` only
- [x] Consumers read settings live: PayWayService, SubscriptionService, SubscriptionGuard, SubscriptionLifecycleJob, AuthService (trial), GroupService (default bid-close offset), JwtService (TTLs), AuthRateLimitFilter (per-request limits)
- [x] Admin UI: `/admin` settings tab rewritten — per-category cards from the API, per-category save, secret keep-hint, configured/default badges; i18n 4 locales (dead PayWay keys removed)
- [x] Legacy `GET/PUT /admin/settings/payway` kept (Step 19 tests depend on it)
- [x] `AdminSettingsTests` (9 new): grouped view 4/4 + masking; anon 401 / host 403 / admin 200 secret-free; 8 × 400 invalid payloads (+1 over HTTP); normalization; grace 5 honored by guard (−4d ok / −6d 403); free_trial 10 → registration expiry; default offset 5 applied on create; reminder day 2 → lifecycle notification; TTL 45 → login `expiresIn` 2700; blank secret unchanged
- [x] `mvn test`: **141 tests, 0 failures** (132 + 9); frontend `tsc` + `eslint` + `npm run build` clean
- [x] Live browser pass over the rewritten admin tab — DONE 2026-10-07 during the i18n verification pass
      (admin settings tab exercised in km/en/vi/zh on a production build; dev admin `0988999999`)

## Step 21 acceptance (2026-10-07)

- [x] Doc gate: no domain/API change (deployment only); `README.md` gained the Vietnamese "Triển Khai Toàn Bộ Bằng Docker" section; `plan.md` §9 records the step
- [x] `apps/api/Dockerfile`: multi-stage (maven:3.9-eclipse-temurin-21 build → eclipse-temurin:21-jre-alpine runtime), non-root `spring` user, `JAVA_OPTS` entrypoint, HEALTHCHECK on `/api/v1/health`
- [x] `apps/web/Dockerfile`: multi-stage (node:22-alpine deps → build → runtime), copies `.next/standalone` + `.next/static` + `public`, non-root `node` user, HEALTHCHECK on `/login`
- [x] `next.config.ts`: `output: "standalone"` only when `NEXT_STANDALONE=1` — local `next dev` / `next start` unchanged
- [x] `docker-compose.yml`: postgres (named volume `tongtin_pgdata`, existing dev data preserved) + api (env-driven DB) + web; `depends_on` gated on healthchecks (`postgres → api → web`); `restart: unless-stopped`
- [x] Next.js rewrites are build-time: web image takes `API_INTERNAL_URL` (default `http://api:8080`) as build ARG — documented in README/.env.example (rebuild required to change)
- [x] Seeding opt-in: `JAVA_OPTS: -Dapp.seed-demo=${APP_SEED_DEMO:-false}` — plain `docker compose up` never seeds demo data
- [x] `.dockerignore` for both apps; `.env.example` documents `JWT_SECRET` / `APP_SEED_DEMO` / `API_INTERNAL_URL`
- [x] Build: both images built clean (`tongtin-api` jar repackaged, `tongtin-web` 332 MB)
- [x] `docker compose up -d --wait`: all 3 containers `(healthy)`
- [x] Health: direct `http://localhost:8080/api/v1/health` → UP; via web proxy `http://localhost:3000/api/v1/health` → UP
- [x] Login e2e via proxy: `POST http://localhost:3000/api/v1/auth/login` (`0900111001`/`demo1234`) → HTTP 200 user+owner JSON (browser → web → api → postgres proven)
- [x] API logs: Flyway on `jdbc:postgresql://postgres:5432/tongtin` (PostgreSQL 16.15), `Started TongTinApplication in 3.777 seconds`, no seeder logs (off by default)
- [x] Frontend `npx tsc --noEmit` clean; local dev flow (`start.sh`) unaffected
- [x] In-browser click-through — DONE 2026-10-07 (fresh production build on `localhost:3001`,
      browser tooling); web image rebuilt afterwards so the stack serves the same code
- [x] Nothing committed (standing rule); stack left running — stop with `docker compose down`

## i18n full-coverage pass (2026-10-07)

> Requested: "help me fix errors and issues and switch languages some not translate."
> Frontend-only (no backend/domain/API change — doc gate not triggered). See `plan.md` §10.

- [x] Untranslated strings fixed across all pages: landing, host dashboard, host member
      directory, group flows, notifications, `/host/subscription`, admin Plans/Hosts tabs
      + extend modal; backend `SettingsCatalog` labels overridden via i18n maps with
      `?? apiValue` fallback (no API change)
- [x] **Bug fixed — page title reverted to Khmer**: Next.js re-applies the static root
      metadata title on hydration/route changes; `LanguageProvider` now re-asserts
      `document.title` (MutationObserver on `<head>`) + `documentElement.lang`
- [x] Verified live in km/en/vi/zh (production build, browser tooling): content per locale
      on landing, host dashboard, subscription, notifications, admin settings/plans/hosts;
      title persists across reload and client-side nav in vi and zh (the previously
      reverting cases)
- [x] Date/money formatting confirmed correct for all 4 `dateLocale`s (headless Chromium
      lacks km-KH ICU data — environment artifact, not an app bug)
- [x] `tsc --noEmit` + `eslint` + `npm run build` (13/13 routes) clean
- [x] Web Docker image rebuilt + recreated: `http://localhost:3000` serves the fixes
      (healthy, proxy health UP, zh chunks present in image)

## Step 22 acceptance (2026-10-08)

- [x] Doc gate: no domain/API change (audit + tests only); `plan.md` §3 cross-cutting release gates
      rewritten with per-gate evidence and all ticked
- [x] Baseline verified first: `mvn test` **141/141 green** (required starting the dev PostgreSQL
      container — the Step 21 stack was down; `docker compose up -d --wait postgres`)
- [x] Audit result — gates already covered by existing suites: sealed-bid confidentiality
      (`SealedBiddingTests` + `AuthzSweepTests` 403 matrix), tie-break by stored rule (3
      `CloseCalculateTests`), idempotency (Step 16), notification failure isolation (Step 14),
      reversal/audit trail (Step 16), currency lock (`PaymentTests.currencyMismatchIs409` + DB FK),
      rules freeze (`GroupDraftTests.draftCanBeEditedButFrozenGroupCannot`)
- [x] New `ReleaseGateTests` (4 tests) formalizes the previously unchecked gates:
      1. `closedBidCannotBeEdited` — resubmit after `bid_close_at` → 400; stored bid row untouched
         (1 row, latest amount 200000)
      2. `payoutReportShowsFullBreakdown` — cycle result scalars (grossPot 1.6M / hostFee 50k /
         netPayout 1.55M / winnerShareId / winningBid) + `GET /groups/{id}/profit` money shape with
         `formulaVersion ≥ 1`, winner identity (shareId/shareNo/memberName), deduction identity
         gross − fee == net, `settledHostFee` 50k
      3. `memberStatementIsReproducibleFromLedgerEvents` — contributed/received/feesPaid/netPosition
         and the full runningBalance chain independently recomputed from `ledger_entries` +
         `payment_allocations` match the API for winner AND payer (01-DOMAIN §9.5)
      4. `mainSourceContainsNoFloatOrDoubleMoneyTypes` — walks `src/main/java`, zero `double`/`float`
         keyword matches (grep-level zero-float gate, regression-proof)
- [x] Fixes found during the step: `CycleResponse` returns raw minor-unit scalars (money shape only
      in report DTOs) — test corrected to match; cleanup SQL paren typo in the new test class;
      leftover test users from a failed run purged (demo + admin rows preserved)
- [x] `mvn test`: **145 tests, 0 failures** (4 new)
- [x] Deferred as planned (post-MVP, documented): rules version + member acknowledgement beyond the
      freeze; payout "rounding" line (engine v1 rounds HALF_UP to integer minor units — covered by
      `RoundingTests`, no separate field)

## Step 24 acceptance (2026-10-08)

- [x] Doc gate: `02-ARCHITECTURE.md` §5 Step 19 — return-flow contract written BEFORE code
      (absolute URLs from web Origin, server-owned status/tran_id params, verify-on-return)
- [x] Gateway redirect params unconfirmed in ABA docs (Purchase API only names
      `return_url`/`cancel_url`/`continue_success_url`/`return_params`), so the flow trusts
      NOTHING in the query string — landing with `tran_id` only triggers
      `POST /subscription/verify/{tranId}`, activation still requires gateway
      checkTransaction APPROVED
- [x] Backend `SubscriptionService.checkoutPayWay(+requestOrigin)`:
      `resolveReturnBase` — absolute client URL must be http/https, no userinfo, host+port
      must equal the `Origin` header (400 `Return URL origin does not match request origin`);
      path-only resolves against Origin; query containing `status=`/`tran_id=` rejected;
      no Origin (tests/curl) keeps relative default `/host/subscription`
- [x] Composed: `return_url` = base + `?status=success&tran_id=...`,
      `continue_success_url` = base + `?status=success&tran_id=...`,
      `cancel_url` = base + `?status=cancelled&tran_id=...` (hash re-signed after composition);
      `SubscriptionController` reads the `Origin` request header; 3-arg service overload kept
- [x] Frontend `/host/subscription`: checkout sends `returnUrl = window.location.origin +
      /host/subscription`; on mount parses `?status&tran_id` (StrictMode-safe ref guard),
      strips the query via `history.replaceState`, shows verifying →
      `verifySubscriptionOrder(tranId)` → success (reload status/invoices) / failed banners;
      `status=cancelled` shows a cancel notice without calling verify
- [x] i18n: `paymentCancelled` added to `types.ts` + en/km/vi/zh locales
- [x] New tests (5): absolute return URLs carry tran_id (service + form field), Origin-derived
      defaults, cross-origin URL 400, smuggled status/tran_id params 400,
      checkout endpoint composes from `Origin` header (MockMvc)
- [x] `mvn test`: **150 tests, 0 failures** (145 + 5); web `eslint` + `tsc --noEmit` clean
      (fixed `react-hooks/set-state-in-effect` by deferring the return handler one tick)

## Step 25 acceptance (2026-10-08)

- [x] Doc gate: `02-ARCHITECTURE.md` §4 (`payment_events` table + index) and §5 callback bullet
      (rate limit + journaling contract) written BEFORE code
- [x] V12 `payment_events`: id, tran_id nullable, source (CALLBACK | CHECK), outcome, payload TEXT
      (raw JSON, app-truncated to 8000 chars), remote_ip nullable, created_at default now;
      index (tran_id, created_at DESC); applied to dev DB (flyway rank 12 verified)
- [x] Append-only by structure: `PaymentEventRepository` extends marker `Repository` and declares
      only `save` + finders — no update/delete Spring Data method can be generated;
      `PaymentEventService.record` is the single write path (failure-swallowing like the
      notification invariant — forensics can never break the payment flow)
- [x] Journaled exchanges: callback controller writes exactly one CALLBACK row per invocation with
      its final outcome (RATE_LIMITED / MISSING_TRAN_ID / ORDER_&lt;status&gt; / ERROR, payload =
      request envelope incl. error on failure, remote IP via XFF-first);
      `verifyAndActivateViaGateway` writes a CHECK row (APPROVED / NOT_APPROVED) with the raw
      check-transaction response before deciding — covers webhook AND host return-page verify
- [x] Rate limit: `CallbackRateLimiter` in-memory sliding window per IP, default 60/min from
      `app.rate-limit.callback-per-minute`, live-overridable via new SECURITY catalog key
      `rate_limit_payway_callback_per_minute` (numeric 1–1000, admin UI auto-picked-up);
      exceeded → 429 + RATE_LIMITED event; i18n labels/descriptions added in en/km/vi/zh
- [x] New `PaymentForensicsTests` (6): CALLBACK+CHECK rows for a real callback (payload, IP,
      outcome, created_at), MISSING_TRAN_ID journaling, direct gateway verify journals CHECK,
      429 + RATE_LIMITED after limit=3 (isolated via unique X-Forwarded-For IPs per test),
      payload truncation to exactly 8000, append-only repository structure guard
- [x] `mvn test`: **156 tests, 0 failures** (150 + 6); web `eslint` + `tsc --noEmit` clean;
      DB left clean (payment_events 0 rows — tests are @Transactional and every record() joins
      the ambient test transaction; settings key row absent = catalog default until first save)

## Step 26 acceptance (2026-10-08)

- [x] Doc gate: `02-ARCHITECTURE.md` §5 checkout bullet (reuse contract) and §5 SUBSCRIPTION
      settings list updated BEFORE code (also backfilled Step 25 SECURITY key in the settings list)
- [x] New SUBSCRIPTION catalog key `checkout_pending_reuse_minutes` (default 10, 0–60,
      0 = reuse disabled); i18n label + description added in en/km/vi/zh
- [x] `checkoutPayWay`: before inserting, looks up the most recent PENDING order for
      (owner, plan) created within the window (`findFirstByOwnerIdAndPlanIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc`);
      if found it REUSES `tran_id`/`req_time` (log.debug), rebuilds everything else from the
      current request (hash, form fields, return URLs), and refreshes `payway_hash` on the same
      row — no duplicate insert. PAID/FAILED/older-than-window orders are never reused; a
      different plan gets its own order
- [x] New `CheckoutIdempotencyTests` (5): same-plan ×2 → same tran_id/req_time, 1 row;
      different plan → separate orders; PAID order not reused; window-expired (backdated
      created_at via native UPDATE) → new order; setting = 0 → reuse disabled
- [x] Release-gate interplay: the zero-float grep gate flagged "double-click" wording in new
      main-source comments/setting description (`\b(double|float)\b`) — reworded to
      "repeated click"/"bấm lặp lại" (gate now green again)
- [x] `mvn test`: **161 tests, 0 failures** (156 + 5); web `eslint` + `tsc --noEmit` clean.
      No frontend changes needed (submit button already disables while in flight; backend
      reuse is the guarantee)

## Step 27 acceptance (2026-10-08)

- [x] Doc gate: `02-ARCHITECTURE.md` §4 (owner_accounts index line) and §5 (`GET /admin/insights`
      contract) written BEFORE code
- [x] V13 `idx_owner_sub_ends_status` on `owner_accounts(subscription_ends_at, subscription_status)`
      — serves the daily lifecycle scan `findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn`;
      applied to dev DB (flyway rank 13 + \di verified)
- [x] `GET /api/v1/admin/insights` (ADMIN-only, follows existing admin controller):
      - `revenueByPlan`: PAID `subscription_orders` grouped by plan (native SQL, plan join) —
        planId/name/currency/paidOrders/revenueMinor (BIGINT minor units)
      - `totalsByCurrency`: rolled-up totals, one row per currency (plans exist in USD + KHR —
        no cross-currency summing)
      - `cohorts`: owner_accounts by registration month (YYYY-MM) with registered /
        activeNow (endsAt >= now OR LIFETIME) / churned (endsAt past, not LIFETIME)
- [x] Admin UI: new **Insights** tab (TrendingUp icon) on `/admin` — revenue-by-plan table with
      a per-currency totals row (formatMoney) + cohort table with active/churned color coding;
      fetched in the same Promise.all as the other admin tabs
- [x] i18n: 11 new `admin.*` keys (tabInsights, insights*, col*) added to types.ts + en/km/vi/zh
- [x] New `AdminInsightsTests` (4): anon 401 / host 403 / admin 200 with array payloads;
      revenue counts only PAID (second same-plan checkout stays PENDING and is excluded) with
      correct amount; KHR plan lands in its own currency total; current-month cohort includes
      the fresh trial host as active (registered >= activeNow + churned)
- [x] `mvn test`: **165 tests, 0 failures** (161 + 4); web `eslint` + `tsc --noEmit` clean

## Step 28 acceptance (2026-10-08)

- [x] Doc gate: README "Kiểm Thử Backend" section rewritten BEFORE code — hermetic behavior,
      Docker prerequisite, correct test count (was stale at 119)
- [x] `org.testcontainers:postgresql` added (test scope) with `<testcontainers.version>1.21.4</testcontainers.version>`
      override: Boot 3.5.5's BOM pins 1.21.3, which negotiates Docker API 1.32 — Docker
      Engine 29 (Docker Desktop ≥4.52) rejects that with `400 on /info` and Testcontainers
      cannot find a Docker environment (upstream fix: 1.21.4, testcontainers-java #11422)
- [x] `TestDatabaseInitializer` (test sources) — ONE singleton `postgres:16` container per JVM
      (matches the dev compose image), started lazily under a lock with a clear
      "is Docker running?" error, stopped via JVM shutdown hook; wired through
      `src/test/resources/META-INF/spring.factories` (`ApplicationContextInitializer`) so
      NO test class needed editing — `spring.datasource.*` injected via `TestPropertyValues`
- [x] Hermetic proof: full suite run with `docker compose stop postgres` → **165/165 pass**;
      dev DB Flyway history untouched (schema now migrated from scratch in the test container)
- [x] `mvn test`: **165 tests, 0 failures** (no behavior changes — same suite, isolated DB).
      Dev postgres container restarted afterwards for dev/inspection use

## Step 29 acceptance (2026-10-08)

- [x] Doc gate: `01-DOMAIN.md` §9.6 gained "Step 29 semantics (host-triggered assessment)"
      (CONTRIBUTION-only scope, half-up rounding formula, direction IN/status UNPAID/dueAt
      copy, cumulative delta idempotency, explicit POST, NONE no-op); `02-ARCHITECTURE.md`
      §4 added the V14 index line (note: `groups.late_fee_type/value` columns existed since V3 —
      plan wording "V14 late_fee_type/value" is stale) and §5 added the
      `POST /groups/{id}/late-fees/assess` contract — all BEFORE code
- [x] V14 `idx_ledger_late_fee_cycle_share` — partial index on
      `ledger_entries(cycle_id, share_id) WHERE type = 'LATE_FEE'`; serves the "already
      charged" delta sum; LATE_FEE is excluded from `uq_ledger_cycle_share_type` (V7) on
      purpose so cumulative rows may be added over successive days
- [x] `FormulaEngine.lateFee(type, value, principal, overdueDays)` — pure, zero-float,
      integer only: NONE -> 0; FIXED -> value once regardless of days; PERCENT_PER_DAY ->
      HALF_UP `(principal * value * days + 50) / 100`; rejects negative inputs and
      unknown types with `FormulaException`
- [x] `LateFeeService.assess(userId, ownerId, groupId)` (`com.tongtin.ledger.service`):
      owner-scoped group lookup (404), NONE no-op `{0,0,[]}`, overdue CONTRIBUTION rows only
      (LATE_FEE/PAYOUT/HOST_FEE never assessed), pessimistic lock in ascending entry id
      (deadlock-safe vs PaymentService), post-lock status re-check, target-vs-charged delta,
      positive-delta insert only; audit `LATE_FEES_ASSESSED` + `LATE_FEE_ASSESSED`
      notification to affected members (same tx as the rows)
- [x] Fee row: type LATE_FEE, direction IN, status UNPAID, dueAt copied from source
      (surfaces in GET /debts immediately), shareId/memberProfileId/cycleId/currency copied;
      principal = full contribution `amount_minor`, overdueDays = whole days since due_at
- [x] Response `{assessed, created, entries[]}` with entry {ledgerEntryId, cycleId, shareId,
      amountMinor, currency, dueAt}; endpoint `POST /groups/{id}/late-fees/assess`
      (HOST-only via class-level `@PreAuthorize`, 404 cross-owner) in `PaymentController`
- [x] Payments can allocate to LATE_FEE rows (existing `FEE_TYPES` in member portal/report
      already include it); paid fees still count as charged -> later assess writes nothing
- [x] New `LateFeeTests` (8): FIXED charged once + idempotent re-run + audit row; PERCENT
      delta growth 48000 -> +32000 -> 0; NONE no-op; not-overdue no-op + PAID skipped;
      payout never assessed; fee surfaces in debts (4 rows = 2 contributions + 2 fees) and
      can be paid (PAID, then assess creates nothing); cross-owner 404 / anon 401;
      half-up wiring (8000 per contribution)
- [x] `FormulaEngineTests` +6 unit cases (NONE/FIXED/percent growth/half-up boundaries
      1.5->2, 1.49->1, 0.5->1, 0.49->0; negative + unknown type rejected);
      `AuthzSweepTests.HOST_ONLY` gained the assess endpoint (404/403/401/owner matrix)
- [x] Web: "Apply Late Fees" button on host group payments tab (shown when `lateFeeType
      !== "NONE"`), `api.assessLateFees()` + `lateFeeType/lateFeeValue` added to `getGroup`
      TS type, success/no-new-fee/error banners; i18n keys x4 (types.ts + en/km/vi/zh)
- [x] `mvn test`: **179 tests, 0 failures** (165 + 14); web `eslint` + `tsc --noEmit` clean.
      Nothing committed (standing rule)

## Step 30 acceptance (2026-10-08)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` §2 stack table gained an
      Export row; §5 gained the full Export (Step 30) contract (endpoints,
      column layouts, money/date rendering, CSV + XLSX rules). `01-DOMAIN.md`
      §14 gained export parity rules (same fields + same permission checks as
      the JSON report; member exports never carry host fee/profit; exports are
      point-in-time snapshots)
- [x] Format decision (user): CSV + real .xlsx via Apache POI — added
      `org.apache.poi:poi-ooxml:5.5.1` to apps/api/pom.xml (Spring Boot does
      not manage POI)
- [x] Endpoints (reuse Step 15 report services as data source — same
      permissions, same 404s, zero new queries/math):
      HOST GET /groups/{id}/export/ledger?format=csv|xlsx,
      HOST GET /groups/{id}/export/profit?format=csv|xlsx,
      MEMBER GET /me/groups/{id}/export/statement?format=csv|xlsx;
      format defaults to csv, unknown value -> 400; responses are attachments
      with filename tongtin-<kind>-g<groupId>.<ext>
- [x] Money rendered as exact major-unit decimals via
      BigDecimal.valueOf(amountMinor, exponent).toPlainString() (integer math
      only; VND exponent 0 = plain integer); dates ISO-8601 UTC text; numbers
      and strings separated so xlsx numeric cells are summable
- [x] Trailing TOTAL rows, self-describing in the existing type column:
      ledger TOTAL_IN/TOTAL_OUT, profit cycleNo=TOTAL, statement
      TOTAL_CONTRIBUTED/TOTAL_RECEIVED/TOTAL_FEES_PAID/TOTAL_NET_POSITION —
      each carries the JSON report total (verified equal to the report in
      tests)
- [x] CSV: UTF-8 BOM first byte (Excel), CRLF, RFC 4180 quoting (embedded "
      doubled); formula-injection guard prefixes non-numeric cells starting
      with = + @ or - with `'` (negative numbers like -5000.00 are emitted raw)
- [x] XLSX: single sheet named ledger/profit/statement, POI round-trip
      verified in tests (header + TOTAL_OUT numeric amount cell > 0)
- [x] Authorization: AuthzSweep gained 5 endpoint rows (export/ledger csv+xlsx,
      export/profit csv, export/statement csv+xlsx) — cross-tenant 404, wrong
      role 403, anonymous 401, owner 2xx-4xx; direct tests cover foreign member
      404 on statement and cross-owner 404 on host exports
- [x] New `ExportTests` (5): BOM/header/guard/totals CSV, POI xlsx
      round-trip, profit settled-total row, member scoping (own group 200,
      foreign member 404, host 403, anon 401), format default + 400
- [x] Web: api.downloadExport (fetch-with-token -> Blob -> anchor; reads
      Content-Disposition filename) + exportLedger/exportProfit/exportStatement;
      CSV+XLSX buttons on /host/groups/[id]/ledger header, host group cycles
      tab (profit), member statement tab; exportError inline; i18n keys x12
      (types.ts + en/vi/km/zh)
- [x] mvn test: **184 tests, 0 failures** (179 + 5); web eslint + tsc clean;
      zero-float grep gate green. Nothing committed (standing rule)

## Step 31 acceptance (2026-10-08)

- [x] Stale-docs pass (no domain/API change → no doc-gate trigger, but the living docs
      were refreshed to match reality):
  - README.md: badges `Spring Boot 3.5.5` + `Tests 184 passed` (was 3.4.4 / 119);
    module tree rewritten to the as-built package layout (common, identity, members,
    groups, cycles/bids, ledger+payments, dashboard, reports+export, notify,
    memberportal, subscription, settings, demo); "10 Flyway Migrations V1→V10" →
    "14 Flyway Migrations (V1→V14)"; testing section 184 with real `mvn test` output;
    "11 routes" → 13; roadmap table extended with rows 20–31 and header "31/31 giai
    đoạn"; export buttons noted on ledger + statement routes; Dark Mode verified real
    (`prefers-color-scheme` in globals.css)
  - `docs/monkey_ai/00-INDEX.md`: rewritten — correct paths under `docs/monkey_ai/`,
    real skill path `.agents/skills/tongtin-builder/SKILL.md`, STATUS.md as live
    tracker, plan.md as full step log, stale "first code step Step 01" removed
  - `docs/monkey_ai/02-ARCHITECTURE.md` §3: module tree now the as-built list
    (+ reports, memberportal, dashboard, demo; notes bids live in `cycles/bids`,
    payments in `ledger/payments`, audit in `common/audit`)
  - `docs/monkey_ai/03-BUILD-ROADMAP.md`: rules STATUS path fixed; P2 note updated
    (CSV/XLSX released Step 30, PDF still parked); post-MVP track table (Steps 18–31)
    appended with live-tracker pointer
- [x] Docker click-through (fresh build of the CURRENT code, not the stale Step 21
      images): `docker compose build` → `docker compose up -d` — postgres 16.15 +
      api + web all healthy; `GET /api/v1/health` UP direct and via web proxy
      (`localhost:3000/api/v1/health`); Flyway V1→V14 applied cleanly ON TOP of the
      existing dev volume (V14 newly applied, `flyway_schema_history` verified 14 rows)
- [x] Live Step 30 endpoint sweep against the new stack: HOST ledger CSV 200
      (`Content-Disposition: tongtin-ledger-g2992.csv`, `text/csv`, UTF-8 BOM `ef bb bf`,
      header + `TOTAL_IN` 1 row); ledger XLSX 200 (OOXML, unzip valid); profit CSV 200
      (TOTAL row); unknown `format=pdf` → 400; MEMBER statement CSV 200 (4 TOTAL rows,
      Unicode group name intact, `tongtin-statement-g2992.csv`) + XLSX valid OOXML;
      member endpoint reset via `POST /members/4447/set-login` (fixture `demo1234`
      restored — the pre-existing dev-DB member password predated the current seeder);
      authz live: member→host ledger export 403, host→member statement 403, anon→401
      (matches AuthzSweep semantics)
- [x] Final full verification: `mvn test` **184 tests, 0 failures, BUILD SUCCESS**
      (incl. ReleaseGateTests zero-float gate); web `eslint` + `tsc --noEmit` clean;
      `npm run build` production build **13/13 routes** compiled (11 static + 2 dynamic)
- [x] No stray artifacts: `git status` shows only the Steps 19–31 source/doc changes
      (all uncommitted per standing rule); /tmp captures of exports are outside the repo
- [x] Stack left RUNNING on the new images (`docker compose down` to stop). Nothing
      committed (standing rule)

## Step 32 acceptance (2026-10-09)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` §4 (`attachments` table + `idx_attachments_owner_entity`),
      §5 "Attachments (Step 32)" contract (endpoints, `AttachmentResponse`, settings) and the new
      `GET /groups/{id}/payments` route; `01-DOMAIN.md` §15 (attachment semantics)
- [x] **Decision:** attachments attach at PAYMENT (receipt) and MEMBER (document) level only — never
      to ledger allocations; money/math never depend on a file
- [x] V15 `attachments`: id, owner_id FK owner_accounts, entity_type CHECK IN ('PAYMENT','MEMBER'),
      entity_id, original_name, content_type, size_bytes CHECK > 0, content BYTEA, uploaded_by_user_id
      FK users, uploaded_at DEFAULT now(); composite index `(owner_id, entity_type, entity_id)`.
      No filesystem path — no path-traversal vector
- [x] New `com.tongtin.attachments` module (entity/repository/dto/service/web). `AttachmentService`:
      owner-scoped attach/stream/delete (cross-owner → 404), empty upload 400, content-type allow-list
      400, size cap 400, writes `ATTACHMENT_UPLOADED` / `ATTACHMENT_DELETED` audit rows in the same tx
- [x] Endpoints (all HOST): `POST /api/v1/payments/{paymentId}/attachments` and
      `POST /api/v1/members/{id}/attachments` (multipart `file`), `GET /api/v1/attachments/{id}`
      (bytes + content-type + `Content-Disposition: attachment; filename*=UTF-8''…`),
      `DELETE /api/v1/attachments/{id}` → 204; plus new `GET /api/v1/groups/{id}/payments`
      (payment history with `allocations[]` + `attachments[]`)
- [x] Limits configurable live via two new SECURITY catalog keys (no migration per change):
      `storage_attachment_max_mb` (numeric 1–50, default 10) and `storage_attachment_allowed_types`
      (STRING allow-list, default png/jpeg/gif/webp/pdf/xlsx/xls/csv)
- [x] Responses carry metadata: `PaymentResponse` and `MemberResponse` gained `attachments[]`
      ({id, entityType, entityId, originalName, contentType, sizeBytes, uploadedAt}); member
      list/update/deactivate batch-fill via `metadataForMany`
- [x] Web: api.ts (`AttachmentMeta`, `PaymentRecord`, getPayments, upload/delete/download helpers,
      `filename*=UTF-8''` parse in download), `formatBytes`, i18n ×4 (membersDirectory file keys +
      hostGroupDetail payment-history keys); host group page "Payment History & Receipts" (per-payment
      receipt upload/list/delete) in the payments tab; members page per-row Files modal
      (upload/list/download/delete)
- [x] `AttachmentTests` (7 new): payment receipt upload + history includes `allocations[]`/`attachments[]`
      + byte-exact stream + content-type/Content-Disposition; member doc upload appears in directory then
      delete removes it + 404 after; disallowed type + empty rejected with nothing persisted; oversize
      rejected against configured limit (11MB default, then live-drop to 1MB) with in-limit upload OK;
      narrowed allowed-types rejects others; cross-owner upload/download/delete/history → 404; anon → 401
- [x] `mvn test`: **191 tests, 0 failures** (184 + 7); web `eslint` + `tsc --noEmit` clean;
      zero-float grep gate green (`AttachmentTests` uses byte[] only). Nothing committed (standing rule);
      stack left RUNNING (`docker compose down` to stop)

## Step 33 acceptance (2026-10-09)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` §4 (`member_profiles.status` note + new `member_blacklists (V16)`
      table block + index), §5 "Blacklist (Step 33)" contract (endpoints + enforcement points); `01-DOMAIN.md` §6
      member status semantics + blacklist rules
- [x] **Decision:** table named `member_blacklists` (roadmap) not the spec doc's `owner_blacklist`; blacklist is
      owner-local only (never global); unlist is a soft `active=false` — the row is never deleted
- [x] V16 `member_blacklists`: id, owner_id FK owner_accounts, phone, reason, active DEFAULT true,
      created_by_user_id FK users, created_at, updated_at; UNIQUE `(owner_id, phone)`; index `(owner_id, active)`
- [x] New `com.tongtin.members.blacklist` (entity/repository/dto/service/web). `BlacklistService`: owner-scoped
      list/add/unlist + `activeReason`; duplicate ACTIVE → 409; re-add reactivates the same row; audit
      `BLACKLIST_ADDED` / `BLACKLIST_REMOVED` in the same tx
- [x] Endpoints (HOST, `/api/v1/members/blacklist`): `GET` list, `POST` add → 201, `DELETE /{id}` unlist → 200
      (cross-owner → 404); path precedence over `/members/{id}` verified
- [x] Enforcement: `MemberService.create` → 409 when phone blacklisted (reason included); `ShareService.assign` →
      409 blacklisted phone / 400 non-ACTIVE member; `BidService.placeBid` → 400 non-ACTIVE member;
      `MemberService.update` runs `syncLoginStatus` (BLOCKED → linked MEMBER user BLOCKED else ACTIVE; HOST /
      non-MEMBER untouched); `setLogin` → 409 on BLOCKED; existing `AuthService` login status check → 401
- [x] Web: `api.ts` (`updateMember`, `getBlacklist`, `addBlacklist`, `unlistBlacklist`); `/host/members` per-row
      status `<select>` + error banner and a "Phone Blacklist" manager (add/list/unlist, badges, banners);
      i18n ×4 (status keys + blacklist keys in types.ts + en/vi/km/zh)
- [x] `BlacklistTests` (5 new): add/list/duplicate-409/soft-unlist/reactivate-same-id; blacklisted phone → 409 on
      add-member + add-share; BLOCKED → login 401 + set-login 409, ACTIVE restores login; INACTIVE → no bid on
      host AND member routes, ACTIVE restores; cross-owner unlist → 404, member 403, anon 401. Phones stored and
      returned normalized `+84…`
- [x] `mvn test`: **196 tests, 0 failures** (191 + 5); web `eslint` + `tsc --noEmit` clean; zero-float grep gate
      green. Nothing committed (standing rule); stack left RUNNING (`docker compose down` to stop)

## Step 34 acceptance (2026-10-09)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` — new "Obligation KHQR (Step 34)" contract under Payments
      (layout, bill ref `TONGTIN <code>-O<entryId>` capped at EMVCo 25 chars, real CRC16-CCITT 0x1021/init 0xFFFF,
      `payments_khqr_enabled` PAYMENT setting, render endpoint + scope/authz rules, subscription KHQR string
      untouched, QR is display-only — money never read back); statement `Entry` contract gains `khqr`;
      `01-DOMAIN.md` **§16 "Obligation QR (Step 34)"** appended; `07-MULTI-CURRENCY.md` **§10 "EMVCo/KHQR amount
      rendering (Step 34)"** with the ISO 4217 numeric-code table (VND 704, KHR 116, LAK 418, USD 840, THB 764,
      SGD 702) + long-math major-unit decimal rule
- [x] `com.tongtin.khqr.KhqrGenerator` — pure-Java, no Spring/DB: `000201010212` + tag 26 GIF `com.tongtin` +
      sanitized merchant subfield, 52 `5999`, 53 numeric code, 54 decimal amount (exponent-aware, long math only),
      58 `KH`, 59 merchant name (max 25), 60 `PHNOM PENH`, 62/01 bill ref, 63/04 genuine CRC16-CCITT;
      `isValid` recomputes the CRC; golden vector `123456789` → `0x29B1`
- [x] `ObligationQrService` + `ObligationQrController` (both authed as HOST **or** MEMBER): `payload(...)` shared
      by debts/statement/render (null when disabled, unsupported currency, or not an obligation);
      `renderable(...)` scopes — host only own group (via `ownerId`), member only own share (phone → profiles →
      shares), cross-scope/disabled → 404, anon → 401; **obligations are `direction = IN`** (contributions), so QR
      is emitted only for IN entries with `remaining > 0` (payouts/PAID never get one)
- [x] Wiring: `DebtResponse` + `PaymentService.debts` gain nullable `khqr` (subtracts allocations for PARTIAL,
      amount reflects remaining); `MemberStatementResponse.Entry` + `MemberReportService` gain `khqr`
      (only UNPAID/PARTIAL IN obligations); `SettingsCatalog` PAYMENT key `payments_khqr_enabled` (BOOLEAN,
      default true); `pom.xml` zxing 3.5.3 (core + javase) for PNG rasterization
- [x] Endpoint `GET /api/v1/obligations/{ledgerEntryId}/khqr` → `image/png` (zxing 480px, PNG magic bytes
      verified); paid-out / foreign / disabled entries → 404
- [x] Web: `api.fetchObligationQr(entryId)` → blob URL (Bearer travels via header, image cannot); `khqr` in
      `getDebts` + statement entry types; QR button + modal (next/image unoptimized, amount = remaining, cycle) in
      host debts table and member statement rows; i18n ×4 (`viewQrBtn`, `qrModalTitle`, `qrModalDesc`,
      `qrModalAmount`, `qrModalCycle`, `closeBtn` in types.ts + en/vi/km/zh)
- [x] `KhqrTests` (10 new): golden CRC; generator layout/ref/amounts per exponent (VND/USD/KHR/THB); unsupported
      currency rejected; tamper (bad CRC + altered amount) rejected; debts khqr present & reflects remaining after
      partial pay-out; statement carries khqr only for unpaid IN obligations; render scope matrix (host 200,
      member own-share 200, cross-owner 404, other member 404, anon 401); disabled → no khqr anywhere + render
      404, re-enable restores
- [x] `mvn test`: **206 tests, 0 failures** (196 + 10); web `eslint` + `tsc --noEmit` clean (0 warnings); zero-float
      grep gate green. Nothing committed (standing rule); stack left RUNNING (`docker compose down` to stop)

## Step 35 acceptance (2026-10-09)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` — module tree gains `telegram`; `owner_accounts` §4 adds
      `telegram_chat_id BIGINT NULL (V17, Step 35)`; new "## Telegram (Step 35)" contract (host-only channel,
      settings keys, endpoints, event matrix, digest job, failure semantics); frontend pages add `/host/telegram`;
      `01-DOMAIN.md` **§17 "Telegram (Step 35)"** (host-only, two message kinds, settings gates, money untouched);
      `06-SETTINGS-TEMPLATES-RISK.md` §8 Telegram notes + §10 decision log 2026-10-09
- [x] `V17__telegram.sql`: `ALTER TABLE owner_accounts ADD COLUMN telegram_chat_id BIGINT` (nullable, no FK —
      third-party id); `OwnerAccount.telegramChatId` + `OwnerAccountRepository.findByTelegramChatIdIsNotNull()`
- [x] `SettingsCatalog` TELEGRAM category (5th): `telegram_bot_token` (SECRET, ""), `telegram_events_enabled`
      (BOOLEAN, true), `telegram_daily_digest_enabled` (BOOLEAN, false), `telegram_daily_digest_time`
      (STRING, "08:00", HH:mm tolerant); settings auto-appear in admin UI; `AdminSettingsTests` 4→5 categories
- [x] `com.tongtin.telegram` — `TelegramClient` seam + `BotApiTelegramClient` (java.net.http, 10s timeout,
      blank-token short-circuit, never throws — mirrors PayWayService), `TelegramNotifier` (best-effort,
      synchronous, guarded: events gate + token + chat id; never rolls back a financial op),
      `TelegramMessages` (pure VN composition, long-only `fmt`, exponent-aware, VND → " đ"),
      `TelegramService` (status / set-chat-id / test-send, audits TELEGRAM_LINKED / TELEGRAM_UNLINKED /
      TELEGRAM_TEST), `TelegramDigestJob` (`@Scheduled` every minute, fires `process(Instant)` only at the
      configured HH:mm; per-host one consolidated "N khoản, Tổng" message; IN-only, remaining after allocations)
- [x] Host-only endpoints `GET /api/v1/host/telegram`, `PUT /chat-id` (null clears, positive-long validated),
      `POST /test` (400 without chat id or when bot not configured) — `@PreAuthorize("hasRole('HOST')")`,
      own-account only
- [x] Event hooks wired in the same `@Transactional` method as the business write, send strictly synchronous
      (Step 14 "sync transitions only" respected): CycleService (CYCLE_OPENED), CycleCloseService
      (WINNER_PUBLISHED, PAYOUT_CONFIRMED, GROUP_COMPLETED on last cycle), PaymentService.notifyPayerMembers
      (PAYMENT_RECORDED), LateFeeService (LATE_FEE_ASSESSED)
- [x] Web: `api.ts` (`getTelegramStatus`, `setTelegramChatId`, `testTelegram`), new `/host/telegram` page (chat-id
      input → save/clear with confirm, test-send button + gate hint, read-only status card: events/digest/time/
      token), host navbar "Telegram" item (Send icon), i18n ×4 (`navbar.telegram` + `telegramChannel` block in
      types.ts + en/vi/km/zh)
- [x] `TelegramTests` (7 new, hermetic — `@MockBean TelegramClient` capturing sends, ZERO network): link/unlink
      round-trip + audit rows; member-scope 403 / anon 401 / negative chat-id 400; test-send gating (no chat →
      400, no token → 400, ok → captured "Kiểm tra Telegram", client-failure → 400); digest messages exactly the
      group with owed obligations (2 khoản — 1.600.000 đ), disabled → no-op; full lifecycle events reach host
      (mở KỲ → thắng ký → thanh toán); last-cycle payout raises GROUP_COMPLETED (2-cycle group played to the
      end); payment-recorded + late-fee-assessed pings; every test cleans its own rows
- [x] `mvn test`: **213 tests, 0 failures** (206 + 7); web `eslint` + `tsc --noEmit` clean (0 warnings); zero-float
      grep gate green. Nothing committed (standing rule); stack left RUNNING (`docker compose down` to stop)

## Step 36 acceptance (2026-10-09)

- [x] Doc gate BEFORE code: `02-ARCHITECTURE.md` §5 new "Quick-pay (Step 36)" contract (endpoint,
      request shape, auto-allocation order, fail-fast errors, receipt attach reuses
      `POST /payments/{id}/attachments`); `01-DOMAIN.md` **§18 "Quick-pay (Step 36)"** (candidate
      pool, allocation order + equality with POST /payments, fail-fast, receipts separate);
      `06-SETTINGS-TEMPLATES-RISK.md` §11 decision log 2026-10-09
- [x] `LedgerEntryRepository.findOutstandingForMember` — member's UNPAID/PARTIAL `direction='IN'`
      entries for the group `ORDER BY due_at, id` (locked plan §20 tie-break)
- [x] `PaymentService` internals SHARED: body of `record()` extracted to `recordCore(...)`;
      `record()` and quick-pay both delegate; quick-pay addition is only allocation origin
      (audit payload `autoAllocated: true`)
- [x] `QuickPayRequest` DTO + `planQuickPay` (membership check → pool → total-remaining bound;
      allocations `min(remaining, leftover)` in pool order, sum exact by construction)
- [x] `POST /api/v1/groups/{id}/quick-pay` (HOST, own group; `Idempotency-Key` header):
      `findQuickPayReplay` → 200 replay, else 201; **fail-fast** member-not-in-group → 404,
      nothing outstanding → 400, over-pay → 400 (no writes on any failure)
- [x] Web: `lib/api.ts quickPay`; host group payments tab "Quick-pay" modal (member picker with
      grouped per-member totals, amount default = total remaining, method, optional note +
      receipt file uploaded to the saved payment, auto-allocation hint); i18n ×4 (`quickPay*`
      block in types.ts + en/vi/km/zh)
- [x] `QuickPayTests` (11 new, hermetic): LATE_FEE inclusion + same-due-at entry-id tie-break +
      audit flag + debts recompute; opt-out leaves fee UNPAID; oldest-first across two settled
      cycles; partial PARTIAL split; over-pay / no-outstanding (winner's PAYOUT never auto-paid) /
      member-not-in-group / cross-owner + anon guards; idempotency 200 replay + 409 diff payload;
      input validation; quick-pay + receipt upload → history
- [x] `mvn test`: **224 tests, 0 failures** (213 + 11); web `eslint` + `tsc --noEmit` clean (0
      warnings); zero-float grep gate green. Nothing committed (standing rule); stack left RUNNING
      (`docker compose down` to stop)

## Step 37 acceptance (2026-10-09)

- [x] **Docs pass**: `README.md` + `README_VN.md` — badges/body `184` → `224`, "14 Flyway
      (V1->V14)" → "17 (V1->V17)", "13 routes" → "14", module tree + `attachments/` `khqr/`
      `telegram/`, roadmap re-titled "37 Giai đoạn / steps" with rows 32–37; `03-BUILD-ROADMAP.md`
      rows 31 ✅ + Phase 2 (32–37) table added; `plan.md` §0 rows 36–37, §20 row 37 ✅, §26 record;
      `STATUS.md` close-out (CURRENT_STEP → DONE, phase complete)
- [x] **Final suite + build**: `mvn -o package` => BUILD SUCCESS, **224 tests / 0 failures**,
      hermetic flyway V1→V17 on fresh postgres:16; fat jar built + repackaged; zero-float gate
      green. Web `npm run build` => **14/14 routes**; `eslint` + `tsc --noEmit` clean
- [x] **Docker click-through**: `docker compose up -d --build` — images built; postgres → api → web
      all `(healthy)`; live probes `GET /api/v1/health` 200, `GET /login` 200, unauth
      `/api/v1/groups` 401. Stack left RUNNING (`docker compose down` to stop)
- [x] **Close-out**: Steps 00–37 all done. Nothing committed (standing rule). Parked P-items and
      §7.5 formula reminders stay user-gated

## Completion

**Steps 00–37 fully complete — Tong Tin Management System shipped end-to-end** (Phase 2 wrap-up
done): suite **224** green + fat jar, web lint/tsc/build clean (14/14 routes), Docker compose
stack healthy. No committed work (standing rule; user commits when ready).

**Post-completion feature (2026-10-09, user-requested):** Subscription master switch
`subscription_enabled` — admin config to disable/enable the whole subscription subsystem (guard
bypass, ACTIVE status, checkout 400, lifecycle parked). See plan.md §27; suite now **230**,
web clean.

Open items remain user-gated only: §7.5 formula reminders (ranks 6–7) and parked P-items
(incl. P2 PDF print templates).

## Blockers

None.

## Demo fixture (matches 08-FORMULA-ENGINE fixture A)

N=10, C=1000000, T=100000, BIDDING
Cycle1 B=200000 netPayout=7100000
Cycle2 B=150000 netPayout=7700000
Last B=0 netPayout=8900000