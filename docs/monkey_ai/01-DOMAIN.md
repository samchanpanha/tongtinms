# Domain Model, Rules, and Formulas

## 1. What this system is

Tong Tin is a rotating savings and credit association (ROSCA).
A host creates a group (day hoi). Members contribute every cycle.
Exactly one eligible member receives the pot each cycle.
After a member receives the pot, they become "dead" and must keep paying until the group ends.

Goal of the software:
- no paper/Excel math errors
- transparent ledgers for host and members
- sealed digital bidding
- automatic payout and contribution calculation
- reminders and debt tracking

## 2. Actors

| Actor | Vietnamese | Can do |
|---|---|---|
| Platform admin | Quan tri | Manage hosts, system config |
| Owner / Host | Chu Hoi / Chu Thao | Self-register tenant, create groups, invite members, open/close cycles, confirm payments, view profit |
| Member | Hoi Vien | Join groups, bid, view own balance, see cycle results |
| Guarantor | Nguoi bao lanh | Optional backing for a member (later phase) |

A person can be owner of their tenant and member of another owner's groups.
Owner self-register is documented in `docs/05-OWNER-SELF-REGISTER.md`.

Public signup in MVP is **owner only**. Members are paper profiles first, app login by invite later.

Owner defaults, formula presets, local blacklist, and group templates are analyzed in `docs/06-SETTINGS-TEMPLATES-RISK.md`. They are not MVP calculators. BIDDING_CLASSIC in section 9 stays the only engine until a new tested preset is added in code.

## 3. Core objects

- User: login account (phone + password)
- OwnerAccount: tenant for one Chu Hoi, 1-1 with a HOST user
- MemberProfile: phone, name, ID number, credit notes, scoped to one OwnerAccount
- Group (Day Hoi): one Tong Tin line with rules
- GroupMember: membership in a group, share count, alive/dead status
- Cycle (Ky): one round of collect-bid-payout
- Bid: sealed offer from an alive member for a cycle
- LedgerEntry: immutable money movement
- Payment: real-world settlement against a ledger obligation
- Notification: reminder or result message
- AuditEvent: who did what, when

## 4. Group types

### 4.1 BIDDING (Hoi khui / dau gia) - default MVP

Alive members bid a discount `B`.
Highest valid bid wins the cycle.
Dead members pay full base amount.
Alive non-winners pay discounted amount.
Last remaining alive member wins with bid `0` (no auction).

### 4.2 FIXED (Hoi deu / super)

No bidding.
Payout order is set at group creation or by host.
Every payer pays full base amount every cycle.

### 4.3 RANDOM (Bo tham)

No money bid.
System draws a fair random winner among alive members.
Contributions are full base amount.

MVP implements BIDDING and FIXED. RANDOM is a later extra.

## 5. Group configuration

Stored on Group. Frozen after the group is STARTED.

| Field | Meaning | Example |
|---|---|---|
| `code` | Human group code | HOI-2026-001 |
| `name` | Display name | Hoi thang 10 |
| `type` | BIDDING or FIXED | BIDDING |
| `baseAmount` | Tien chan `C` | 1000000 |
| `shareCount` | Total shares `N` | 10 |
| `cycleUnit` | DAY, WEEK, MONTH | MONTH |
| `cycleCount` | Usually equals `N` | 10 |
| `startAt` | First cycle open time | 2026-10-15 |
| `bidOpenOffset` | When bidding opens relative to cycle start | 0 |
| `bidCloseOffset` | Bidding deadline | 2 days before payout |
| `hostFeeType` | NONE, FIXED_PER_CYCLE, PERCENT_OF_POT, FIRST_CYCLE_TO_HOST | FIXED_PER_CYCLE |
| `hostFeeValue` | Amount or percent | 100000 or 1.00 |
| `hostIsMember` | Host also holds shares | false |
| `currency` | ISO 4217, frozen at START | VND |
| `minBid` | Minimum discount, minor units | 0 |
| `maxBid` | Maximum discount, must be < C | 500000 |
| `bidStep` | Bid multiple, minor units | 10000 |
| `lateFeeType` | NONE, FIXED, PERCENT_PER_DAY | FIXED |
| `lateFeeValue` | Minor units or bps by type | 20000 |
| `allowMultiShare` | One person can hold many shares | true |
| `tieBreak` | LOWEST_MEMBER_CODE, EARLIEST_BID, HOST_DECISION | EARLIEST_BID |

Constraints:
- `N >= 2`
- `C > 0`
- `maxBid < C`
- `bidStep > 0` for BIDDING
- `cycleCount == N` in MVP (one pot per share)

## 6. Member and share states

Each GroupMember holds `shareCount >= 1`.
Each share is tracked separately as GroupShare.

Member profile status (host-scoped, `member_profiles.status`):
- ACTIVE: normal — may hold shares, log in (if a login is set), and bid.
- INACTIVE: de-listed — cannot bid on any share; existing shares/obligations are
  untouched (no money changes). A shares list stays readable.
- BLOCKED: INACTIVE plus login refused; the linked `users` row is set to BLOCKED so
  the standard login status check rejects it. The host cannot set-login a blocked member.

Blacklist (owner-local, `member_blacklists`): a phone the owner will not onboard.
Adding a member or assigning a share with a blacklisted phone is rejected (409) with the
reason. Unlisting is a soft `active=false` (row kept, audited). Scope is the owning
tenant only — never global/public (`06-SETTINGS-TEMPLATES-RISK.md` §4).

Share status:
- ALIVE (Hoi Song): has not received pot
- DEAD (Hoi Chet): already received pot, must pay full `C` every later cycle
- DEFAULTED: broke rules, excluded from bidding, debt remains
- EXITED: left before start only (not allowed after STARTED in MVP)

A member with 2 shares is 2 independent payers/receivers.

## 7. Cycle lifecycle

```
DRAFT -> OPEN -> BIDDING -> CLOSED_FOR_CALC -> PAYOUT_PENDING -> SETTLED -> ARCHIVED
                    |
                    +-> FAILED (not enough bids / host abort before money moves)
```

Rules:
- Only one active cycle per group.
- Cycle `k` cannot open until cycle `k-1` is SETTLED.
- In FIXED groups, BIDDING is skipped.
- In last cycle of BIDDING groups, BIDDING is skipped and winner is the last ALIVE share with `B = 0`.

## 8. Bidding rules

Eligible bidder: GroupShare status ALIVE, not DEFAULTED, belongs to this group.
One bid per share per cycle.
Bid is sealed until `bidCloseAt`.
Host cannot see bid amounts before close.
Member can update own bid until close. Latest bid wins.
After close, bids are immutable.

Valid bid `B`:
- integer
- `minBid <= B <= maxBid`
- `B % bidStep == 0`

Winner:
1. Highest `B`
2. Tie: use `tieBreak`
3. If no bid and cycle is not last: host may extend deadline or mark FAILED
4. Last cycle: auto winner, `B = 0`

## 9. Money formulas

Use integer minor units of the group currency. Never floating money. Percents are basis points, rounded HALF_UP. See `docs/07-MULTI-CURRENCY.md` and `docs/08-FORMULA-ENGINE.md`.

Notation:
- `C` = baseAmount
- `N` = total shares still in group (active, not exited)
- `D` = dead shares at cycle start (already won in previous cycles)
- `A` = alive shares at cycle start = `N - D`
- `B` = winning bid
- `T` = host fee for this cycle
- Winner share does not contribute this cycle

### 9.1 Host fee `T`

```
if hostFeeType == NONE: T = 0
if hostFeeType == FIXED_PER_CYCLE: T = hostFeeValue
if hostFeeType == PERCENT_OF_POT: T = round(grossPot * hostFeeValue / 100)
if hostFeeType == FIRST_CYCLE_TO_HOST:
    if cycleNumber == 1: winner is HOST_VIRTUAL, T = 0, special first cycle
    else: T = 0
```

MVP implements NONE, FIXED_PER_CYCLE, PERCENT_OF_POT.
FIRST_CYCLE_TO_HOST is phase 2.

### 9.2 BIDDING contribution

For cycle with winner bid `B`:

```
deadPay     = C
alivePay    = C - B
winnerPay   = 0
```

Who pays:
- DEAD shares: `deadPay`
- ALIVE shares except winner: `alivePay`
- Winner share: `0`

```
grossPot = D * C + (A - 1) * (C - B)
netPayout = grossPot - T
```

Invariants:
- `alivePay >= 0`
- `netPayout >= 0`
- `A >= 1`
- `D + A == N`
- After settle, winner share becomes DEAD
- Sum of all contributions across all cycles for one share is tracked in ledger, not assumed equal

### 9.3 FIXED contribution

```
B = 0
deadPay = C
alivePay = C
winnerPay = 0
grossPot = (N - 1) * C
netPayout = grossPot - T
```

### 9.4 Worked example (must be used in tests)

Group: N=10, C=1_000_000, T=100_000 FIXED_PER_CYCLE, BIDDING

Cycle 1: D=0, A=10, B=200_000
```
grossPot = 0 * 1_000_000 + 9 * 800_000 = 7_200_000
netPayout = 7_200_000 - 100_000 = 7_100_000
```
Winner becomes DEAD.

Cycle 2: D=1, A=9, B=150_000
```
grossPot = 1_000_000 + 8 * 850_000 = 7_800_000
netPayout = 7_800_000 - 100_000 = 7_700_000
```

Last cycle: D=9, A=1, B=0
```
grossPot = 9 * 1_000_000 + 0 = 9_000_000
netPayout = 9_000_000 - 100_000 = 8_900_000
```

Host profit if all 10 cycles settle with same T:
```
hostProfit = 10 * 100_000 = 1_000_000
```

### 9.5 Member balance

For each member in a group:

```
contributed = sum(payments posted as CONTRIBUTION)
received    = sum(payouts posted as PAYOUT)
feesPaid    = sum(LATE_FEE + OTHER_FEE)
netPosition = received - contributed - feesPaid
```

Positive netPosition means they took more than they paid (early winners).
Negative means they paid more than they received (late winners / still alive).

### 9.6 Late fee

If obligation dueAt passed and unpaid:

```
if lateFeeType == FIXED: extra = lateFeeValue (once)
if lateFeeType == PERCENT_PER_DAY:
    extra = round(principal * lateFeeValue / 100 * overdueDays)
```

Late fee creates a new LedgerEntry. It does not rewrite the original obligation.

Step 29 semantics (host-triggered assessment):

- Applies to **CONTRIBUTION** obligations only (UNPAID/PARTIAL, `due_at < now`).
  Payout and host-fee obligations are never late-fee'd — the host is the debtor there.
- `principal` = the contribution's `amount_minor` (the full stake), `overdueDays` =
  whole days since `due_at`. Rounding is half-up integer:
  `(principal * lateFeeValue * overdueDays + 50) / 100`.
- The fee row is `type = LATE_FEE`, `direction = IN` (money owed by the member, like
  a contribution), `status = UNPAID`, and copies the source obligation's `due_at` so it
  surfaces in the overdue debts list immediately.
- Assessment is **cumulative and idempotent**: `target = lateFee(...)` (FIXED: the
  fixed value once; PERCENT_PER_DAY: grows with overdueDays). Rows already written for
  that (cycle, share) are summed and only the positive delta is inserted — re-running
  the same day writes nothing. LATE_FEE rows themselves are never fee'd again.
- Triggered explicitly by the host (`POST /groups/{id}/late-fees/assess`); `lateFeeType
  = NONE` is a no-op. No automatic/scheduled assessment in MVP.

## 10. Ledger rules

Ledger is append-only.
Every cycle settle writes:
- one CONTRIBUTION obligation per paying share
- one HOST_FEE obligation or income entry
- one PAYOUT obligation to winner
- optional LATE_FEE later

A Payment may settle one or many obligations.
Status: UNPAID, PARTIAL, PAID, WAIVED.
Host confirms money in real life (cash, bank, VietQR later).

Never delete ledger rows. Reverse with a reversing entry.

## 11. Group lifecycle

```
DRAFT -> RECRUITING -> READY -> RUNNING -> COMPLETED
                    \-> CANCELLED (only if no settled cycle)
```

- DRAFT: host edits rules
- RECRUITING: invite members, assign shares
- READY: `sum(shares) == N` and rules frozen
- RUNNING: cycles in progress
- COMPLETED: all cycles SETTLED
- CANCELLED: never started or aborted with no money moved

## 12. Risk controls (include in design, some later)

Must have in MVP:
- unique member identity per host (phone)
- share-level alive/dead flag
- immutable bids after close
- immutable ledger
- audit log
- host confirmation of payments (no silent auto-cash)

Good later:
- ID card image
- guarantor
- deposit before start
- credit score from past groups
- default waterfall (use deposit, then guarantor)
- dispute ticket with snapshot of cycle math
- export traditional so sach PDF/Excel

## 13. Notification events

- cycle opened
- bid reminder (T-24h, T-2h)
- bid closed and winner published
- contribution due
- overdue
- payout ready
- payment confirmed
- group completed

Channels later: in-app, Telegram, Zalo, SMS, email.
MVP: in-app only.

## 14. Transparency rules

Member can see:
- group rules
- cycle calendar
- own bids
- published winner after close (amount B and winner name)
- own obligations and payments
- own running balance
- public cycle summary (grossPot, T, netPayout)

Member cannot see:
- other members' sealed bids before close
- other members' private contact extras unless host shares
- host-only profit notes

Host can see everything in their groups.

Export parity (Step 30):
- an export file contains exactly the same fields and passes exactly the same
  permission checks as its JSON report endpoint; exporting exposes nothing new
- member exports cover own shares only and never contain host fee or host
  profit numbers
- exports are point-in-time snapshots of a report; the append-only ledger
  stays the source of truth and the JSON endpoints remain live

## 15. Attachments (Step 32)

- A file document (receipt photo, PDF contract, CCCD scan, expense sheet, ...)
  attaches to exactly one target: a **PAYMENT** (proof of collection) or a
  **MEMBER profile** (member paperwork). No free-floating files.
- Bytes live in PostgreSQL (BYTEA), owner-scoped like every other entity; a
  query only ever sees the caller's own tenant (`owner_id`).
- HOST uploads only. A file must pass the type allow-list and the size cap,
  both governed by SECURITY settings; failures are explicit 400s and never
  part-persist.
- Deleting an attachment deletes the row + bytes; the payment/ledger itself is
  untouchable (append-only ledger still holds). Attachment deletion is not a
  ledger event.
- Money or math never depend on attachments; files are evidence for the host's
  own records, not inputs to any formula. Export parity (Step 30) is
  unaffected — files are not part of export responses.

## 16. Obligation QR (Step 34)

- Every obligation that a member still owes (a `debts` row, or a `statement`
  entry with `remaining > 0`) can carry a **merchant-presented QR payload**
  describing that one obligation and nothing else. Its purpose is convenience:
  the member scans it in their banking app to wire the exact `remaining` amount
  with an unambiguous reference. It is NOT proof of payment and no code ever
  derives money from a QR — all financial truth stays in the append-only ledger
  (host records the payment manually per §10).
- One QR = one ledger entry. Reference (bill number, EMVCo 62/01):
  `TONGTIN <groupCode>-O<ledgerEntryId>` (e.g. `TONGTIN A-12-O345`). Amount =
  the entry's `remaining` minor units rendered as a major-unit decimal from the
  group currency exponent (pure long math; zero float per §9).
- The payload is a real EMVCo string (CRC16-CCITT checked), generated by a pure
  Java helper. Hosts and members both read QRs; scope rules are owner/member —
  a host reads own-group obligations, a member reads their own share's.
- Presence is governed by the `payments_khqr_enabled` PAYMENT setting: when
  false the `khqr` fields are simply absent from `debts`/`statement` responses
  and the render endpoint 404s. The subscription KHQR (ABA PayWay checkout) is
  a separate string and is untouched.

## 17. Telegram (Step 35)

- **Host-only channel.** A host links one Telegram chat id
  (`owner_accounts.telegram_chat_id`) and receives messages on their own chat.
  Members are never messaged over Telegram; the existing in-app notifications
  (§13) remain the member channel. Step 14's "sync transitions only" decision
  stands — Telegram is synchronous best-effort, not a scheduler for money.
- **Two message kinds:**
  1. **Events** — instant pings mirroring the host-relevant in-app milestones:
     cycle opened, winner published / payout confirmed, payment recorded,
     group completed, late fees assessed. Vietnamese copy, same tone as §13.
  2. **Daily due-digest** — one message per host every day at the configured
     `HH:mm` (opt-in, `telegram_daily_digest_enabled`; default off) listing
     each of their groups that has unpaid obligations (`UNPAID`/`PARTIAL`, due
     in the past): group name + code, number of outstanding member-obligations,
     total remaining minor, and a per-host total line. No member names, no PII;
     amounts are minor units rendered with exponent-aware long math (§9).
- **Settings gates** — everything is off until an admin configures a bot:
  `telegram_bot_token` blank ⇒ events, digest and test-send all no-op. A host
  cannot receive anything until they (a) link a chat id and (b) the operator
  has set the token. Additional gates: `telegram_events_enabled`,
  `telegram_daily_digest_enabled`, `telegram_daily_digest_time`.
- **Failure semantics** — outbound sends are strictly best-effort: any error
  (unset token, network, bad chat id, Telegram 4xx/5xx) is logged and swallowed.
  A Telegram send can never throw into, delay-financially-harm, or roll back a
  financial operation. Audit rows for link/unlink/test are written in the same
  transaction; the send itself is a side effect after the business write.
- **Money is untouched** — Telegram only *announces*; it never creates,
  confirms, or mutates a payment or obligation. Financial truth stays the
  append-only ledger (§10).

## 18. Quick-pay (Step 36)

- **Purpose.** The host can settle a member in one step instead of typing one
  allocation per obligation. Only the amount + member is provided; the backend
  chooses *which* obligations to pay.
- **Candidate pool.** All obligations of that member *in this group* with
  `direction = 'IN'` (contributions AND LATE_FEE rows) whose status is
  `UNPAID` or `PARTIAL`. Payout/credit-side entries are never auto-paid.
  `allocateLateFees = false` drops LATE_FEE rows from the pool (contributions
  only).
- **Allocation order (locked).** `ORDER BY due_at ASC, entry id ASC` — the
  oldest-due obligation is paid first; ties break to the lower entry id
  (deterministic regardless of replay). Each obligation receives
  `min(remaining, leftover)`; the allocations sum exactly to the paid amount.
- **Equality rules.** A quick-pay is still a normal `payments` row: allocations
  reference `ledger_entries`, the sum rule of §10 applies, idempotency replay
  (`Idempotency-Key`) returns the same payment, and notifications/Telegram/audit
  behave identically to POST /payments. The only difference is allocation
  *origin*: the server computed them (`autoAllocated = true` in the audit
  payload), not the host.
- **Fail-fast.** Amount above the member's total remaining → 400 before any
  write; member with no share in the group → 404; nothing outstanding → 400.
  No partial writes on a failed quick-pay.
- **Receipt.** Attaching a receipt stays a separate step on the saved payment
  (`POST /payments/{id}/attachments`, §15) — the quick-pay JSON call carries no
  file.
