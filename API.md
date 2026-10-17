# SplitForce Backend API

Reference for wiring a frontend to the SplitForce backend. Every path, verb, request body, and response shape needed to build a UI.

## Base

- **Base URL (local):** `http://localhost:9090`
- **Frontend origin allowed by CORS:** value of `FRONTEND_URL` env (default `http://localhost:5173`)
- **Auth model:** cookie-based session (`JSESSIONID`) from Google OAuth 2.0 (openid, email, profile). Every request from the browser must use `credentials: 'include'`.
- **Content-Type:** `application/json` for bodies unless noted (uploads use `multipart/form-data`).
- **Currency amounts** are `BigDecimal` (JSON numbers, 2-decimal). Currency codes are ISO-4217 uppercase 3-letter strings.
- **Dates** are ISO `YYYY-MM-DD` strings. Timestamps (`createdAt`, `lastActivityAt`) are ISO-8601 UTC strings.

## Auth

Auth is not a JSON endpoint — it's a redirect flow.

| Purpose | Method | URL |
|---|---|---|
| Start Google login | `GET` (browser navigate) | `/oauth2/authorization/google` |
| OAuth callback (Google → backend) | `GET` | `/login/oauth2/code/google` |
| Success redirect (backend → frontend) | 302 | `${FRONTEND_URL}/app/activities` |
| Failure redirect | 302 | `${FRONTEND_URL}/?login=failed` |
| Log out | `POST` | `/api/logout` → `204 No Content` |

Any `/api/**` request without a valid session returns **`401 Unauthorized`** with an empty body. Use `GET /api/me` on app load to check auth state.

**Public (no auth required):** `/`, `/api/health`, `/error`, `/pictures/**`, `/receipts/**` (but receipts respect group membership implicitly — no one guesses random UUID filenames).

## Errors

All error responses use Spring's standard body:

```json
{
  "timestamp": "2026-09-18T10:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "cost must be > 0",
  "path": "/api/groups/abc/expenses"
}
```

Common status codes: `400` validation, `401` unauthenticated, `403` not a member, `404` not found, `409` conflict (e.g. delete currency in use), `413` upload too large, `415` unsupported media type, `500` server error.

---

## 1. Health

### `GET /api/health`
Public. No auth.

**Response 200**
```json
{ "status": "ok" }
```

---

## 2. Me (current user)

### `GET /api/me`
Returns the signed-in user's profile.

**Response 200**
```json
{
  "userId": "u_xxx",
  "email": "you@example.com",
  "name": "Your Name",
  "picture": "http://localhost:9090/pictures/u_xxx?v=1690000000000"
}
```

- `picture` may be an uploaded server URL (`/pictures/<userId>?v=<epoch>`) or the Google-provided URL for users who never uploaded — always safe to `<img src>` directly.
- **401** when session is missing / expired. Trigger a login redirect.

### `PATCH /api/me`
Update the current user's profile.

**Request**
```json
{ "name": "New Name" }
```
Only `name` is settable. Email is locked (from Google).

**Response 200** — same shape as `GET /api/me`.

### `POST /api/me/picture`
Upload a new profile picture.

**Request:** `multipart/form-data`, one part named `file`.
- Max size: **5 MB**
- `Content-Type` must start with `image/`

**Response 200** — same shape as `GET /api/me` (with updated `picture` URL).

**Errors:** `400` empty file / not image / too large.

### `GET /pictures/{userId}`
Serves the uploaded picture bytes. Public. Cache-Control `no-cache`. Returns `404` if the user has no upload.

---

## 3. Activities (dashboard)

### `GET /api/activities`
List of groups the current user belongs to, enriched with counts, last activity, and the user's net balance in each group.

**Response 200**
```json
[
  {
    "groupId": "abc",
    "name": "Bali 2026",
    "type": "trip",
    "currency": "INR",
    "memberCount": 4,
    "expenseCount": 12,
    "lastActivityAt": "2026-08-30",
    "myNet": 1234.56
  }
]
```

- `type` is one of `trip`, `home`, `general`.
- `currency` is the group's **base currency** (not per-expense).
- `myNet` is the current user's net balance across all expenses in that group, **converted to the base currency**. Positive = they owe you; negative = you owe them.
- Sorted by `lastActivityAt` descending (nulls last).

---

## 4. Groups

### `GET /api/groups`
Lightweight list — just the groups you belong to.

**Response 200**
```json
[
  {
    "groupId": "abc",
    "name": "Bali 2026",
    "type": "trip",
    "baseCurrency": "INR"
  }
]
```

Use `/api/activities` instead when you also need counts / net / last activity.

### `POST /api/groups`
Create a group. The current user is automatically added as the first member (owner).

**Request**
```json
{
  "name": "Bali 2026",
  "type": "trip",
  "baseCurrency": "INR",
  "members": [
    { "displayName": "Alice",  "email": "alice@example.com" },
    { "displayName": "Bob",    "email": "" }
  ]
}
```

- `type` ∈ `trip`, `home`, `general`.
- `baseCurrency` — 3-letter uppercase.
- `members` — array of other people. `email` is optional; when supplied, the user is linked so they see the group on their next Google sign-in. Members without an email are stored as **placeholder users**.
- Name is trimmed to 80 chars.

**Response 201**
```json
{
  "groupId": "new-id",
  "name": "Bali 2026",
  "type": "trip",
  "baseCurrency": "INR"
}
```

### `GET /api/groups/{groupId}`
Full group detail — members with net balances, all expenses, and the minimum-payments settlement plan.

**Response 200**
```json
{
  "groupId": "abc",
  "name": "Bali 2026",
  "type": "trip",
  "baseCurrency": "INR",
  "fxRates": { "USD": 84.5, "EUR": 92.1 },
  "ownerUserId": "u_creator",
  "createdAt": "2026-08-01T09:00:00Z",

  "members": [
    {
      "userId": "u_me",
      "displayName": "You",
      "active": true,
      "you": true,
      "net": 500.00,
      "email": "you@example.com",
      "placeholder": false
    }
  ],

  "expenses": [
    {
      "expenseId": "2026-08-15#abcd1234abcd",
      "date": "2026-08-15",
      "description": "Dinner",
      "category": "Food & drink",
      "cost": 1200.00,
      "currency": "INR",
      "payment": false,
      "myNet": -300.00,
      "paidByUserId": "u_other",
      "shares": { "u_me": -300.00, "u_other": 900.00 },
      "notes": null,
      "receiptKey": "abc/8f7e...heic",
      "splitMode": "equal"
    }
  ],

  "settlement": [
    {
      "fromUserId": "u_a", "fromName": "Alice",
      "toUserId":   "u_b", "toName":   "Bob",
      "amount": 250.00
    }
  ]
}
```

Field notes:

- `fxRates` — map from currency code to "how many base units per 1 foreign unit" (e.g. `USD: 84.5` means 1 USD = 84.5 INR). Only present for non-base currencies. May be `{}`.
- `members[].you` — convenience boolean; `true` for the current user.
- `members[].placeholder` — `true` for people added by name only (no email, never signed in).
- `members[].net` — total in **base currency**, 2 dp. Positive → they get back; negative → they owe.
- `expenses[].shares` — signed per-user impact in **expense currency**: paying `+cost`, participants `−owed`. Rows for users with zero impact are omitted.
- `expenses[].myNet` — the current user's share entry (from `shares`) if present, else `0`. In **expense currency**, not base.
- `expenses[].paidByUserId` — set explicitly when the expense was created; older data derives it from the max positive `shares` entry.
- Expenses are sorted descending by `expenseId` (which starts with the date, so effectively newest first).
- `settlement` — greedy minimum-transfers plan derived from member `net`. Amounts in **base currency**. Empty array means everyone's square (within a 1-cent epsilon).

**Errors:** `403` if the current user is not a member. `404` unknown group.

### `PATCH /api/groups/{groupId}`
Rename a group.

**Request**
```json
{ "name": "New name" }
```

Sending `baseCurrency` different from the current one returns **400** — base currency is locked after creation. Type is not editable at all.

**Response 200**
```json
{
  "groupId": "abc",
  "name": "New name",
  "type": "trip",
  "baseCurrency": "INR"
}
```

### `POST /api/groups/{groupId}/members`
Add people to an existing group. Any member can call this.

**Request**
```json
{ "members": [ { "email": "sam@example.com", "displayName": "" } ] }
```

Same member shape as `POST /api/groups`. People already active in the group are returned in `skipped`; inactive (removed) members are reactivated. New members don't share in existing expenses.

**Response 200**
```json
{
  "groupId": "abc",
  "added": [ { "userId": "u2", "displayName": "Sam" } ],
  "skipped": []
}
```

**Errors:** `400` empty `members` or a member with neither name nor email, `403` not a member, `404` unknown group.

---

## 5. FX rates

Additional currencies inside a group. Rate is how many **base-currency units** equal 1 unit of the foreign code.

### `PUT /api/groups/{groupId}/fx-rates/{code}`
Add or replace the rate for `code`. Any member can call this.

**Request**
```json
{ "rate": 84.5 }
```

- `rate` must be positive.
- `code` must be 3 letters, not equal to the group's base currency.

**Response 200**
```json
{
  "groupId": "abc",
  "baseCurrency": "INR",
  "fxRates": { "USD": 84.5, "EUR": 92.1 }
}
```

Editing an existing rate immediately re-values all past expenses (balances are computed at read time).

### `DELETE /api/groups/{groupId}/fx-rates/{code}`
Remove a currency.

**Response 200** — same body shape as PUT.

**Errors:** `409` if any expense in the group still uses this currency. Delete those expenses first.

---

## 6. Expenses

### `POST /api/groups/{groupId}/expenses`
Add an expense to a group. Caller must be a member.

**Request**
```json
{
  "description": "Dinner at Silk Road",
  "date": "2026-08-15",
  "category": "Food & drink",
  "notes": "Aditi paid, we split among four",
  "cost": 1200.00,
  "currency": "INR",
  "paidByUserId": "u_other",
  "splitMode": "equal",
  "participants": [
    { "userId": "u_me",    "value": 0 },
    { "userId": "u_other", "value": 0 }
  ],
  "receiptKey": "abc/xyz.jpg"
}
```

Rules:

- `cost` > 0.
- `currency` — must be the base currency, OR a currency with an FX rate already configured on the group. Otherwise **400** ("add one in Settings first").
- `paidByUserId` — omit or blank to default to the current user. Must be an active member.
- `splitMode` — `equal` | `percent` | `amount`.
  - **equal:** `participants[].value` is ignored. Cost divided evenly; remainder cent goes to the payer (or first participant if payer isn't one).
  - **percent:** `value` per participant, must sum to `100 ± 0.01`. Remainder cent goes to payer.
  - **amount:** `value` per participant, must sum exactly to `cost`.
- `participants` — at least one; each must be an active member; no duplicates.
- `description` truncated to 140 chars.
- `notes` up to 1000 chars, null when blank.
- `category` null when blank.
- `date` defaults to today (server-local) if blank.
- `receiptKey` optional — obtained from the receipt upload endpoint below.

**Response 201**
```json
{
  "expenseId": "2026-08-15#abcd1234abcd",
  "groupId": "abc"
}
```

The full expense record and updated balances become available on the next `GET /api/groups/{groupId}`.

### `PUT /api/groups/{groupId}/expenses/{expenseId}`
Full edit. Same body and validation as `POST` — every field is replaced (omitted `receiptKey` removes the receipt). Caller must be a member. People already on the expense stay valid as payer/participants even if they've since left the group.

`expenseId` contains `#` — URL-encode it (`%23`).

Changing `date` moves the expense to a new key (the id starts with the date), so the response `expenseId` may differ from the one in the path.

**Response 200**
```json
{ "expenseId": "2026-08-16#9f8e7d6c5b4a", "groupId": "abc" }
```

`GET /api/groups/{groupId}` returns `expenses[].splitValues` — the raw per-participant inputs (`percent` or `amount`, `0` for `equal`) for expenses saved since this field was added; `null` for older ones.

### `PATCH /api/groups/{groupId}/expenses/{expenseId}`
Partial update. Only `description` and `category` are editable.

**Request**
```json
{ "description": "Dinner @ Silk Road", "category": "Food & drink" }
```

Send empty strings to clear a field.

**Response 200**
```json
{
  "expenseId": "2026-08-15#abcd1234abcd",
  "date": "2026-08-15",
  "description": "Dinner @ Silk Road",
  "category": "Food & drink",
  "cost": 1200.00,
  "currency": "INR"
}
```

**Note:** To change amount, split, participants, or payer, use `PUT` above.

### `DELETE /api/groups/{groupId}/expenses/{expenseId}`
**Response 204** — no body.

---

## 7. Receipts

### `POST /api/groups/{groupId}/receipts`
Upload a receipt image before saving the expense. Caller must be a group member.

**Request:** `multipart/form-data`, one part named `file`.
- Max size: **5 MB**
- Allowed types: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`, `image/heic`, `image/heif`

**Response 200**
```json
{
  "receiptKey": "abc/8f7e0a2b1c3d.jpg",
  "url":        "/receipts/abc/8f7e0a2b1c3d.jpg"
}
```

Pass `receiptKey` into `POST /api/groups/{groupId}/expenses`.

**Errors:** `400` empty file, `403` not a member, `413` too large, `415` unsupported type.

### `GET /receipts/{groupId}/{filename}`
Serves the receipt bytes. Public route (unlisted), but the filename is a random UUID — treat it as an unguessable capability. Cache-Control `private, max-age=86400`.

Frontend usage: build the URL as `${API_BASE}/receipts/${receiptKey}`.

---

## Enum reference

**GroupType:** `trip`, `home`, `general`  
**SplitMode:** `equal`, `percent`, `amount`

## Data flow summary

A typical UI session:

1. `GET /api/me` on app load → decide sign-in vs authed shell. On `401`, send the user to `/oauth2/authorization/google`.
2. `GET /api/activities` on the dashboard.
3. `POST /api/groups` from the create flow.
4. `GET /api/groups/{id}` on the group screen. Cache this — it has members, expenses, and settlement in one payload.
5. `POST /api/groups/{id}/receipts` if the user picked an image → `POST /api/groups/{id}/expenses` with the returned `receiptKey`. Refetch step 4.
6. `PATCH /api/me` / `POST /api/me/picture` from the profile screen.
7. `POST /api/logout` to sign out.

## Environment reference

Server config (`application.yml`):

| Env var | Default | Notes |
|---|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `changeme` | Required for real login |
| `FRONTEND_URL` | `http://localhost:5173` | CORS allowed origin + OAuth redirect target |
| `DYNAMODB_ENDPOINT` | `http://localhost:8000` | Local DynamoDB |
| `DYNAMODB_TABLE_PREFIX` | `splitforce_` | Table name prefix |
| `AWS_REGION` | `us-east-1` | |
| `UPLOADS_DIR` | `./data/profile-pics` | Where profile pictures land on disk |
| `RECEIPTS_DIR` | `./data/receipts` | Where receipts land on disk |
| `SEED_ENABLED` | `true` | Runs sample-data seeder on startup |

Server port: **9090**. Max multipart request: **5 MB**.
