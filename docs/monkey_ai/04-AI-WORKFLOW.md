# AI Session Workflow

Use this file at the start of every continuation session.

## Boot sequence

1. Read `docs/STATUS.md`
2. Read `docs/01-DOMAIN.md` if the task touches money, bids, or states
3. Read the current step in `docs/03-BUILD-ROADMAP.md`
4. Implement only that step
5. Run the step's tests or manual accept checks
6. Update `docs/STATUS.md`
7. Stop. Do not start the next step unless the user says so

## Hard rules

- Money is `long` minor units plus currency code; never `double`
- Formulas must match fixtures in `docs/08-FORMULA-ENGINE.md`
- One currency per group; reports always include currency; no FX
- Ledger is append-only
- Browser is not the source of truth for payout math
- One active cycle per group
- Winner share pays 0 that cycle, then becomes DEAD
- Last BIDDING cycle has B = 0 and no auction
- Vietnamese UI labels, English code identifiers
- Frontend `/api` proxy to Spring Boot
- Do not add Zalo/SMS/microservice split in MVP
- Do not commit unless the user asks
- Do not delete files
- Do not invent extra group types before RANDOM is scheduled
- Public signup is owner-only (`/auth/register-owner`). Do not add public member register in MVP
- Scope all host data by `owner_id`
- Phone is login id, unique on users
- No free-form / eval formulas. Presets + parameters only. See `docs/06-SETTINGS-TEMPLATES-RISK.md`
- Blacklist is per owner_id only. No global public blacklist
- Group templates must not mutate RUNNING groups

## State machine cheat sheet

Group: DRAFT -> RECRUITING -> READY -> RUNNING -> COMPLETED
Cycle: DRAFT -> OPEN -> BIDDING -> CLOSED_FOR_CALC -> PAYOUT_PENDING -> SETTLED
Share: ALIVE -> DEAD after receiving pot

## Formula cheat sheet

BIDDING:
```
grossPot = D * C + (A - 1) * (C - B)
netPayout = grossPot - T
```

FIXED:
```
grossPot = (N - 1) * C
netPayout = grossPot - T
```

Test fixture:
N=10, C=1000000, T=100000, cycle1 B=200000, netPayout=7100000

## How to talk to the next session

User should say:
```
Continue Tong Tin from docs/STATUS.md. Do only the current step.
```

If STATUS is missing or CURRENT_STEP is 00, start Step 01.

Reusable skill (same process): `.opencode/skills/tongtin-continue/SKILL.md`

## File ownership

- Domain change: update `docs/01-DOMAIN.md` first, then code
- New API: update `docs/02-ARCHITECTURE.md` then code
- Step done: update `docs/STATUS.md` only after accept checks pass
