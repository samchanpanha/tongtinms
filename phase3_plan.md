# Phase 3 — Implementation Plan

> **Status**: Proposed — Steps 38–42  
> **Base**: Steps 00–37 complete (suite 224, web 14/14, Docker healthy)  
> **Tracker**: `docs/monkey_ai/STATUS.md` + `plan.md`

---

## Summary of requested features

| # | Feature | Where |
|---|---------|-------|
| A | **Payment Invoice** — printable/downloadable invoice PDF or HTML per payment | Backend + Web |
| B | **Member Bids** — member portal can submit sealed bids on active cycles | Web (already has API from Step 09/13) |
| C | **Random / Equal-Max Bid for Host Winner** — host picks winner by random draw among all ALIVE shares at equal max amount | Backend + Web |
| D | **Disable landing page for logged-in users** — redirect `/` to dashboard if JWT is present | Web only |
| E | **Pagination + Filter on Reports** — ledger, profit, member statement pages support page/size + cycle/status/date filters | Backend + Web |

---

## Step 38 — Payment Invoice (HTML/PDF per payment)

### Scope
Generate a structured payment invoice for each recorded payment. The invoice shows: issuer (host bank + contact), payer (member name + phone), payment date, amount, method, itemised obligation rows, and a confirmation note.

### Backend changes
1. **New endpoint** `GET /api/v1/groups/{groupId}/payments/{paymentId}/invoice`
   - Returns `application/pdf` (via OpenHTML-to-PDF / Flying Saucer) **or** `text/html` when `?format=html`
   - Scoped: host who owns the group only (403 otherwise)
   - Reads `payments`, `payment_allocations`, `ledger_entries`, `member_profiles`, `owner_accounts`
   - No new migration needed (reads only)

2. **New class** `com.tongtin.reports.invoice.InvoiceService`
   - Builds an `InvoiceModel` POJO (plain Java, no Spring)
   - Renders a Thymeleaf HTML template → byte[] PDF via `openhtmltopdf`
   - Alternative lightweight approach: produce a self-contained HTML invoice (no new library) rendered in-browser via a new `/host/groups/[id]/payments/[pid]/invoice` Next.js page (print-ready CSS)

> **Recommendation**: Use the **HTML-only approach** (no Java PDF lib) — render the invoice in a Next.js page with `@media print` CSS. This avoids a new dependency, keeps the zero-float invariant simpler, and users can `Ctrl+P` / browser-print to PDF. Backend only returns structured JSON.

**New endpoint (JSON):**
```
GET /api/v1/groups/{groupId}/payments/{paymentId}/invoice
→ {
    invoiceNo: string,          // "INV-{groupCode}-{paymentId}"
    issuedAt: Instant,
    group: { code, name, currency },
    host: { displayName, bankName, bankAccount, accountHolder },
    payer: { memberName, phone },
    payment: { amountMinor, currency, method, paidAt, note },
    lines: [{ description, cycleNo, type, amountMinor, allocatedMinor }],
    totalAllocatedMinor: long
  }
```

### Frontend changes
- New page: `apps/web/app/host/groups/[id]/payments/[pid]/invoice/page.tsx`
  - Fetches the invoice JSON and renders a styled print layout
  - "Print / Save as PDF" button triggers `window.print()`
  - `@media print` hides nav/header; shows only the invoice card
- In group detail `payments` tab: add an **Invoice** icon button next to each payment row → opens the invoice page in a new tab

### Acceptance
- [ ] `GET /payments/{id}/invoice` 200 for own group payment; 404 cross-group
- [ ] Invoice page renders with host bank details, member name, line items, total
- [ ] Print button visible; `@media print` hides chrome
- [ ] No new `double`/`float` in main source

---

## Step 39 — Member Bids via Member Portal (UI Polish)

> The API already exists (Step 09 + 13: `POST /me/cycles/{id}/bids`).  
> This step wires it fully into the **member portal** UI and adds the sealed-bid reveal animation.

### What's missing in the current web
Looking at `apps/web/app/app/` (member portal), the bid submission form exists but needs:
1. Real-time sealed/revealed state indicator per cycle (countdown to `bid_close_at`)
2. A **bid history** row showing the member's current latest bid amount (after the seal lifts)
3. Input validation UI matching the backend rules (minBid / maxBid / bidStep)
4. Success/error toast feedback

### Backend changes
None — existing `POST /me/cycles/{id}/bids`, `GET /me/groups/{id}`, `GET /me/groups/{id}/cycles` already return what's needed.

### Frontend changes
In `apps/web/app/app/groups/[id]/page.tsx`:
1. **Sealed countdown**: show a live countdown (`bid_close_at - now`) while sealed; flip to "Bids Revealed" with winner highlight after close
2. **My current bid row**: after close, show `myBid.amountMinor` in the cycle summary card
3. **Bid input form**: add `min`, `max`, `step` attributes from group settings; show `formatMoney` range hint
4. **Toast feedback**: success "Bid submitted successfully" / error message from API

### Acceptance
- [ ] Member sees countdown to bid close
- [ ] After seal lifts, member sees their submitted bid amount
- [ ] Bid form rejects < minBid or > maxBid with client-side message
- [ ] Error from API shows as toast (e.g. cycle already closed)

---

## Step 40 — Random / Equal-Max-Bid Winner Selection

### Background
Currently the host closes bids and the winner is determined automatically (highest bid wins, tiebreak per group rule). The new feature: **host can request a random draw** among all ALIVE shares, treating every eligible share as if they bid the group's `maxBid`. This is useful when no members placed bids (or the host wants an equitable lottery round).

### Domain rule
- Applicable only when `cycleType = BIDDING` and cycle is in `BIDDING` status  
- Trigger: `POST /cycles/{id}/close-and-calculate` with `{ "winnerSelectionMode": "RANDOM_EQUAL_MAX" }`
- Logic: pick a winner uniformly at random from all ALIVE shares (ignoring bid amounts); set `winning_bid = maxBid` for the selected share
- Audit: log `CYCLE_SETTLED` with `winnerMode=RANDOM_EQUAL_MAX`

### Backend changes
1. Extend `CycleCloseRequest` DTO: add `winnerSelectionMode` enum field (`HIGHEST_BID` default, `RANDOM_EQUAL_MAX`)
2. In `CycleCloseService.closeAndCalculate()`:
   - If mode = `RANDOM_EQUAL_MAX`: use `ThreadLocalRandom.current().nextInt(aliveShares.size())` to pick winner; set `winningBid = group.maxBid`
   - Ledger math unchanged (formula engine uses the winning bid value normally)
3. New field in `CycleResponse`: `winnerSelectionMode`
4. Audit event: include `winnerMode` in context

### Frontend changes
In the group detail page, in the **Close Bids** modal:
- Add a toggle: "Winner Selection: `Highest Bid` / `Random Draw (equal max)`"
- When `RANDOM_EQUAL_MAX` selected, show info: "All shares treated as equal bidders at max amount. Winner chosen randomly."
- Send `winnerSelectionMode` in the close request body

### Acceptance
- [ ] `POST /cycles/{id}/close-and-calculate` with `RANDOM_EQUAL_MAX` picks a winner among ALIVE shares
- [ ] `winning_bid` stored = `group.maxBid`; ledger math correct
- [ ] Running the close 5× on a fresh identical cycle in tests distributes randomly (stochastic test: at least 2 different winners across 5 runs with N ≥ 3 shares)
- [ ] UI toggle visible in close modal; confirmation text mentions random
- [ ] No `float`/`double` in main source (use `ThreadLocalRandom`, pure integer index)

---

## Step 41 — Disable Landing Page for Logged-In Users

### Scope
When a user with a valid JWT visits `/` (or `/register/owner`), redirect them straight to their dashboard instead of showing the public marketing page.

### Implementation
**Option A — Middleware (recommended):** 
`apps/web/middleware.ts` — check for the `access_token` cookie (or `localStorage` token via a server-readable cookie); if present and role = HOST → redirect to `/host`; role = MEMBER → redirect to `/app`.

**Implementation detail** (since JWT is stored in `localStorage` on the client, not a cookie):  
Use a **client-side redirect** in the landing page itself — check `localStorage.getItem('access_token')` on mount and push to the relevant dashboard.

```tsx
// apps/web/app/page.tsx — add at top of component:
useEffect(() => {
  const token = localStorage.getItem('access_token');
  const roles = JSON.parse(localStorage.getItem('user_roles') || '[]');
  if (token) {
    if (roles.includes('HOST')) router.replace('/host');
    else if (roles.includes('MEMBER')) router.replace('/app');
  }
}, []);
```

Also apply the same redirect to `/register/owner` page (if already logged in, no need to re-register).

### Acceptance
- [ ] Logged-in HOST visiting `/` is immediately redirected to `/host`
- [ ] Logged-in MEMBER visiting `/` is immediately redirected to `/app`
- [ ] Logged-out user sees the landing page normally
- [ ] `/register/owner` redirects to `/host` if already a HOST

---

## Step 42 — Pagination + Filtering for Reports

### Scope
Three reports need pagination and filtering:
1. **Ledger report** (`GET /groups/{id}/ledger`) — filter by cycle, type (CONTRIBUTION/PAYOUT/HOST_FEE/LATE_FEE), status; paginate entries
2. **Profit report** (`GET /groups/{id}/profit`) — filter by cycle status; paginate cycles list
3. **Member statement** (`GET /me/groups/{id}/statement`) — filter by share, type, status; paginate entries

### Backend changes

**New query parameters (all optional, backward-compatible):**

| Endpoint | New params |
|---|---|
| `GET /groups/{id}/ledger` | `cycleId`, `type`, `status`, `page` (0-based), `size` (default 50, max 200) |
| `GET /groups/{id}/profit` | `cycleStatus`, `page`, `size` |
| `GET /me/groups/{id}/statement` | `shareId`, `type`, `status`, `page`, `size` |

**Response envelope** (add `page` wrapper around existing arrays):
```json
{
  "content": [ /* existing entries array */ ],
  "page": { "number": 0, "size": 50, "totalElements": 342, "totalPages": 7 }
}
```

**Java changes:**
- Add `Page<T>` return type using Spring Data `Pageable` in the ledger/profit/statement repository queries
- Add `@RequestParam` for each filter in `ReportController` and `MemberPortalController`
- `ReportService.ledger()` and `MemberReportService.statement()` accept filter + pageable

> [!IMPORTANT]  
> Existing callers that don't pass `page`/`size` get page 0, size 50 — fully backward-compatible. Export endpoints (`/export/ledger`, `/export/profit`) are NOT paginated (they dump all rows).

**New tests** (`ReportPaginationTests`):
- Ledger: 60 entries, fetch page 0 size 10 → 10 rows, totalPages=6
- Filter by `type=CONTRIBUTION` → only CONTRIBUTION rows
- Filter by `status=UNPAID` → only UNPAID rows
- Profit: filter by `cycleStatus=SETTLED`
- Statement: filter by `shareId`

### Frontend changes

**Ledger page** (`apps/web/app/host/groups/[id]/ledger/page.tsx`):
- Add filter bar: Cycle (dropdown), Type (dropdown), Status (dropdown)
- Add pagination controls: Prev / Page N of M / Next
- Show total row count badge

**Group detail — Payments tab:**
- Currently shows all debts/payments; add pagination (page size 20) with Prev/Next
- Add filter by status (UNPAID / PARTIAL / PAID), cycle

**Member portal statement** (`apps/web/app/app/groups/[id]/page.tsx`):
- Statement section: add Type filter + pagination controls

### Acceptance
- [ ] `GET /groups/{id}/ledger?page=1&size=10` returns page 1 with correct `page.totalElements`
- [ ] `GET /groups/{id}/ledger?type=CONTRIBUTION` returns only CONTRIBUTION rows
- [ ] `GET /groups/{id}/ledger?status=UNPAID` returns only UNPAID rows
- [ ] `GET /groups/{id}/profit?cycleStatus=SETTLED` returns only SETTLED cycles
- [ ] Export endpoints still return ALL rows (no pagination applied)
- [ ] UI filter bar + pagination controls render; changing filters resets to page 0
- [ ] `mvn test` suite passes (no regressions)

---

## Bonus ideas (parked unless you approve)

| Idea | Description |
|---|---|
| **Payment Receipt Email** | Send a simple HTML email to member after payment is recorded (requires SMTP settings key) |
| **Bid Analytics Chart** | Host sees a bar chart of all bids in a cycle before/after close (Chart.js) |
| **Member Self-Bid Portal** | Allow members to see and place bids directly on their portal without host action (already API-complete, just needs UI exposure) |
| **Group QR Share Link** | Generate a QR code link that lets a member view their cycle summary on a public (read-only) page |
| **Overdue SMS Alert** | Trigger via Telegram bot when a debt becomes overdue (extend Step 35 hooks) |

---

## Execution order

```
Step 38 → Step 39 → Step 40 → Step 41 → Step 42
```

Each step is independently shippable. Recommended to do them in order since:
- Step 38 (invoice) is pure read — zero risk
- Step 39 (member bid UI) touches only frontend — zero backend risk  
- Step 40 (random winner) is a small backend extension to an existing flow
- Step 41 (landing redirect) is 5 lines of frontend code
- Step 42 (pagination) is the largest change (backend + frontend, new tests)

> [!TIP]
> Say **"implement step 38"** (or any step number) and I'll execute exactly that step, run tests, and update `STATUS.md`.
