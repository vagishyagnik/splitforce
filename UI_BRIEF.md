# SplitForce — Product & UX Brief for UI Generation

> Paste this whole document into a UI-generation tool (v0, Lovable, Uizard, Galileo, etc.).
> If the tool has a short prompt window, feed §1–4 first, then one screen at a time from §5. Keep §9 (mobile) in scope for every screen — it is non-negotiable.

## 1. What SplitForce is
A web app for splitting shared expenses among people you actually live and travel
with. Google sign-in only. Multi-currency. Any split rule. No payments
integration, no ads, no commercial features.

Three flavors of groups: **Trip**, **Home**, and **General** (just-the-two-of-us).
Everything else in the product hangs off of those.

## 2. Users
Primary user: someone tracking a group trip, a shared flat, or day-to-day money
with a partner. They open the app 2–5 times a week to log an expense in under
20 seconds, and once at the end of a trip to settle up.

Design for a phone in one hand, in poor lighting, on the third day of a trip
when everyone is tired.

## 3. Aesthetic direction — GO BONKERS
Build this as a **React app**. That's the only technical constraint on the UI.

Beyond that: **make it jaw-dropping**. I don't want a safe, calm, "editorial"
design. I want the UI equivalent of walking into a room and going "whoa, wait,
what is this?". Push type. Push color. Push motion. Push composition. Take
risks. Reject any instinct to make it look like every other SaaS product on
Dribbble.

Rules only for what NOT to do:
- Don't invent net-new interaction patterns for basic form fields (an amount
  input still needs to look like an amount input, a date picker still needs to
  look like a date picker).
- Don't sacrifice legibility for spectacle — a user has to be able to read
  "You owe Priya USD 42.10" on a phone screen in bright sunlight.
- Don't sacrifice touch usability — see §9. Bonkers *and* tappable.

Everything else — go for it. Bold typography, unexpected color pairings,
generous motion, hero moments, illustrations, ambient animation, whatever you
want. Surprise me.

The user has explicitly asked for a design that feels **premium and
distinctive**, not one that could be mistaken for any other expense-splitting
app. If you find yourself defaulting to a standard "productivity app" look,
push harder.

## 4. Global patterns (structural, not aesthetic)

### Authenticated app shell (mounted on /app/*)
- Persistent navigation to: My Activities, Create, Profile.
- Some indication of the signed-in user (Google-provided name + picture) and
  a way to sign out.

### Marketing shell (on /)
- Same identity mark.
- If logged out: primary CTA to sign in with Google.
- If logged in: the same navigation as the authenticated shell (My Activities,
  Create, Profile) plus a way to jump straight into the app.

### Money display (functional rule, not style)
- Show currency code alongside every amount ("INR 1,240.00").
- Positive balances read as "gets back", negative as "owes", zero as "Settled".
- Money must always be legible — use tabular figures wherever it's tabular.

### Empty / loading / error
- Every data-bearing view needs designed states for: loading (prefer
  skeleton over spinner), empty (with a next action), and error (with a
  retry or recovery path).

## 5. Screens (design each of these)

### 5.1 Landing (/) — public
Purpose: convert a first-time visitor to sign in with Google.
Content it must contain (order and treatment are up to you — this is where you
should go especially bonkers):
- A hero moment with a big headline promising to split expenses, not
  friendships, and a sub-line about tracking trips / home / shared money.
  Primary CTA: sign in with Google.
- A "three shapes" section that surfaces the three group types (Trip / Home /
  General) with a short description each.
- A features section covering: fair splits, multi-currency with user-defined
  rates, categories, itineraries + bookings, shared checklists, thoughtful AI.
- A three-step "how it works" section: start a group → log life → stay even.
- A closing CTA band.
- A footer.

Logged-in variant of this page: the primary CTAs swap to route into the app
("Open My Activities", "Create a group"), and the header shows the app nav
plus the user's avatar.

### 5.2 Sign-in redirect
Google OAuth is handled server-side; the UI needs a brief "Signing you in…"
placeholder if the redirect stalls. No email/password screens.

### 5.3 My Activities (/app/activities)
Purpose: the user's home base — the list of groups they belong to.
Must contain:
- Page identity + a "Create a group" primary action.
- A way to filter by type (All / Trips / Homes / General) and search by name.
- One card per group showing: group type, group name, member avatars, base
  currency, the user's balance summary in that group ("You are owed" / "You
  owe" / "Settled"), and last activity.
- An empty state with a single primary action.

### 5.4 Create group (/app/create)
Purpose: new-group wizard, single page.
Fields, top to bottom:
- Group type: three big selectable options (Trip / Home / General) with a
  short description under each.
- Group name.
- Base currency (searchable dropdown, defaults to the user's locale).
- Optional secondary currencies with rate inputs. Rate is expressed as
  "1 X = ? {base}". Add more with a "+ Add currency" affordance.
- Members: rows with name + email. Members without an email are flagged as
  placeholders ("they'll show up but can't sign in").
- Primary "Create group" action + secondary "Cancel".

Inline validation. On success, route directly into the new group.

### 5.5 Group detail (/app/activities/:groupId)
Purpose: the working page — balances, settlement, expenses.
Must contain:
- A way back to My Activities.
- A group header: name, type, base currency. Top-right actions: primary
  "Add expense" and secondary "Settings" (opens the settings panel).
- A "Balances" surface: per-member row with name, a "You" tag if it's you, a
  "Not signed in" tag for placeholders, and a right-aligned pill showing
  "gets back / owes / Settled" + amount.
- A "Settle up" surface: suggested minimal transfers ("A pays B  USD 42.00")
  with a short explanation. Empty state: "Everyone's square."
- An "Expenses" surface with:
  - Count "N of M" and an "Export CSV" action.
  - A "Filters" toggle (off by default) revealing:
    1. Search description, date preset, type (all / expenses only / payments
       only), sort, group by, amount min, amount max. Custom-date preset
       reveals from/to date inputs.
    2. Chip multi-selects for Paid by, Category, and (only if the group has
       >1 currency) Currency.
    3. "Show amounts in {base}" toggle, density control.
    4. Column visibility checkboxes and a "Clear all filters" action.
  - A selection bar (appears when rows are selected) with: Delete, Change
    category, Export selection, Clear selection.
  - Rows show: Date, Description (with "Payment" tag on payment rows),
    Category, Paid by, Amount, My net. Rows can be grouped by
    date/month/category/paid-by.
  - Empty state when filters exclude everything.

### 5.6 Group settings side panel
Slides in from the side (on mobile: full-screen sheet). Closes on Esc /
backdrop tap.
- Rename group — name field + save.
- Currencies:
  - Base currency (read-only) with an explanation that base can't be changed
    after the group is created.
  - Secondary currencies with a rate for each, edit + delete controls.
  - Delete is blocked (with a tooltip explaining why) if any expense uses
    that currency.
  - Adding a new secondary currency and saving a new rate should feel
    reassuring — a subdued confirmation like "Balances updated for the new
    rate."

### 5.7 Add expense (/app/activities/:groupId/add-expense)
Purpose: single-page form, fast to complete on a phone.
Sections in order:
1. **Amount + currency.** The most important input on the whole screen — make
   it feel important. Currency picker offers only the group's permitted
   currencies. Caption below: "Add more in Settings."
2. **Receipt (optional).** File picker (JPG/PNG/WebP/HEIC, max 5MB). After
   upload, show a thumbnail + "Remove".
3. **Who paid, and how to split.**
   - Paid-by picker + split-mode picker (Equally among selected / By
     percentage / By exact amount).
   - Participants list with a checkbox per member; the payer is tagged.
   - For percent / amount modes, inline inputs per participant.
   - A live preview of per-person net (green + / red −).
   - Inline validation: percentages sum to 100; exact amounts sum to the
     total.
4. **Details.** Description (140 max), date (optional, defaults to today),
   category from a fixed list of 15 (see below) + "None", notes (1000 max,
   with a live counter).
5. Primary "Add expense" action + "Cancel".

**Fixed category list (15):** Food & drink, Groceries, Transport, Fuel,
Flights, Trains & buses, Lodging, Activities & entertainment, Shopping,
Utilities, Rent, Bills, Health, Gifts, Other.

### 5.8 Expense detail / edit
Clicking a row opens a drawer with the full expense — image at top, all
fields readable, edit / delete actions at the bottom, and a "Who owes what
for this expense" mini-breakdown.

### 5.9 Settle up flow
Tapping a suggested transfer opens a small confirmation modal:
"Record that A paid B USD 42.00?" with an optional note and Confirm / Cancel.
Recording it creates a Payment-type row in the expense list.

### 5.10 Profile (/app/profile)
Read-only avatar, name, and email (all sourced from Google). A section
listing groups the user is in. Sign out at the bottom. That's it.

### 5.11 404 / error routes
A gentle "this page wandered off" page with a link back to My Activities.

## 6. Key user journeys (design happy-path frames for each)

### J1. First-time sign-in
Landing → click "Get started with Google" → Google OAuth → land on empty
My Activities → primary CTA "Create a group" → wizard → new group detail.

### J2. Log an expense on a trip
Group detail → "Add expense" → 1,240 INR → paid by "You" → split equally
among 4 → description "Dinner at Silk Road" → date today → category
"Food & drink" → Save → balances updated on the group page.

### J3. Handle a foreign-currency expense
Same as J2 but the currency picker exposes only permitted currencies. If the
currency the user wants isn't there, an inline hint deep-links into Settings
to add it.

### J4. Change an FX rate mid-trip
Group detail → Settings → Currencies → change EUR rate from 90 to 92 → save →
subdued banner "Balances updated for the new rate" → close panel → balances
and Settle Up have already refreshed.

### J5. Filter and export
Group detail → Filters → pick "Last 30 days", "Category: Food & drink" →
list narrows → select 8 rows → Bulk actions: Export selection → CSV downloads.

### J6. Settle up at the end of a trip
Group detail → Settle Up shows 3 minimal transfers → tap one → confirm modal
→ Payment recorded → balances zeroed → empty state on Settle Up: "Everyone's
square."

### J7. Return visitor
Land on / while already logged in → header shows nav + avatar → hero CTA now
reads "Open My Activities →" → tap through.

## 7. Non-goals (do NOT design these)
- Payment/collection integration (no Stripe, no bank links).
- Notifications / email reminders.
- Chat, comments, or reactions on expenses.
- Onboarding tours or product-tour tooltips.
- Multi-workspace, team management, or admin dashboards.
- Dark mode (single theme for v1 — but pick whatever theme you want).

## 8. What I want back from the UI tool
1. A **React app** — components, routes, state wiring stubbed with mock data
   is fine. Every screen in §5 built and navigable.
2. **All states** for every data view: loading (skeleton), empty, error,
   filled.
3. **Both viewports** for every screen: mobile (375px) and desktop (1280px).
4. Whatever design tokens / component library / motion primitives you use to
   achieve the look, exposed so I can extend them later.

Don't hold back on the visuals. If in doubt, make it bolder.

## 9. Mobile-first requirements (non-negotiable)

Design mobile screens FIRST, then scale up. Assume >70% of sessions happen on
a phone, one-handed. If a decision trades mobile clarity for desktop density,
choose mobile clarity. Bonkers on mobile is still bonkers — don't downgrade
the ambition, just make sure it works with a thumb.

### Viewports to deliver
- 375×812 (iPhone SE / smaller iPhone)
- 393×852 (iPhone 15 baseline)
- 768×1024 (tablet)
- 1280×800 (desktop)
Every screen in §5 must be shown at 375 and 1280 minimum.

### Touch & hit targets
- Minimum 44×44px hit target for any tap.
- Primary CTAs at least 48px tall, full-width on mobile.
- Icon-only buttons must have visible labels or unmistakable icons; never
  rely on hover for meaning.
- Never place two taps within 8px of each other.

### Layout rules on mobile
- Single column. No horizontal scrolling except inside the expense table
  (which may scroll horizontally with a shadow-fade edge to hint at more).
- Body text: 16px minimum (iOS zooms if inputs are <16px).
- Long descriptions cap at ~40ch line length so they don't wrap ugly.

### Navigation on mobile
- Sticky top bar: identity left, avatar chip right.
- Persistent bottom tab bar with three tabs: **Activities**, **Add** (big
  center action — opens Add Expense in the currently-open group, or a group
  picker if none is open), **Profile**. Bar is safe-area-inset aware.
- Sticky header may collapse on scroll-down and reappear on scroll-up.
- Back navigation uses a chevron-back icon top-left, not a text link.

### Page-specific mobile behaviors

**Landing (/)**
- Hero headline scales down cleanly on 375px (3 lines max).
- Sticky "Sign in with Google" CTA at the bottom of the viewport once the
  user scrolls past the hero.

**My Activities**
- Group cards become full-width vertical cards.
- Type filter row is a horizontally-scrollable chip strip.
- Search is a floating icon that expands into an input on tap, so it doesn't
  eat vertical space.

**Create group**
- One long scroll with generous section spacing (don't do a multi-step
  wizard).
- Currency rows collapse to two-line cards (code above, rate below).
- Members list uses swipe-to-remove or a 44px trailing X button.

**Group detail**
- Header: name + type/base on one row; the two action buttons on the next
  row full-width side-by-side (48px tall) — "+ Add expense" primary and
  "Settings" secondary.
- Balances and Settle-Up stack vertically. Settle-Up rows become tappable
  cards, not tiny inline text.
- Expense list on mobile is **not** a table — it's a list of rows:
  - Line 1: description (bold) + amount right-aligned
  - Line 2: category · paid by · date, muted
  - Line 3: "My net" pill if non-zero
  - Left-swipe reveals Delete; long-press enters multi-select.
- Table view is tablet+ only.
- "Filters" opens a full-screen bottom sheet with a sticky "Apply filters
  (N)" bar.
- Selection bar for bulk actions is a bottom sheet when >0 rows are picked.

**Group settings**
- Full-screen sheet on mobile, sticky close top-right.

**Add expense**
- Sections stack vertically. Each is a card with generous padding.
- Amount + currency at the top is unmissable: amount input is large-type
  (32–40px), currency picker is a segmented pill row when ≤4 currencies
  exist, else a bottom-sheet picker.
- Split-mode picker is a segmented control (3 pills), not a dropdown.
- Participants: full-width rows, 44px checkbox left, inline input right.
- Live preview docks at the bottom above the CTA.
- Primary "Add expense" CTA is a sticky bottom pill, full-width, 48–56px,
  safe-area-inset aware.
- Keyboard-aware: when the amount input is focused on iOS, the sticky CTA
  moves above the keyboard.
- Receipt picker uses the native camera-or-library sheet on iOS/Android.
  Offer a "Take photo" and "Choose from library" pair for clarity.

**Settle-up modal**
- Full-screen sheet on mobile with a big amount, "From X to Y", optional
  note, and a big Confirm CTA.

**Profile**
- Single scroll. Sign out is a full-width tinted button at the bottom.

### Input & keyboard
- Money inputs: inputmode="decimal".
- Search: inputmode="search".
- Email: inputmode="email".
- Date pickers: use native <input type="date">, don't rebuild them.

### Gestures
- Don't intercept the browser's edge-swipe-back.
- Pull-to-refresh on My Activities and Group detail.
- Long-press on an expense row = multi-select.
- No custom horizontal-swipe gestures inside pages besides the expense-row
  swipe-to-delete.

### Performance targets on mobile
- LCP under 2.5s on a throttled 4G / Moto G4 profile.
- No layout shift after images load — reserve avatar/receipt dimensions.
- Skeleton screens for group detail, not spinners.
- Group detail fetch → render within 300ms once data arrives.

### Deliverables for mobile (in addition to §8)
- Full frames at 375px for every screen in §5.
- The bottom nav bar shown across all authenticated screens.
- A "safe area" annotation on any sticky bottom element.
