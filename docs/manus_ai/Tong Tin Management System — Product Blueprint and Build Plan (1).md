# Tong Tin Management System — Product Blueprint and Build Plan

**Working name:** Tong Tin Manager  
**Domain:** Hội / Chụi / Rotating Savings and Credit Association (ROSCA)  
**Document status:** Product planning baseline v0.1  
**Purpose:** Provide one clear, auditable workflow for hosts and members, replacing paper/Excel tracking while keeping the system configurable for local Tong Tin rules.

> **Important:** Tong Tin rules vary by community. The system must never hard-code one interpretation of “highest bid,” “winner,” “dead member,” “alive member,” commission, or payout. Each group stores its own rule configuration, and every calculation records the formula version used.

## 1. Product goal

Build a secure system that lets a **Host (Chủ Hội)** create and operate one or more Tong Tin groups, while members can see their obligations, submit bids privately, review results, and verify their transaction history.

The product should optimize for:

- **Accuracy:** deterministic calculations with no hidden spreadsheet formulas.
- **Transparency:** members can see the group rules, cycle results, and their own ledger.
- **Privacy:** private bids and role-based access.
- **Auditability:** every change and financial event has a timestamp, actor, reason, and before/after values.
- **Operational simplicity:** a host can run a cycle in a few guided steps.

## 2. Plain-language domain model

| Term | Meaning in the system |
|---|---|
| Host / Chủ Hội | Person who creates and administers a group. |
| Member / Hội Viên | Participant who contributes and may receive a payout. |
| Group / Dây Hội | One Tong Tin line with its own members, amount, schedule, and rules. |
| Cycle / Kỳ | One round in which contributions are collected and one payout is determined. |
| Contribution | Amount a member is required to pay for a cycle. |
| Bid / Mức Huê Hồng or discount | Amount offered under the group’s configured bidding rule. Local terminology and direction may differ. |
| Winner | Member selected by the configured winner rule for the cycle. |
| Payout | Amount paid to the winner after the configured deductions/additions. |
| Host commission / Tiền Thảo | Host fee; may be fixed or percentage-based and must be disclosed in the group rules. |
| Alive member / Hội Sống | Member who has not yet received the group payout, according to the group’s rules. |
| Dead member / Hội Chết | Member who has already received the payout and continues to owe required contributions. |
| Ledger | Immutable record of required, received, paid, adjusted, and outstanding amounts. |

## 3. Recommended MVP scope

### Include in MVP

1. Host account and member accounts.
2. Create a group with configurable:
   - contribution amount;
   - frequency: daily, weekly, biweekly, monthly, or custom;
   - number of members and planned cycles;
   - fixed or bidding mode;
   - bid direction and tie-break rule;
   - host fee type and amount;
   - late-payment policy;
   - start date and timezone;
   - currency.
3. Invite/register members and assign stable member IDs.
4. Guided cycle workflow:
   - open cycle;
   - collect or record contributions;
   - open private bid window;
   - close bids;
   - calculate winner and payout;
   - host reviews and confirms result;
   - record payout and carry balances forward.
5. Member portal showing active groups, due amounts, cycle status, bids, wins, payouts, and outstanding balance.
6. Double-entry-inspired operational ledger: every money event is recorded separately from the current balance.
7. Basic reminders through email or in-app notifications. Add Zalo/Telegram/SMS after the core workflow is stable.
8. Exportable CSV/PDF reports for the host and member statement.
9. Audit log and role-based authorization.

### Defer until after MVP

- Automatic bank/e-wallet money movement.
- Credit scoring or member risk ratings.
- Multiple currencies in one group.
- Complex penalties, collateral, guarantors, or collections automation.
- Native mobile apps; start with responsive web UI or PWA.
- AI predictions. Use AI first for explanation, support, and anomaly flags—not for authoritative financial calculations.

## 4. Canonical end-to-end workflow

### A. Host creates a group

1. Host selects **Create Group**.
2. System asks for group rules using a guided form.
3. System shows a **calculation preview** for one hypothetical cycle.
4. Host confirms the rules.
5. System creates a versioned `GroupRuleSet` and locks critical terms after the first cycle begins.
6. Host invites members by phone/email/link and assigns each a member number.
7. Each member accepts the invitation and acknowledges the rules.

### B. Host opens a cycle

1. System creates the next cycle from the current rule version.
2. It calculates each member’s required contribution and due date.
3. It shows the cycle schedule and outstanding prerequisites.
4. Notifications are queued.
5. Cycle status becomes `OPEN_FOR_CONTRIBUTIONS`.

### C. Contributions are recorded

1. Member pays outside the system or through a future integrated payment provider.
2. Host records the receipt, or the provider sends a verified event.
3. System creates an immutable contribution transaction.
4. Member balance and group collection progress update.
5. Late or missing contributions are flagged; they do not silently change the mathematical result.

### D. Bidding is conducted

1. Host opens a private bid window.
2. Eligible members see the bid instructions and deadline.
3. Each member may submit, revise, or withdraw a bid until the deadline, according to the rules.
4. The system stores bids encrypted/hidden from other members.
5. At close, the system freezes the bid set and calculates the result.
6. The host sees a review screen with the exact inputs, formula, tie-break, and output.
7. Host confirms; the result becomes immutable except through a documented correction/reversal process.

### E. Winner and payout are settled

1. System marks the winner for that cycle.
2. It determines whether the winner changes from alive to dead under the group rules.
3. It calculates the payout and every member’s obligation.
4. Host records or verifies the payout.
5. System creates payout and fee transactions.
6. The cycle closes and the next cycle is scheduled.
7. All members receive a result summary and can view their personal statement.

## 5. Mathematical model

Because communities differ, use a **rule engine with explicit formulas**, not one universal formula. A practical base model is:

### Core variables

- `N` = number of active members in the cycle.
- `C` = base contribution per member for the cycle.
- `B_w` = winning bid/discount under the group’s bid convention.
- `F` = host commission under the selected rule.
- `A` = other configured adjustments, such as penalties or carry-forward credits.
- `P` = winner payout.

### Example: bid is a discount from the pot

If the pot is built from all required contributions and the winner offers a discount:

```text
Gross pot = N × C
Winner payout P = Gross pot − B_w − F + A
```

The system must separately state who receives the bid discount. Common possibilities include:

```text
Shared discount per eligible member = B_w ÷ eligible_member_count
```

or a rule-specific allocation such as a credit to members who have not yet won. Do not infer this allocation; store it as `discount_allocation_rule`.

### Example: fixed contribution after a member wins

A group may require a winner to continue paying `C_dead` in future cycles:

```text
Member required amount for next cycle =
  C_alive, if member has not won
  C_dead,  if member has won
```

The exact values and whether “dead” means winner or non-winner are configurable because local usage differs.

### Example: host fee

```text
Fixed fee:       F = configured_fixed_fee
Percentage fee:  F = fee_rate × Gross pot
Capped fee:      F = min(fee_rate × Gross pot, fee_cap)
```

### Rounding policy

Every group must store:

- currency;
- minor-unit precision;
- rounding mode, such as half-up or down;
- the stage at which rounding occurs;
- treatment of a remainder of one or more minor units.

### Example calculation

Assume:

- `N = 10`
- `C = 1,000,000 VND`
- `B_w = 120,000 VND`
- `F = 50,000 VND`
- `A = 0`

Then:

```text
Gross pot = 10 × 1,000,000 = 10,000,000 VND
Payout = 10,000,000 − 120,000 − 50,000
Payout = 9,830,000 VND
```

This is only an example. The group’s signed rule set is authoritative.

## 6. Essential data model

### Identity and access

- `users`: account, phone/email, locale, timezone, status.
- `roles`: host, member, auditor/support.
- `memberships`: user-to-group relationship, member number, status, joined date.

### Group configuration

- `groups`: name, host, currency, timezone, status.
- `group_rule_versions`: contribution mode, schedule, bidding mode, bid direction, tie-break, fee rules, payout rules, late policy, effective dates, formula version.
- `group_cycles`: sequence number, open/close dates, status, rule version ID.
- `cycle_eligibility`: who may bid and why someone is excluded.

### Bidding

- `bids`: cycle, member, amount, version, submitted/withdrawn timestamps, status.
- `bid_closures`: closing timestamp, frozen bid-set hash, calculation run ID.
- `cycle_results`: winner, winning bid, gross pot, fee, payout, allocation summary, confirmed by, confirmed at.

### Ledger

- `accounts`: group cash account, member receivable/payable, host fee account, payout account.
- `transactions`: immutable event type, amount, currency, actor, external reference, timestamp.
- `ledger_entries`: debit/credit or positive/negative entries tied to a transaction.
- `member_balances`: derived/read model, never the sole source of truth.
- `adjustments`: reason, approval, original transaction, replacement transaction.

### Governance and communication

- `audit_events`: actor, action, entity, before/after, IP/device metadata where appropriate.
- `notifications`: template, recipient, channel, queued/sent/failed status.
- `consents`: rule acknowledgement, privacy consent, terms version.

## 7. State machines

### Group state

```text
DRAFT → ACTIVE → PAUSED → COMPLETED
                 └──────→ CANCELLED
```

### Cycle state

```text
PLANNED
  → OPEN_FOR_CONTRIBUTIONS
  → BIDDING_OPEN
  → BIDDING_CLOSED
  → RESULT_PENDING_CONFIRMATION
  → RESULT_CONFIRMED
  → PAYOUT_PENDING
  → SETTLED
  → RECONCILIATION_REQUIRED (if mismatch)
```

No cycle should move backward through ordinary UI actions. Corrections use reversal/adjustment events and preserve the original record.

## 8. Technical architecture recommendation

### MVP architecture

- **Frontend:** Next.js + TypeScript, responsive web/PWA.
- **Backend:** Spring Boot modular monolith first; split into microservices only when there is a demonstrated operational need.
- **Database:** PostgreSQL with migrations and transaction constraints.
- **Infrastructure:** Docker Compose locally; managed container deployment later.
- **Authentication:** passwordless phone/email OTP or a reputable identity provider; avoid storing passwords if possible.
- **Jobs:** scheduled worker for cycle creation, reminders, and reconciliation checks.
- **Notifications:** provider abstraction with in-app first, then Zalo/Telegram/SMS adapters.
- **Observability:** structured logs, error tracking, audit logs, job metrics.

### Why modular monolith first

The core workflow is highly transactional and rule-dependent. A modular monolith makes it easier to keep calculation, ledger, authorization, and cycle state changes atomic. Define module boundaries now so services can be extracted later:

1. Identity and access.
2. Groups and memberships.
3. Rules and calculation engine.
4. Cycles and bidding.
5. Ledger and reconciliation.
6. Notifications.
7. Reporting and audit.

## 9. Security, privacy, and operational controls

- Enforce host/member data isolation at the API and database query layers.
- Never expose another member’s private bid before the bid window closes.
- Use idempotency keys for payment/event ingestion.
- Use database transactions for result confirmation and ledger writes.
- Encrypt sensitive data in transit and at rest.
- Store only necessary identity and payment-reference data.
- Add export, correction, and deletion policies that preserve legally required audit records.
- Require explicit rule acknowledgement before a member joins.
- Add two-person review for payout confirmation above a configurable threshold.
- Start with manual payout recording; add regulated payment integrations only after legal and provider review.
- Confirm applicable Vietnamese/local laws, consumer-protection requirements, privacy rules, money-transmission obligations, tax treatment, and any licensing requirements before public launch.

## 10. Prioritized implementation backlog

### Phase 0 — Discovery and rule confirmation

- [ ] Interview at least one host and three members.
- [ ] Collect 3–5 real but anonymized Tong Tin examples.
- [ ] Document differences in terminology and formulas.
- [ ] Select the first supported rule profile.
- [ ] Define the money-event and correction policy.
- [ ] Decide whether MVP records external payments only or integrates one provider.
- [ ] Confirm legal/privacy requirements for the target market.

**Exit criteria:** one signed-off rule sheet, one end-to-end example calculated by hand, and one agreed MVP workflow.

### Phase 1 — Foundation

- [ ] Create repository, branching rules, CI, Docker Compose, and environment configuration.
- [ ] Create PostgreSQL schema and migration tooling.
- [ ] Implement authentication and role-based authorization.
- [ ] Implement users, groups, memberships, invitations, and audit events.
- [ ] Add timezone, currency, locale, and date conventions.

**Exit criteria:** a host can create a draft group and invite a test member securely.

### Phase 2 — Rule engine and preview

- [ ] Define typed `GroupRuleSet` schema.
- [ ] Implement deterministic calculator as a pure function.
- [ ] Implement validation for invalid rules, impossible schedules, ties, rounding, and member counts.
- [ ] Add formula versioning and calculation trace output.
- [ ] Build a host preview page with sample inputs and outputs.
- [ ] Create golden test cases from anonymized examples.

**Exit criteria:** calculator results match approved examples and every output explains its inputs and formula.

### Phase 3 — Cycle operations

- [ ] Create cycle scheduler.
- [ ] Implement contribution records and outstanding status.
- [ ] Implement private bid submission, revision, closure, and freeze.
- [ ] Implement winner selection and tie-break handling.
- [ ] Implement host confirmation and immutable cycle result.
- [ ] Implement payout recording and reversal/adjustment flow.

**Exit criteria:** a complete test group can run at least three cycles without spreadsheet intervention.

### Phase 4 — Member experience and reports

- [ ] Build member dashboard and personal statement.
- [ ] Build host dashboard with collection, risk, and profit views.
- [ ] Add notifications and delivery status.
- [ ] Add CSV/PDF exports.
- [ ] Add bilingual Vietnamese/English labels if required.

**Exit criteria:** host and members can independently verify their current obligations and historical events.

### Phase 5 — Hardening and pilot

- [ ] Threat model and permission review.
- [ ] Load-test cycle closing and notification jobs.
- [ ] Test duplicate events, retries, timezone boundaries, rounding, and partial payments.
- [ ] Run a controlled pilot with a small group.
- [ ] Reconcile every pilot cycle against an independent spreadsheet.
- [ ] Fix workflow friction and document support procedures.

**Exit criteria:** pilot sign-off, zero unexplained ledger differences, and an incident/correction playbook.

## 11. Acceptance tests for the most important workflow

A release is not ready unless these pass:

1. A member cannot read another member’s private bid.
2. A closed bid cannot be edited through the normal API.
3. A tie is resolved according to the stored rule, not server ordering.
4. Retrying a contribution or payout event does not duplicate money.
5. A failed notification does not change financial state.
6. A correction creates a reversal/adjustment trail and never erases history.
7. Every payout shows gross pot, deductions, fee, allocation, rounding, winner, and formula version.
8. A member can reproduce their statement from ledger events.
9. A host cannot change critical group rules after a cycle has begun without a new rule version and explicit member acknowledgement.
10. All amounts are calculated in integer minor units; floating-point currency math is prohibited.

## 12. Good ideas to include

### Trust and transparency

- “Why this amount?” expandable explanation beside every balance.
- Member acknowledgement of each new rule version.
- Public group summary without exposing private bids.
- Downloadable signed cycle receipt with a result ID.
- Independent reconciliation report for the host.

### Host efficiency

- One-click repeat of the previous cycle’s schedule.
- Bulk import of members from CSV.
- Reminder templates with Vietnamese-friendly phrasing.
- Dashboard alerts for missing payments, conflicting adjustments, and overdue payouts.
- Offline-friendly draft entry for poor connectivity, with sync conflict handling.

### Member confidence

- Personal contribution calendar.
- Clear distinction between required, received, overdue, and adjusted amounts.
- Notification preferences per group.
- View-only access for a trusted family member, if allowed by the group.

### Safe AI additions

- Explain a formula in plain Vietnamese or English.
- Convert a host’s informal rules into a draft structured rule set for review.
- Detect unusual patterns or missing entries and ask the host to verify them.
- Generate a cycle summary from verified ledger data.

AI must not silently choose a winner, alter balances, or override the deterministic calculator.

## 13. Next step to build one-by-one

Start with **Step 1: Rule Discovery Workshop**. Do not code bidding or money calculations until the first rule profile is explicit.

### Step 1 deliverable

Create one `RULE_PROFILE_V1` containing:

- contribution amount and frequency;
- member count and cycle count;
- who may bid;
- bid direction and allowed range;
- tie-break rule;
- gross pot formula;
- host fee formula;
- discount allocation formula;
- alive/dead status transition;
- contribution rule after winning;
- late-payment treatment;
- rounding and remainder treatment;
- payout confirmation process;
- correction/reversal process.

### Suggested next user input

Provide one real or hypothetical example in this format:

```text
Number of members:
Contribution per cycle:
Frequency:
Number of cycles:
Who can bid:
How is the bid expressed:
How is the winner selected:
Host fee:
How is the winner payout calculated:
What do winners pay in later cycles:
What happens when someone pays late:
What happens in a tie:
Currency:
```

Once that is filled, the next build step is to convert it into a validated rule schema and a calculator with test cases.

## 14. Client and owner registration module

The system should support two related registration needs:

1. **Owner/Host registration:** create and manage the account of the person who operates Tong Tin groups.
2. **Client/Member registration:** allow the Owner/Host to register new clients and later assign them to one or more groups.

All later features—bidding, payments, statements, reminders, and reports—depend on reliable client identity records.

### Owner/Host registration

The first Owner/Host account should capture:

- Full name and preferred display name.
- Phone number and email address, where available.
- Preferred language and timezone.
- Business or organization name, if applicable.
- Operating location or service area, if needed.
- Account status: `Pending`, `Active`, `Suspended`, or `Closed`.
- Terms, privacy, and host-responsibility acknowledgement.

The system should create a stable `host_id`. The host can then create groups and register clients. If the product later supports multiple staff members, the Owner can invite staff with limited permissions instead of sharing one account.

### Client registration information

The Host should be able to register:

- Full legal name and preferred name.
- Phone number.
- Email address, if available.
- Address or service location, only when genuinely needed.
- Optional government-ID reference, stored securely and minimized.
- Emergency contact or guarantor, only if required by the agreed group rules.
- Notes that are operationally relevant.
- Registration date.
- Client status: `Pending`, `Active`, `Inactive`, or `Blocked`.

Do not collect sensitive identity information merely because it is available. The required fields should be configurable by jurisdiction and operating policy.

### Client registration workflow

```text
Owner/Host opens Client Management
        ↓
Selects “Add New Client”
        ↓
Enters required client information
        ↓
System checks duplicate phone/email/ID references
        ↓
System creates a unique client ID
        ↓
Client receives an invitation or registration confirmation
        ↓
Host assigns client to a Tong Tin group
        ↓
Client accepts the group rules
        ↓
Client becomes an active group member
```

### Duplicate and identity checks

Before creating a new profile, the system should warn the Host when:

- The phone number already exists.
- The email address already exists.
- The client-ID or protected identity reference already exists.
- The client is already a member of the selected group.
- The client has an inactive or blocked account.
- A likely matching name and phone number exists.

The Host must choose whether to open the existing profile or continue with a documented exception. The system should not silently create duplicate clients.

### Client profile

Each client profile should show:

- Unique client ID.
- Contact information and communication preferences.
- Groups joined and membership status.
- Current participation and next due amount.
- Payment and contribution history.
- Outstanding amounts.
- Previous payouts.
- Late-payment history.
- Notes and permitted documents.
- Activity and audit history.

Financial history must remain available even if a client becomes inactive. Deactivation is not deletion.

### Owner/Host permissions

The Owner/Host may:

- Add a new client.
- Edit permitted contact information.
- Search, filter, and export client lists according to permissions.
- Assign a client to a group.
- Invite or resend an invitation.
- Mark a client inactive or blocked with a reason.
- View the client’s authorized payment and membership history.
- Correct a data-entry mistake through an audit-tracked correction.

The Host should not be able to silently delete a client’s financial history. Removing a client from a group should be allowed only when the group rules permit it and no unresolved financial obligation remains; otherwise, use a documented transfer, settlement, or closure workflow.

### Recommended data model additions

```text
hosts
- id
- user_id
- business_name
- service_area
- status
- created_at

users
- id
- full_name
- preferred_name
- phone
- email
- locale
- timezone
- status
- created_at

client_profiles
- id
- user_id
- client_number
- address_or_area
- identity_reference_token
- notes
- status
- created_at
- updated_at

group_memberships
- id
- group_id
- client_id
- member_number
- joined_at
- rule_acknowledged_at
- status

invitations
- id
- client_id
- group_id
- contact_method
- token_hash
- expires_at
- accepted_at
- status

audit_events
- id
- actor_id
- action
- entity_type
- entity_id
- before_data
- after_data
- reason
- created_at
```

### Recommended implementation order

Add this module before bidding and financial operations:

1. Owner/Host account registration.
2. Client profile creation.
3. Search and duplicate detection.
4. Client invitation and acceptance.
5. Group assignment and member number generation.
6. Group-rule acknowledgement.
7. Client/member dashboard.
8. Payment, bidding, and ledger workflows.

### Acceptance tests

1. A Host can create a client with the minimum required fields.
2. Every client receives a stable unique ID.
3. A duplicate phone number produces a warning instead of a silent duplicate.
4. A client cannot be added twice to the same group.
5. A client can belong to multiple groups without losing group-specific member numbers.
6. A client cannot access another client’s profile or financial statement.
7. Deactivating a client does not erase historical transactions.
8. A client must accept the group rules before becoming active.
9. Every profile edit records the actor, timestamp, changed fields, and reason where required.
10. A blocked client cannot be added to a new group without an authorized override.

## 15. System configuration and governance module

These are good additions, but they should be implemented as controlled configuration—not as unrestricted settings. The purpose is to let the Owner/Host reuse safe defaults while still supporting legitimate differences between Tong Tin groups.

### 15.1 Formula library

The system should provide:

- **Default formula profiles:** approved starting points for common group types.
- **Custom formula profiles:** a Host can create a draft variation for a specific group.
- **Formula preview:** show sample inputs, outputs, rounding, and allocation before activation.
- **Formula versioning:** every formula has a version, author, creation time, and effective date.
- **Formula locking:** once used by an active cycle, a formula version cannot be edited; a new version must be created.
- **Calculation trace:** every result records the formula version and all input values used.
- **Test examples:** a formula cannot be activated until it passes required examples and validation rules.
- **Approval status:** `Draft`, `Tested`, `Pending Review`, `Approved`, `Active`, `Retired`.

Do not allow users to enter arbitrary executable code as a formula. Use a safe structured formula language or a fixed set of reviewed operators, such as addition, subtraction, multiplication, division, minimum, maximum, percentage, rounding, and conditional rules.

### Formula safety checks

Before activation, the system should check:

1. No division by zero.
2. No negative payout unless explicitly allowed and reviewed.
3. No payout greater than the configured available funds unless the rule explicitly supports carry-forward funding.
4. No missing member or cycle inputs.
5. Currency and minor-unit consistency.
6. Valid fee range and fee cap.
7. Valid bid range and tie-break rule.
8. Correct handling of zero, one, minimum, and maximum members.
9. Correct rounding and remainder allocation.
10. Reproducible results when the same inputs are run again.

### Formula governance workflow

```text
Choose default formula or create custom formula
        ↓
Enter rules using safe fields/operators
        ↓
Run sample calculations and validation checks
        ↓
Show plain-language explanation and preview
        ↓
Review/approve formula
        ↓
Attach formula version to a group
        ↓
Lock formula version when the first active cycle begins
```

### 15.2 Meeting and dispute-prevention records

The product should include a **Meeting and Resolution** feature to prevent misunderstandings before they become payment or trust disputes. This is not a replacement for legal or financial advice; it is an operational record.

Useful meeting types include:

- Group setup and rule confirmation.
- New member orientation.
- Cycle result review.
- Late-payment discussion.
- Calculation correction review.
- Payout or reconciliation meeting.
- Complaint, mediation, or resolution meeting.

Each meeting record can include:

- Group and related cycle.
- Meeting type, date, time, timezone, and location or online method.
- Participants and attendance status.
- Agenda and rule sections discussed.
- Questions or objections raised.
- Decisions made.
- Follow-up tasks, owner, and due date.
- Attachments or signed acknowledgement, where appropriate.
- Status: `Scheduled`, `Held`, `Cancelled`, or `Follow-up Required`.

The system should send reminders, but it must not mark a participant as agreeing merely because they attended. Agreement or acknowledgement must be explicit and timestamped.

### Meeting workflow

```text
Create meeting → Invite participants → Record agenda/questions
        → Hold meeting → Record decisions and tasks
        → Request explicit acknowledgements → Close or schedule follow-up
```

### 15.3 Client blacklist and risk restriction

A blacklist can help prevent repeated fraud, abuse, identity duplication, or unresolved serious issues, but it is a sensitive feature and should not be a simple permanent delete/block button.

Recommended statuses:

- `Clear` — no restriction.
- `Watch` — additional review required.
- `Restricted` — cannot join selected groups or perform selected actions.
- `Blocked` — cannot be invited or activated until reviewed.
- `Under Review` — temporary status pending evidence or meeting.
- `Resolved` — prior case closed; history retained.

Each case should contain:

- Subject client or account.
- Scope: one group, one Host, or platform-wide.
- Category and factual reason.
- Evidence or linked transaction/meeting records.
- Created by, reviewed by, and timestamps.
- Start date, expiry/review date, and appeal status.
- Required resolution steps.
- Visibility rules; sensitive notes must not be visible to ordinary members.

### Blacklist safeguards

- Do not use a name-only match to block a person.
- Require a reason and supporting record.
- Prefer temporary restrictions with a review/expiry date.
- Provide an authorized review or appeal workflow.
- Log every view, creation, update, and override.
- Never expose blacklist labels publicly to other members.
- Do not use blacklist status as an automatic financial conclusion.
- Restrict platform-wide blocking to authorized administrators, not ordinary Hosts.
- Check local privacy, consumer-protection, and data-retention requirements before launch.

### 15.4 Default and custom design templates

The system should support reusable templates for a consistent, understandable experience:

- Host dashboard.
- Member dashboard.
- Group creation form.
- Cycle result statement.
- Payment receipt.
- Payout receipt.
- Meeting invitation and minutes.
- Reminder notification.
- CSV/PDF report.
- Bilingual Vietnamese/English labels.

Template capabilities should include:

- Default system templates that cannot be accidentally deleted.
- Host or organization custom templates.
- Group-level template selection.
- Draft, preview, test, publish, and retire lifecycle.
- Version history and rollback to a previous version.
- Required-field validation so financial values cannot be hidden.
- Localization, timezone, currency, and date-format support.
- Safe variables only, such as member name, cycle number, due date, amount, formula version, and result ID.

Do not allow templates to change the underlying calculation or hide mandatory disclosures. A custom design should change presentation, not financial truth.

### 15.5 Recommended configuration data model

```text
formula_profiles
- id
- scope (system, host, group)
- name
- description
- structured_definition
- version
- status
- effective_from
- effective_to
- created_by
- approved_by

formula_test_cases
- id
- formula_profile_id
- input_fixture
- expected_output
- passed_at

meetings
- id
- group_id
- cycle_id
- type
- scheduled_at
- status
- agenda
- decisions
- created_by

meeting_participants
- meeting_id
- client_id
- attendance_status
- acknowledgement_status
- acknowledged_at

risk_cases
- id
- subject_client_id
- scope
- status
- category
- factual_reason
- evidence_reference
- review_date
- created_by
- reviewed_by

design_templates
- id
- scope
- type
- name
- version
- structured_content
- locale
- status
- created_by
- published_at
```

### 15.6 Implementation priority

Implement these features in this order:

1. Default formula profiles and a safe calculator preview.
2. Formula versioning, tests, approvals, and locking.
3. Default design templates and required-field validation.
4. Custom templates with preview and rollback.
5. Meeting records, attendance, decisions, and follow-up tasks.
6. Watch/review restrictions for clients.
7. Restricted/blocked workflow with review and appeal safeguards.

This order prevents the most dangerous problem: allowing custom formulas or custom screens to change or conceal financial results before the core governance controls exist.

### 15.7 Additional acceptance tests

1. A formula used by an active cycle cannot be edited in place.
2. A custom formula cannot become active without passing test cases.
3. Every calculation result identifies its formula version.
4. A template cannot remove required payout, fee, currency, or result-ID fields.
5. A custom template can be previewed and rolled back.
6. Meeting attendance does not equal rule agreement without explicit acknowledgement.
7. A blacklist case requires a factual reason, scope, creator, and review date.
8. A name-only similarity cannot automatically block a client.
9. Ordinary members cannot view sensitive risk notes.
10. Every override or appeal decision is audit logged.
