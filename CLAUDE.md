# Zonik Android App

## Project Overview
A native Android music player app that streams music from a self-hosted [Zonik](https://github.com/Pr0zak/Zonik) server. Single-user sideloaded APK with Android Auto support. Self-updates from GitHub releases.

**Repo:** https://github.com/Pr0zak/Zonik-mobile

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (dynamic color)
- **Playback:** AndroidX Media3 (ExoPlayer) + MediaLibraryService
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Local DB:** Room + Paging 3
- **DI:** Hilt (KSP)
- **Image Loading:** Coil + AndroidX Palette
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
- Single-activity, Compose Navigation with bottom nav (5 tabs)
- MediaLibraryService for background playback + Android Auto browse tree
- Two API layers: OpenSubsonic (`/rest`) for playback, Zonik native (`/api`) for downloads & extended features
- Download search via Zonik native API (Soulseek backend)
- Last.fm scrobbling via Last.fm API v2 (direct from app)
- Self-update checker queries GitHub releases API

## Key Conventions
- Package: `com.zonik.app`
- Use coroutines + Flow for async work
- All network calls go through repository layer
- Subsonic auth uses token auth (md5(apiKey + salt)) passed as query params
- Keep UI state in ViewModels using StateFlow
- Login verifies connection (ping + auth) before saving credentials

## Project Structure
```
app/src/main/java/com/zonik/app/
  data/
    api/          # SubsonicApi, SubsonicAuthInterceptor, UpdateChecker
    db/           # Room entities, DAOs, ZonikDatabase
    repository/   # LibraryRepository, SettingsRepository
  di/             # AppModule (Hilt)
  media/          # ZonikMediaService (MediaLibraryService), PlaybackManager
  model/          # Domain models, SubsonicResponse wrappers
  ui/
    components/   # MiniPlayer
    navigation/   # Screen/MainTab route definitions
    screens/
      home/       # HomeScreen + ViewModel
      library/    # LibraryScreen, AlbumDetailScreen, ArtistDetailScreen
      search/     # SearchScreen + ViewModel
      downloads/  # DownloadsScreen + ViewModel (Soulseek search/queue)
      playlists/  # PlaylistsScreen + ViewModel
      nowplaying/ # NowPlayingScreen + ViewModel
      login/      # LoginScreen + ViewModel (with connection test)
      settings/   # SettingsScreen + ViewModel (with update checker)
    theme/        # ZonikTheme (Material 3 dynamic color)
  MainActivity.kt     # Main activity, nav host, bottom nav
  ZonikApplication.kt # Hilt app, notification channels, WorkManager
```

## Android Auto
- Uses MediaLibraryService with MediaLibrarySession.Callback
- 4 root tabs: Recent, Library, Playlists, Mix (Android Auto max)
- Max 4 browse levels deep, 10s load timeout
- Content style: GRID for albums/artists, LIST for tracks/playlists
- Voice search with empty query handling (plays recent/shuffled)
- Browse tree works without Activity; rebuilds from scratch on force-stop

## API Notes
- Zonik Subsonic API at `{server}/rest/{endpoint}.view`
- Auth params: `u`, `t` (md5(apiKey+salt)), `s` (random salt), `v=1.16.1`, `c=ZonikApp`, `f=json`
- Streaming: `GET /rest/stream.view?id={trackId}&estimateContentLength=true`
- Cover art: `GET /rest/getCoverArt.view?id={coverId}&size={pixels}`
- All responses wrapped in `subsonic-response` JSON envelope
- Query `getOpenSubsonicExtensions` on connect to detect server capabilities

## Release Process
1. `/release patch` (or `minor`/`major`) — automates everything
2. Or manually: bump version in `app/build.gradle.kts`, build, copy APK to `release/`, commit, push, `gh release create`
3. App checks for updates on Settings screen and prompts user to download/install
