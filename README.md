# SplitForce

Split expenses, not friendships. Track trips, home expenses, and everyday shared money — with your people. Side project — not for commercial launch.

## Scope (v1 direction)
- **Groups** with types: `trip`, `home`, `general`. Types can grow later.
- Trip groups get itineraries, bookings, stories.
- Home groups get shopping lists, chores, categories.
- General groups: minimal (two-person tally, expenses only).
- **Expenses** with any split rule, per-group base currency + user-set FX rates.
- **AI hooks** for itinerary drafting, receipt OCR, booking-email parsing (separate service, TBD).

## Stack
- **Backend** — Spring Boot 3, Java 17, Spring Security OAuth2 client (Google login), session-cookie auth.
- **Frontend** — React 18 + Vite + Tailwind. Responsive; works on phone and laptop browsers.
- **AI layer** — separate service, TBD.

## Prerequisites
- Java 17+
- Maven 3.9+ (or use your IDE's bundled Maven)
- Node 18.14+ (Node 20+ recommended)
- A Google Cloud OAuth 2.0 Client ID (Web application):
  - Authorized redirect URI: `http://localhost:9090/login/oauth2/code/google`
  - Copy the client id + client secret.

## Run locally

### 1. Backend
```bash
cd backend
export GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=your-client-secret
mvn spring-boot:run
```
Serves on `http://localhost:9090`.

### 2. Frontend
```bash
cd frontend
npm install
npm run dev
```
Serves on `http://localhost:7173`.

Visit `http://localhost:7173`, click **Sign in with Google**, bounce through Google, land on `/app/activities`.

## Routes
- `/` — Landing (public)
- `/app/activities` — My Activities (auth-gated)
- `/app/groups` — My Groups (auth-gated)
- `/app/profile` — My Profile (auth-gated). Name + email locked (from Google). Profile picture UI is editable; upload endpoint TBD.

## API
- `GET /api/health` — public health probe
- `GET /api/me` — current user `{email, name, picture}` (401 when signed out)
- `POST /api/logout` — clears session
- `GET /oauth2/authorization/google` — starts the Google login flow

## Folder layout
```
splitforce/
├── backend/                 # Spring Boot 3 (Java 17)
│   ├── pom.xml
│   └── src/main/java/com/splitforce/
│       ├── SplitForceApplication.java
│       ├── config/SecurityConfig.java
│       └── user/UserController.java
└── frontend/                # Vite + React + Tailwind
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── index.css
        ├── api/client.js
        ├── auth/AuthContext.jsx
        └── routes/
            ├── Landing.jsx
            ├── AppLayout.jsx
            ├── ProtectedRoute.jsx
            ├── Activities.jsx
            ├── Groups.jsx
            └── Profile.jsx
```
