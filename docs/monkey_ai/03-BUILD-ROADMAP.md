# Build Roadmap - One Step At A Time

Rules:
- Do exactly one step per coding session unless the user asks for more
- Do not skip formula tests
- Update `docs/STATUS.md` when a step is done
- Commit only when the user asks

## Extra product ideas (parked, not MVP)

P1. VietQR payment reference on each obligation
P2. Excel/PDF so sach export matching paper books
P3. Zalo OA and Telegram reminders
P4. Guarantor and deposit to reduce hoi vo
P5. Member reputation across groups
P6. Multi-share calendar heatmap
P7. Host what-if simulator before starting a group
P8. Sealed bid commit-reveal for extra privacy
P9. PWA install on phone
P10. Dark mode, large-number keypad for VND
P11. Dispute snapshot: freeze cycle math as PDF
P12. Recurring group templates (superseded by P14)
P13. Owner default formula + default group numbers
P14. System + owner custom group design templates (mau hoi)
P15. Owner-local phone blacklist + defaulted-phone warning
P16. CycleSession attendance, dispute lock, math snapshot
P17. Print/visual statement templates
P18. Extra ISO currencies beyond seeded VND/KHR/LAK/THB/USD/SGD
P19. FX / cross-currency owner dashboard (not allowed until explicit)

See `docs/06-SETTINGS-TEMPLATES-RISK.md`, `docs/07-MULTI-CURRENCY.md`, `docs/08-FORMULA-ENGINE.md`.
Currency FK + minor units belong in schema from Step 02/05, not as a later rewrite.
Default engine is BIDDING_CLASSIC; custom = parameter clone only.

## Step 00 - Planning artifacts

Status: DONE in this session
Deliverable: docs in `docs/`

## Step 01 - Monorepo skeleton

Create:
- `apps/api` Spring Boot empty app with health endpoint
- `apps/web` Next.js app with proxy to API
- `docker-compose.yml` Postgres
- root README with how to run

Accept:
- GET `/api/v1/health` returns `{ "status": "UP" }`
- Next.js page loads

## Step 02 - Database and Flyway

Create Postgres schema for users only first.
Accept: app starts against empty DB, Flyway V1 runs.

## Step 03 - Identity and owner self-register

Follow `docs/05-OWNER-SELF-REGISTER.md`.
Public `POST /auth/register-owner` creates User HOST + OwnerAccount.
Login by phone. JWT includes `ownerId`.
No public member register.
Accept: duplicate phone 409; `/api/v1/me` returns HOST + owner; second owner isolated.

## Step 04 - Member directory (host)

Host CRUD member profiles (name, phone, note).
Accept: host lists members; cannot see other host members.

## Step 05 - Group draft

Create/update DRAFT group with all rule fields and validation.
Accept: invalid maxBid >= C rejected.

## Step 06 - Shares and READY

Assign shares until count == N, start group, freeze rules.
Accept: cannot start if share total != N.

## Step 07 - Formula engine (no HTTP)

Pure Java calculator + unit tests from domain examples.
Accept: Cycle 1/2/last example numbers match exactly.

## Step 08 - Cycle open

Open cycle 1 in RUNNING group.
FIXED skips bidding; BIDDING enters BIDDING status.

## Step 09 - Sealed bidding

Member submits/updates bid; host cannot read amounts before close.
Accept: other members get 403 on foreign bid; host summary hides amounts.

## Step 10 - Close and calculate

Close bids, pick winner, write ledger obligations, publish summary.
Accept: example group settles to 7_100_000 net payout on cycle 1.

## Step 11 - Payments

Host records payment and allocates to obligations.
Overdue marks debt list.

## Step 12 - Host dashboard

Groups, next due, unpaid count, estimated host profit.

## Step 13 - Member portal

My groups, bid screen, balance statement.

## Step 14 - In-app notifications

Events in section 13 of domain doc.

## Step 15 - Reports

Host profit, member statement, cycle public summary.

## Step 16 - Hardening

Audit log, authz tests, money rounding tests, seed demo data.

## Step 17 - Preview polish

Vietnamese copy, VND formatting, empty states, allowedHosts, start script.

Do not implement a later step before the previous step is accepted.
