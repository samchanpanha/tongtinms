# Tong Tin Management System - Planning Index

This folder is the source of truth for building the product one step at a time.
Read files in this order at the start of every new session.

1. `docs/monkey_ai/00-INDEX.md` - this file
2. `docs/monkey_ai/01-DOMAIN.md` - business terms, states, formulas
3. `docs/monkey_ai/02-ARCHITECTURE.md` - stack, modules, data model, APIs
4. `docs/monkey_ai/03-BUILD-ROADMAP.md` - MVP steps 00-17 + post-MVP track 18-31
5. `docs/monkey_ai/04-AI-WORKFLOW.md` - how the next session should continue
6. `docs/monkey_ai/05-OWNER-SELF-REGISTER.md` - Chu Hoi self-signup analysis
7. `docs/monkey_ai/06-SETTINGS-TEMPLATES-RISK.md` - formulas, blacklist, templates, meeting safety
8. `docs/monkey_ai/07-MULTI-CURRENCY.md` - currency choice, minor units, report shape
9. `docs/monkey_ai/08-FORMULA-ENGINE.md` - default presets and custom engine rules
10. `docs/monkey_ai/STATUS.md` - current step, done items, next action
11. `plan.md` (root) - full step log: §0 status rows, §1-§19 records/decisions
12. `docs/slides/index.html` - presentation summary
13. `.agents/skills/tongtin-builder/SKILL.md` - step-by-step continuation skill

Product name: Tong Tin Manager
Also known as: Hoi, Chui, ROSCA

All 31 steps (MVP 00-17 + post-MVP 18-31) are implemented and verified
(184 backend tests, ESLint/tsc clean, Docker compose verified). `docs/monkey_ai/STATUS.md`
records the current state. Further changes require an explicit user request; parked
P-items and §7.5 formula reminders are open only with the user's explicit go-ahead.