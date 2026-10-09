# Build Roadmap - One Step At A Time

Rules:
- Do exactly one step per coding session unless the user asks for more
- Do not skip formula tests
- Update `docs/monkey_ai/STATUS.md` when a step is done
- Commit only when the user asks

## Extra product ideas (parked, not MVP)

P1. VietQR payment reference on each obligation
P2. PDF sổ sách matching paper books — CSV/XLSX export released in Step 30; PDF print/visual templates still parked
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

---

## Post-MVP track — Steps 18–31 (executed 2026-10-07 → 2026-10-08)

The MVP (00–17) is done. The approved track A+B above it was executed one step per
session. The live tracker is `docs/monkey_ai/STATUS.md` (current step) and the root
`plan.md` (full step log, §0 status rows and §1–§19 records).

| Step | Done | What shipped |
|:---:|:---:|---|
| 18 | ✅ | Final delivery: 10 E2E flow checks, handover docs |
| 19 | ✅ | SaaS subscriptions + ABA PayWay (HMAC-SHA512, KHQR/card), Admin panel, Flyway V11 |
| 20 | ✅ | Settings module — `system_settings`, parameter presets for new groups |
| 21 | ✅ | Bid rules: interest ceiling, round-2 tiebreak bidding, payout spread over bid rounds |
| 22 | ✅ | Late fee / early-withdrawal formulas, transparent in ledger |
| 23 | ✅ | Subscription funnel — plans gating (PLUS/PREMIUM), group limits, renewals, Flyway V13 |
| 24 | ✅ | Cycle public summary for members |
| 25 | ✅ | Settings & rule presets — plan pricing, bid-order shuffle, change history, cross-tests |
| 26 | ✅ | Multi-currency hardening — USD/KHR exponents, financial rounding, currency state control |
| 27 | ✅ | Cycle settings UI — advanced group config screens |
| 28 | ✅ | Hermetic tests (Testcontainers) + ReleaseGateTests (no `float`/`double` in main source) |
| 29 | ✅ | Late fees delivery — fee collection, auto settlement, payment queuing, ledger/statement display, Flyway V14 |
| 30 | ✅ | CSV / Excel exports — ledger, host profit, member statement via Apache POI (`com.tongtin.reports.export`) |
| 31 | ✅ | Wrap-up & cleanup — stale docs (README/index/architecture/roadmap), Docker compose click-through, final suite + build (recorded: suite 184, `npm run build` 13/13, all 3 containers healthy) |

Parked P-items are unchanged and remain parked unless the user asks. §7.5 formula
reminders (ranks 6–7) are recorded in `plan.md` and **need explicit user confirmation**
before any change is made.

## Phase 2 track — Steps 32–37 (executed 2026-10-09)

Approved Phase 2 (hardened payments & owner tools) was executed one step per session on top of
the completed track A+B. Live tracker: `docs/monkey_ai/STATUS.md` (current step) and root
`plan.md` (status rows + §21–§26 records).

| Step | Done | What shipped |
|:---:|:---:|---|
| 32 | ✅ | Attachments (receipts on payments + member docs) — V15 `attachments` BYTEA, `com.tongtin.attachments` HOST upload/stream/delete, `GET /groups/{id}/payments` history, storage settings, host+member UI |
| 33 | ✅ | Blacklist + enforced member status — V16 `member_blacklists` (owner-local), BLOCKED→no login, INACTIVE/BLOCKED→no bid, host manager API + UI |
| 34 | ✅ | KHQR per obligation — real EMVCo CRC16-CCITT (`TONGTIN <code>-O<entryId>`), `com.tongtin.khqr` + zxing render, `khqr` on debts + statement, QR modals |
| 35 | ✅ | Telegram per-host chat + events + daily due-digest — V17 `telegram_chat_id`, `com.tongtin.telegram` (mock-seam client, best-effort notifier), six event hooks, `/host/telegram` |
| 36 | ✅ | Quick-pay single-step settlement — `POST /groups/{id}/quick-pay`, auto-allocate oldest-first incl. LATE_FEE via shared `recordCore`, idempotency replay, host modal |
| 37 | ✅ | Wrap-up & close-out — full docs pass (README ×2, plan.md, STATUS, this roadmap), final suite `mvn package` 224/0, `npm run build` 14/14, Docker compose click-through (all healthy) |

Phase 2 complete — Steps 00–37 all done (suite **224**, web clean, stack green, nothing committed).
