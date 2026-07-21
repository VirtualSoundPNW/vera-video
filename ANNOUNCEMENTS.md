# Vera Project announcements — a starting plan

A slot in the app where **The Vera Project**, at their discretion, can promote
shows, volunteer calls, and other opportunities — including links to sign up or
buy tickets.

This is not an ad network and should never become one. No third-party SDK, no
tracking, no auction, no remnant inventory. One nonprofit's own announcements,
in an app about that nonprofit. Every decision below follows from that.

Status: **design proposal.** Nothing here is implemented.

---

## 1. What this has to get right

The hard parts are not technical.

1. **Vera must be able to publish without us.** If posting a show requires a
   developer, the slot will be empty within a month and the feature is worse than
   nothing — a dead promo box makes the app look abandoned.
2. **Empty must look deliberate.** Most of the time there will be nothing to
   say. The UI has to be invisible when empty, not a hole.
3. **Stale is worse than empty.** A show that happened last week must remove
   itself. Expiry is mandatory, not optional.
4. **It must not quietly break our privacy claim.** The app currently declares
   *no data collected*. The moment we count clicks, that stops being true (see
   §6).
5. **Editorial responsibility is ours.** It ships in our app under our
   developer account. We need an agreement about what may appear, and a kill
   switch that doesn't require a release.

---

## 2. Data model (backend)

New table; same D1 database. Conventions match the existing schema — timestamps
are ISO-8601 TEXT, booleans are INTEGER 0/1.

```sql
CREATE TABLE IF NOT EXISTS announcements (
  id          TEXT PRIMARY KEY,          -- stable id from the source row
  kind        TEXT NOT NULL CHECK (kind IN ('show','volunteer','notice')),
  title       TEXT NOT NULL,
  body        TEXT NOT NULL DEFAULT '',
  image_url   TEXT,
  cta_label   TEXT,                      -- "Get tickets", "Sign up"
  cta_url     TEXT,
  starts_at   TEXT,                      -- when to begin showing it
  ends_at     TEXT,                      -- REQUIRED for shows: auto-expiry
  priority    INTEGER NOT NULL DEFAULT 0,
  active      INTEGER NOT NULL DEFAULT 1,
  updated_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_announcements_window
  ON announcements (active, starts_at, ends_at, priority DESC);
```

`GET /announcements` returns only rows that are `active` and inside their
`[starts_at, ends_at]` window, ordered by `priority DESC, starts_at ASC`, and
carries an ETag like `/catalog` does.

Serving it as a **separate endpoint** from `/catalog` is deliberate: announcements
change on a different cadence than the video catalog, and a promo edit should not
invalidate the catalog ETag and force every device to re-sync videos.

---

## 3. How Vera provides content

The authoring surface is the whole ballgame. Phased, so that something works in
week one and nothing is thrown away later.

### Phase 0 — we author it (week 1)

Rows inserted by hand via `wrangler d1 execute`, from content Vera emails us.
Good enough to prove the UI and get the shape right. Explicitly a stopgap; do
not present it as the solution.

### Phase 1 — Google Sheet (the recommended starting point)

Vera edits a Google Sheet. A Worker cron pulls it every ~15 minutes and upserts
into `announcements`.

- One row per announcement; columns mirror the schema.
- Publish the sheet to the web as CSV (**File → Share → Publish to web → CSV**)
  and fetch that URL — no OAuth, no service account, no new Cloudflare seats.
- Validate on ingest: require `title`; require `ends_at` when `kind='show'`;
  require `cta_url` to be `https:`; skip and log bad rows rather than failing the
  whole sync.

**Why a spreadsheet, and not an admin UI:** it is the tool a small nonprofit
staff already knows, needs no accounts or training, has version history and
undo built in, and lets several people edit. An admin UI we build is a
login to forget, a password to reset, and a thing that rots. Revisit only if the
sheet demonstrably fails.

The sheet is also the kill switch: set `active` to 0, and it's gone within 15
minutes without a deploy.

### Phase 2 — pull from a source they already maintain

The best content is content nobody has to re-enter. If Vera's site or ticketing
platform exposes an events feed (RSS/JSON/iCal — most venue ticketing platforms
do), ingest that for `kind='show'` and keep the sheet only for volunteer calls
and notices. Zero ongoing effort for shows, which is most of the volume.

**Ask them first**: which platform sells their tickets, and does their site have
an events feed? That answer might let us skip Phase 1 for shows entirely.

### Phase 3 — a real admin UI, only if earned

D1 + a small authenticated page behind **Cloudflare Access** (Google SSO against
their Workspace, no passwords to manage). Only worth it if the sheet is
genuinely limiting — e.g. they want image uploads or scheduling previews.

---

## 4. App surface

- A single **card at the top of Browse**, above the video list. Not a
  full-screen interstitial, not a banner on every screen. One place, easy to
  ignore, easy to find.
- Shows: title, date, optional image, CTA button.
- **Dismissible**, with the dismissal remembered per announcement id (DataStore).
  A user who has dismissed an announcement has told us something; respect it.
- **Renders nothing at all when the list is empty** — no placeholder, no
  skeleton.
- Cap at one visible card, with the rest reachable only if we later add a
  dedicated tab. Don't stack them.
- CTA opens in a **Custom Tab** (`androidx.browser`), not an in-app WebView.
  Custom Tabs share the user's browser session, so an existing Vera or ticketing
  login just works, and the URL bar shows the user where they're actually going.
- Cache with the catalog sync (same WorkManager job, same ETag pattern) so the
  card works offline and costs no extra request.

---

## 5. Ticket and signup links — the policy question, resolved

**Linking out to buy tickets is fine, and does not require Google Play Billing.**

Play's payments policy requires its billing system for *digital* goods consumed
in the app. It explicitly does **not** apply to physical goods or real-world
services — and event tickets are named as an example of a physical service. A
button that opens Vera's ticketing page in a browser is squarely allowed.
Volunteer signups and donations to a nonprofit are likewise outside Play billing.

Two constraints that do apply:

- Don't build a *digital* purchase flow in-app later without revisiting this.
- Declare **ads** honestly. Third-party promotional content generally counts as
  ads even when the third party is a nonprofit and no money changes hands. If the
  app ships under **The Vera Project's own developer account**, this is
  first-party self-promotion and the answer plausibly changes — one more reason
  to prefer that account (see `DEPLOYMENT.md` §1).

---

## 6. The privacy tension — decide it deliberately

The app declares **no data collected**, and that is currently true.

Vera will eventually and reasonably ask: *did anyone click?* The honest options:

| Option | Cost |
|---|---|
| **No tracking** (recommended for v1) | Vera gets no click data from us |
| Aggregate counts, no identifiers (e.g. Analytics Engine, count only) | Arguably still "no data collected", but needs a careful re-read of the Data safety definitions and a privacy-policy update |
| Per-user tracking | Breaks the "no data collected" claim outright. Don't. |

Better answer, at no privacy cost: **let the destination measure it.** If Vera's
ticketing platform reports referrals, or they accept a `?utm_source=vera-video`
parameter on the `cta_url` they publish in the sheet, they get attribution from
their own analytics, we collect nothing, and the declaration stays true. Suggest
this before building any counter.

Whatever is chosen: a change here means re-doing the Play **Data safety**
declaration. It is an app-removal offence to get that wrong.

---

## 7. What to agree with The Vera Project

Worth settling in writing, briefly, before building:

- **Who publishes**, and who at Vera owns the sheet.
- **What may appear**: their shows, volunteer calls, fundraising, org notices.
  Not third-party advertising sold by them — that would turn the slot into ad
  inventory and change both the Play declaration and the character of the app.
- **Kill switch**: we can pull anything immediately (set `active=0`); they can
  too. No release needed either way.
- **Brand use**: explicit permission for name/logo in the app and store listing.
- **Ownership**: ideally the app lives under their developer account (see
  `DEPLOYMENT.md` §1) — which also makes this whole section simpler.

---

## 8. Suggested order

1. Ask the two questions that could change the design: *do you have a D-U-N-S
   number?* and *does your ticketing platform expose an events feed?*
2. Ship the app **without** this feature. Don't block v1 on a partnership
   conversation.
3. Backend: `announcements` table + `GET /announcements` + sheet ingest cron.
4. App: the Browse card, dismissal, Custom Tab CTA.
5. Hand Vera the sheet, watch what they actually post, and let that decide
   Phase 2 vs Phase 3.
