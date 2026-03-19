<p align="center">
  <img src="cast/icon.png" width="128" height="128" alt="Zonik Logo">
</p>

<h1 align="center">Zonik Mobile</h1>

<p align="center">
  A native Android music player for <a href="https://github.com/Pr0zak/Zonik">Zonik</a> self-hosted music servers.<br>
  Streams your library over OpenSubsonic with Android Auto and Chromecast support.
</p>

## Features

- **Streaming playback** with smart bitrate (Wi-Fi/cellular), adaptive degradation on slow connections
- **5-band equalizer** with 10 presets, custom band levels, and system EQ launch
- **Android Auto** with configurable browse tabs (Mix, Recent, Library, Playlists), star/delete buttons, voice search
- **Chromecast** support via Google Cast SDK (Styled Media Receiver)
- **Audio caching** with configurable size and read-ahead pre-caching for offline-like playback
- **Connection resilience** — automatic retry with exponential backoff, network reconnect recovery, larger buffers
- **Library sync** via OpenSubsonic `search3` API with starred track sync via `getStarred2` and flagged-for-deletion sync via `userRating`
- **Mark for deletion** synced with Zonik server (rating=1 = flagged) — tracks appear in server's "Flagged" view for review/bulk delete
- **Now Playing** with album art, Palette colors, queue with alpha scroll, star/delete actions, keep-screen-on
- **Stats page** — format/bitrate/genre/decade distributions, most played albums, top artists
- **Scrobbling** via Subsonic API (server forwards to Last.fm)
- **Self-update** from GitHub releases
- **Debug logging** with upload to private GitHub Gists

## Screenshots

*Coming soon*

## Requirements

- Android 8.0+ (API 26)
- A running [Zonik](https://github.com/Pr0zak/Zonik) server

## Install

Download the latest APK from [Releases](https://github.com/Pr0zak/Zonik-mobile/releases) and sideload it.

For **Android Auto**: enable Developer Mode (tap version 10x in Android Auto settings), then enable "Unknown sources" in developer settings.

## Build

```bash
export JAVA_HOME=$HOME/tools/jdk-17.0.12
export ANDROID_HOME=$HOME/tools/android-sdk
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- AndroidX Media3 (ExoPlayer) + MediaLibraryService
- SimpleCache + CacheDataSource for audio caching
- Retrofit + OkHttp + Kotlinx Serialization
- Room + Paging 3
- Hilt (DI), Coil (images), WorkManager
- Google Cast SDK + AndroidX MediaRouter

## License

Private — for personal use only.
