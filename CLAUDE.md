# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Zonik Android App

## Project Overview
A native Android music player app that streams music from a self-hosted [Zonik](https://github.com/Pr0zak/Zonik) server. Single-user sideloaded APK with Android Auto, Chromecast, and Google TV support. Self-updates from GitHub releases.

**Repo:** https://github.com/Pr0zak/Zonik-mobile

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 + custom dark theme (glass morphism, gradient buttons, gold format badges, floating MiniPlayer)
- **Playback:** AndroidX Media3 (ExoPlayer) + MediaLibraryService + Google Cast + SimpleCache
- **Networking:** Retrofit + OkHttp + Kotlinx Serialization
- **Local DB:** Room (v3) + Paging 3
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
- `/logs [id]` — list uploaded app logs from server, or fetch a specific log by ID

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
- Debug log upload to Zonik server or private GitHub Gists

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
- **UI design system**: `ZonikColors` (gold, goldDim, glassBg, gradientStart/End, navBarBg), `ZonikShapes` (cardShape 16dp, miniPlayerShape 16dp, navBarShape 24dp top, coverArtShape 12dp, coverArtLargeShape 24dp, buttonShape 12dp, badgeShape 6dp)
- **Glass morphism**: semi-transparent backgrounds (80-90% opacity `ZonikColors.glassBg`/`navBarBg`) — true backdrop blur requires API 31+ so not used (min SDK 26)
- **Gradient buttons**: `Brush.horizontalGradient` with `containerColor = Color.Transparent` wrapping a `Box` with gradient background
- **Format badges**: gold (`ZonikColors.gold`) for lossless (FLAC/ALAC), gray for lossy (MP3/AAC/OGG/OPUS)
- **MainScreen layout**: `Box` instead of `Scaffold` — floating MiniPlayer above glass nav bar
- **Settings editable server details**: Server URL, username, API key editable via tap-to-edit dialogs. Test Connection button (pings server). GitHub link with proper icon in About section.
- **Screens use custom top bars** (Row with headlineMedium bold title) instead of M3 TopAppBar, with `statusBarsPadding()`

## Project Structure
```
app/src/main/java/com/zonik/app/
  data/
    api/          # SubsonicApi, SubsonicAuthInterceptor, ZonikApi, UpdateChecker, LogUploader
    db/           # Room entities, DAOs, ZonikDatabase (v3)
    repository/   # LibraryRepository, SettingsRepository, SyncManager
    DebugLog.kt   # In-memory debug log buffer (500 entries)
  di/             # AppModule (Hilt — OkHttpClient, Retrofit, Room, SimpleCache, dynamic base URL)
  media/
    ZonikMediaService.kt  # MediaLibraryService, ExoPlayer, Auto browse tree, onAddMediaItems
    PlaybackManager.kt    # MediaController wrapper, queue, playback state, smart bitrate
    CastManager.kt        # Google Cast session management, remote media playback, queue transfer
    CastOptionsProvider.kt # Cast Framework config, receiver app ID (B621DA15)
    OfflineCacheManager.kt # Offline track download manager (persistent, never evicted)
    WaveformManager.kt    # Track waveform extraction and caching (server API + client fallback)
  model/          # Domain models (Track, Album, Artist, etc), SubsonicResponse wrappers
  ui/
    components/
      CoverArt.kt       # Reusable album art via Coil (auth handled by ImageLoader)
    CoverArtProvider.kt # ContentProvider for Android Auto cover art (disk cache, network check, log throttle)
      MiniPlayer.kt     # Floating glass mini player with skip prev/next, circular play/pause, progress bar at bottom
      TrackListItem.kt  # Unified track row with gold/gray format badge, left accent border for playing track, context menu
      AudioVisualizerBars.kt # WaveformBars — static waveform seek bar background (200 bars)
    navigation/   # Screen/MainTab route definitions
    screens/
      home/       # HomeScreen — custom top bar, gradient shuffle button, recently played cards (176dp), recent tracks
      library/    # LibraryScreen (8 tabs: Tracks/Albums/Artists/Favorites/Genres/Playlists/Flagged/Offline), AlbumDetail, ArtistDetail — gradient Play All, alpha scroll sidebar
      search/     # SearchScreen — debounced search across library
      downloads/  # DownloadsScreen — Soulseek search/active transfers/job history (real Zonik API)
      playlists/  # PlaylistsScreen
      nowplaying/ # NowPlayingScreen — glass info card, glass control bar, album art glow, swipe-to-dismiss, gradient play button, zebra-stripe queue
      login/      # LoginScreen — connection test (ping + auth verification)
      stats/      # StatsScreen — library overview, format/bitrate/genre/decade distributions, most played, top artists
      settings/   # SettingsScreen — uppercase section headers, rounded cards, cache progress bar, rounded icon containers, editable server details
    theme/        # ZonikTheme — ZonikColors (gold, glass, gradients), ZonikShapes (cards, nav, cover art), custom Typography
    tv/
      TvMainScreen.kt    # TV-specific interface with TvViewModel, sidebar nav, now playing card, screensaver
      ParticleSystem.kt  # Screensaver particle system (ORB/RING/SPARKLE shapes, blur glow, trails)
    util/
      FormatUtils.kt  # Duration, file size formatting
      TvUtils.kt      # isTvDevice(), isTv(), tvFocusHighlight() modifier
  MainActivity.kt     # Main activity, nav host, glass bottom nav bar, floating MiniPlayer, Now Playing overlay with slide animation
  ZonikApplication.kt # Hilt app, notification channels, WorkManager, Coil ImageLoader
```

## Playback Architecture
- ExoPlayer in `ZonikMediaService` with `CacheDataSource` wrapping `OkHttpDataSource`
- Auth params baked into stream URLs (ExoPlayer doesn't go through app's OkHttp interceptors)
- **Playlist loading**: `PlaybackManager` sends track IDs via custom `PLAY_TRACKS` SessionCommand → service looks up tracks from Room DB, builds MediaItems, sets them directly on ExoPlayer. This bypasses Media3's per-item `onAddMediaItems` IPC which reorders playlists.
- `onAddMediaItems` still handles Android Auto browse-tree playback and single-item URI resolution
- `PlaybackManager` connects via `MediaController`, tracks queue locally. Queue auto-syncs from player's media items when out of sync (e.g. Android Auto starts playback or playback resumption restores queue).
- Track transitions matched by metadata (title/artist) — `mediaId` and index are unreliable after IPC
- Skip next/previous: use `seekToNext()` / `seekToPrevious()` (NOT `seekToNextMediaItem()` — command availability issues)
- **Smart bitrate**: auto-detects Wi-Fi vs cellular, applies configured max bitrate. Adaptive degradation on slow connections (steps down 320→256→192→128→64 after 3 buffering events in 2-minute rolling window, auto-restores after 3 stable tracks).
- **Connection resilience**: custom `LoadErrorHandlingPolicy` with 10 retries + exponential backoff (up to 16s). `DefaultLoadControl` with 15s min / 2min max buffer, 1.5s playback start, 5s rebuffer. 60s read timeout, 30s connect timeout (for server transcode queue backpressure). `ConnectivityManager.NetworkCallback` auto-resumes playback when network returns. `onPlayerError` auto-prepares on IO errors. OkHttp connection pool (5 connections, 30s keep-alive) for connection reuse.
- **Now playing scrobble**: sends `submission=false` scrobble on track start for server now-playing state.
- **Track transition dedup**: PLAYLIST_CHANGED transitions for the same track are skipped to prevent UI flicker and duplicate scrobbles.
- **Audio caching**: `SimpleCache` (Hilt singleton) with `LeastRecentlyUsedCacheEvictor`, custom `CacheKeyFactory` uses track ID (not full URL with auth salt). Configurable size (off/250MB–10GB, default 500MB).
- **Read-ahead pre-caching**: on track transition (AUTO/SEEK) and on queue load (`PLAY_TRACKS`), next N tracks are pre-cached via `CacheWriter` on IO thread. Configurable count (0–5, default 5). Pre-caching pauses when player is buffering to yield bandwidth. Deduplicates via `ConcurrentHashMap.newKeySet` to prevent redundant downloads.
- **Offline caching**: `OfflineCacheManager` singleton downloads tracks to `filesDir/offline_tracks/` — separate from streaming LRU cache, never evicted. Auto-cache queue on `playTracks` (if enabled). Auto-cache favorites after sync (if enabled). Settings: master toggle, auto-cache queue/favorites, storage limit (2–50GB or no limit). Library "Offline" tab shows cached tracks. Green cloud icon on cached tracks in `TrackListItem`. Queue sheet has download button with per-track status (cached/downloading/queued) and cancel support. ExoPlayer checks offline dir first, then streaming cache, then network. `offlineCached` field in `TrackEntity` (DB v3), preserved during sync upsert.
- **Waveform seek bar**: static track waveform rendered as seek bar background. `WaveformManager` tries server API first (`/api/tracks/{id}/waveform`), falls back to client-side `MediaExtractor`/`MediaCodec`. Persistent file cache (`filesDir/waveforms/{trackId}.wfm`, 800 bytes each). 200 bars, played/unplayed portions colored differently. Toggle in Settings: "Waveform Seek Bar". Files: `media/WaveformManager.kt`, `ui/components/AudioVisualizerBars.kt` (contains `WaveformBars`).
- **Buffering UI**: Now Playing shows spinner on play button when buffering, error banner for connection issues. Android Auto gets buffering state automatically via MediaSession.
- **Equalizer**: 5-band EQ (60Hz/230Hz/910Hz/3.6kHz/14kHz) via `android.media.audiofx.Equalizer` bound to ExoPlayer's audio session ID. 10 presets + custom band levels. Settings persisted in DataStore, restored on service startup. System EQ launch button for OEM equalizers. EQ commands sent via `SET_EQ` SessionCommand (must be on main thread).
- **Keep screen on**: optional setting prevents display sleep while Now Playing is visible and playing (uses `FLAG_KEEP_SCREEN_ON`)
- Now Playing auto-shows instantly on tap via `playbackRequested` SharedFlow (emits before buffering)
- Now Playing: swipe-down to dismiss (vertical drag gesture with 300px threshold), glass info card with format badge, glass secondary control bar, gradient play/pause button (80dp), small dot seek thumb
- `currentTrack` set immediately in `playTracks()` for instant UI update
- Slide animation: 250ms up / 200ms down
- Seek bar polling: 100ms (Now Playing), 200ms (MiniPlayer)
- **Queue restore from idle**: after process kill, queue restored from DataStore (`lastQueueTrackIds`, `lastQueueIndex`, `lastQueuePositionMs`). Calls `playTracks` with `startPaused=true`, then `seekTo` saved position. `playWhenReady` must be set `false` explicitly before `prepare()` when `startPaused`.
- Play All / Shuffle capped to 500 tracks to avoid Binder `TransactionTooLargeException`

## Track Management
- **Mark for Deletion**: tracks can be marked/unmarked via long-press context menu on any track list, Now Playing screen, and Android Auto now playing button
- Marked tracks show red title text as visual indicator
- Synced with Zonik server via Subsonic `setRating` API: marking sets `rating=1`, unmarking sets `rating=0`
- Server is authoritative during sync: `userRating == 1` in `search3` response → `markedForDeletion = true`
- Zonik server treats `rating=1` as "flagged for deletion" — tracks appear in server web UI's "Flagged" filter
- Available on: HomeScreen, LibraryScreen, AlbumDetailScreen, SearchScreen, TrackListItem component, Android Auto
- **Flagged tab** in Library: shows all flagged tracks with "Delete All from Server" button (confirmation dialog). Calls `POST /api/tracks/bulk-delete` to permanently remove tracks from server filesystem + DB, then removes from local Room DB.
- **Star/Unstar**: calls Subsonic `star`/`unstar` API then updates local Room DB; available in Now Playing, Android Auto, AlbumDetailScreen
- Starred status synced from server: `getStarred2` API is authoritative — during sync, server starred list replaces local starred state
- Now Playing reads starred status directly from Room DB (not stale in-memory Track object)

## Android Auto
- MediaLibraryService with MediaLibrarySession.Callback
- 4 root tabs (configurable order in Settings): Mix, Recent, Library, Playlists
- Mix section: Shuffle, Newly Added, Favorites, Non-Favorites
- Content style: GRID for albums/artists, LIST for tracks/playlists
- Voice search with empty query handling
- Custom buttons in now playing: Star/Unstar (heart), Mark for Deletion (trash), Shuffle, Start Radio
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

## Google TV
- Dedicated TV interface (`TvMainScreen`) with left sidebar navigation (Home / Settings), no Library tab on TV
- D-pad navigation with gold focus highlights (`tvFocusHighlight` modifier)
- Shuffle Mix + Shuffle Favorites buttons side by side on Home
- Now Playing card with ambient color glow (palette extraction), controls, star, progress bar
- No gesture detectors on TV (swipe-to-dismiss, alpha scroll sidebar disabled via `isTv()` checks)
- **Full-screen screensaver** (activates after 10s idle): album art with breathing animation, floating particles, pulsing glow rings, aurora color bands
  - Particles: multi-shape (ORB/RING/SPARKLE), multi-layer blur glow, album art palette colors, no pulsing (calm floating), trails
  - Glow rings expand from album art edge on bass hits
  - Aurora: flowing vertical color bands, bass-reactive intensity
  - Beat detection via Visualizer API (`RECORD_AUDIO` permission, runtime request)
  - Bass + highs reactivity (mids ignored) for glow rings and aurora
  - Screensaver controls: D-pad left/right = skip, center = play/pause, Back = exit screensaver only
  - Main content hidden during screensaver (prevents input leak to focused buttons behind overlay)
- **Music stops on app exit on TV** (`onTaskRemoved` stops player)
- **Faster TV startup**: skip Cast SDK init, skip queue restore, defer starred loading
- **Pairing code login**: enter server URL → "Pair with code" → TV shows 6-digit code → user enters on server `/pair` page to authenticate
- **Install via Downloader app**: `zonik:3000/app`
- **TV detection**: `FEATURE_LEANBACK`, `FEATURE_TELEVISION`, `UI_MODE_TYPE_TELEVISION` — checked via `isTvDevice()` / `isTv()`
- **Settings tab** (TV): Sync Library, Upload Logs, Check Update (downloads + installs APK), Disconnect
- `HorizontalPager` causes flash on D-pad — use direct tab rendering on TV instead
- Files: `ui/tv/TvMainScreen.kt`, `ui/tv/ParticleSystem.kt`, `ui/util/TvUtils.kt`

## API Notes
- Subsonic API at `{server}/rest/{endpoint}.view`
- Auth params: `u`, `t` (md5(apiKey+salt)), `s` (random salt), `v=1.16.1`, `c=ZonikApp`, `f=json`
- Streaming: `GET /rest/stream.view?id={trackId}&estimateContentLength=true` (NO `f=json` — returns binary). Server supports Range requests on direct files (206 Partial Content) and returns `Accept-Ranges: none` on transcoded streams. Server caches transcoded output — second play is instant with range support.
- Cover art: `GET /rest/getCoverArt.view?id={coverId}&size={pixels}` (NO `f=json`). Server resizes server-side (Pillow LANCZOS, JPEG q85, max 1200px). Default CoverArt size is 100px for list items; 600px for Now Playing main art. `Cache-Control: public, max-age=604800` (7 days).
- Scrobble: `GET /rest/scrobble.view?id={trackId}&submission=false` for now-playing, `submission=true` (default) for final scrobble at 50% playback.
- Track metadata: `search3` response includes `transcodedSuffix` and `transcodedContentType` for lossless files (server will transcode to MP3).
- Library sync: `search3` with empty query, 500 items per page (Symfonium approach) + `getStarred2` for starred status + `userRating` for flagged-for-deletion status
- Zonik native API: `POST /api/download/search`, `/api/download/trigger`, `GET /api/jobs` (`Accept: application/json` header required, response wrapped in `{"items": [...]}`), `POST /api/tracks/bulk-delete` (request: `{"track_ids": [...]}`, response: `{"deleted": N}`)
- Zonik waveform API: `GET /api/tracks/{id}/waveform` — returns pre-computed waveform data; client falls back to local `MediaExtractor`/`MediaCodec` extraction if unavailable
- Zonik pairing API: TV shows 6-digit code, user enters on server `/pair` page to authenticate TV device
- Zonik log API: `POST /api/logs` (Subsonic auth via query params, request: `{device, app_version, timestamp, logs}`), `GET /api/logs/app` (list, unauthenticated), `GET /api/logs/app/{id}` (detail, unauthenticated)

## Debugging
- `DebugLog` singleton captures last 500 entries with timestamps
- Settings > Debug: "Upload to Server" sends logs to Zonik server (`POST /api/logs` with Subsonic auth) — no extra config needed
- Settings > Debug: "Upload Logs" to private GitHub Gist (requires GitHub PAT with gist scope)
- Settings > Debug: "Copy Logs" to clipboard
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
- `starred` sync uses `getStarred2` as authoritative source — server starred replaces local state during sync
- `search3` response may NOT include `starred` field reliably — always use `getStarred2`
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
- OkHttp HTTP cache (50MB) on primary client enables Cache-Control header support (cover art 7-day cache, stream 24h private)
- Pre-cache downloads may queue on server (concurrent transcode limit: 3) — don't cancel/retry requests that are just waiting; 30s connect timeout handles this
- Server transcode cache means second play of same track+bitrate is instant with full range support — response changes from StreamingResponse to FileResponse between first and subsequent requests
- `transcodedSuffix`/`transcodedContentType` are NOT stored in Room DB — only available from API responses (set to null in `TrackEntity.toDomain()`)
- Android Auto requires Developer Mode + Unknown Sources for sideloaded APKs
- DataStore emits on any field change — don't use `collect` on `isLoggedIn` to trigger sync (causes infinite loop); use `first()` instead
- Room DB uses `fallbackToDestructiveMigration()` — DO NOT remove entities or change DB version without testing on device (caused startup crash on Android 16)
- `MediaController.sendCustomCommand` must be called on main thread — use `Dispatchers.Main` not `Dispatchers.IO`
- `Equalizer` must be created in the same process as ExoPlayer (ZonikMediaService) — communicate via SessionCommand
- SQLite `NOT IN` clause has 999 variable limit — use chunked `deleteByIds` instead of `deleteNotIn` for large lists
- `markedForDeletion` is server-authoritative via `userRating == 1` — synced from `search3` response, not preserved locally
- Cast `MediaRouteButton` crashes without `AppCompatActivity` context — `MainActivity` extends `AppCompatActivity` (not `ComponentActivity`)
- Cast initialization wrapped in try-catch — graceful fallback on devices without Google Play Services
- Cast receiver CSS/images must use jsdelivr CDN URLs (raw GitHub returns wrong MIME types)
- **MainScreen uses `Box` not `Scaffold`** — MiniPlayer and nav bar are manually positioned at bottom; content needs explicit bottom padding for floating elements
- **Album art glow** uses `Modifier.blur()` + `alpha()` — degrades gracefully on API < 31 (blur is a no-op)
- **Now Playing swipe-to-dismiss** uses `detectVerticalDragGestures` with `graphicsLayer` translation — only allows downward drag (`coerceAtLeast(0f)`)
- **Library alpha scroll sidebar** has semi-transparent background + LazyColumn has `end` padding to prevent overlap with track trailing content
- **CoverArtProvider must check network** before making OkHttp calls — returns null if offline to avoid crash and error spam. Error logging throttled to 1 per 30s.
- **Sync writes must be wrapped in `database.withTransaction{}`** — upsert+delete without transaction causes CursorWindow crash when Room Flow re-queries mid-write (seen with 4400+ tracks)
- **Cast track change collector must run on `Dispatchers.Main`** — it calls `setCurrentTrack` → `persistPlaybackState` → `controller.currentPosition` which requires main thread
- **`PlaybackManager._queue` is NOT populated by playback resumption or Android Auto** — only `playTracks()` from UI sets it. `syncQueueFromPlayer()` auto-rebuilds queue from player's media items via Room DB lookup when queue is out of sync (triggered on connect and on track transitions with empty queue).
- **Downloads polling uses adaptive intervals** — 5s when active transfers, 30s when idle, stops after 3 consecutive idle checks. Restarts via `DisposableEffect` when screen re-enters composition.
- **`_ignoreNextAutoTransition` must be cleared on any real track change** — not just AUTO transitions; otherwise subsequent manual track changes can be incorrectly suppressed
- **Cast track change**: use `castManager.getCurrentPosition()` instead of `controller.currentPosition` — thread-safe and avoids main-thread requirement
- **Sync upsertAll overwrites `offlineCached`** — must preserve value from existing DB rows during sync upsert
- **TV: `HorizontalPager` causes flash on D-pad** — use direct tab rendering on TV instead of pager
- **TV: `playTracks` must be called on main thread** — `controller.sendCustomCommand` requirement applies; use `Dispatchers.Main`
- **Queue restore: `playWhenReady` must be set `false` before `prepare()`** when `startPaused` — setting it after `prepare()` may cause a brief audio blip
- **TV: Visualizer API error -3** on some devices (RECORD_AUDIO granted but API not supported) — glow rings/aurora still react, particles just float
- **TV: `sharedAudioSessionId` via companion object** — `sendCustomCommand` deadlocks, `sessionExtras` doesn't sync in time; use static field instead
- **TV: screensaver must hide main content entirely** (not overlay) — Compose focus system delivers clicks to focused buttons behind overlay
- **TV: Back key in `onPreviewKeyEvent`** must consume both ACTION_DOWN and ACTION_UP to prevent BackHandler from also firing
- **TV: build failures don't prevent `git commit`** if APK exists from previous build — always verify BUILD SUCCESSFUL before releasing
