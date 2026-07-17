# Vera-Video

An Android app for discovering, organizing, and playing YouTube videos related to
**The Vera Project**, Seattle's all-ages music and arts venue.

## Features

- **Browse & search** a curated catalog, with full-text search that runs locally
  (instant, and works offline), plus channel / duration / date filters and sorting
- **Saved searches** — name a query and its filters, and see live match counts
- **Playlists** — create, reorder, rename, and share
- **Playback** through the embedded YouTube player, auto-advancing through a
  playlist

## How it works

This repo contains the Android app. A companion service,
[`vera-video-backend`](https://github.com/VirtualSoundPNW/vera-video-backend)
(TypeScript on Cloudflare Workers), uses the YouTube Data API on a cron to build
and refresh the video catalog, filter out the many unrelated things named "Vera",
and serve it as a small JSON API.

```
YouTube Data API ──cron──▶ vera-video-backend ──JSON catalog──▶ Android app ──▶ embedded player
```

The app never calls the YouTube API itself. It syncs the catalog into a local
Room database and searches that, which keeps the API key server-side, spends one
quota budget instead of one per device, and makes search instant and offline.

## Tech stack

- Kotlin + Jetpack Compose (Material 3), MVVM, Hilt
- Room — catalog cache with an FTS4 index, saved searches, playlists
- Retrofit + kotlinx.serialization; WorkManager for the daily catalog sync
- [`android-youtube-player`](https://github.com/PierfrancescoSoffritti/android-youtube-player)
  for embedded playback (Google's native YouTube Android Player API is defunct;
  the IFrame player is the ToS-compliant option)
- `minSdk 26`, `targetSdk 37`

## Building

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Needs a JDK 17+ and the Android SDK. Android Studio writes `local.properties`
(gitignored) pointing at your SDK; create it by hand if building from the CLI:

```properties
sdk.dir=/path/to/Android/Sdk
```

The backend URL is not hardcoded. It comes from `vera.catalog.baseUrl` in
`gradle.properties`; point it at your deployment, or at a local backend from an
emulator:

```bash
./gradlew :app:assembleDebug -Pvera.catalog.baseUrl=http://10.0.2.2:8787/
```

See [CLAUDE.md](CLAUDE.md) for the toolchain constraints (AGP 9 + built-in
Kotlin, KSP, Robolectric) — several of them are non-obvious.

## Status

Both the app and the backend are implemented. The app builds and its unit tests
pass; it has not yet been exercised on a device or emulator, and the backend has
not been run against the real YouTube API. See [PLAN.md](PLAN.md) for the design
and remaining milestones.

## License

Apache-2.0 — see [LICENSE](LICENSE).
