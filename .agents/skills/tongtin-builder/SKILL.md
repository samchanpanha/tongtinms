---
name: tongtin-builder
description: Step-by-step implementation and continuation skill for building the Tong Tin (Hội / ROSCA) Management System based on docs/manus_ai and docs/monkey_ai specifications.
---

# Tong Tin Builder & Continuous Delivery Skill

This skill governs the autonomous, disciplined implementation of the **Tong Tin Management System** (Hội / Chụi / ROSCA) following the strict, sequential, one-step-at-a-time methodology defined in `docs/monkey_ai/03-BUILD-ROADMAP.md` and `docs/monkey_ai/04-AI-WORKFLOW.md`.

## Protocol & Execution Rules

1. **Check Status First**:
   Always read `docs/monkey_ai/STATUS.md` to identify the `CURRENT_STEP`.
2. **Execute Exactly One Step**:
   Do NOT implement multiple steps in a single session unless the user explicitly commands it.
3. **Reference Source Docs**:
   - For domain rules, state machines, and invariants: read [01-DOMAIN.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/01-DOMAIN.md).
   - For architecture, modules, and database schemas: read [02-ARCHITECTURE.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/02-ARCHITECTURE.md).
   - For multi-currency minor units: read [07-MULTI-CURRENCY.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/07-MULTI-CURRENCY.md).
   - For formula calculations & fixtures: read [08-FORMULA-ENGINE.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/08-FORMULA-ENGINE.md).
   - For owner registration: read [05-OWNER-SELF-REGISTER.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/05-OWNER-SELF-REGISTER.md).
   - For governance, meeting dispute safety & blacklist: read [06-SETTINGS-TEMPLATES-RISK.md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/monkey_ai/06-SETTINGS-TEMPLATES-RISK.md) and [Tong Tin Management System — Product Blueprint and Build Plan (4).md](file:///Users/samchanpanha/Desktop/projects/projects/tong_tin/tongtin/docs/manus_ai/Tong%20Tin%20Management%20System%20%E2%80%94%20Product%20Blueprint%20and%20Build%20Plan%20%284%29.md).
4. **Hard Invariants**:
   - **Money**: Always `long` minor units (`BIGINT` in PostgreSQL), paired with ISO 4217 `CHAR(3)` currency. NEVER `double` or `float`.
   - **Formulas**: Pure Java calculator with ZERO Spring or DB dependencies in the engine class.
   - **Tenant Boundary**: Scoped by `owner_id`. No cross-tenant data leakage.
   - **Public Registration**: In MVP, public signup is ONLY for Owners/Hosts (`/register/owner`). Members are paper profiles first.
   - **Sealed Bids**: Member bids are sealed until deadline. Hosts cannot read amounts prior to closure.
   - **Ledger**: Append-only. No row deletes. Corrections use explicit reversing entries.
5. **Acceptance Verification**:
   Run tests or verify acceptance criteria for the current step before marking done.
6. **Update Status**:
   Update `docs/monkey_ai/STATUS.md` with completed deliverables and advance `CURRENT_STEP`.
