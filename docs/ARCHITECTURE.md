# Zonik Android App — Architecture & Requirements

## 1. Overview

A native Android app for streaming music from a self-hosted Zonik server. Designed for a single user with full Android Auto support.

## 2. Distribution

The app is distributed as a sideloaded APK (not via Google Play Store).

For Android Auto to recognize the app, the user must enable **Developer Mode** in Android Auto settings (tap version 10 times) and toggle **"Unknown sources"** in developer settings. This is a one-time setup.

## 3. Core Requirements

### 3.1 Server Connection
- User configures server URL and credentials on first launch
- Credentials stored securely in EncryptedSharedPreferences
- Connection test on setup (Subsonic `ping` endpoint)
- Support both HTTP and HTTPS
- **Server capability detection:** query `getOpenSubsonicExtensions` on connect to determine which extended features are available; gracefully disable unsupported features

### 3.2 Authentication
- Subsonic token auth: `t = md5(apiKey + salt)`, random `s` per request
- Credentials persist across app restarts
- Graceful handling of auth failures (re-prompt login)

### 3.3 Library Browsing
- **Artists** — alphabetical list with **fast scrollbar / section index** (A-Z sidebar), tap to see albums
- **Albums** — grid/list view, sortable (name, year, recently added)
- **Tracks** — within album context, with track number ordering
- **Genres** — list genres, tap to browse albums/tracks in genre
- **Playlists** — list all playlists, view/play tracks
- **Search** — full-text search across artists, albums, tracks (Subsonic `search3`)
- **Random** — shuffle/random album or track selection
- **Large library performance:** use Paging 3 for paginated loading from API/Room; stable keys on all LazyColumn/LazyRow items; `@Immutable`/`@Stable` annotations on data classes to minimize recomposition
- **Song details view** — accessible from track context menu or Now Playing; shows bitrate, format, sample rate, file size, duration, path

### 3.4 Playback
- Stream via Media3 ExoPlayer
- Background playback via MediaSessionService
- Play queue management (add, remove, reorder, clear)
- **Two distinct playback modes:**
  - **Shuffle** — shuffles the current queue/album/playlist, plays each track once in random order
  - **True Random** — continuously picks random tracks from the entire library with no regard for play history (tracks can repeat); uses Subsonic `getRandomSongs` to fetch batches and auto-refills the queue as it depletes
- Repeat modes (off, all, one)
- Seek within track
- Gapless playback
- **Crossfade** — configurable 1–10s crossfade between tracks; toggle per-context (off for albums, on for playlists/shuffle)
- **ReplayGain** — volume normalization using track or album gain tags; configurable pre-amp adjustment (±20dB); fallback to server-side normalization if tags absent
- **Skip silence** — automatically skip silent sections at start/end of tracks
- **Playback speed** — 0.5x to 3.0x with pitch correction
- Transcoding support — request specific format/bitrate from server
- **Network-aware bitrate** — separate max bitrate settings for Wi-Fi vs cellular; auto-switch on network change; uses Subsonic `maxBitRate` param on `stream` endpoint
- **Stream caching** — ExoPlayer `SimpleCache` + `CacheDataSource` for disk caching of streamed audio; LRU eviction when cache exceeds configured size limit
- MediaSession integration (lock screen controls, notification)
- **Playback resumption after reboot** — implement `onPlaybackResumption()` for system media card on lock screen / quick settings; serve locally-cached metadata since network may not be available at resume time
- **Queue persistence** — full queue (track list, position within track, shuffle state, repeat mode) saved to Room database; survives app kill and device reboot

### 3.5 Android Auto
- Full Android Auto support via MediaBrowserServiceCompat / Media3
- Browse tree limited to **4 root tabs** (Android Auto maximum): Recent, Library, Playlists, Random
- Playback controls (play/pause, next, previous)
- Album art display — placeholder art provided for items without cover to avoid visual holes
- **Content style hints:** `GRID` for albums/artists (image-heavy), `LIST` for tracks/playlists (text-heavy)
- **Voice search:**
  - "Play [artist/album/song]" — parse `extras` bundle for `EXTRA_MEDIA_ARTIST`, `EXTRA_MEDIA_ALBUM`, `EXTRA_MEDIA_TITLE`
  - **Empty query handling** — "OK Google, play music" sends empty string; respond by playing recent or shuffled tracks
  - Register `MEDIA_PLAY_FROM_SEARCH` intent filter in manifest
- Must comply with Android Auto driver-distraction guidelines (no custom UI)
- **Max browse depth:** 4 levels (Auto supports up to 5, keep conservative)
- **Load timeout:** all browse/search results must return within 10 seconds or Auto shows error
- **Force-stop resilience:** browse tree must rebuild from scratch without relying on in-memory state
- Browse tree must work **without Activity being open** — MediaLibraryService runs standalone

### 3.6 Library Sync
- Local Room database mirrors server library metadata for fast browsing
- **Sync triggers:**
  - On app launch (if last sync older than configured interval)
  - Manual pull-to-refresh on any library screen
  - Configurable background interval (off / 15min / 1hr / 6hr / daily)
  - After download completes (auto-refresh to pick up new tracks)
- **Sync strategy:**
  - Incremental: use Subsonic `getAlbumList2` sorted by newest + `getIndexes` with `ifModifiedSince` to detect changes
  - Full: periodic full resync to catch deletions and metadata edits (configurable frequency, default daily)
  - Delta detection: compare server response against local Room data, upsert new/changed, remove deleted
- **What syncs:**
  - Artists, albums, tracks (title, duration, bitrate, format, year, genre, track number)
  - Cover art URLs (Coil re-fetches if URL changes)
  - Playlists and playlist contents
  - Star/favorite state
  - Play queue state (via Subsonic `getPlayQueue`)
- **Sync settings (in Settings screen):**
  - Auto-sync interval (off / 15min / 1hr / 6hr / daily)
  - Sync on Wi-Fi only toggle
  - Last sync timestamp display
  - "Sync Now" manual trigger button
  - "Full Resync" button (clears local cache, re-pulls everything)
- **Sync status:**
  - Subtle indicator in toolbar during sync (spinning icon)
  - Notification of new tracks added since last sync ("12 new tracks added")

### 3.7 Caching
- **Cover art:** Coil disk cache with multiple resolutions (thumbnail for lists, medium for grids, full for Now Playing background)
- **Streamed audio:** ExoPlayer `SimpleCache` with LRU eviction; configurable max size (default 500MB)
- **Configurable total cache size** with breakdown display (art vs audio)
- Optional track download for offline playback (future feature)

### 3.8 User Interactions
- Star/unstar tracks, albums, artists (Subsonic `star`/`unstar`)
- Scrobble currently playing track (Subsonic `scrobble`)
- Rate tracks (Subsonic `setRating`) — rating=1 is used as "flagged for deletion" (synced with Zonik server's Flagged view)
- **Mark for Deletion** — sets `rating=1` on server via `setRating` API; server-authoritative during sync (`userRating == 1` → flagged)
- Save/restore play queue (Subsonic `savePlayQueue`/`getPlayQueue`)
- **Sleep timer** — presets: 15 / 30 / 45 / 60 min, custom duration, "end of current track"; gradual volume fade over last 30 seconds before stopping; timer visible in Now Playing and notification

### 3.9 Download Search & Queue
- Search for music not yet in the library via Zonik's native API (`/api/downloads/search`)
- Zonik uses Soulseek under the hood — the app sends search queries and displays results
- Search results show: track title, artist, format, bitrate, file size, source
- User can queue individual tracks or bulk-select results for download
- "Download" action sends the request to Zonik server (`/api/downloads/download`)
- Download queue view shows: pending, downloading, completed, failed items (via `/api/downloads/active`, `/api/downloads/history`)
- Real-time download progress via Zonik WebSocket (`/api/ws` — `transfer_progress` messages)
- Completed downloads automatically appear in library after server-side scan
- Pull-to-refresh or auto-refresh when downloads complete
- Download history with retry option for failed items

### 3.10 Discovery — Similar Tracks & Remixes
- **Find Similar** — available from Now Playing and track context menus
  - Uses Zonik native API (`/api/discovery/similar-tracks/{id}`) to find similar tracks already in library
  - Option to play similar tracks as a radio station or add to queue
  - Also available via Subsonic `getSimilarSongs2` for artist-based similarity
- **Find Remixes** — available from track context menus
  - Uses Zonik native API (`/api/discovery/remix-suggestions/{id}`) to find remixes
  - Results show which are already in library vs available to download
  - One-tap to queue missing remixes for Soulseek download
- Both features accessible from:
  - Now Playing screen (overflow menu or dedicated buttons)
  - Track long-press context menu anywhere in the app
  - Album detail screen (per-track action)

### 3.11 Last.fm Scrobbling
- Direct Last.fm scrobbling from the app (independent of server-side scrobbling)
- Last.fm OAuth authentication flow (in-app browser / custom tab)
- Scrobble rules: submit when track has played >50% or >4 minutes (per Last.fm spec)
- "Now Playing" update sent when a track starts
- Offline scrobble queue: cache scrobbles when offline, submit when connectivity returns
- Scrobble history viewable in settings
- Toggle scrobbling on/off in settings
- Uses Last.fm API v2 (`track.scrobble`, `track.updateNowPlaying`)

### 3.12 Error Handling & Connectivity
- **Server unreachable:** show offline banner, serve cached library data from Room, disable streaming actions
- **Network drop mid-stream:** ExoPlayer auto-retries with exponential backoff; show "reconnecting" indicator
- **Wi-Fi ↔ cellular transition:** seamless handoff (OkHttp connection pool handles this); respect "sync on Wi-Fi only" setting
- **Auth failure (401):** clear stored session, redirect to login screen with error message
- **Server errors (5xx):** toast/snackbar with retry option, don't crash
- **Timeout handling:** configurable connect/read timeouts (default 15s connect, 30s read)
- **No internet on launch:** app opens normally with cached data, syncs when connectivity returns
- **Connectivity monitoring:** use `ConnectivityManager.NetworkCallback` to reactively update UI and pause/resume sync

### 3.13 Foreground Service & Notifications
- **Playback notification:** auto-generated by Media3 from MediaSession — album art, track info, play/pause/next/previous; do NOT build custom notification manually
- **Custom notification actions:** add shuffle, repeat, favorite/heart via `CommandButton` and `MediaSession.setCustomLayout()`; sleep timer countdown shown in notification subtitle when active
- **Notification channel:** `zonik_playback` channel created on app startup (required Android 8+)
- **Foreground service:** `MediaSessionService` runs as foreground service during playback; stops when playback ends and user dismisses notification
- **Sync notification:** separate `zonik_sync` channel for background sync progress (low priority, silent)
- **Download notification:** separate `zonik_downloads` channel for download progress/completion

### 3.14 Audio Focus & Hardware Controls
- **Audio focus:** request `AUDIOFOCUS_GAIN` on play; handle:
  - `AUDIOFOCUS_LOSS` → pause and release focus
  - `AUDIOFOCUS_LOSS_TRANSIENT` → pause, resume when focus regained (phone calls, navigation)
  - `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` → lower volume to 20% (notifications, brief interruptions)
- **Becoming noisy (headphone unplug):** register `ACTION_AUDIO_BECOMING_NOISY` receiver, pause playback immediately
- **Bluetooth controls:** `MediaSession` handles AVRCP commands automatically (play/pause/next/prev/seek)
- **Wired headset buttons:** handled via `MediaSession` media button receiver
- **Media button long-press:** configurable (default: skip track)

### 3.15 Battery & Background Optimization
- **Wake lock:** `PARTIAL_WAKE_LOCK` held during active playback to prevent CPU sleep
- **Wi-Fi lock:** `WifiManager.WifiLock` held during streaming to prevent Wi-Fi sleep
- **Background sync:** use `WorkManager` with constraints (network available, optionally Wi-Fi only) for periodic library sync
- **Doze mode:** `WorkManager` handles Doze-compatible scheduling; playback foreground service is exempt
- **Battery-efficient sync:** coalesce sync operations, avoid polling — use configurable intervals via `PeriodicWorkRequest`

### 3.16 Track Context Menu
Available on long-press of any track throughout the app:
- **Play** — play immediately, replacing queue
- **Play Next** — insert after currently playing track
- **Add to Queue** — append to end of queue
- **Go to Artist** — navigate to artist detail screen
- **Go to Album** — navigate to album detail screen
- **Star / Unstar** — toggle favorite
- **Rate** — 1–5 star rating
- **Find Similar** — open similar tracks
- **Find Remixes** — open remix suggestions
- **Song Details** — view bitrate, format, sample rate, file size, path
- **Share** — share track info (artist — title) to other apps

### 3.17 Permissions
| Permission | When | Purpose |
|---|---|---|
| `INTERNET` | Always | Server communication |
| `FOREGROUND_SERVICE` | Always | Background playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Always | Media foreground service type (Android 14+) |
| `WAKE_LOCK` | Always | Prevent CPU sleep during playback |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Playback, sync, download notifications |
| `ACCESS_NETWORK_STATE` | Always | Connectivity monitoring |
| `ACCESS_WIFI_STATE` | Always | Wi-Fi lock for streaming |

### 3.18 Accessibility
- **Content descriptions** on all interactive elements: play, pause, skip, seek bar, favorite, shuffle, repeat, queue — descriptive labels (not just "button")
- **Minimum touch targets:** 48dp × 48dp for all interactive elements
- **Seek bar accessibility:** announce current position and duration; TalkBack swipe up/down adjusts in 5–10 second increments
- **Color contrast:** 4.5:1 minimum for normal text, 3:1 for large text and UI components
- **Track change announcements:** `AccessibilityEvent` or `LiveRegion` announces new track name when playback advances
- **Logical focus order:** ensure TalkBack traverses Now Playing controls in sensible order
- **Alternative to gestures:** every swipe action (skip, queue add, dismiss) has an equivalent accessible action via long-press menu or explicit button
- **Predictive back gestures:** support required for Android 14+

### 3.19 Dynamic Theming
- **Album art color extraction** using Palette API for Now Playing screen background
- Material You / Material 3 dynamic color support — theme derives from album art or system wallpaper
- Smooth color transition when track changes
- Respects system dark/light mode

## 4. Technical Architecture

### 4.1 Layers

```
┌─────────────────────────────────────┐
│           UI (Compose)              │
│  Screens, Components, Navigation    │
├─────────────────────────────────────┤
│         ViewModels                  │
│  UI State, User Actions             │
├─────────────────────────────────────┤
│         Repositories                │
│  Data orchestration, caching logic  │
├──────────────┬──────────────────────┤
│  Remote API  │   Local DB (Room)    │
│  (Retrofit)  │   Cached metadata    │
├──────────────┴──────────────────────┤
│     Media3 Playback Service         │
│  ExoPlayer, MediaSession, Auto      │
└─────────────────────────────────────┘
```

### 4.2 Key Components

#### Media Service (`ZonikMediaService`)
- Extends `MediaLibraryService` (only one per app)
- Hosts ExoPlayer with `DefaultHttpDataSource` (auth baked into URLs, not via OkHttp interceptor)
- `onAddMediaItems` resolves URIs from `requestMetadata.mediaUri` (survives Media3 IPC)
- Publishes `MediaLibrarySession` for system integration + Android Auto
- Implements browse tree callback with 4 root tabs
- Handles audio focus, becoming noisy (headphone unplug)
- `WAKE_MODE_NETWORK` for streaming over Wi-Fi

#### Playback Manager (`PlaybackManager`)
- Connects to MediaService via `MediaController`
- Builds stream URLs with baked-in auth params (ExoPlayer doesn't use OkHttp)
- Sets `requestMetadata.mediaUri` so stream URL survives IPC
- Tracks current track by `currentMediaItemIndex` (mediaId lost in IPC)
- Smart bitrate: auto-detects Wi-Fi vs cellular network
- Recently played tracking (last 20 tracks)

#### Subsonic API Client (`SubsonicApi`)
- Retrofit interface for all `/rest` endpoints
- OkHttp interceptor appends auth params to every request
- JSON deserialization handles Subsonic response envelope
- Endpoints return domain models (not raw API models)

#### Zonik Native API Client (`ZonikApi`)
- Retrofit interface for `/api` endpoints
- Used for download search, download queue, and WebSocket progress
- Same OkHttp client, different base path

#### Last.fm Client (`LastFmApi`)
- Retrofit interface for Last.fm API v2
- Methods: `track.scrobble`, `track.updateNowPlaying`, `auth.getSession`
- Requests signed with API secret (md5 signature per Last.fm spec)
- Session key stored in EncryptedSharedPreferences

#### Repositories
- `LibraryRepository` — artists, albums, tracks, genres, search
- `SyncRepository` — library sync logic, incremental/full sync, change detection
- `PlaylistRepository` — playlist CRUD
- `PlaybackRepository` — play queue save/restore, scrobble
- `FavoriteRepository` — star/unstar, ratings
- `DownloadRepository` — Soulseek search, download queue, progress tracking via WebSocket
- `ScrobbleRepository` — Last.fm scrobbling, offline queue, now-playing updates

#### Local Database (`ZonikDatabase`)
- Room DB with entities: Artist, Album, Track, Genre, Playlist, PendingScrobble
- DAOs with Flow-returning queries for reactive UI
- Sync strategy: pull from server, upsert locally, serve from cache

### 4.3 Android Auto Integration

Android Auto requires:
1. `MediaBrowserServiceCompat` (or Media3 equivalent) declared in manifest
2. `automotive_app_desc.xml` declaring media capabilities
3. Browsable content tree with proper `MediaItem` hierarchy
4. `MediaSession` with proper metadata and playback state

Browse tree structure (4 root tabs max):
```
Root
├── Recent (tab 1)
│   ├── Recently Added albums
│   └── Continue Listening
├── Library (tab 2)
│   ├── Artists → Albums → Tracks
│   ├── Albums → Tracks
│   └── Genres → Albums → Tracks
├── Playlists (tab 3)
│   ├── Starred
│   └── [User Playlists] → Tracks
└── Mix (tab 4)
    ├── Shuffle Mix (plays each once)
    └── True Random (endless)
```

### 4.4 Navigation (Compose)

```
LoginScreen (first launch / no credentials)
    ↓
MainScreen (bottom nav)
├── HomeTab — recently added, shuffle mix, true random, continue listening
├── LibraryTab — artists / albums / genres sub-navigation
├── SearchTab — search bar + results (library search)
├── DownloadTab — search for new music + download queue
├── PlaylistsTab — playlist list
└── SettingsTab — server config, cache, playback prefs

NowPlayingScreen — full-screen player (slide up from mini player)
AlbumDetailScreen — track list for an album
ArtistDetailScreen — albums by artist
```

## 5. Dependencies

| Library | Purpose |
|---------|---------|
| AndroidX Media3 (ExoPlayer, Session, UI) | Playback, MediaSession, Android Auto |
| Retrofit + OkHttp | HTTP client |
| Kotlinx Serialization | JSON parsing |
| Room | Local database |
| Paging 3 | Paginated loading for large libraries |
| Hilt | Dependency injection |
| Coil | Image loading + caching |
| Compose Navigation | Screen routing |
| Compose Material 3 | UI components + dynamic color |
| AndroidX Palette | Album art color extraction |
| DataStore | Settings/preferences |
| WorkManager | Background sync scheduling |
| AndroidX Browser (CustomTabs) | Last.fm OAuth flow |

## 6. Screens — UI Summary

### Login / Setup
- Server URL input
- Username + API key input
- "Test Connection" button
- Error display

### Home
- "Recently Added" horizontal album carousel
- "Shuffle Mix" quick-play button (shuffled selection, plays each once)
- "True Random" quick-play button (endless random from entire library)
- "Continue Listening" (restored play queue)
- Mini player bar at bottom (persistent across tabs)

### Library
- Tab row: Artists | Albums | Genres
- Artists: alphabetical list with fast-scroll index
- Albums: grid with cover art, title, artist, year
- Genres: chip list or vertical list

### Album Detail
- Album art (large), title, artist, year
- Track list with number, title, duration
- Play all / shuffle buttons
- Star button

### Now Playing
- Full-screen album art with **dynamic color background** (Palette API extraction)
- Track title, artist, album
- Seek bar with elapsed/remaining time
- Controls: shuffle, true random, previous, play/pause, next, repeat
- Star button, queue button, sleep timer button
- **Song details** accessible via info icon (bitrate, format, sample rate, file size)
- **Playback speed** control
- Overflow menu: Find Similar, Find Remixes, Go to Artist, Go to Album, Share
- **Mini player** on all other screens — swipe up to expand; shows art, title, artist, play/pause, next

### Search
- Search bar with debounced input
- Results grouped: Artists, Albums, Tracks

### Downloads
- **Search tab**: search bar to find music on Soulseek via Zonik
- Results list: title, artist, format badge (FLAC/MP3/etc), bitrate, size
- Long-press or checkbox for multi-select
- "Download" FAB or button to queue selected results
- **Queue tab**: active/pending/completed/failed downloads
- Progress bars for active downloads (WebSocket real-time updates)
- Swipe to retry failed, tap completed to browse in library
- Badge on bottom nav tab showing active download count

### Playlists
- List of playlists with track count
- Tap to view tracks, play all

### Settings
- **Server:** URL (editable), re-authenticate, server capabilities display
- **Sync:** auto-sync interval, Wi-Fi only toggle, last sync time, Sync Now / Full Resync buttons
- **Playback:** max bitrate Wi-Fi, max bitrate cellular, preferred transcode format, crossfade duration, crossfade context toggle (albums vs playlists), ReplayGain mode (track/album/off) + pre-amp, skip silence toggle, playback speed default
- **Cache:** total size display with breakdown (art/audio), max cache size slider, clear cache (art only / audio only / all)
- **Last.fm:** connect/disconnect account, scrobbling on/off toggle, pending scrobble count
- **Theme:** system / light / dark
- **About:** version, server info, licenses

## 7. Implemented Features (v0.2.4)
- Library sync via search3 empty query (Symfonium approach, ~2s for 1724 tracks)
- FLAC/MP3 streaming with ExoPlayer + DefaultHttpDataSource
- Smart bitrate switching (Wi-Fi vs cellular)
- Shuffle Mix and True Random playback modes
- Symfonium-style Now Playing: blurred album art background, Palette-based adaptive colors, accent-colored seek bar and play button
- Instant Now Playing on track tap (250ms slide-up, shows before buffering completes)
- Mini player with thin progress bar, cover art, controls (200ms position polling)
- Cover art everywhere (Coil + auth-aware ImageLoader + 250MB disk cache)
- Soulseek download search/trigger/cancel via Zonik native API (3 tabs: Search/Active/History)
- Self-update from GitHub releases with download progress
- Debug log upload to private GitHub Gists (Settings > Debug)
- Custom dark theme (deep dark #0A0A0F + purple #7C4DFF / teal #03DAC6 accents)
- Track-centric library (Tracks tab first, 5 tabs: Tracks/Albums/Artists/Genres/Playlists)
- Connection verification on login (ping + auth test with clear error messages)
- Android Auto: MediaLibraryService with 4-tab browse tree, voice search
- Reusable TrackListItem with format badge, context menu, currently-playing highlight

## 8. Future Features
- Offline downloads (pin albums/playlists for offline playback)
- Zonik native API features (AI playlists, vibe search, discovery)
- Lyrics display (OpenSubsonic `getLyricsBySongId`)
- Equalizer (system EQ integration or custom multi-band)
- Chromecast / DLNA casting
- Wear OS companion
- Queue screen with drag-to-reorder
- Smart/dynamic playlists (filter by genre, year, rating, play count)
- Listening stats (top artists, genres, hours played)
- Playlist import/export (M3U, XSPF)
- Sleep timer with gradual volume fade
- Collapsing toolbar on Album/Artist detail screens
- Fast scroll sidebar (A-Z) for Artists
- Genre/Playlist detail navigation (tap to browse tracks)
- Last.fm scrobbling integration
- Crossfade between tracks
- ReplayGain volume normalization
