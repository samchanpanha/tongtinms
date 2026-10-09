# Multi-Currency Groups and Minor-Unit Storage

Decision: **one currency per group, amounts stored as integer minor units, reports always carry currency, no FX in MVP.**

Do not code until Step 02 schema. Current code step remains 01.

## 1. Why

Chu Hoi in VN uses VND. Same product may run USD / THB / KHR / LAK / SGD groups for overseas or border communities. Mixing float money or silent VND labels in reports is how disputes start.

Rules:
- User **chooses currency** when creating a group (prefilled from owner default).
- Currency is **frozen** at group START with the rest of the rules.
- Every money column is **bigint minor units** of that currency. Never `numeric`/`double`/`decimal` for pots, bids, fees, payments.
- Every report row includes `currency` + `amountMinor` + `exponent`. UI formats from that. No report may assume VND.

## 2. Minor units

ISO 4217 exponent = number of digits after the major unit.

| Code | Name | Exponent | 1 major unit stored as |
|---|---|---|---|
| VND | Vietnamese dong | 0 | 1 |
| KHR | Cambodian riel | 0 | 1 |
| LAK | Lao kip | 0 | 1 |
| THB | Thai baht | 2 | 100 |
| USD | US dollar | 2 | 100 |
| SGD | Singapore dollar | 2 | 100 |

Examples:
- VND 1_000_000 dong -> store `1000000`
- USD 10.50 -> store `1050`
- THB 500.00 -> store `50000`

Display: divide by `10^exponent` only in the presenter. Calculator stays on longs.

Percent / rate fields are **not** money. Store as integer basis points (`bps`): 100 bps = 1.00%.

## 3. What is not in scope

- No FX conversion, no mid-market rate table
- No group with two currencies
- No payment in currency B against a group in currency A
- No owner-profit total that sums VND+USD into one number
- No crypto

Owner dashboard profit is a **map by currency**. Cross-currency need a later FX module (parked).

## 4. Schema changes

### 4.1 New lookup: `currencies`

```sql
CREATE TABLE currencies (
  code        CHAR(3) PRIMARY KEY,          -- ISO 4217
  name        TEXT NOT NULL,
  symbol      TEXT NOT NULL,                -- d, $, THB
  exponent    SMALLINT NOT NULL CHECK (exponent BETWEEN 0 AND 4),
  rounding    TEXT NOT NULL DEFAULT 'HALF_UP',
  active      BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO currencies (code, name, symbol, exponent) VALUES
  ('VND', 'Vietnamese dong', 'd', 0),
  ('KHR', 'Cambodian riel', 'CR', 0),
  ('LAK', 'Lao kip', 'K', 0),
  ('THB', 'Thai baht', 'THB', 2),
  ('USD', 'US dollar', '$', 2),
  ('SGD', 'Singapore dollar', 'S$', 2);
```

Seed is system data. Hosts cannot insert currencies in MVP.

### 4.2 Owner default

```sql
ALTER TABLE owner_accounts
  ADD COLUMN default_currency CHAR(3) NOT NULL DEFAULT 'VND'
    REFERENCES currencies(code);
```

`POST /groups` prefills `currency = owner.default_currency`.

### 4.3 Group (frozen at START)

Replace the loose `currency` comment with a real FK. All group money columns are minor units of `groups.currency`.

```sql
ALTER TABLE groups
  ALTER COLUMN currency SET DATA TYPE CHAR(3),
  ADD CONSTRAINT groups_currency_fk
    FOREIGN KEY (currency) REFERENCES currencies(code);

-- money (minor units of groups.currency)
-- groups.base_amount              BIGINT NOT NULL CHECK (base_amount > 0)
-- groups.min_bid                  BIGINT NOT NULL CHECK (min_bid >= 0)
-- groups.max_bid                  BIGINT NOT NULL
-- groups.bid_step                 BIGINT NOT NULL CHECK (bid_step > 0)
-- groups.late_fee_value           BIGINT NOT NULL DEFAULT 0

-- split mixed host fee:
-- groups.host_fee_type            TEXT  NONE | FIXED_PER_CYCLE | PERCENT_OF_POT | FIRST_CYCLE_TO_HOST
-- groups.host_fee_minor           BIGINT NOT NULL DEFAULT 0   -- used when FIXED_PER_CYCLE
-- groups.host_fee_bps             INTEGER NOT NULL DEFAULT 0  -- used when PERCENT_OF_POT, 100 = 1%

ALTER TABLE groups
  ADD CONSTRAINT groups_max_bid_lt_base CHECK (max_bid < base_amount),
  ADD CONSTRAINT groups_fee_nonneg CHECK (host_fee_minor >= 0 AND host_fee_bps >= 0);
```

`host_fee_value` from earlier docs is **removed**. Use `host_fee_minor` or `host_fee_bps` by type. Do not store percent in a money column.

### 4.4 Cycle snapshot (already minor units)

```sql
-- cycles.winning_bid   BIGINT  -- minor units, 0 on last cycle / FIXED
-- cycles.gross_pot     BIGINT
-- cycles.host_fee      BIGINT  -- computed T in minor units
-- cycles.net_payout    BIGINT
```

Add denormalized currency for report queries without join surprises:

```sql
ALTER TABLE cycles
  ADD COLUMN currency CHAR(3) NOT NULL REFERENCES currencies(code);
```

Copy from group at cycle open. Must equal `groups.currency`. App enforces; DB trigger optional.

### 4.5 Bids, ledger, payments

```sql
ALTER TABLE bids
  ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
  ADD COLUMN currency CHAR(3) NOT NULL REFERENCES currencies(code);

ALTER TABLE ledger_entries
  ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  ADD COLUMN currency CHAR(3) NOT NULL REFERENCES currencies(code);

ALTER TABLE payments
  ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  ADD COLUMN currency CHAR(3) NOT NULL REFERENCES currencies(code);

ALTER TABLE payment_allocations
  ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor > 0);
```

If a previous draft used `amount`, rename to `amount_minor` in Flyway. Never keep both.

Invariant: `bids.currency = cycles.currency = groups.currency`.
Payment currency must equal group currency or reject 409.

### 4.6 Formula / template money

```sql
-- owner_formula_defaults and group_templates (phase 2, P13/P14)
-- include currency CHAR(3) and all money as minor units of that currency
```

A VND template cannot be applied to a USD group without explicit convert, which we do not have. Apply-template copies currency too, or requires the new group to use the template currency.

### 4.7 Reports / read models

No separate report tables required in MVP. Views or queries **must** select currency:

```sql
CREATE VIEW v_cycle_public_summary AS
SELECT
  c.id,
  c.group_id,
  c.cycle_no,
  c.currency,
  cur.exponent,
  cur.symbol,
  c.winning_bid   AS winning_bid_minor,
  c.gross_pot     AS gross_pot_minor,
  c.host_fee      AS host_fee_minor,
  c.net_payout    AS net_payout_minor
FROM cycles c
JOIN currencies cur ON cur.code = c.currency;
```

Host profit:

```sql
-- never SUM across different currency
SELECT g.currency, SUM(c.host_fee) AS host_fee_minor
FROM cycles c
JOIN groups g ON g.id = c.group_id
WHERE g.owner_id = :ownerId AND c.status = 'SETTLED'
GROUP BY g.currency;
```

## 5. API money shape (all reports)

Every money field in JSON:

```json
{
  "currency": "VND",
  "amountMinor": 7100000,
  "exponent": 0,
  "symbol": "d"
}
```

Never return a bare number named `amount` without currency.
UI formatters: `formatMoney(amountMinor, exponent, symbol)` per locale (VN uses `.` thousands).

List/report endpoints that mix groups: array of totals **per currency**, plus `incomplete: true` if some rows lack currency (must not happen).

## 6. Formula engine interaction

Calculator inputs and outputs are `long` minor units. It does not know currency except:
- `bidStep` already in minor units
- percent fee: `T = round(grossPot * bps / 10000)` HALF_UP in integer math

Worked VND example is unchanged because VND exponent is 0.
Add one USD fixture in Step 07:

```
N=10, C=10_000 (USD 100.00), T=500 (USD 5.00), B=2_000 (USD 20.00)
grossPot = 0 + 9 * 8_000 = 72_000
netPayout = 72_000 - 500 = 71_500
```

Same ratios as the VND fixture, different exponent.

## 7. Flyway placement

| Version | When | Content |
|---|---|---|
| V1 | Step 02 | users only |
| V2 | Step 03 | owner_accounts + default_currency |
| V3 | Step 05 | currencies seed + groups with currency FK and minor-unit money |
| V4 | Step 08-11 | cycles/bids/ledger/payments amount_minor + currency |

If tables are created greenfield in later steps, bake these columns in from the first CREATE. Do not add `amount` then rename.

## 8. Application checks

On group create/update (DRAFT only):
- `currency` in `currencies.active`
- `base_amount % (10^0)` always (already integer)
- `bid_step` >= 1 minor unit
- `max_bid < base_amount`

On START: freeze `currency`.
On payment: body.currency must equal group.currency.
On report: join `currencies` for exponent; missing join is a bug.

## 9. Decision log

- Multi-currency: YES, choose per group, owner default VND
- Storage: bigint minor units + CHAR(3) currency on every money row
- FX: NO
- Reports: always include currency; profit grouped by currency
- `host_fee_value` split into `host_fee_minor` and `host_fee_bps`

## 10. EMVCo/KHQR amount rendering (Step 34)

EMVCo merchant-presented QRs carry the currency by its ISO 4217 **numeric**
code and the amount as a **major-unit decimal string**. Mapping (the only place
numeric codes live; a static `Map<String,Integer>` in the pure-Java KhqrGenerator
— no DB column):

| Code | Numeric | Exponent |
|---|---|---|
| VND | 704 | 0 |
| KHR | 116 | 0 |
| LAK | 418 | 0 |
| USD | 840 | 2 |
| THB | 764 | 2 |
| SGD | 702 | 2 |

Amount rule: `remaining` minor units → decimal string with exactly `exponent`
fraction digits, zero-padded, using long math only (zero-float invariant):
- exponent 0 → `aposLong(remaining)` (e.g. `800000`)
- exponent 2 → `whole + "." + zero-padded fraction` (e.g. `125045` → `1250.45`)

Any group currency not in the map is not QR-able (the generator throws a
`BadRequestException`-grade error that the read paths treat as "no QR").
