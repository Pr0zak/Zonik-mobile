# Zonik Android App

## Project Overview
A native Android music player app that streams music from a self-hosted [Zonik](https://github.com/Pr0zak/Zonik) server. Single-user app with Android Auto support.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Playback:** AndroidX Media3 (ExoPlayer) + MediaSessionService
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Local DB:** Room + Paging 3
- **DI:** Hilt
- **Image Loading:** Coil + AndroidX Palette
- **Background:** WorkManager
- **Build:** Gradle (Kotlin DSL), version catalogs
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## Architecture
- MVVM with Repository pattern
- Single-activity, Compose Navigation
- MediaSessionService for background playback + Android Auto integration
- Two API layers: OpenSubsonic (`/rest`) for playback compatibility, Zonik native (`/api`) for downloads & extended features
- Download search via Zonik native API (Soulseek backend) with real-time WebSocket progress
- Last.fm scrobbling via Last.fm API v2 (direct from app, independent of server)

## Key Conventions
- Package: `com.zonik.app`
- Use coroutines + Flow for async work
- All network calls go through repository layer
- Subsonic auth uses token auth (md5(apiKey + salt)) passed as query params
- Keep UI state in ViewModels using StateFlow

## Build & Run
```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on connected device
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests
```

## Project Structure
```
app/
  src/main/
    java/com/zonik/app/
      data/           # API clients, Room DB, repositories
        api/          # Retrofit interfaces (Subsonic + Zonik native + Last.fm)
        db/           # Room entities, DAOs
        repository/   # Repository implementations
      di/             # Hilt modules
      media/          # Media3 service, playback manager
      ui/             # Compose screens + ViewModels
        auto/         # Android Auto related
        components/   # Reusable composables
        navigation/   # Nav graph
        screens/      # Feature screens
      model/          # Domain models
    res/
      xml/
        automotive_app_desc.xml  # Android Auto config
    AndroidManifest.xml
```

## Android Auto
- Uses Media3 MediaSessionService (required for Auto)
- Max 4 root tabs, max 4 levels deep, 10s load timeout
- Content style hints: GRID for albums/artists, LIST for tracks/playlists
- Voice search with empty query handling (play recent/shuffled)
- Browse tree must work without Activity open; rebuild from scratch on force-stop
- All media items must have proper MediaMetadata + placeholder art for missing covers

## API Notes
- Zonik Subsonic API is at `{server}/rest/{endpoint}` or `{server}/rest/{endpoint}.view`
- Auth params on every request: `u`, `t` (md5(apiKey+salt)), `s` (random salt), `v=1.16.1`, `c=ZonikApp`, `f=json`
- Streaming: `GET /rest/stream?id={trackId}` — supports transcoding via `format` and `maxBitRate` params
- Cover art: `GET /rest/getCoverArt?id={coverId}&size={pixels}`
- All Subsonic responses are wrapped in `subsonic-response` JSON envelope
- Query `getOpenSubsonicExtensions` on connect to detect server capabilities
- Use `estimateContentLength=true` on stream endpoint for seek support
