# Tong Tin Manager — Project Context Memory

Use this file as the continuity record for future build steps.

## Product objective

Build a secure, transparent Tong Tin / Hội / Chụi / ROSCA management system for Hosts (Chủ Hội) and Members (Hội Viên). It replaces paper/Excel tracking with configurable rules, private bidding, deterministic calculations, an immutable ledger, reminders, reports, and member self-service.

## Current status

- Planning baseline created: `TONG_TIN_BLUEPRINT.md`
- Implementation status: **Not started**
- Current next step: **Rule Discovery Workshop**
- Do not implement a universal bidding formula before the first rule profile is approved.

## Agreed product principles

1. Group rules are configurable and versioned.
2. Money calculations use integer minor units, not floating point.
3. The ledger is the source of truth; balances are derived views.
4. Closed bids and confirmed results are immutable through normal UI actions.
5. Corrections use reversals/adjustments and preserve history.
6. AI may explain and detect anomalies, but deterministic code decides financial outcomes.
7. MVP records external payments manually unless a compliant payment provider is explicitly selected.
8. Start with a responsive web/PWA and a modular monolith; use Next.js + TypeScript, Spring Boot, PostgreSQL, and Docker.

## Canonical roles

- Host: creates groups, manages members, opens/closes cycles, reviews results, records payments and payouts.
- Member: joins groups, views obligations/statements, submits private bids, reviews results.
- Auditor/support: optional read-only role for controlled support and reconciliation.

## Canonical workflow

`Create group → confirm rules → invite members → open cycle → record contributions → open private bids → close/freeze bids → calculate → host confirms → record payout → close cycle → schedule next cycle`

## Required state protections

- Private bids are not visible to other members before the bidding window closes.
- Closed bids cannot be edited normally.
- Result confirmation and ledger writes are atomic.
- Duplicate events are idempotent.
- Notifications never mutate financial state.
- Critical rule changes create a new rule version and require acknowledgement.

## Terminology caution

“Alive member,” “dead member,” “bid,” and “winner” can have different meanings in different Vietnamese communities. Always ask for the community’s exact meaning and document it in `RULE_PROFILE_V1`.

## Preferred implementation sequence

1. Rule profile and examples.
2. Schema and calculator.
3. Authentication, groups, and memberships.
4. Cycle and contribution workflow.
5. Private bidding and result confirmation.
6. Ledger, payout, and corrections.
7. Member/host dashboards.
8. Notifications and reports.
9. Security review and pilot.

## Required next artifact

`RULE_PROFILE_V1` with at least one worked example and expected result. After it is approved, create:

- typed rule schema;
- pure calculation function;
- golden test cases;
- calculation trace format;
- API contract for previewing a cycle.

## Open decisions

- Target jurisdiction and compliance obligations.
- First exact bidding convention.
- Manual payment recording versus one payment provider.
- Vietnamese-only or Vietnamese/English UI.
- Web/PWA only versus later native Flutter app.
- Host fee and discount allocation rules.
- Whether members may transfer/assign participation.
