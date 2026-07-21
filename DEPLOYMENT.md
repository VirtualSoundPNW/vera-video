# Deployment — Vera-Video (Android)

Getting to a signed build is a day of work. Getting onto Play is mostly waiting
on Google, and **one decision made on day one determines whether that wait is
days or a month**. Read §1 before doing anything else.

---

## 1. Decide the developer account type first

This is the highest-leverage decision in the whole plan.

| | **Personal account** | **Organization account** |
|---|---|---|
| Cost | $25 one-time | $25 one-time |
| Identity proof | Government ID | **D-U-N-S number** |
| Lead time to open | Hours–days | **Up to ~30 days** to get a D-U-N-S, if you don't have one |
| **Closed testing before production** | **12 testers, opted in, for 14 continuous days** | **Not required** |
| Store listing shows | An individual's legal name | The organization name |

The 12-tester rule applies to **personal accounts created after 13 Nov 2023**.
It is not a formality: 12 real people must opt in and *stay* opted in for 14
continuous days, and dropping below 12 resets the clock. For a venue app, that
means recruiting a dozen humans and keeping them engaged for two weeks before
you can even *apply* for production access.

An organization account skips that entirely.

**Recommendation: publish under an organization account.** Two sub-options:

1. **The Vera Project's own account** — best outcome. The listing says "The Vera
   Project", they own the app long-term, and as an established Seattle nonprofit
   they very likely already have a D-U-N-S number (they are commonly required
   for grants and federal registration). If they have one, this is also the
   *fastest* path. Requires their buy-in and someone there to hold the account.
2. **VirtualSoundPNW's account** — viable if Vera can't or won't hold it; needs a
   D-U-N-S for VirtualSoundPNW, which is free from Dun & Bradstreet but can take
   up to 30 days.

Start the D-U-N-S lookup/application **now**, in parallel with everything else —
it is pure lead time. Check first at [dnb.com](https://www.dnb.com/duns/get-a-duns.html);
many organizations already have one and don't know it.

> Fall back to a personal account only if the org path is blocked. If you do,
> start recruiting the 12 testers on day one — that 14-day clock is the critical
> path, and internal testing does **not** count toward it.

---

## 2. Signing

Play uses **Play App Signing**: Google holds the *app signing key* that end users
verify against; you hold an *upload key* that only proves uploads are from you.
The practical consequence: **a lost upload key is recoverable** (Google can reset
it), but only if you enrolled. Enroll.

### 2.1 Generate the upload key

```bash
keytool -genkeypair -v \
  -keystore vera-upload.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias vera-upload
```

- Store `vera-upload.jks` **outside the repo** and back it up somewhere durable
  (a password manager's file attachment, not just a laptop).
- `-validity 10000` (~27 years): Play requires a key valid well beyond any
  plausible release.
- Record the store password, key password, and alias with the file.

### 2.2 Wire signing into Gradle without committing secrets

Create `keystore.properties` **outside version control** (repo root is fine —
add it to `.gitignore` first):

```properties
storeFile=/absolute/path/to/vera-upload.jks
storePassword=…
keyAlias=vera-upload
keyPassword=…
```

Add to `.gitignore`:

```
keystore.properties
*.jks
```

Then in `app/build.gradle.kts` — note it must tolerate the file being absent, so
that CI, contributors, and `assembleDebug` still work:

```kotlin
import java.util.Properties

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use(::load)
}

android {
    signingConfigs {
        create("release") {
            // Env vars let CI supply the same values without a file on disk.
            val storePathValue = keystoreProps.getProperty("storeFile")
                ?: System.getenv("VERA_KEYSTORE_FILE")
            if (storePathValue != null) {
                storeFile = file(storePathValue)
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: System.getenv("VERA_KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias")
                    ?: System.getenv("VERA_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: System.getenv("VERA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Only attach if we actually have a key, so an unsigned release
            // build still succeeds for CI verification.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            // … existing minify/proguard config …
        }
    }
}
```

### 2.3 Set the production backend URL

`vera.catalog.baseUrl` in `gradle.properties` is baked into `BuildConfig` at
build time. Point it at the deployed Worker (see the backend's `DEPLOYMENT.md`)
**before** building the release, or the app ships pointing at a placeholder.

```bash
./gradlew :app:bundleRelease -Pvera.catalog.baseUrl=https://vera-video-backend.<subdomain>.workers.dev/
```

### 2.4 Build the App Bundle

Play requires an **AAB**, not an APK:

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

The R8 mapping file is embedded in the bundle automatically (minify is on), so
Play deobfuscates crash stacks with no extra upload step.

Verify the signature before uploading:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | head
```

### 2.5 Version discipline

`versionCode` must strictly increase on every upload; Play rejects reuse
permanently — even for a deleted draft. `versionName` is cosmetic.

Bump both in `app/build.gradle.kts` per release. Keep it manual until releases
are frequent enough to be annoying; a git-tag-derived scheme is easy to add
later and adds moving parts now.

---

## 3. Play Console setup

### 3.1 Account

Register at [play.google.com/console](https://play.google.com/console) — $25 one
time. For an organization, have the D-U-N-S ready; verification is not instant.

### 3.2 Create the app

**All apps → Create app.** Free, not a game, default language en-US.

### 3.3 App content declarations

Play blocks production until every item under **Policy → App content** is
answered. The ones that need actual thought:

**Privacy policy — required even though the app collects nothing.** Every app
needs a public URL. Cheapest good option: serve it from the backend Worker (a
`GET /privacy` route) or a GitHub Pages file. It must state plainly: no accounts,
no analytics, no data leaves the device except fetching the public catalog.

**Data safety — likely "No data collected", but verify the claim.** The app has
no accounts, no analytics, no crash SDK, and sends no user data. Two things to
confirm before you tick the box:

- The backend Worker has `observability` enabled, so Cloudflare logs requests
  (including IPs) as infrastructure. Play's *ephemeral processing* exemption
  generally covers this, but if you ever add retained analytics or click
  tracking, the answer changes and the declaration must change with it.
- **A false declaration is an app-removal offence**, so re-check this on any
  release that adds a network call. See §5 for the announcements feature, which
  is exactly the kind of change that could break it.

**Ads — this needs a decision, and the conservative answer is "yes".** See §5.
An app showing promotional content for a *third party* (The Vera Project is a
third party relative to the developer) generally counts as containing ads, even
though it is a nonprofit and there is no ad network. Declaring "contains ads"
costs a badge on the listing; declaring wrongly risks enforcement. If the app
ships under **The Vera Project's own account**, the content is first-party
self-promotion and the calculus genuinely changes — another reason to prefer
that account.

Also complete: content rating questionnaire (IARC — expect Everyone), target
audience (13+; do **not** target children, which would trigger Families policy),
news app (no), government app (no), financial features (no), and app access (no
login required — say so, or reviewers may get stuck).

### 3.4 Store listing assets

You need these before you can publish; none exist yet:

| Asset | Spec | Note |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | The in-app icon is an adaptive vector; this is a separate export |
| Feature graphic | 1024×500 PNG/JPG | Required |
| Phone screenshots | ≥2, 16:9 or 9:16, min 320px | From the emulator once the UI is exercised |
| Short description | ≤80 chars | |
| Full description | ≤4000 chars | |

Ask The Vera Project for logo/brand assets rather than deriving them — using a
nonprofit's identity should be at their invitation, and the listing should look
like theirs.

### 3.5 Track progression

1. **Internal testing** — up to 100 testers, no review wait. Do the first real
   device validation here. This is where the player screen finally gets
   exercised (see §4).
2. **Closed testing** — mandatory only for post-Nov-2023 personal accounts
   (12 testers × 14 days). Skip if on an org account, though a short closed test
   with Vera staff is still worthwhile.
3. **Production** — staged rollout (start ~20%). First-time app review commonly
   takes days, not hours; budget for it.

---

## 4. Before the first upload: verify on a real device

The app builds and its unit tests pass, but **it has never run on a device or
emulator** — there is no AVD on the build machine. Do not let internal testing be
the first time the UI renders. Highest-risk area is the embedded player's
lifecycle (`ui/player/PlayerScreen.kt`), which is written against the library's
documented API but unproven at runtime.

```bash
# create an AVD (API 36 image), then:
./gradlew :app:installDebug
```

Walk: catalog syncs → search returns hits → save a search → build a playlist →
reorder → play → auto-advance at end of video → share a playlist link → rotate
the device mid-playback → background/foreground during playback.

---

## 5. Release checklist

- [ ] `vera.catalog.baseUrl` points at production
- [ ] `versionCode` incremented
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` clean
- [ ] Exercised on a device (§4)
- [ ] `bundleRelease` signed with the upload key, verified with `jarsigner`
- [ ] Data safety declaration still true for this release
- [ ] Backend deployed and `/catalog` returning a non-empty, sane catalog

---

## 6. Later: automated Play deploys

Not worth building for v1 — the value of CI publishing shows up around the third
or fourth release. When it does:

- [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) or
  Fastlane `supply`.
- Play Console → **Setup → API access** → link a Google Cloud project → create a
  service account → grant it *Release manager* → download the JSON key.
- Store as GitHub secrets: the service-account JSON, plus the keystore
  base64-encoded (`base64 -w0 vera-upload.jks`) and the passwords. Decode to a
  temp file at build time and feed via the `VERA_KEYSTORE_*` env vars from §2.2.
- Target the **internal** track from CI; promote to production by hand. Automated
  promotion straight to production is a good way to ship a bad build to
  everyone.
