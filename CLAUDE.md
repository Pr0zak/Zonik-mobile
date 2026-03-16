# Zonik Android App

## Project Overview
A native Android music player app that streams music from a self-hosted [Zonik](https://github.com/Pr0zak/Zonik) server. Single-user sideloaded APK with Android Auto support. Self-updates from GitHub releases.

**Repo:** https://github.com/Pr0zak/Zonik-mobile

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 + custom dark theme
- **Playback:** AndroidX Media3 (ExoPlayer) + MediaLibraryService
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Local DB:** Room + Paging 3
- **DI:** Hilt (KSP)
- **Image Loading:** Coil + AndroidX Palette (album art color extraction)
- **Background:** WorkManager
- **Build:** Gradle 8.11.1 (Kotlin DSL), version catalogs
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35
- **JDK:** 17 (at `~/tools/jdk-17.0.12`)
- **Android SDK:** at `~/tools/android-sdk`

## Slash Commands
- `/build [debug|release]` — build the APK
- `/version` — show current version; `/version bump patch|minor|major` or `/version set X.Y.Z`
- `/release [patch|minor|major]` — bump version, build, commit, push, create GitHub release
- `/update-release` — rebuild APK and update current GitHub release (hotfix, no version bump)

## Build & Run
```bash
export JAVA_HOME=$HOME/tools/jdk-17.0.12
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=$HOME/tools/android-sdk
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on connected device
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture
- MVVM with Repository pattern
- Single-activity, Compose Navigation with bottom nav (5 tabs: Home, Library, Search, Downloads, Settings)
- MediaLibraryService for background playback + Android Auto browse tree
- Two API layers: OpenSubsonic (`/rest`) for playback/library, Zonik native (`/api`) for downloads
- Fast library sync via `search3` empty query (Symfonium approach) — bulk fetches all artists/albums/tracks
- Self-update checker queries GitHub releases API
- Debug log upload to private GitHub Gists

## Key Conventions
- Package: `com.zonik.app`
- Use coroutines + Flow for async work
- All network calls go through repository layer
- Subsonic auth: token auth `md5(apiKey + salt)` as query params
- Stream/cover art URLs: auth baked directly into URL (not via interceptor) because ExoPlayer uses its own HTTP stack
- Media3 IPC: `requestMetadata.mediaUri` carries stream URL across controller↔service boundary (`localConfiguration` is stripped during IPC)
- Track identification after IPC: use `currentMediaItemIndex` not `mediaId` (mediaId lost in IPC)
- Keep UI state in ViewModels using StateFlow

## Project Structure
```
app/src/main/java/com/zonik/app/
  data/
    api/          # SubsonicApi, SubsonicAuthInterceptor, ZonikApi, UpdateChecker, LogUploader
    db/           # Room entities, DAOs, ZonikDatabase
    repository/   # LibraryRepository, SettingsRepository, SyncManager
    DebugLog.kt   # In-memory debug log buffer (500 entries)
  di/             # AppModule (Hilt — OkHttpClient, Retrofit, Room, dynamic base URL)
  media/
    ZonikMediaService.kt  # MediaLibraryService, ExoPlayer, Auto browse tree, onAddMediaItems
    PlaybackManager.kt    # MediaController wrapper, queue, playback state, smart bitrate
  model/          # Domain models (Track, Album, Artist, etc), SubsonicResponse wrappers
  ui/
    components/
      CoverArt.kt       # Reusable album art via Coil (auth handled by ImageLoader)
      MiniPlayer.kt     # Persistent mini player bar with progress indicator
      TrackListItem.kt  # Unified track row with format badge and context menu
    navigation/   # Screen/MainTab route definitions
    screens/
      home/       # HomeScreen — recent albums, recent tracks, shuffle/random, recently played
      library/    # LibraryScreen (5 tabs: Tracks/Albums/Artists/Genres/Playlists), AlbumDetail, ArtistDetail
      search/     # SearchScreen — debounced search across library
      downloads/  # DownloadsScreen — Soulseek search/active transfers/job history (real Zonik API)
      playlists/  # PlaylistsScreen
      nowplaying/ # NowPlayingScreen — Symfonium-style with blurred background, Palette colors
      login/      # LoginScreen — connection test (ping + auth verification)
      settings/   # SettingsScreen — server, sync, playback, Last.fm, cache, updates, debug logs
    theme/        # ZonikTheme — custom dark color scheme (deep dark + purple/teal accents)
    util/         # FormatUtils (duration, file size formatting)
  MainActivity.kt     # Main activity, nav host, bottom nav, Now Playing overlay with slide animation
  ZonikApplication.kt # Hilt app, notification channels, WorkManager, Coil ImageLoader
```

## Playback Architecture
- ExoPlayer in `ZonikMediaService` with `DefaultHttpDataSource` (not OkHttpDataSource)
- Auth params baked into stream URLs (ExoPlayer doesn't go through OkHttp interceptors)
- `onAddMediaItems` resolves URIs from `requestMetadata.mediaUri` (survives IPC)
- `PlaybackManager` connects via `MediaController`, tracks queue locally
- Track transitions identified by `currentMediaItemIndex` (mediaId lost in IPC)
- Smart bitrate: auto-detects Wi-Fi vs cellular, applies configured max bitrate
- Now Playing auto-shows instantly on tap via `playbackRequested` SharedFlow (emits before buffering)
- `currentTrack` set immediately in `playTracks()` for instant UI update
- Slide animation: 250ms up / 200ms down
- Seek bar polling: 100ms (Now Playing), 200ms (MiniPlayer)

## Android Auto
- MediaLibraryService with MediaLibrarySession.Callback
- 4 root tabs: Recent, Library, Playlists, Mix
- Content style: GRID for albums/artists, LIST for tracks/playlists
- Voice search with empty query handling
- Browse tree works without Activity; rebuilds from scratch on force-stop
- Cover art URIs include baked-in auth params (fetched by system UI)
- **Sideloaded APK setup**: user must enable Developer Mode in Android Auto settings (tap version 10x), then enable "Unknown sources" in developer settings

## API Notes
- Subsonic API at `{server}/rest/{endpoint}.view`
- Auth params: `u`, `t` (md5(apiKey+salt)), `s` (random salt), `v=1.16.1`, `c=ZonikApp`, `f=json`
- Streaming: `GET /rest/stream.view?id={trackId}&estimateContentLength=true` (NO `f=json` — returns binary)
- Cover art: `GET /rest/getCoverArt.view?id={coverId}&size={pixels}` (NO `f=json`)
- Library sync: `search3` with empty query, 500 items per page (Symfonium approach)
- Zonik native API: `POST /api/download/search`, `/api/download/trigger`, `GET /api/jobs` (`Accept: application/json` header required, response wrapped in `{"items": [...]}`)

## Debugging
- `DebugLog` singleton captures last 500 entries with timestamps
- Settings > Debug: "Copy Logs" to clipboard or "Upload Logs" to private GitHub Gist
- Requires GitHub PAT with gist scope for upload
- Logs cover: API calls (path + status), sync progress, playback events (connect, play, errors, state changes), login flow

## Release Process
1. `/release patch` (or `minor`/`major`) — automates everything
2. Or manually: bump version in `app/build.gradle.kts`, build, copy APK to `release/`, commit, push, `gh release create`
3. App checks for updates on Settings screen and prompts user to download/install
4. `/update-release` for hotfixes without version bump

## Known Gotchas
- Media3 strips `localConfiguration` (URI) during controller↔service IPC — must use `requestMetadata.mediaUri`
- Media3 `mediaId` becomes empty after IPC — track by queue index not mediaId
- `onAddMediaItems` callback MUST be implemented or setMediaItems silently does nothing (ExoPlayer goes BUFFERING→ENDED with no error)
- ExoPlayer uses `DefaultHttpDataSource`, NOT our OkHttpClient — auth must be baked into stream URLs
- Stream/cover art URLs must NOT include `f=json` (server returns binary audio/image, not JSON)
- Zonik `/api/jobs` returns HTML without `Accept: application/json` header
- Job history response is `{"items": [...]}` not a raw array
- `cleartext` HTTP must be allowed via `network_security_config.xml` for local servers
- Coil ImageLoader must use the auth-aware OkHttpClient for cover art (configured in ZonikApplication)
- Android Auto requires Developer Mode + Unknown Sources for sideloaded APKs
- DataStore emits on any field change — don't use `collect` on `isLoggedIn` to trigger sync (causes infinite loop); use `first()` instead
