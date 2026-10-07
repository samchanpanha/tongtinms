# Default Formulas and Custom Formula Engine Rules

Source of truth for Step 07 calculator. Complements `docs/01-DOMAIN.md` section 9 and `docs/06-SETTINGS-TEMPLATES-RISK.md`.
Money is `long` minor units. Currency is a label; see `docs/07-MULTI-CURRENCY.md`.

## 1. Engine shape

```
FormulaEngine.calculate(CycleInput) -> CycleResult
```

Pure Java. No Spring, no DB, no clock, no random (except future RANDOM preset, injected RNG).
`engineVersion` is an integer frozen on the group. Changing Java code for a preset requires a new version id and must not change results for old versions.

```
CycleInput
  presetCode
  engineVersion
  C, N, D, A, B, cycleNo, cycleCount
  hostFeeType, hostFeeMinor, hostFeeBps
  minBid, maxBid, bidStep

CycleResult
  deadPay, alivePay, winnerPay
  grossPot, T, netPayout
  winnerShareId is NOT chosen here (bidding module supplies B + winner)
```

Winner selection is outside the money engine. Engine receives the winning bid `B` already validated.

## 2. Shared invariants (all presets)

Reject input if any fail:

1. `N >= 2`, `C > 0`, `A >= 1`, `D >= 0`, `A + D == N`
2. `cycleNo` in `1..cycleCount`, `cycleCount == N` in MVP
3. All money fields `>= 0` and `long`
4. `winnerPay == 0` always
5. `alivePay >= 0`, `deadPay >= 0`
6. `netPayout == grossPot - T`, `netPayout >= 0`, `T >= 0`
7. Last cycle (`cycleNo == cycleCount`) implies `A == 1` and `B == 0`
8. No floating point. Percent: `round(x * bps / 10000)` HALF_UP away from zero on .5

Rounding helper (integer only):

```
roundBps(amount, bps) =
  // HALF_UP: (amount * bps + 5000) / 10000  for amount >= 0, bps >= 0
```

## 3. Host fee `T` (shared)

```
NONE:                T = 0
FIXED_PER_CYCLE:     T = hostFeeMinor
PERCENT_OF_POT:      T = roundBps(grossPot, hostFeeBps)
FIRST_CYCLE_TO_HOST: not in engine v1 (phase 2)
```

Compute `grossPot` first, then `T` if percent, then `netPayout`.

## 4. System default presets

### 4.1 BIDDING_CLASSIC (engine v1, MVP default)

Vietnamese: Hoi khui / dau gia.

```
deadPay   = C
alivePay  = C - B
winnerPay = 0
grossPot  = D * C + (A - 1) * (C - B)
T         = as section 3
netPayout = grossPot - T
```

Bid checks (bidding module, before engine):
- `minBid <= B <= maxBid < C`
- `B % bidStep == 0`
- last cycle forces `B = 0` and skips auction

### 4.2 FIXED_EQUAL (engine v1)

Vietnamese: Hoi deu / super.

```
B is ignored and treated as 0
deadPay   = C
alivePay  = C
winnerPay = 0
grossPot  = (N - 1) * C
T         = as section 3
netPayout = grossPot - T
```

### 4.3 BIDDING_NO_FEE (engine v1)

Same as BIDDING_CLASSIC with `hostFeeType = NONE`. Can be a preset alias, not a second calculator.

### 4.4 FIRST_CYCLE_HOST (engine v2, phase 2)

Cycle 1: virtual host share wins, `B = 0`, members pay `C`, pot goes to host as first-cycle thuong / von. Not implemented until scheduled.

## 5. Worked fixtures (must unit-test)

### Fixture A - VND classic (exponent 0)

N=10, C=1_000_000, FIXED_PER_CYCLE T=100_000, BIDDING_CLASSIC

| Cycle | D | A | B | grossPot | netPayout |
|---|---|---|---|---|---|
| 1 | 0 | 10 | 200_000 | 7_200_000 | 7_100_000 |
| 2 | 1 | 9 | 150_000 | 7_800_000 | 7_700_000 |
| 10 | 9 | 1 | 0 | 9_000_000 | 8_900_000 |

### Fixture B - USD classic (exponent 2)

N=10, C=10_000 (USD 100.00), T=500 (USD 5.00), B cycle1=2_000 (USD 20.00)

| Cycle | grossPot | netPayout |
|---|---|---|
| 1 | 72_000 | 71_500 |

Same algebra as A. Proves engine is currency-blind.

### Fixture C - FIXED_EQUAL VND

N=10, C=1_000_000, T=100_000
Every cycle: grossPot=9_000_000, netPayout=8_900_000

### Fixture D - reject

- `B >= C` -> error
- `A + D != N` -> error
- last cycle `B != 0` -> error
- `PERCENT_OF_POT` that would make `T > grossPot` -> error

## 6. Custom formula engine rules

Custom does **not** mean user-typed code. Layers, in allowed order:

| Layer | Who | When |
|---|---|---|
| L0 Parameters | Host edits C,N,T,bids on a system preset | MVP group draft |
| L1 Owner template | Save those parameters as mau hoi | P14 |
| L2 New preset in git | Developer adds calculator + fixtures | product release |
| L3 Closed DSL | Host picks expressions from a whitelist | not scheduled |

### 6.1 Forbidden forever at runtime

- `eval`, JS, Excel, SQL, Groovy, SpEL
- division by a variable (only constant bps divisor 10000)
- loops, recursion, member names, wall clock
- reading other groups
- random except RANDOM group type with injected RNG
- changing a RUNNING group's preset or engineVersion

### 6.2 If L3 DSL is ever built

Allowed tokens: `C N D A B T cycleNo cycleCount`
Allowed ops: `+ - *`, `roundBps(x, bps)`, parentheses
Each of `deadPay alivePay winnerPay grossPot T` assigned once.
Must pass section 2 invariants on 20 generated random non-neg integer inputs plus fixtures A-C.
DSL source stored on group as text **and** compiled fingerprint. START freeze both.

### 6.3 Custom = clone preset (the real product rule)

```
OwnerCustomFormula
  owner_id
  name
  basePreset   -- must be a system code
  params snapshot (C, N, fees, bid limits, currency)
  engineVersion copied from base
```

Creating a group from custom formula copies snapshot. No new algebra.

## 7. Reports and formulas

Reports call `CycleResult` already stored on `cycles` (gross_pot, host_fee, net_payout). They do not re-run algebra except a "recalculate check" in admin tests.

Every report money field uses the JSON shape in `docs/07-MULTI-CURRENCY.md` section 5.

## 8. Module boundary

```
bidding -> validates B, picks winner share
formula -> CycleResult from C,N,D,A,B,fees
ledger  -> writes obligations from CycleResult
reports -> read ledger + cycle snapshot + currency
```

Do not put winner picking inside FormulaEngine.

## 9. Decision log

- Default engine: BIDDING_CLASSIC v1
- Custom: parameter clone only until a new git preset
- No eval
- Fixtures A-D required in Step 07
- Currency-blind longs; fixture B proves it
