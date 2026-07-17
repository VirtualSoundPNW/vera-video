# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

**Vera-Video** — an Android app for discovering, organizing, and playing YouTube
videos related to The Vera Project (the Seattle all-ages music and arts venue).
The original design is in [PLAN.md](PLAN.md).

This repo holds **only the Android app**. The catalog-building service lives in a
separate repo, **`vera-video-backend`** (TypeScript on Cloudflare Workers), which
crawls the YouTube Data API on a cron and serves `GET /catalog`. The app syncs
that catalog into Room and searches it locally; it never calls YouTube's API.

## Build

```bash
./gradlew :app:assembleDebug     # build
./gradlew :app:testDebugUnitTest # unit tests (Robolectric + Room + MockWebServer)
./gradlew :app:lintDebug         # lint
```

Requires a JDK 17+ and an Android SDK. `local.properties` (gitignored) must
point `sdk.dir` at the SDK; Android Studio writes it automatically.

The backend URL is **not** hardcoded — it comes from `vera.catalog.baseUrl` in
`gradle.properties` and reaches code as `BuildConfig.CATALOG_BASE_URL`. To run
against a local `wrangler dev` from an emulator:

```bash
./gradlew :app:assembleDebug -Pvera.catalog.baseUrl=http://10.0.2.2:8787/
```

## Toolchain constraints

These combinations were arrived at by trial; changing one usually breaks another.

- **AGP 9 is required, not optional.** Hilt's Gradle plugin refuses to load on
  AGP < 9. AGP 9 has *built-in Kotlin support*, so `org.jetbrains.kotlin.android`
  must **not** be applied — applying it fails the build.
- **KSP must be newer than the one AGP bundles.** AGP 9.3 ships KSP 2.2.10-2.0.2,
  which registers generated sources through the `kotlin.sourceSets` DSL that
  built-in Kotlin rejects. The pinned KSP (2.3.x) uses the supported path.
- **Gradle 9.6+ is required.** AGP 8.x relied on a Gradle internal API removed in
  9.6, so the 8.x fallback is not available on this wrapper anyway.
- **Robolectric is pinned to SDK 36** (`app/src/test/resources/robolectric.properties`)
  because it has no sandbox for the app's targetSdk 37 yet.
- **Robolectric + spaces in the home path**: Robolectric builds the path to its
  `android-all` jar from an undecoded URL, so a user home containing spaces
  yields a `%20` path and every test dies with "Unable to load Robolectric
  native runtime library". `app/build.gradle.kts` points the test JVM's
  `user.home` at the Windows 8.3 short name to dodge this. It is a no-op on
  paths without spaces.

## Architecture

Standard MVVM + repository, all reads flowing from Room so the app works offline.

| Layer | Where |
|---|---|
| UI (Compose M3, Navigation) | `ui/` — screens grouped by feature |
| ViewModels | `ui/<feature>/*ViewModel.kt`, Hilt-injected |
| Repositories | `data/*Repository.kt` |
| Room (entities, DAOs, FTS) | `data/local/` |
| Retrofit + DTOs | `data/remote/` |
| WorkManager sync | `sync/` |
| DI | `di/AppModule.kt` |

Key decisions worth knowing before changing things:

- **Search is local and full-text.** `videos_fts` is an *external-content* FTS4
  table (`@Fts4(contentEntity = VideoEntity::class)`) — it stores no data and
  relies on Room-generated triggers. `VideoDaoTest` covers this; if those tests
  go red, search silently returns nothing.
- **User input must go through `FtsQuery.sanitize`.** FTS MATCH has its own
  syntax; raw input like `"` or `-` throws at query time.
- **Search uses `@RawQuery`** because filters and sort are chosen at runtime.
  Values are always bound, never interpolated.
- **Videos are never deleted, only marked `removed`.** Playlists point at them,
  and dropping the row would silently lose a user's entry. Browse filters on
  `status = 'active'`; playlists show removed entries as unavailable.
- **A full sync reconciles rather than wipes** (`CatalogRepository.apply`), for
  the same reason.
- **`updatedAt` is the delta cursor.** The backend only advances it on real
  content changes; the app stores it in DataStore along with the ETag.

## Hard constraints

- **YouTube ToS:** playback only through the embedded IFrame player
  (`android-youtube-player`; Google's native YouTube Android Player API is
  defunct). No downloading, no background audio. Don't add features that need
  either.
- **No YouTube Data API calls from the app**, and no API keys in this repo — all
  crawling lives in `vera-video-backend`.

## Conventions

- Kotlin, Compose, `minSdk 26` (native `java.time`, no desugaring).
- Version catalog: `gradle/libs.versions.toml`. Don't hardcode versions in
  `build.gradle.kts`.
- Tests: JUnit + Truth + Robolectric for Room/utility, MockWebServer for sync.
  Prefer testing behavior that would otherwise fail silently (FTS sync, delta
  reconciliation, playlist reordering).
