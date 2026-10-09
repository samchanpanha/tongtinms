# Settings: Formulas, Meeting Safety, Blacklist, Design Templates

Verdict: **good product direction, not all of it is MVP, and custom formulas must not be free-form code.**

Related current objects: OwnerAccount defaults, Group frozen rules, MemberProfile phone.
Do not code until scheduled. Current code step remains 01.
Currency storage: `docs/07-MULTI-CURRENCY.md`. Engine rules: `docs/08-FORMULA-ENGINE.md`.

## 1. Overall judgment

| Idea | Product value | MVP? | Why |
|---|---|---|---|
| Owner default formula preset | High | Late MVP after groups exist | Speeds every new day hoi |
| Custom formulas as a closed preset pack | High if parameterized | Phase 2 | VN hosts use local variants |
| Free-form / script formulas | Dangerous | Never | Money bugs, exploits, untestable |
| Meeting issue prevention | High | Partial in MVP | Disputes are why paper fails |
| Owner blacklist | High | After member directory | Stops known hoi vo in that tenant |
| Global public blacklist | High risk | No | Defamation, PII, false reports |
| Default group design templates | High | After Step 05 | Same as "mau hoi" |
| Custom group templates | High | Phase 2 | Owner clones own recipes |
| Visual UI/PDF themes | Medium | With so sach export | Not needed to calculate money |

## 2. Formulas: default vs custom

### 2.1 What is actually needed

Chu Hoi does not want to type algebra. They want:

- pick a known style (Hoi khui, Hoi deu, first cycle for host)
- save their usual `C`, `N`, `T`, bid step as defaults
- reuse last month's group as a template
- never change math after the group is RUNNING

That is **presets + parameters**, not a formula editor.

### 2.2 Allowed model

```
FormulaPreset (system, immutable)
  code, name, engineVersion, description
  allowedParams: C, N, T, minBid, maxBid, bidStep, hostFeeType...
  calculatorId: BIDDING_CLASSIC | FIXED_EQUAL | FIRST_CYCLE_HOST | ...

OwnerFormulaDefaults (per tenant)
  presetCode
  default C, N, cycleUnit, hostFee...

Group.formula
  presetCode + frozen param snapshot + engineVersion
```

At group START, copy preset + params onto the group. Forever frozen.
Engine version is stored so old groups do not silently change if we patch a formula later.

### 2.3 System default presets (ship these)

| Code | Meaning |
|---|---|
| `BIDDING_CLASSIC` | docs/01-DOMAIN.md section 9.2 (MVP source of truth) |
| `FIXED_EQUAL` | section 9.3 |
| `BIDDING_NO_FEE` | classic with T=0 |
| `FIRST_CYCLE_HOST` | phase 2, already named in domain |

### 2.4 "New custom formulas" - how to do it safely

Do **not** accept owner-typed JavaScript, Excel, or `eval`.

Safe custom = one of:

1. **Clone a system preset and change parameters only** (C, T, min/max bid). This is MVP-compatible as group fields we already have.
2. **Named owner template** that stores those parameters. Phase 2.
3. **New calculator in code** added by developers with unit tests against worked examples. Not a runtime editor.

If a real custom engine is ever needed, use a closed DSL:

Allowed tokens: `C N D A B T cycleNo`
Allowed ops: `+ - *` and `round`
Forbidden: division by variable, loops, member names, time, random except RANDOM group type
Invariants the engine must reject before save:

```
netPayout >= 0
winnerPay == 0
alivePay >= 0
deadPay == C           (unless preset explicitly says otherwise)
all values integer VND
A + D == N
```

Custom formula cannot apply to a RUNNING group. Only DRAFT.

### 2.5 Default formulas setting (Owner)

On OwnerAccount / onboarding:

- defaultPreset = BIDDING_CLASSIC
- defaultBaseAmount, shareCount, cycleUnit, hostFeeType, hostFeeValue, bidStep

`POST /groups` prefills from owner defaults. Host can still edit before START.

This is the right "settings" feature. Small, high value.

## 3. Prevent issues in meetings (ky hop / ngay khui)

"Meeting" here is the cycle session: collect money, open bids, announce winner. Not a video product.

Typical failures:

- two people claim they won
- host recalculates on paper and numbers differ
- member absent but was bid for
- cash received but not marked
- collusion / bid leaked
- argument with no snapshot

### 3.1 MVP meeting safety (already planned, keep)

- sealed bids until close
- server-side winner + payout (browser is not truth)
- append-only ledger
- published cycle summary (grossPot, T, netPayout, winner)
- host confirms payments, no silent cash
- audit log

### 3.2 Add later as CycleSession (phase 2)

Not in Step 01-10.

```
CycleSession
  cycle_id
  scheduled_at, opened_at, closed_at
  location / online note
  attendance[]  PRESENT, ABSENT, PROXY
  minutes_text
  dispute_open boolean
```

Rules:

- cannot close-and-calculate if `dispute_open`
- proxy bid only if share owner pre-authorized
- after calculate, freeze a **math snapshot** (P11) members can download
- host cannot edit snapshot; only reversing ledger entries
- optional second-person confirm (host + one witness) before PAYOUT_PENDING

This prevents meetings from turning into "trust my Excel".

Do **not** build chat rooms, Zoom, or live video.

## 4. Blacklist

### 4.1 Owner-local blacklist: YES

Each Chu Hoi keeps their own list of phones they will not add.

```
owner_blacklist
  owner_id, phone, reason, created_at, created_by
  unique (owner_id, phone)
```

On add share / add member: if phone in this owner's blacklist -> 409 with reason.

Owner can remove (unlist), which is audited. Do not delete the row; set `active=false`.

This is the correct first blacklist. Scope is tenant. No public shaming.

Schedule: after Step 04 member directory, as Step 04b or phase 2. Not before identity exists.

### 4.2 Global / community blacklist: NO in this product phase

Reasons:

- false report destroys someone's name in a village network
- phone reuse, family phones, shop phones
- legal / defamation
- conflicts with owner-only data isolation

Later, if ever:

- platform admin flag only, not visible as a public wall
- facts only (unpaid obligation in a COMPLETED group), not free-text insults
- member can dispute

Reputation (P5) can stay internal score, not a public blacklist.

### 4.3 Auto-suggest, not auto-ban

When adding a member who DEFAULTED in **this same owner's** past groups, warn:

```
This phone defaulted in HOI-2025-003, unpaid 2_000_000 d
```

Host still decides. Blacklist is explicit, warning is automatic.

## 5. Design templates

Two different things. Do not mix them in one table.

### 5.1 Group design templates (mau hoi) - good

A template is a **recipe for a group**, not CSS.

System defaults (read-only):

| Template | Typical |
|---|---|
| Hoi thang 10 nguoi 1 trieu (khui) | N=10, C=1e6, MONTH, BIDDING, T=100000 |
| Hoi tuan 20 nguoi | N=20, WEEK, BIDDING |
| Hoi deu (super) | FIXED_EQUAL |

Owner custom templates:

- clone from a completed group or from a system template
- store name, formula preset, params, reminder offsets, default late fee
- creating a group = apply template then edit

This replaces parked P12 and is the right "custom design templates use in system".

Freeze: changing a template never changes RUNNING groups.

### 5.2 Visual / print templates - later

Receipt, so sach PDF, member statement layout, logo, colors.

Only useful with P2 export. Owner logo + 2-3 print skins is enough.
Do not build a full theme marketplace in MVP.

## 6. Settings surface (where it lives)

Owner settings page `/host/settings` later:

1. Profile / bank / CCCD (already in owner onboarding)
2. Default formula + default group numbers
3. Templates list
4. Blacklist
5. Meeting defaults (bid close offset, require attendance) phase 2

Do not put formula scripts on this page.

## 7. Invariants (never violate)

- Money stays `long` VND
- Calculator is Java on the server
- Worked examples in `docs/01-DOMAIN.md` remain the BIDDING_CLASSIC tests
- Custom/preset cannot break `winnerPay = 0` and last-cycle `B = 0` unless a tested new preset says so
- Blacklist is per `owner_id`
- Templates do not mutate running groups
- Meeting dispute flag blocks calculate

## 8. Roadmap placement

Keep Step 01-17 as they are.

Step 35 Telegram (delivered) — settings live in the admin catalog as a new
**TELEGRAM** category (5th), NOT on `/host/settings`:
`telegram_bot_token` (SECRET; blank ⇒ whole channel off), `telegram_events_enabled`
(BOOLEAN true), `telegram_daily_digest_enabled` (BOOLEAN false), `telegram_daily_digest_time`
(STRING `HH:mm`, `08:00`). Hosts only ever supply their own chat id + a test-send
(`/host/telegram`), matching the "host gateway" pattern: the secret stays
admin-owned and admins can switch every outbound ping off in one place.

Telegram-specific risk notes:
- **Message ≠ money.** Telegram is announce-only; a phishing-style false
  "payment recorded" ping can never confirm a payment server-side.
- **No member PII in digests.** Digest messages carry group name/code and
  aggregate counts + total minor units — no member names/phones.
- **Outbound is fire-and-forget.** Wrap every send; a Telegram 403/404 (bot
  removed from chat) or network failure is logged, never raised into the
  business call.
- **Token is SECRET-typed** (masked on GET like `payway_api_key`); a token can
  be rotated by overwriting the value.

Parked additions:

```
P13. Owner default formula + group number settings
P14. System group templates + owner custom templates
P15. Owner-local phone blacklist + defaulted-phone warning
P16. CycleSession attendance + dispute lock + math snapshot
P17. Print/visual statement templates
```

Earliest sensible code:

- P13 after Step 05 (group draft exists to prefill)
- P14 after Step 05
- P15 after Step 04
- P16 after Step 10 (calculate exists)
- P17 after Step 15 reports

## 9. Decision log

- Default formulas as owner settings: YES, later
- Custom formulas as parameter templates: YES, later
- Custom formulas as user-written expressions: NO
- Meeting issue prevention: YES as cycle snapshot + dispute lock, not a meeting app
- Blacklist: YES owner-local; NO global wall
- Default + custom design templates: YES as group recipes; visual themes later

## 10. Telegram decision log (Step 35, 2026-10-09)

- Channel is **host-only** (no member Telegram); in-app feed stays the member channel.
- Real Bot API call (`sendMessage`) behind a tiny `TelegramClient` seam — hermetic
  tests never touch the network (token blank short-circuits; `@MockBean` captures sends).
- Settings-gated: `telegram_bot_token` blank ⇒ events + digest + test all no-op.
- Digest opt-in (`telegram_daily_digest_enabled` default false), runs daily at
  `telegram_daily_digest_time`; Step 14's "sync transitions only, no @Scheduled money
  reminders" is respected — the digest only *announces* owed totals, never triggers them.
- Events are delivered synchronously after the business write, best-effort
  (mirror `NotificationService`); never throws into / rolls back a financial op.
- Chat id stored on `owner_accounts.telegram_chat_id` (V17, BIGINT, nullable, no FK —
  it is a third-party identifier, not an app row).

## 11. Quick-pay decision log (Step 36, 2026-10-09)

- The **backend** picks which obligations are paid; the host only supplies
  member + total. Pool = member's UNPAID/PARTIAL `direction='IN'` obligations in
  the group, LATE_FEE rows included by default (`allocateLateFees` opt-out).
- Allocation order is **oldest-due-first, tie to lower entry id** (plan §20);
  allocations sum exactly to the paid amount (min(remaining, leftover)).
- Quick-pay is a **normal `payments` row** — same core path as POST /payments
  (locking, idempotency, notifications, Telegram ping, audit with
  `autoAllocated: true`). Receipt attach stays the existing
  `POST /payments/{id}/attachments`.
- Fail-fast: amount > total remaining → 400; member not in group → 404; nothing
  outstanding → 400. No migrations.
