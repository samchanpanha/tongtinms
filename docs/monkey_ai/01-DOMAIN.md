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
