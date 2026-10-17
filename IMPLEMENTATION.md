# SplitForce — Implementation Spec

Canonical scope + tech decisions for the SplitForce side project. Anything not written here is not committed to; anything written here should be true of the code at HEAD.

## 1. What SplitForce is

A single app for tracking shared money across three group shapes: **trips**, **homes**, and **general** (two people or an ad-hoc group). Every group has a base currency and a set of members. Every expense is split among members. Balances net out over time.

Purpose: side project. Training ground for HLD/LLD/AI-first application design and end-to-end AWS deployment. **Not commercial.** Expected scale: ≤ 20 users, ≤ 20 groups, over 2 years.

Tagline: *Split expenses. Not friendships.*

## 2. Scope

### 2.1 In scope (v1)

- **Auth**: Google Sign-In via Spring Security OAuth2. Session-cookie auth. Email is the stable user identity.
- **Groups**: One `Group` entity with a `type` field (`trip` / `home` / `general`). Each group has a base currency and user-set FX rates. Extras (itineraries, bookings, shopping list, chores, story) are optional overlays keyed off the type — NOT separate entities.
- **Members**: A group has members. Members are `User` rows. Not-yet-registered members are stored as placeholder users (`placeholder=true`, `<slug>@placeholder.splitforce.local` email), upgraded in place when they first sign in with matching Google email. Every member has a **role** (`manager` / `planner` / `general` / `npc`) — see §5.4.
- **Expenses**: Any split rule (equal, share, exact, percentage). Category tag. Base currency + per-expense currency. Denormalised: each expense stores `Map<memberId, netAmount>` — one query per group gets everything you need to compute balances. Add/edit/delete UI live (`/app/activities/:groupId/add-expense`, `/app/activities/:groupId/expenses/:expenseId/edit`).
- **Balances**: Derived on read. `balance(member) = sum(net over all expenses in group)`.
- **Currency + FX rates**: Each group has a `baseCurrency` (already stored) + a user-set `Map<currencyCode, BigDecimal> fxRates` (rate = "1 unit of this currency in baseCurrency units"). Configured per-group by a manager. Missing rate blocks entering an expense in that currency until the user adds it. **Currency picker options come from `Intl.supportedValuesOf('currency')` — the full ISO 4217 list (~180 codes) on any modern browser, with a static fallback for older ones. Labels via `Intl.DisplayNames`.** UI for FX rate config not built yet — see §12.
- **Group notes**: A free-text notes section on every group, visible to all members, editable by all non-NPC members. One markdown blob per group; last-write-wins. **Not built yet** — see §12.
- **Export**: Per-group CSV export of all expenses. Button on group detail page. **Not built yet** — see §12.
- **Profile**: Editable name and profile picture. Email locked (comes from Google). Picture stored locally in dev; S3 in prod.
- **Checklist primitive**: One entity, labeled per group type — "Shopping list" (home), "Packing list" (trip), "Checklist" (general). One implementation, three labels. Not built yet.
- **AI hooks (deferred spec, but planned)**: draft itineraries from a prompt, OCR receipts, parse booking emails. Runs in a separate service. Architecture TBD.

### 2.2 Explicitly out of scope

Do not build these unless the user reopens them:

- Payment / settlement integrations (Splitwise-style "settle via UPI" links, Venmo, etc.)
- Offline mode / local sync
- Native mobile app (web is mobile-responsive; that is enough)

### 2.3 Deliberately deferred

User is aware; may come back. Flag if a home-tracking feature naturally wants them, but don't build until asked.

- Recurring expenses (rent, utilities monthly repeats)
- Monthly budgets per category

## 3. Stack

| Layer | Choice | Why |
|---|---|---|
| Backend | Spring Boot 3.3 (Java 17) | Vagish's daily driver; fast local iteration |
| Auth | Spring Security oauth2-client, Google provider | May migrate to Cognito on AWS deploy |
| Persistence | DynamoDB (AWS SDK v2 Enhanced Client) | Serverless free-tier target; zero rewrite on prod |
| DB local | `amazon/dynamodb-local:2.5.2`, `-inMemory -sharedDb` | No sqlite volume permission issues; seeder is idempotent so re-seed on restart is fine |
| Frontend | Vite + React 18 + Tailwind | Fast dev; small bundle |
| AI | Separate service, TBD | Not on the critical path |

**Ports (local dev)**

| Service | Port |
|---|---|
| DynamoDB Local | 8000 |
| Backend (Spring) | 9090 |
| Frontend (Vite) | 7173 (`strictPort: true`) |

Port 5173 is used by mc-studio-services (work project). Port 8080 is also owned by work stuff. Do not use either.

## 4. Deployment target

**AWS free tier.** Must stay free at the expected scale.

Prefer serverless:
- Compute: Lambda
- DB: DynamoDB
- Static: S3 + CloudFront
- Auth: Cognito (migrate from Spring Security oauth2-client when deploying)

Avoid always-on: RDS, EC2, NAT Gateway. They blow the free tier.

## 5. Persistence — DynamoDB layout

Table prefix: `splitforce_`. All tables use single-region on-demand billing in prod.

### `splitforce_users`

- PK: `userId` (String, UUID v3 name-based, seed `"user:" + email`)
- Attrs: `email`, `name`, `picture` (URL — Google's remote pic OR null when a local upload exists), `pictureContentType`, `pictureUpdatedAt`, `placeholder` (bool), `createdAt`
- **GSI**: `email-index` — PK: `email`. Used to look up user by Google email at login.

### `splitforce_groups`

- PK: `groupId` (UUID)
- Attrs: `name`, `type` (`trip` | `home` | `general`), `baseCurrency`, `ownerUserId`, `createdAt`
- `fxRates`: `Map<String, BigDecimal>` — currency code → rate vs `baseCurrency`. `baseCurrency` itself is implicitly 1.0 and not stored in the map. Missing entry means expenses in that currency can't be entered until a manager sets a rate.
- `notes`: String (markdown, plain-text OK, ~10 KB soft cap). Free-text section shown on the group page; editable by all non-NPC members.

### `splitforce_group_members`

- PK: `groupId`, SK: `userId`
- Attrs: `displayName`, `active`, `joinedAt`, `role`
- `role`: enum — `manager` / `planner` / `general` / `npc` (default `general`; `npc` for members who don't participate in expenses and just exist to be tagged).
- **GSI**: `user-groups-index` — PK: `userId`. Used for "list my groups" without a scan.

### 5.4 Member roles + permissions

Role determines what a member can do inside a group. Enforced server-side on every mutation.

| Action | manager | planner | general | npc |
|---|---|---|---|---|
| Log/edit own expenses | ✓ | ✓ | ✓ | — |
| Edit others' expenses / delete expenses | ✓ | — | — | — |
| Add members | ✓ | ✓ | — | — |
| Remove members / change roles | ✓ | — | — | — |
| Rename group / edit type | ✓ | — | — | — |
| Change base currency | — (immutable — set at creation) |||
| Add / edit FX rates | ✓ | ✓ | ✓ | — |
| Remove FX rate | ✓ (only if no expense in that currency exists) | ✓ (same) | ✓ (same) | — |
| Edit group notes | ✓ | ✓ | ✓ | — |
| Add itinerary/booking items (trip groups) | ✓ | ✓ | — | — |
| Delete group | owner only | — | — | — |
| Export transactions | ✓ | ✓ | ✓ | — |

- The group's `ownerUserId` (creator) is always a manager and cannot be demoted or removed.
- **NPC** = placeholder-style member with no login, or an active member you don't want participating in shares (e.g. a kid, or a business partner you tag in notes but don't split with). They can't log in or edit anything; they just exist to be referenced.
- On group creation, the creator is added as `manager`; all invited members default to `general`.

### `splitforce_expenses`

- PK: `groupId`, SK: `expenseId` (composite: `date + "#" + uuid` — sorts chronologically inside a group)
- Attrs: `date`, `description`, `category`, `cost`, `currency`, `payment` (bool), `createdAt`
- **`shares`**: `Map<userId, BigDecimal>` — net = paid − share. Sums to zero across all members of the group. **Balance = sum of this member's `shares` values across all group expenses.**

Design rationale: no separate `ExpenseShare` table. One `Query(PK=groupId)` returns everything needed to compute balances + list expenses. Trades storage for query simplicity — fine at our scale.

## 6. Auth flow

1. User hits landing page. Clicks *Sign in with Google*.
2. Frontend redirects to `GET http://localhost:9090/oauth2/authorization/google`.
3. Spring Security redirects to Google. User consents.
4. Google redirects to `http://localhost:9090/login/oauth2/code/google`.
5. Spring exchanges the code, extracts email/name/picture from the ID token.
6. `UserService.resolveOrCreate` finds by email (`email-index` GSI):
   - Placeholder row exists → flip `placeholder=false`. **Keep existing memberships intact** (userId is email-derived, so it's stable).
   - Real row exists → do NOT overwrite user-edited name/picture. Only backfill blank fields from Google.
   - No row → create with deterministic UUID.
7. Redirect to `${FRONTEND_URL}/app/activities`.

Sessions are cookie-based (`JSESSIONID`, `SameSite=Lax`, `HttpOnly`). Frontend calls the API with `credentials: 'include'`.

## 7. API surface (current)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | public | health probe |
| GET | `/api/me` | required | current user `{userId, email, name, picture}` |
| PATCH | `/api/me` | required | edit name (body `{name}`) |
| POST | `/api/me/picture` | required | multipart image upload (5 MB max, image/* only) |
| POST | `/api/logout` | required | clears session |
| GET | `/api/groups` | required | list groups the current user is a member of |
| GET | `/pictures/{userId}` | public | serves uploaded picture bytes |
| GET | `/oauth2/authorization/google` | public | start OAuth flow |

**Planned (not yet built)** — see §12 for the shape of each page:

| Method | Path | Auth / role | Purpose |
|---|---|---|---|
| POST | `/api/groups` | required | create group (creator becomes `manager`) |
| GET | `/api/groups/{id}` | member | group detail (settings, notes, members, fxRates) — **live** |
| PATCH | `/api/groups/{id}` | manager | update name only. baseCurrency is immutable — sending a different value returns 400 — **live** (currently any member, roles TBD) |
| PUT | `/api/groups/{id}/fx-rates/{code}` | any active member | set/update FX rate (body `{rate}`, rate > 0, code != baseCurrency). Balances re-derive on next read — **live** |
| DELETE | `/api/groups/{id}/fx-rates/{code}` | any active member | remove FX rate. Rejected with 409 if any expense in the group uses that currency — **live** |
| PATCH | `/api/groups/{id}/notes` | non-npc member | replace notes blob |
| POST | `/api/groups/{id}/members` | manager or planner | add member (existing user or placeholder) |
| PATCH | `/api/groups/{id}/members/{userId}` | manager | change role / displayName |
| DELETE | `/api/groups/{id}/members/{userId}` | manager | remove member |
| GET | `/api/groups/{id}/expenses` | member | list expenses (sorted date desc) |
| POST | `/api/groups/{id}/expenses` | non-npc member | log an expense |
| PUT | `/api/groups/{id}/expenses/{expenseId}` | author or manager | full edit (same body as POST) — **live** (currently any member, roles TBD) |
| PATCH | `/api/groups/{id}/expenses/{expenseId}` | author or manager | edit description/category only — **live** |
| DELETE | `/api/groups/{id}/expenses/{expenseId}` | manager | delete expense |
| GET | `/api/groups/{id}/export` | member | CSV export of group transactions |
| GET | `/api/activities` | required | see §11 (My Activities feed) |

**Picture URL semantics.** `/api/me` returns `picture`:
- Local upload present → `http://<host>/pictures/{userId}?v=<epoch>` (cache-busted per upload).
- Otherwise → Google's remote URL (or null).

## 8. Frontend structure

- `/` — `Landing.jsx`, public, marketing page + Google login CTA.
- `/app` (`ProtectedRoute` — 401 kicks to `/`):
  - `/app/activities` — `Activities.jsx`. Cross-group feed / balances (see §11).
  - `/app/activities/:groupId` — `GroupDetail.jsx`. Group page: header (editable name + baseCurrency), balances, settle-up, expenses list. Add-expense, notes, members, roles, FX rates, export still to come — see §12.
  - `/app/create` — `Create.jsx`. Trip / group / one-off flows; creates group + memberships in one call. Picker options: full ISO currency list.
  - `/app/profile` — `Profile.jsx`. Editable name + working picture upload. Email locked.
- **No standalone "My Groups" tab.** Groups are surfaced from Activities and reached individually via `/app/activities/:groupId`. `GET /api/groups` still exists (used by Activities to list balances); there is no top-level page for it.
- Top-right of `AppLayout` shows the avatar (Google URL or uploaded pic). Click → `/app/profile`. No "My Profile" nav item.

State: `AuthProvider` (`AuthContext.jsx`) holds `user`, exposes `logout`, `refresh`, `setUser`. Fetched from `/api/me` on mount.

## 9. Seed data

4 real Splitwise CSV exports at `backend/src/main/resources/seed/`:

- `kazakhstan-trip.csv` → "Kazakhstan Trip" (trip)
- `flat-with-ashish.csv` → "Flat with Ashish" (home)
- `madurai-trip.csv` → "Madurai Trip" (trip)
- `college-general.csv` → "College Group" (general)

`SeedRunner` (`@Order(2)`) runs on boot:
- Deterministic UUIDs for user/group IDs (`UUID.nameUUIDFromBytes`) → re-runs are idempotent.
- Vagish (`vagishyagnik@gmail.com`) is created as a real (non-placeholder) user. Other members are placeholders with `<slug>@placeholder.splitforce.local` emails. Upgraded when they sign in.
- CSV format is Splitwise's `net = paid − share` per member. **Do not attempt to reconstruct `(payer, split)`** — that's under-determined from a net-only export. Store the raw net map.
- Skips a group entirely if the deterministic groupId is already present.

Current seed state (verified): 18 users / 4 groups / 21 memberships / 171 expenses.

## 10. Constraints for anything you build

- **No commercial launch code paths.** No pricing, no plans, no upgrade CTAs.
- **No destructive git actions** without explicit user OK: no force-push, no reset --hard, no --no-verify.
- **No unrelated cleanup / refactors** in a feature commit. If you see cruft, mention it separately.
- **No fake fallbacks / mocks.** If a backend endpoint doesn't exist yet, don't write a UI that silently falls back to hardcoded data — say so and stop.
- **Small over abstract.** Three similar lines beat a premature abstraction.

## 11. Next up: `/app/activities` — open decision

Two viable shapes, pick one:

**Option A — "Today" view (recommended).** Anchored to today. Sections:
- *Recent expenses* — last N expenses across all groups the user is in, most recent first, each showing group name + amount + net-for-me (green/red).
- *Balances by group* — one row per group with the user's net position in that group's base currency.
- *Upcoming* — placeholder for later (trip start dates, checklist due items).

Rationale: matches how someone actually uses a splits app — "what changed since I looked yesterday, and where do I stand?".

**Option B — Cross-group feed.** Just a chronological feed of expenses across groups. Simpler; less useful; a lot of overlap with per-group expense list once that exists.

Backend work needed either way:
- New endpoint `GET /api/activities` returning `{ recent: Expense[], balances: {groupId, groupName, currency, net}[] }`. Cap `recent` at ~30. Recent is a query per group the user is in (scale is fine — user is in ≤ 20 groups) then merge-sort by date desc.
- Reuse `ExpenseRepository.listByGroup(groupId)` and `GroupMemberRepository.listByUser(userId)`.

Frontend work: replace the `Activities.jsx` placeholder with the sections above. Two cards + a simple list.

## 12. Group Detail page — sections

Route: `/app/activities/:groupId`. Data: `GET /api/groups/{id}` (returns group + members + expenses + settlement suggestions).

Sections 1, 2, 4, and the Currencies panel (7-lite) are live in `GroupDetail.jsx` today. 3, 5, 6, and the settings-heavy parts of 7 are still to build.

Sections, top to bottom:

1. **Header** — group name, type badge, base currency, member count, "Export CSV" button (calls `GET /api/groups/{id}/export`, saves file).
2. **Balances** — one row per active (non-npc) member with net position in base currency, colored green/red.
3. **Add expense** — inline form or modal:
   - Amount + currency (defaults to base currency; if a non-base currency is chosen and no `fxRate` exists yet, prompt manager to set it inline)
   - Description, category, date (defaults to today)
   - Payer (defaults to current user)
   - Split rule: `equal` | `share` (weights) | `exact` (per-member amounts) | `percentage`
   - Save → `POST /api/groups/{id}/expenses`; server computes the `shares` map so the invariant `sum(shares) == 0` is enforced server-side.
4. **Expense list** — chronological, most recent first. Each row: date, description, category, payer, amount + currency, your net. Click → edit modal (author or manager).
5. **Notes** — markdown text area editable by all non-npc members. `PATCH /api/groups/{id}/notes`. Last-write-wins (no realtime collab at this scale).
6. **Members** — list with role badges. Managers see role dropdowns + remove buttons. Non-managers see read-only. "Add member" button opens a picker (existing user by email, or create placeholder from name).
7. **Settings** — split into two panels:
   - **Currencies (any member)** — live. Base currency shown at top; below it, a list of `fxRates` entries (`code → rate in baseCurrency units`) with per-row edit + remove, and an "Add currency" form. Backed by `PUT /api/groups/{id}/fx-rates/{code}` and `DELETE /api/groups/{id}/fx-rates/{code}`.
   - **Group settings (managers only, still to build)** — edit name, type, base currency (changing base invalidates FX rates — needs a confirm + wipe flow).
8. **Trip-only overlays** (later) — itinerary, bookings, packing list. Keyed off `group.type === 'trip'`.
9. **Home-only overlays** (later) — shopping list, chores. Keyed off `group.type === 'home'`.

### 12.1 CSV export format

Columns: `date, description, category, cost, currency, base_cost, payer, <one column per member: net in base currency>`.

Content-Type: `text/csv`. Filename: `<group-slug>-YYYYMMDD.csv` via `Content-Disposition`.

### 12.2 FX rate lookup semantics

- Group stores `baseCurrency` (set once at creation, **immutable**) and `fxRates: Map<code, rate>`.
- Rate meaning: `rate` = how many `baseCurrency` units you get for **1 unit** of `code`.
- To convert an expense of `cost` in `currency` to base:
  - If `currency == baseCurrency`: base_cost = cost.
  - Else if `fxRates[currency]` set: base_cost = cost × fxRates[currency].
  - Else: reject the expense at write time with "please set FX rate for X first". Do NOT silently default to 1.0.
- **Balances are derived on read**, not stored. `GET /api/groups/{id}` sums each expense's `shares` × current `rateToBase(expense.currency)`. Changing a rate therefore automatically re-runs the "who owes what" calculation on the next fetch — no batch job, no stored aggregates to invalidate.
- **Rate removal is blocked if any expense in the group uses that currency** (409). Delete or re-currency the expenses first.
- Rates are set manually by any group member. No live FX API — deliberately. User-set is truthful for how they actually settled.

## 13. Local dev — run it

```bash
# 1. DynamoDB Local
cd ~/IdeaProjects/splitforce && docker compose up -d

# 2. Backend (reads backend/.env.local for GOOGLE_CLIENT_ID/SECRET, FRONTEND_URL)
cd ~/IdeaProjects/splitforce/backend && ./run.sh

# 3. Frontend
cd ~/IdeaProjects/splitforce/frontend && npm run dev
```

Then `http://localhost:7173`.

**Google Cloud OAuth client** (`hazel-goal-324212`) must have authorized redirect URI `http://localhost:9090/login/oauth2/code/google`.

DynamoDB Local runs in-memory — restarting the container wipes state, and `SeedRunner` rebuilds it on next backend boot. That's the whole persistence loop for dev.

## 14. Where things live

```
splitforce/
├── docker-compose.yml            # DynamoDB Local
├── IMPLEMENTATION.md             # this file
├── README.md                     # short readme
├── .gitignore
├── backend/
│   ├── pom.xml
│   ├── run.sh                    # sources .env.local, mvn spring-boot:run
│   ├── .env.local                # secrets (gitignored)
│   ├── data/profile-pics/        # uploaded pics (gitignored)
│   └── src/main/
│       ├── java/com/splitforce/
│       │   ├── SplitForceApplication.java
│       │   ├── config/           # SecurityConfig, DynamoDbConfig, DynamoDbTables, DynamoDbTableInitializer
│       │   ├── user/             # User, UserRepository, UserService, UserController, ProfilePictureStore
│       │   ├── group/            # Group, GroupType, GroupMember, GroupRepository, GroupMemberRepository, GroupController
│       │   ├── expense/          # Expense, ExpenseRepository
│       │   └── seed/             # CsvExpenseParser, SeedRunner
│       └── resources/
│           ├── application.yml
│           └── seed/*.csv
└── frontend/
    ├── vite.config.js            # port 7173, strictPort
    ├── tailwind.config.js
    └── src/
        ├── App.jsx
        ├── main.jsx
        ├── api/client.js         # apiGet/apiPost/apiPatch/apiUpload
        ├── auth/AuthContext.jsx  # user, refresh, setUser, logout
        └── routes/
            ├── Landing.jsx
            ├── AppLayout.jsx
            ├── ProtectedRoute.jsx
            ├── Activities.jsx
            ├── Create.jsx
            ├── GroupDetail.jsx   # /app/activities/:groupId
            └── Profile.jsx
```
