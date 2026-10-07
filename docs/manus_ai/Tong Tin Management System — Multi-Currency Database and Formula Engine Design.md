# Tong Tin Management System — Multi-Currency Database and Formula Engine Design

**Version:** 0.1  
**Status:** Design baseline for implementation  
**Scope:** Multi-currency group support, integer minor-unit storage, default Tong Tin formulas, and safe custom formula execution.

## 1. Design decisions

1. A Tong Tin group has one settlement currency for the MVP.
2. Every monetary record stores the ISO 4217 currency code and an integer `amount_minor`.
3. Currency is selected before a group becomes active and is locked when the first active cycle begins.
4. Historical amounts are never rewritten because a group setting changes.
5. Formula definitions are structured data, not arbitrary code.
6. A formula used by an active cycle is immutable; changes create a new version.
7. The ledger is authoritative. Calculated balances are read models.
8. Reports group and total by currency. Cross-currency conversion is a separate, explicit reporting operation.

## 2. Currency model

### 2.1 Currency reference table

```sql
CREATE TABLE currencies (
    code                CHAR(3) PRIMARY KEY,
    name                TEXT NOT NULL,
    symbol              TEXT NOT NULL,
    minor_unit_digits   SMALLINT NOT NULL CHECK (minor_unit_digits BETWEEN 0 AND 6),
    rounding_mode       TEXT NOT NULL DEFAULT 'HALF_UP'
                        CHECK (rounding_mode IN ('HALF_UP', 'DOWN', 'UP', 'HALF_EVEN')),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Use the currency code as the stable key. Do not use the display symbol as an identifier because symbols can be ambiguous.

### 2.2 Group changes

```sql
ALTER TABLE groups
    ADD COLUMN currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    ADD COLUMN currency_locked_at TIMESTAMPTZ NULL,
    ADD COLUMN rounding_mode TEXT NOT NULL DEFAULT 'HALF_UP',
    ADD CONSTRAINT groups_rounding_mode_ck
        CHECK (rounding_mode IN ('HALF_UP', 'DOWN', 'UP', 'HALF_EVEN'));
```

Recommended state rule:

```text
DRAFT: currency may be changed.
ACTIVE with no active cycle: currency may be changed through a logged settings action.
First active cycle created: set currency_locked_at.
After lock: ordinary updates to currency_code are rejected.
```

Enforce the lock in the service layer and with a database trigger or stored procedure for defense in depth.

### 2.3 Monetary columns

Use `BIGINT amount_minor` for monetary values. The value represents the smallest unit for the currency:

```text
VND with 0 minor digits: 1,000 VND → amount_minor = 1000
USD with 2 minor digits: $12.34 → amount_minor = 1234
```

Add these columns to all money-bearing entities:

```sql
ALTER TABLE transactions
    ADD COLUMN currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0);

ALTER TABLE ledger_entries
    ADD COLUMN currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    ADD COLUMN amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0);

ALTER TABLE bids
    ADD COLUMN currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    ADD COLUMN bid_amount_minor BIGINT NOT NULL CHECK (bid_amount_minor >= 0);

ALTER TABLE cycle_results
    ADD COLUMN currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    ADD COLUMN gross_pot_minor BIGINT NOT NULL CHECK (gross_pot_minor >= 0),
    ADD COLUMN total_fee_minor BIGINT NOT NULL CHECK (total_fee_minor >= 0),
    ADD COLUMN payout_minor BIGINT NOT NULL CHECK (payout_minor >= 0);
```

For a new implementation, prefer explicit names such as `contribution_amount_minor`, `winning_bid_minor`, and `payout_minor` over a generic `amount`.

### 2.4 Group-currency integrity

A PostgreSQL foreign key cannot directly compare a transaction’s currency with its group’s currency unless the relationship is modeled for that purpose. Use one of these patterns:

**Preferred service + trigger pattern:** validate the currency in the command service and in a database trigger before insert/update.

**Composite-key pattern:** expose `(group_id, currency_code)` from `groups` through a unique constraint and reference it from group-scoped tables.

```sql
ALTER TABLE groups
    ADD CONSTRAINT groups_id_currency_uq UNIQUE (id, currency_code);

ALTER TABLE transactions
    ADD COLUMN group_id UUID NOT NULL,
    ADD CONSTRAINT transactions_group_currency_fk
      FOREIGN KEY (group_id, currency_code)
      REFERENCES groups (id, currency_code);
```

Use the composite-key pattern for the ledger and transaction tables where currency mismatch would be dangerous.

### 2.5 Constraints and indexes

```sql
ALTER TABLE transactions
    ADD CONSTRAINT transactions_amount_ck CHECK (amount_minor >= 0);

CREATE INDEX transactions_group_currency_time_idx
    ON transactions(group_id, currency_code, occurred_at);

CREATE INDEX ledger_entries_group_currency_idx
    ON ledger_entries(group_id, currency_code);

CREATE INDEX reports_group_currency_period_idx
    ON report_runs(group_id, currency_code, period_start, period_end);
```

Use `BIGINT` overflow checks in the application. Reject amounts outside the supported range before database insertion.

## 3. Historical data migration

1. Create and seed the `currencies` table.
2. Identify the currency for every existing group.
3. Backfill `currency_code` on groups.
4. Convert existing decimal amounts to integer minor units using the group currency precision.
5. Recalculate checksums and compare old versus new balances.
6. Backfill transaction and ledger currency codes from the group.
7. Run reconciliation reports by group and currency.
8. Only then make new currency columns `NOT NULL`.
9. Lock historical groups according to their cycle state.

Never multiply amounts by 100 blindly. VND commonly uses whole-unit recording while USD commonly uses two decimal places; use the currency reference table.

## 4. Reporting model

Every report run should store:

```sql
CREATE TABLE report_runs (
    id                  UUID PRIMARY KEY,
    group_id            UUID NULL REFERENCES groups(id),
    currency_code       CHAR(3) NOT NULL REFERENCES currencies(code),
    report_type         TEXT NOT NULL,
    period_start        TIMESTAMPTZ NOT NULL,
    period_end          TIMESTAMPTZ NOT NULL,
    timezone             TEXT NOT NULL,
    formula_version_ids  JSONB NOT NULL DEFAULT '[]',
    generated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_by         UUID NOT NULL REFERENCES users(id),
    parameters           JSONB NOT NULL DEFAULT '{}'
);
```

A cross-group dashboard must return separate currency buckets:

```json
{
  "currency": "VND",
  "total_contributions_minor": 150000000,
  "total_payouts_minor": 90000000
}
```

If a future conversion view is requested, persist the conversion metadata:

```sql
CREATE TABLE exchange_rate_snapshots (
    id                  UUID PRIMARY KEY,
    source_currency     CHAR(3) NOT NULL REFERENCES currencies(code),
    target_currency     CHAR(3) NOT NULL REFERENCES currencies(code),
    rate                NUMERIC(30, 12) NOT NULL CHECK (rate > 0),
    rate_source         TEXT NOT NULL,
    rate_timestamp      TIMESTAMPTZ NOT NULL,
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

A converted total is a reporting estimate, not a settlement amount.

## 5. Formula domain model

### 5.1 Formula profile

```sql
CREATE TABLE formula_profiles (
    id                  UUID PRIMARY KEY,
    scope_type          TEXT NOT NULL CHECK (scope_type IN ('SYSTEM', 'HOST', 'GROUP')),
    scope_id            UUID NULL,
    name                TEXT NOT NULL,
    description         TEXT NOT NULL,
    version              INTEGER NOT NULL CHECK (version > 0),
    definition          JSONB NOT NULL,
    status               TEXT NOT NULL CHECK (status IN
                         ('DRAFT', 'TESTED', 'PENDING_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED')),
    created_by           UUID NOT NULL REFERENCES users(id),
    approved_by          UUID NULL REFERENCES users(id),
    effective_from      TIMESTAMPTZ NULL,
    effective_to        TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (scope_type, scope_id, name, version)
);
```

A group cycle stores the exact formula profile and version used:

```sql
ALTER TABLE group_cycles
    ADD COLUMN formula_profile_id UUID NOT NULL REFERENCES formula_profiles(id),
    ADD COLUMN formula_version INTEGER NOT NULL;
```

### 5.2 Formula test cases

```sql
CREATE TABLE formula_test_cases (
    id                  UUID PRIMARY KEY,
    formula_profile_id  UUID NOT NULL REFERENCES formula_profiles(id),
    name                TEXT NOT NULL,
    input_fixture       JSONB NOT NULL,
    expected_output     JSONB NOT NULL,
    last_result         JSONB NULL,
    passed              BOOLEAN NULL,
    tested_at           TIMESTAMPTZ NULL
);
```

## 6. Default formula profiles

The defaults below are safe starting profiles, not universal legal or cultural definitions. A Host must confirm that the selected profile matches the community’s written rules.

### Profile A — Fixed rotation

Use when the winner/order is predetermined and no bidding discount is used.

Inputs:

```text
N = active member count
C = configured contribution per member for the cycle
F = host fee
A = other approved adjustment
```

Formulas:

```text
Gross pot = N × C
Payout = Gross pot − F + A
```

Winner selection:

```text
winner = member assigned to the current rotation position
```

Validation:

- Exactly one winner per cycle.
- Winner must be eligible and not already paid for the same cycle.
- Payout cannot be negative.
- Fee cannot exceed the configured cap.

### Profile B — Highest discount wins

Use only when the group’s written rule says the highest valid discount wins.

Inputs:

```text
B_w = highest valid bid/discount
E = number of members eligible for discount allocation
D = discount allocation amount
```

Formulas:

```text
Gross pot = N × C
Payout = Gross pot − B_w − F + A
Shared discount credit = D ÷ E
```

The default is `D = B_w`, but the allocation rule must be explicit. Possible allocation modes:

- `SHARED_AMONG_ELIGIBLE`: distribute `B_w` across eligible members.
- `CREDIT_TO_NON_WINNERS`: credit only members who have not won.
- `HOST_DEFINED`: require a custom allocation map.
- `NONE`: discount reduces payout but is not redistributed.

Winner selection:

```text
winner = eligible member with the highest valid B_w
```

### Profile C — Lowest bid wins

Some communities define the bid as a fee or amount where the lowest valid offer wins. This must be a separate profile to prevent using the wrong direction.

```text
winner = eligible member with the lowest valid bid
Payout = Gross pot − winning_bid − F + A
```

Use an explicit `bid_direction = LOWEST_WINS`. Never infer bid direction from a label such as “bid.”

### Profile D — Draw/ticket selection

Use when eligible members are selected by a transparent draw rather than bid amount.

```text
winner = deterministic draw from eligible member set
Payout = Gross pot − F + A
```

Store the draw seed, eligibility snapshot, algorithm version, and result ID so the outcome can be audited without exposing private information unnecessarily.

### Dead/alive contribution rule

The terms “dead” and “alive” vary by community. Model the status transition explicitly:

```text
status_after_win = DEAD or WON or CUSTOM_LABEL
contribution_for_next_cycle(status) = configured amount for that status
```

Example structured rule:

```json
{
  "winner_status_after_cycle": "DEAD",
  "contribution_rules": {
    "ALIVE": {"type": "FIXED", "amount_minor": 1000000},
    "DEAD": {"type": "FIXED", "amount_minor": 1000000}
  }
}
```

Do not assume that “dead” always means a person who already won. Confirm the local meaning.

## 7. Safe custom formula engine

### 7.1 Allowed expression model

Represent formulas as an abstract syntax tree (AST), for example:

```json
{
  "type": "SUBTRACT",
  "left": {"type": "MULTIPLY", "left": {"ref": "active_member_count"}, "right": {"ref": "contribution_minor"}},
  "right": {"ref": "winning_bid_minor"}
}
```

Allowed node types:

- `CONST_INTEGER`
- `INPUT_REF`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE_EXACT`
- `MIN`
- `MAX`
- `CLAMP`
- `PERCENT_OF`
- `ROUND`
- `IF`
- `ALLOCATE_REMAINDER`

Do not allow loops, recursion, network calls, file access, reflection, SQL, arbitrary JavaScript, arbitrary Python, or user-defined functions in the formula definition.

### 7.2 Allowed inputs

Only expose an allowlisted, typed input catalog:

```text
active_member_count: INTEGER > 0
eligible_member_count: INTEGER >= 0
contribution_minor: INTEGER >= 0
gross_pot_minor: INTEGER >= 0
winning_bid_minor: INTEGER >= 0
host_fee_minor: INTEGER >= 0
fee_rate_basis_points: INTEGER 0..10000
adjustment_minor: INTEGER, signed only when policy permits
currency_code: ISO 4217 code
minor_unit_digits: INTEGER 0..6
cycle_number: INTEGER > 0
```

Each input must declare whether it is required, its unit, and its valid range.

### 7.3 Evaluation rules

1. Parse and validate the AST before saving.
2. Validate every referenced input against the schema.
3. Evaluate using integer arithmetic where possible.
4. Use decimal/rational arithmetic for percentage operations, then apply the configured rounding mode once at the documented stage.
5. Reject division by zero.
6. Reject negative payouts unless the profile explicitly permits them and approval policy allows it.
7. Enforce maximum expression depth and node count.
8. Enforce a timeout and deterministic evaluation order.
9. Return a calculation trace for every intermediate value.
10. Store the formula ID/version, input snapshot, output snapshot, and engine version.

### 7.4 Rounding and allocation

Define rounding explicitly:

```json
{
  "rounding": {
    "mode": "HALF_UP",
    "scale_minor_digits": 0,
    "round_after": "PAYOUT_SUBTOTAL"
  }
}
```

For division that leaves a remainder:

```text
base_share = total ÷ recipient_count
remainder = total mod recipient_count
```

Choose one rule and record it:

- `FIRST_IN_ORDER`: distribute remainder by stable member-number order.
- `ROTATE_REMAINDER`: rotate the first recipient by cycle number.
- `WINNER_CREDIT`: assign remainder to the winner where permitted.
- `HOST_REVIEW`: stop and require manual review.

Never distribute a remainder based on database row order.

### 7.5 Formula lifecycle

```text
DRAFT → TESTED → PENDING_REVIEW → APPROVED → ACTIVE → RETIRED
```

Activation gates:

- All required fields are present.
- At least one normal test case passes.
- Zero/minimum/maximum boundary tests pass.
- Tie, rounding, and remainder tests pass.
- Currency mismatch tests pass.
- A reviewer approves the profile.
- The system generates a human-readable formula explanation.

### 7.6 Calculation trace example

```json
{
  "formula_profile_id": "highest_discount_v1",
  "formula_version": 1,
  "engine_version": "1.0.0",
  "currency_code": "VND",
  "inputs": {
    "active_member_count": 10,
    "contribution_minor": 1000000,
    "winning_bid_minor": 120000,
    "host_fee_minor": 50000,
    "adjustment_minor": 0
  },
  "steps": [
    {"name": "gross_pot", "value_minor": 10000000},
    {"name": "subtract_bid", "value_minor": 9880000},
    {"name": "subtract_fee", "value_minor": 9830000}
  ],
  "outputs": {
    "payout_minor": 9830000
  },
  "rounding": {"mode": "HALF_UP", "remainder_minor": 0}
}
```

## 8. API contracts

### Preview a formula

```http
POST /formula-profiles/{id}/preview
```

```json
{
  "currency_code": "VND",
  "inputs": {
    "active_member_count": 10,
    "contribution_minor": 1000000,
    "winning_bid_minor": 120000,
    "host_fee_minor": 50000
  }
}
```

Response includes `outputs`, `calculation_trace`, `warnings`, `formula_version`, and `engine_version`.

### Confirm a cycle result

```http
POST /groups/{groupId}/cycles/{cycleId}/confirm-result
```

The command must include an idempotency key and the expected formula version. The server re-evaluates the formula; it must not trust a client-supplied payout.

## 9. Test matrix

At minimum test:

- One member, minimum group size, and maximum configured group size.
- Zero bid and maximum valid bid.
- Tie bids with every tie-break option.
- Zero fee and fee at cap.
- Rounding up, down, half-up, and half-even.
- Remainder of 1 and remainder larger than 1.
- Missing input and unknown input.
- Currency mismatch.
- Duplicate result confirmation.
- Formula edited after cycle activation.
- Negative output.
- Overflow boundary.
- Replaying the same transaction event.

## 10. Recommended implementation sequence

1. Add `currencies` and integer minor-unit fields.
2. Migrate and reconcile existing group data.
3. Add group-currency integrity constraints.
4. Implement the calculator using the default fixed-rotation profile.
5. Add highest-discount and lowest-bid profiles behind feature flags.
6. Add formula AST validation and calculation traces.
7. Add formula test cases and approval workflow.
8. Add report currency grouping and exports.
9. Add explicit conversion snapshots only if the product needs cross-currency reporting.
10. Run a controlled pilot and compare every cycle with an independent calculation.
