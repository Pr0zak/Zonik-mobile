# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Zonik Android App

## Project Overview
A native Android music player app that streams music from a self-hosted [Zonik](https://github.com/Pr0zak/Zonik) server. Single-user sideloaded APK with Android Auto and Chromecast support. Self-updates from GitHub releases.

**Repo:** https://github.com/Pr0zak/Zonik-mobile

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 + custom dark theme (gold/amber accents, vinyl record logo)
- **Playback:** AndroidX Media3 (ExoPlayer) + MediaLibraryService + Google Cast + SimpleCache
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Local DB:** Room (v2) + Paging 3
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
- Playlist playback: `PlaybackManager` sends track IDs via custom `SessionCommand` (`PLAY_TRACKS`), service builds MediaItems and sets them directly on the player — bypasses Media3 per-item IPC which reorders playlists
- Track identification: use metadata matching (title/artist) for transitions — `mediaId` and index are unreliable after IPC
- Keep UI state in ViewModels using StateFlow
- All track lists support long-press context menus (Play, Play Next, Add to Queue, Mark for Deletion)

## Project Structure
```
app/src/main/java/com/zonik/app/
  data/
    api/          # SubsonicApi, SubsonicAuthInterceptor, ZonikApi, UpdateChecker, LogUploader
    db/           # Room entities, DAOs, ZonikDatabase (v2)
    repository/   # LibraryRepository, SettingsRepository, SyncManager
    DebugLog.kt   # In-memory debug log buffer (500 entries)
  di/             # AppModule (Hilt — OkHttpClient, Retrofit, Room, SimpleCache, dynamic base URL)
  media/
    ZonikMediaService.kt  # MediaLibraryService, ExoPlayer, Auto browse tree, onAddMediaItems
    PlaybackManager.kt    # MediaController wrapper, queue, playback state, smart bitrate
    CastManager.kt        # Google Cast session management, remote media playback, queue transfer
    CastOptionsProvider.kt # Cast Framework config, receiver app ID (B621DA15)
  model/          # Domain models (Track, Album, Artist, etc), SubsonicResponse wrappers
  ui/
    components/
      CoverArt.kt       # Reusable album art via Coil (auth handled by ImageLoader)
      MiniPlayer.kt     # Persistent mini player bar with progress indicator
      TrackListItem.kt  # Unified track row with format badge and context menu
    navigation/   # Screen/MainTab route definitions
    screens/
      home/       # HomeScreen — app icon in top bar, recent albums, recent tracks, shuffle/random, recently played
      library/    # LibraryScreen (5 tabs: Tracks/Albums/Artists/Genres/Playlists), AlbumDetail, ArtistDetail
      search/     # SearchScreen — debounced search across library
      downloads/  # DownloadsScreen — Soulseek search/active transfers/job history (real Zonik API)
      playlists/  # PlaylistsScreen
      nowplaying/ # NowPlayingScreen — Symfonium-style with blurred background, Palette colors, queue (85% height, auto-scroll to current)
      login/      # LoginScreen — connection test (ping + auth verification)
      stats/      # StatsScreen — library overview, format/bitrate/genre/decade distributions, most played, top artists
      settings/   # SettingsScreen — server, sync, playback (bitrate, crossfade, keep screen on), cache (size, read-ahead), Android Auto (tab order), Last.fm, updates, debug logs
    theme/        # ZonikTheme — custom dark color scheme (dark brown/black + gold/amber accents)
    util/         # FormatUtils (duration, file size formatting)
  MainActivity.kt     # Main activity, nav host, bottom nav, Now Playing overlay with slide animation
  ZonikApplication.kt # Hilt app, notification channels, WorkManager, Coil ImageLoader
```

## Playback Architecture
- ExoPlayer in `ZonikMediaService` with `CacheDataSource` wrapping `OkHttpDataSource`
- Auth params baked into stream URLs (ExoPlayer doesn't go through app's OkHttp interceptors)
- **Playlist loading**: `PlaybackManager` sends track IDs via custom `PLAY_TRACKS` SessionCommand → service looks up tracks from Room DB, builds MediaItems, sets them directly on ExoPlayer. This bypasses Media3's per-item `onAddMediaItems` IPC which reorders playlists.
- `onAddMediaItems` still handles Android Auto browse-tree playback and single-item URI resolution
- `PlaybackManager` connects via `MediaController`, tracks queue locally
- Track transitions matched by metadata (title/artist) — `mediaId` and index are unreliable after IPC
- Skip next/previous: use `seekToNext()` / `seekToPrevious()` (NOT `seekToNextMediaItem()` — command availability issues)
- **Smart bitrate**: auto-detects Wi-Fi vs cellular, applies configured max bitrate. Adaptive degradation on slow connections (steps down 320→256→192→128→64 after 3 consecutive buffering events, auto-restores after 3 stable tracks).
- **Connection resilience**: custom `LoadErrorHandlingPolicy` with 10 retries + exponential backoff (up to 16s). `DefaultLoadControl` with 30s min / 2min max buffer. 60s read timeout. `ConnectivityManager.NetworkCallback` auto-resumes playback when network returns. `onPlayerError` auto-prepares on IO errors.
- **Audio caching**: `SimpleCache` (Hilt singleton) with `LeastRecentlyUsedCacheEvictor`, custom `CacheKeyFactory` uses track ID (not full URL with auth salt). Configurable size (off/250MB–10GB, default 500MB).
- **Read-ahead pre-caching**: on track transition (AUTO/SEEK) and on queue load (`PLAY_TRACKS`), next N tracks are pre-cached via `CacheWriter` on IO thread. Configurable count (0–10, default 5).
- **Buffering UI**: Now Playing shows spinner on play button when buffering, error banner for connection issues. Android Auto gets buffering state automatically via MediaSession.
- **Keep screen on**: optional setting prevents display sleep while Now Playing is visible and playing (uses `FLAG_KEEP_SCREEN_ON`)
- Now Playing auto-shows instantly on tap via `playbackRequested` SharedFlow (emits before buffering)
- `currentTrack` set immediately in `playTracks()` for instant UI update
- Slide animation: 250ms up / 200ms down
- Seek bar polling: 100ms (Now Playing), 200ms (MiniPlayer)
- Play All / Shuffle capped to 500 tracks to avoid Binder `TransactionTooLargeException`

## Track Management
- **Mark for Deletion**: tracks can be marked/unmarked via long-press context menu on any track list, Now Playing screen, and Android Auto now playing button
- Marked tracks show red title text as visual indicator
- `markedForDeletion` flag stored in Room DB, preserved across library syncs
- Available on: HomeScreen, LibraryScreen, AlbumDetailScreen, SearchScreen, TrackListItem component, Android Auto
- **Star/Unstar**: calls Subsonic `star`/`unstar` API then updates local Room DB; available in Now Playing, Android Auto, AlbumDetailScreen
- Starred status synced bidirectionally: `getStarred2` API fetched during sync merges server-side stars with local DB
- Now Playing reads starred status directly from Room DB (not stale in-memory Track object)

## Android Auto
- MediaLibraryService with MediaLibrarySession.Callback
- 4 root tabs (configurable order in Settings): Mix, Recent, Library, Playlists
- Mix section: Shuffle, Newly Added, Favorites, Non-Favorites
- Content style: GRID for albums/artists, LIST for tracks/playlists
- Voice search with empty query handling
- Custom buttons in now playing: Star/Unstar (heart) + Mark for Deletion (trash)
- `starredTrackIds` and `markedForDeletionIds` loaded from Room DB on service startup
- Browse tree works without Activity; rebuilds from scratch on force-stop
- Cover art URIs include baked-in auth params (fetched by system UI)
- **Sideloaded APK setup**: user must enable Developer Mode in Android Auto settings (tap version 10x), then enable "Unknown sources" in developer settings

## Chromecast
- Google Cast SDK (Play Services Cast Framework) + AndroidX MediaRouter
- Styled Media Receiver registered in Cast Developer Console (app ID: `B621DA15`)
- `CastOptionsProvider` configures receiver app ID + notification target activity
- `CastManager` singleton: session lifecycle, `RemoteMediaClient` for playback, queue loading
- `PlaybackManager` routes all playback commands through `CastManager` when casting is active
- Auto-transfers current queue to Cast device when session starts, pauses local ExoPlayer
- Cast button in Now Playing screen via `MediaRouteButton` wrapped in Compose `AndroidView`
- `MediaRouteButton` requires `AppCompatActivity` + AppCompat theme (not `ComponentActivity`)
- Receiver CSS hosted via jsdelivr CDN: `https://cdn.jsdelivr.net/gh/Pr0zak/Zonik-mobile@main/cast/style.css`
- Cast receiver assets in `cast/` directory (icon.png, icon.svg, style.css)

## API Notes
- Subsonic API at `{server}/rest/{endpoint}.view`
- Auth params: `u`, `t` (md5(apiKey+salt)), `s` (random salt), `v=1.16.1`, `c=ZonikApp`, `f=json`
- Streaming: `GET /rest/stream.view?id={trackId}&estimateContentLength=true` (NO `f=json` — returns binary)
- Cover art: `GET /rest/getCoverArt.view?id={coverId}&size={pixels}` (NO `f=json`)
- Library sync: `search3` with empty query, 500 items per page (Symfonium approach) + `getStarred2` for starred status
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
- **DO NOT use `controller.setMediaItems()` for playlist playback** — Media3 decomposes items and calls `onAddMediaItems` per-item over IPC, reordering the playlist. Use the custom `PLAY_TRACKS` SessionCommand instead (sends track IDs, service builds and sets items directly on player).
- Media3 strips `localConfiguration` (URI) during controller↔service IPC — `onAddMediaItems` must reconstruct from `requestMetadata.mediaUri`
- Media3 `mediaId` becomes empty after IPC — use metadata matching (title/artist) not mediaId
- `onAddMediaItems` callback MUST be implemented for Android Auto browse-tree playback (ExoPlayer goes BUFFERING→ENDED without it)
- Use `seekToNext()` / `seekToPrevious()` not `seekToNextMediaItem()` / `seekToPreviousMediaItem()` — the MediaItem variants require `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` which may not be available on the MediaController after playlist changes
- ExoPlayer uses `CacheDataSource` wrapping `OkHttpDataSource` with a clean client (no auth interceptor) — auth must be baked into stream URLs
- Cache key factory must extract track ID from URL (not use full URL) because auth params include random salt
- Play All / Shuffle must cap tracks at 500 to avoid `TransactionTooLargeException` (Binder 1MB IPC limit)
- `starred` flag must be preserved during sync — `syncAllTracks` fetches `getStarred2` and merges server + local starred IDs before upserting
- `search3` response may NOT include `starred` field reliably — always use `getStarred2` as authoritative source
- Now Playing starred status must read from Room DB directly, not from in-memory Track objects (which may be stale)
- Network callback must be registered/unregistered in `onCreate`/`onDestroy` and must post to main thread (ExoPlayer threading requirement)
- `SimpleCache.isCached()` with `Long.MAX_VALUE` always returns false — use `getCachedBytes() > 0` instead
- `CacheWriter.cache()` is blocking and not coroutine-cancellation-aware — cancel the job between tracks, not mid-download
- Adaptive bitrate `bitrateOverride` must be `@Volatile` — accessed from both UI and player threads
- Stream/cover art URLs must NOT include `f=json` (server returns binary audio/image, not JSON)
- Zonik `/api/jobs` returns HTML without `Accept: application/json` header
- Job history response is `{"items": [...]}` not a raw array
- `cleartext` HTTP must be allowed via `network_security_config.xml` for local servers
- Coil ImageLoader must use the auth-aware OkHttpClient for cover art (configured in ZonikApplication)
- Android Auto requires Developer Mode + Unknown Sources for sideloaded APKs
- DataStore emits on any field change — don't use `collect` on `isLoggedIn` to trigger sync (causes infinite loop); use `first()` instead
- Room DB uses `fallbackToDestructiveMigration()` — bump version number when changing entities (no manual migration needed)
- `markedForDeletion` flag must be preserved during sync — `syncAllTracks` and `getAlbumDetail` read existing marked IDs before upserting
- Cast `MediaRouteButton` crashes without `AppCompatActivity` context — `MainActivity` extends `AppCompatActivity` (not `ComponentActivity`)
- Cast initialization wrapped in try-catch — graceful fallback on devices without Google Play Services
- Cast receiver CSS/images must use jsdelivr CDN URLs (raw GitHub returns wrong MIME types)
