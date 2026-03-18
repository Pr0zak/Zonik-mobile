# Zonik Mobile

A native Android music player for [Zonik](https://github.com/Pr0zak/Zonik) self-hosted music servers. Streams your library over OpenSubsonic with Android Auto and Chromecast support.

## Features

- **Streaming playback** with smart bitrate (Wi-Fi/cellular), adaptive degradation on slow connections
- **Android Auto** with configurable browse tabs (Mix, Recent, Library, Playlists), star/delete buttons, voice search
- **Chromecast** support via Google Cast SDK (Styled Media Receiver)
- **Audio caching** with configurable size and read-ahead pre-caching for offline-like playback
- **Connection resilience** — automatic retry with exponential backoff, network reconnect recovery, larger buffers
- **Library sync** via OpenSubsonic `search3` API with starred track sync via `getStarred2`
- **Now Playing** with album art, Palette colors, blurred background, queue, star/delete actions, keep-screen-on
- **Stats page** — format/bitrate/genre/decade distributions, most played albums, top artists
- **Last.fm** scrobbling
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
