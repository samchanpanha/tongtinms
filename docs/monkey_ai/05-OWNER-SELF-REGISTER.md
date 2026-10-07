# Owner Self-Registration Analysis

Feature name: Owner (Chu Hoi) self-serve signup
Vietnamese: Chu Hoi tu dang ky tai khoan so huu
Related step: Step 03 Identity (do not code until that step)

## 1. What you asked for

You want a **new client to register as owner**, not as a group member.

That means a stranger can open `/register/owner`, create their own host account, then run Tong Tin groups under that account. Members are added later by that owner (or by invite). Owner signup is not the same as Hoi Vien signup.

## 2. Why this must be a separate flow

If one `/register` creates both HOST and MEMBER, you get:

- a member accidentally becomes a Chu Hoi and can create fake groups
- no tenant boundary: one host sees another host's members
- phone reuse fights: same person is owner of one group and paper-only member of another
- no KYC / business profile for the person who holds other people's money

Decision: **two public signup paths**.

| Path | Role created | Tenant created | After success |
|---|---|---|---|
| `/register/owner` | HOST | yes, new OwnerAccount | `/host` onboarding |
| `/register/member` | MEMBER | no | `/app` wait for invite / join code |
| Host adds paper member | no User yet | stays in that host tenant | MemberProfile only |

MVP public signup: **owner only**.
Member login accounts come later via invite (Step 13). Host can still add paper Hoi Vien without an app login (Step 04).

## 3. Tenant model (new)

Owner = tenant. Data is isolated by `owner_id`.

```
OwnerAccount 1-1 User (role HOST)
  ├── MemberProfiles (Hoi Vien of this Chu Hoi)
  ├── Groups
  ├── Cycles / Bids / Ledger / Payments
  └── optional Staff later
```

A User with HOST can also hold MEMBER role in **another** owner's group later. That is a second membership, not a second User.

Hard rule: `host_id` / `owner_id` on every business table. Never list across owners.

## 4. Owner register fields

### Required now (Step 03)

| Field | Rule |
|---|---|
| `fullName` | 2..120 chars |
| `phone` | VN mobile, unique among users |
| `password` | 8+ chars, not equal to phone |
| `confirmPassword` | must match |
| `acceptTerms` | must be true |

Phone format: `0xxxxxxxxx` or `+84xxxxxxxxx`, store E.164 `+84...`.

### Strongly recommended same screen (still Step 03 if cheap)

| Field | Rule |
|---|---|
| `displayName` | public Chu Hoi name, default = fullName |
| `email` | optional unique, for recovery |

### Phase 2 onboarding (after first login, not blocking register)

| Field | Why |
|---|---|
| `cccd` | identity of person holding the pot |
| `bankName` `bankAccount` `accountHolder` | payout destination shown to members |
| `zalo` | reminders later |
| `city` | trust / directory later |
| `defaultFeeType` `defaultFeeValue` | template for new groups |

Do not block signup on CCCD in MVP. Show onboarding checklist on `/host` until bank + CCCD filled. Groups can still be created.

## 5. Register workflow

```
GET /register/owner
  -> form
POST /api/v1/auth/register-owner
  -> validate
  -> reject duplicate phone / email
  -> hash password
  -> create User status ACTIVE role HOST
  -> create OwnerAccount status ACTIVE
  -> create audit OWNER_REGISTERED
  -> return access + refresh JWT
  -> redirect /host/onboarding
```

No email verify in MVP. Phone is the login id.

State:

```
User: PENDING_VERIFY (unused in MVP) | ACTIVE | SUSPENDED | CLOSED
OwnerAccount: ACTIVE | SUSPENDED | CLOSED
```

MVP: register creates ACTIVE + ACTIVE.

## 6. Login identity

Owner logs in with **phone + password**.
Email login is extra if email was provided.

JWT claims:

```
sub = userId
ownerId = ownerAccountId
roles = ["HOST"]
```

All host APIs require `roles` contains HOST and `ownerId` matches resource.

## 7. Duplicate and takeover rules

- Same phone cannot register twice
- If phone exists as MEMBER user, do not auto-upgrade to HOST
- If host wants that person as Hoi Vien, they add a MemberProfile linked by phone, not by stealing the User
- If a MEMBER later wants to become owner: separate `POST /auth/upgrade-to-owner` (phase 2), creates OwnerAccount, adds HOST role, keeps same User
- Paper MemberProfile phone may match an Owner phone in another tenant. That is allowed. Linking User is optional and explicit

## 8. Security analysis

Risk: anyone can become Chu Hoi and collect money in the app narrative.

MVP controls:
- rate limit register by IP (20 / hour)
- rate limit login
- password BCrypt
- no default demo group with real money
- owner only sees own tenant
- audit register IP + user-agent (store hash of IP if you want less PII)

Later controls (parked):
- CCCD photo
- admin approve owner before first RUNNING group
- deposit / escrow
- device fingerprint

Do not add admin-approval gate in Step 03. Keep signup instant so you can demo. Add `requireOwnerApproval` config default false.

## 9. API (Step 03)

```
POST /api/v1/auth/register-owner
  body: { fullName, phone, password, confirmPassword, email?, displayName?, acceptTerms }
  201: { user, owner, tokens }
  409: phone or email taken
  400: validation

POST /api/v1/auth/login
  body: { phone, password }
  200: { user, owner?, roles, tokens }

GET  /api/v1/me
  200: { user, owner, roles, onboarding }

PATCH /api/v1/me/owner-profile
  host only, onboarding fields
```

Do **not** ship `POST /auth/register` as a generic role picker in MVP.
If a generic register exists, it must default to MEMBER and must not create OwnerAccount.

## 10. UI

- `/register/owner` Chu Hoi dang ky
- `/login` Dang nhap (phone)
- `/host/onboarding` checklist: CCCD, bank, first group
- Login page link: "Ban la Chu Hoi moi? Dang ky chu hoi"

Copy VN:
- Dang ky Chu Hoi
- So dien thoai
- Mat khau
- Ten Chu Hoi
- Toi dong y dieu khoan

## 11. Data model add-on

```
users
  id, phone unique, email unique nullable, password_hash,
  full_name, status, created_at

owner_accounts
  id, user_id unique, display_name, status,
  cccd nullable, bank_name, bank_account, account_holder,
  zalo, city, created_at

user_roles
  user_id, role  -- HOST, MEMBER, ADMIN
```

Replace loose `groups.host_id -> users.id` with `groups.owner_id -> owner_accounts.id`.
Member profiles: `member_profiles.owner_id`.

This is a domain correction. Apply in Step 02/03 schema, not as a later refactor if we have not coded yet.

## 12. What not to mix in

- Do not create a Group on register
- Do not create a default share for the owner
- Do not require the owner to be a Hoi Vien (`hostIsMember` stays a group flag, default false)
- Do not send SMS OTP in MVP
- Do not open public member registration until invite flow exists

## 13. Accept checks when Step 03 is built

1. New phone can register owner and get JWT
2. Duplicate phone returns 409
3. Login with that phone reaches `/api/v1/me` with role HOST and ownerId
4. Second owner cannot see first owner's empty member list
5. `/register` without owner path does not create OwnerAccount
6. Password is not stored plaintext

## 14. Decision log

- Owner self-register: YES, MVP, Step 03
- Member self-register: NO in MVP (invite later)
- Member login (Step 13): host-set password. Host calls POST /members/{id}/set-login
  to create the member's `users` row (phone from profile, MEMBER role) or reset its
  password. No SMS/OTP in MVP (05 §8), no public member register. The member logs in
  with phone + that password via the shared POST /auth/login (role-agnostic). A
  member's identity = every member_profiles row with that phone across owners.
- Instant activate: YES
- Admin approve owner: NO in MVP, config hook only
- Tenant column: owner_id on business tables
