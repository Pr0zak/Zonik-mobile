package com.zonik.app.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zonik.app.R
import com.zonik.app.data.db.ZonikDatabase
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.model.Genre
import com.zonik.app.model.Playlist
import com.zonik.app.model.Track
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import com.zonik.app.util.md5
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class ZonikMediaService : MediaLibraryService() {

    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var database: ZonikDatabase
    @Inject lateinit var simpleCache: SimpleCache
    @Inject lateinit var offlineCacheManager: OfflineCacheManager

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var equalizer: android.media.audiofx.Equalizer? = null
    private var lastEqEnabled: Boolean? = null
    private var lastEqPreset: Int? = null
    private var lastEqBandLevels: String? = null
    private val preCacheScope = CoroutineScope(Dispatchers.IO)
    private var preCacheJob: Job? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private val preCachingInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var isPlayerBuffering = false
    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val starredTrackIds = mutableSetOf<String>()
    private val markedForDeletionIds = mutableSetOf<String>()
    private val toggleStarCommand = SessionCommand(ACTION_TOGGLE_STAR, Bundle.EMPTY)
    private val toggleDeleteCommand = SessionCommand(ACTION_TOGGLE_DELETE, Bundle.EMPTY)
    private val playTracksCommand = SessionCommand(ACTION_PLAY_TRACKS, Bundle.EMPTY)
    private val setEqCommand = SessionCommand(ACTION_SET_EQ, Bundle.EMPTY)
    private val getAudioSessionCommand = SessionCommand(ACTION_GET_AUDIO_SESSION, Bundle.EMPTY)
    private val toggleShuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val startRadioCommand = SessionCommand(ACTION_START_RADIO, Bundle.EMPTY)

    companion object {
        // Shared audio session ID (same process, no IPC needed)
        @Volatile var sharedAudioSessionId: Int = 0

        // Browse tree node IDs
        private const val ROOT_ID = "root"
        private const val RECENT_ID = "recent"
        private const val LIBRARY_ID = "library"
        private const val PLAYLISTS_ID = "playlists"
        private const val MIX_ID = "mix"
        private const val ARTISTS_ID = "artists"
        private const val ALBUMS_ID = "albums"
        private const val GENRES_ID = "genres"
        private const val SHUFFLE_MIX_ID = "shuffle_mix"
        private const val NEWLY_ADDED_ID = "newly_added"
        private const val FAVORITES_ID = "favorites"
        private const val NON_FAVORITES_ID = "non_favorites"

        // Custom session commands
        private const val ACTION_TOGGLE_STAR = "com.zonik.app.TOGGLE_STAR"
        private const val ACTION_TOGGLE_DELETE = "com.zonik.app.TOGGLE_DELETE"
        private const val ACTION_PLAY_TRACKS = "com.zonik.app.PLAY_TRACKS"
        private const val ACTION_SET_EQ = "com.zonik.app.SET_EQ"
        private const val ACTION_GET_AUDIO_SESSION = "com.zonik.app.GET_AUDIO_SESSION"
        private const val ACTION_TOGGLE_SHUFFLE = "com.zonik.app.TOGGLE_SHUFFLE"
        private const val ACTION_START_RADIO = "com.zonik.app.START_RADIO"
        private const val EXTRA_TRACK_IDS = "track_ids"
        private const val EXTRA_START_INDEX = "start_index"
        private const val EXTRA_EQ_ENABLED = "eq_enabled"
        private const val EXTRA_EQ_PRESET = "eq_preset"
        private const val EXTRA_EQ_BAND_LEVELS = "eq_band_levels"

        // Prefixes for dynamic node IDs
        private const val ARTIST_PREFIX = "artist:"
        private const val ALBUM_PREFIX = "album:"
        private const val GENRE_PREFIX = "genre:"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val TRACK_PREFIX = "track:"

        // Content style extras keys (Media3 / Android Auto)
        private const val CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_SUPPORTED =
            "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
        private const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
    }

    override fun onCreate() {
        super.onCreate()

        com.zonik.app.data.DebugLog.d("MediaService", "onCreate — setting up ExoPlayer with OkHttpDataSource (clean client)")
        // Use a clean OkHttpClient without auth interceptor — auth is baked into URLs
        val streamClient = okhttp3.OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(5, 30, java.util.concurrent.TimeUnit.SECONDS))
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 30s for transcode queue backpressure
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val rangeHeader = request.header("Range") ?: "none"
                com.zonik.app.data.DebugLog.d("MediaService", "ExoPlayer fetching: ${url.take(100)}... Range=$rangeHeader")
                val response = chain.proceed(request)
                val acceptRanges = response.header("Accept-Ranges") ?: "unknown"
                val contentRange = response.header("Content-Range")
                com.zonik.app.data.DebugLog.d("MediaService", "ExoPlayer response: ${response.code} ${response.header("Content-Type")} len=${response.header("Content-Length") ?: "chunked"} accept-ranges=$acceptRanges${if (contentRange != null) " range=$contentRange" else ""}")
                response
            }
            .build()
        val upstreamFactory = OkHttpDataSource.Factory(streamClient)
        val cacheKeyFactory = androidx.media3.datasource.cache.CacheKeyFactory { dataSpec ->
            dataSpec.uri.getQueryParameter("id") ?: dataSpec.key ?: dataSpec.uri.toString()
        }
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setCacheKeyFactory(cacheKeyFactory)
        cacheDataSourceFactory = dataSourceFactory

        // Wrap with offline-aware factory: check offline dir first, then streaming cache
        val offlineAwareFactory = androidx.media3.datasource.DataSource.Factory {
            object : androidx.media3.datasource.DataSource {
                private var activeSource: androidx.media3.datasource.DataSource? = null
                private var fileSource: androidx.media3.datasource.FileDataSource? = null
                private var cacheSource: androidx.media3.datasource.DataSource? = null

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val trackId = dataSpec.uri.getQueryParameter("id")
                    if (trackId != null) {
                        val offlineFile = offlineCacheManager.getOfflineFile(trackId)
                        if (offlineFile != null) {
                            val fileDs = androidx.media3.datasource.FileDataSource()
                            val fileSpec = dataSpec.buildUpon()
                                .setUri(android.net.Uri.fromFile(offlineFile))
                                .build()
                            fileSource = fileDs
                            activeSource = fileDs
                            return fileDs.open(fileSpec)
                        }
                    }
                    val ds = dataSourceFactory.createDataSource()
                    cacheSource = ds
                    activeSource = ds
                    return ds.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeSource?.read(buffer, offset, length) ?: -1
                }

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    // No-op — individual sources handle their own listeners
                }

                override fun getUri(): android.net.Uri? = activeSource?.uri

                override fun close() {
                    activeSource?.close()
                    activeSource = null
                    fileSource = null
                    cacheSource = null
                }
            }
        }

        // Buffer config tuned for low-latency transcoding server
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,   // min buffer: 15s (reduced from 30s — server now uses low-latency transcoding)
                120_000,  // max buffer: 2 min
                1_500,    // buffer for playback: 1.5s (faster start with server flush_packets)
                5_000     // buffer for playback after rebuffer: 5s (reduced — pre-cache now yields to playback)
            )
            .build()

        // Aggressive retry policy for network errors (tunnels, dead zones)
        val resilientErrorPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val error = loadErrorInfo.exception
                // Give up on HTTP 4xx (auth, not found)
                if (error is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    if (error.responseCode in 400..499) return C.TIME_UNSET
                }
                // Retry IO errors with exponential backoff up to 16s, max 10 retries
                if (error is java.io.IOException && loadErrorInfo.errorCount <= 10) {
                    val delay = minOf(1000L * (1L shl (loadErrorInfo.errorCount - 1)), 16_000L)
                    com.zonik.app.data.DebugLog.d("MediaService", "Retry ${loadErrorInfo.errorCount}/10 in ${delay}ms: ${error.message}")
                    return delay
                }
                return C.TIME_UNSET
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(offlineAwareFactory)
                    .setLoadErrorHandlingPolicy(resilientErrorPolicy)
            )
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Share audio session ID for Visualizer (same process)
        sharedAudioSessionId = player.audioSessionId
        com.zonik.app.data.DebugLog.d("MediaService", "Audio session ID: ${player.audioSessionId}")

        // Network connectivity callback for auto-resume after connection loss
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                mainHandler.post {
                    com.zonik.app.data.DebugLog.d("MediaService", "Network available — checking player state")
                    if (player.playbackState == androidx.media3.common.Player.STATE_IDLE && player.playerError != null) {
                        com.zonik.app.data.DebugLog.d("MediaService", "Auto-resuming after network reconnect")
                        player.prepare()
                    }
                }
            }

            override fun onLost(network: android.net.Network) {
                com.zonik.app.data.DebugLog.d("MediaService", "Network lost")
            }
        }
        connectivityManager = cm
        networkCallback = cb
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(networkRequest, cb)

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                com.zonik.app.data.DebugLog.e("MediaService", "ExoPlayer error: ${error.errorCodeName} - ${error.message}")
                com.zonik.app.data.DebugLog.e("MediaService", "Cause: ${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")
                error.cause?.cause?.let {
                    com.zonik.app.data.DebugLog.e("MediaService", "Root cause: ${it.javaClass.simpleName}: ${it.message}")
                }
                // Auto-retry on IO/network errors
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                    com.zonik.app.data.DebugLog.d("MediaService", "Auto-recovering from IO error at index=${player.currentMediaItemIndex} pos=${player.currentPosition}")
                    player.prepare()
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Update custom buttons for the new track
                val trackId = mediaItem?.mediaId?.removePrefix(TRACK_PREFIX) ?: ""
                mediaLibrarySession?.setCustomLayout(buildCustomLayout(trackId))
                // Pre-cache upcoming tracks (skip during playlist setup)
                if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    || reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    preCacheUpcoming(player)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                    androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "READY"
                    androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                com.zonik.app.data.DebugLog.d("MediaService", "ExoPlayer state: $state")
                isPlayerBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                // Cancel pre-caching when player is buffering to free bandwidth
                if (isPlayerBuffering) {
                    preCacheJob?.cancel()
                }
            }
        })

        val callback = BrowseTreeCallback()

        val sessionActivityIntent = android.content.Intent(this, com.zonik.app.MainActivity::class.java).apply {
            putExtra("SHOW_NOW_PLAYING", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Pre-populate starred and marked-for-deletion track IDs from DB
        // On TV, defer to background to speed up startup (only needed for Android Auto custom buttons)
        val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            || packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
        if (isTv) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                database.trackDao().getStarred().forEach { starredTrackIds.add(it.id) }
                database.trackDao().getMarkedForDeletionIds().forEach { markedForDeletionIds.add(it) }
                com.zonik.app.data.DebugLog.d("MediaService", "Loaded ${starredTrackIds.size} starred, ${markedForDeletionIds.size} marked for deletion (deferred)")
            }
        } else {
            runBlocking {
                database.trackDao().getStarred().forEach { starredTrackIds.add(it.id) }
                database.trackDao().getMarkedForDeletionIds().forEach { markedForDeletionIds.add(it) }
            }
            com.zonik.app.data.DebugLog.d("MediaService", "Loaded ${starredTrackIds.size} starred, ${markedForDeletionIds.size} marked for deletion")
        }

        // Restore equalizer settings
        try {
            val eqEnabled = runBlocking { settingsRepository.eqEnabled.first() }
            val eqPreset = runBlocking { settingsRepository.eqPreset.first() }
            val eqBandLevels = runBlocking { settingsRepository.eqBandLevels.first() }
            applyEqualizer(player.audioSessionId, eqEnabled, eqPreset, eqBandLevels)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("MediaService", "EQ init failed: ${e.message}")
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(pendingIntent)
            .setSessionExtras(Bundle().apply {
                putInt("audio_session_id", player.audioSessionId)
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        savePlaybackState(player)
        // On TV, always stop when app exits (no background playback)
        val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            || packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
        if (isTv || player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePlaybackState(mediaLibrarySession?.player)
        // Unregister network callback
        networkCallback?.let { cb ->
            try { connectivityManager?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        networkCallback = null
        preCacheJob?.cancel()
        equalizer?.release()
        equalizer = null
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    private fun savePlaybackState(player: androidx.media3.common.Player?) {
        if (player == null || player.mediaItemCount == 0) return
        try {
            val trackIds = (0 until player.mediaItemCount).map { i ->
                player.getMediaItemAt(i).mediaId.removePrefix(TRACK_PREFIX)
            }
            val index = player.currentMediaItemIndex
            val position = player.currentPosition.coerceAtLeast(0L)
            runBlocking {
                settingsRepository.savePlaybackState(trackIds, index, position)
            }
            com.zonik.app.data.DebugLog.d("MediaService", "Saved playback state: ${trackIds.size} tracks, index=$index, pos=${position}ms")
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("MediaService", "Failed to save playback state: ${e.message}")
        }
    }

    // -- Helpers --

    private fun getServerUrl(): String? {
        return runBlocking {
            settingsRepository.serverConfig.firstOrNull()?.url?.trimEnd('/')
        }
    }

    private fun coverArtUri(coverArtId: String?): Uri? {
        if (coverArtId == null) return null
        // Use ContentProvider URI so Android Auto and other external processes
        // can fetch artwork without needing cleartext HTTP access
        return com.zonik.app.data.CoverArtProvider.buildUri(coverArtId)
    }

    private fun buildStreamUrlForTrack(trackId: String): String {
        val config = runBlocking { settingsRepository.serverConfig.first() }
            ?: return "http://localhost/rest/stream.view?id=$trackId"
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        // Apply smart bitrate based on network type
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val bitrate = runBlocking {
            if (isWifi) settingsRepository.wifiBitrate.first()
            else settingsRepository.cellularBitrate.first()
        }
        val bitrateParam = if (bitrate > 0) "&maxBitRate=$bitrate" else ""
        return "${config.url.trimEnd('/')}/rest/stream.view?id=$trackId${bitrateParam}&estimateContentLength=true&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun buildAuthenticatedUrl(baseUrl: String): String {
        val config = runBlocking {
            settingsRepository.serverConfig.first()
        } ?: return baseUrl
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        val separator = if (baseUrl.contains('?')) '&' else '?'
        return "${baseUrl}${separator}u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun applyEqualizer(audioSessionId: Int, enabled: Boolean, preset: Int, bandLevelsStr: String?) {
        // Skip if settings unchanged
        if (enabled == lastEqEnabled && preset == lastEqPreset && bandLevelsStr == lastEqBandLevels && equalizer != null) return
        lastEqEnabled = enabled
        lastEqPreset = preset
        lastEqBandLevels = bandLevelsStr
        try {
            if (equalizer == null) {
                equalizer = android.media.audiofx.Equalizer(0, audioSessionId)
            }
            val eq = equalizer ?: return
            if (!enabled) {
                eq.enabled = false
                return
            }
            if (bandLevelsStr != null) {
                // Custom band levels
                val levels = bandLevelsStr.split(",").mapNotNull { it.toShortOrNull() }
                for (i in levels.indices) {
                    if (i < eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), levels[i])
                    }
                }
            } else if (preset >= 0 && preset < eq.numberOfPresets) {
                eq.usePreset(preset.toShort())
            }
            eq.enabled = true
            com.zonik.app.data.DebugLog.d("MediaService", "EQ applied: enabled=$enabled preset=$preset custom=${bandLevelsStr != null}")
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("MediaService", "EQ failed: ${e.message}")
        }
    }

    private fun gridExtras(): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    }

    private fun listExtras(): Bundle = Bundle().apply {
        putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
    }

    private fun buildBrowsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        extras: Bundle? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        if (subtitle != null) metadata.setSubtitle(subtitle)
        if (artworkUri != null) metadata.setArtworkUri(artworkUri)
        if (extras != null) metadata.setExtras(extras)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildPlayableItem(
        id: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        trackNumber: Int? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (artist != null) metadata.setArtist(artist)
        if (album != null) metadata.setAlbumTitle(album)
        if (subtitle != null) metadata.setSubtitle(subtitle)
        if (artworkUri != null) metadata.setArtworkUri(artworkUri)
        if (trackNumber != null) metadata.setTrackNumber(trackNumber)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildStarButton(isStarred: Boolean): CommandButton {
        val icon = if (isStarred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        val iconRes = if (isStarred) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        return CommandButton.Builder(icon)
            .setDisplayName(if (isStarred) "Unstar" else "Star")
            .setIconResId(iconRes)
            .setSessionCommand(toggleStarCommand)
            .setEnabled(true)
            .build()
    }

    private fun buildDeleteButton(isMarked: Boolean): CommandButton {
        val iconRes = if (isMarked) R.drawable.ic_delete_filled else R.drawable.ic_delete_outline
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(if (isMarked) "Unmark" else "Mark for Deletion")
            .setIconResId(iconRes)
            .setSessionCommand(toggleDeleteCommand)
            .setEnabled(true)
            .build()
    }

    private fun buildShuffleButton(isShuffled: Boolean): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(if (isShuffled) "Shuffle On" else "Shuffle Off")
            .setIconResId(R.drawable.ic_shuffle)
            .setSessionCommand(toggleShuffleCommand)
            .setEnabled(true)
            .build()
    }

    private fun buildRadioButton(): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Start Radio")
            .setIconResId(R.drawable.ic_radio)
            .setSessionCommand(startRadioCommand)
            .setEnabled(true)
            .build()
    }

    private fun buildCustomLayout(trackId: String): List<CommandButton> {
        val isStarred = trackId.isNotBlank() && trackId in starredTrackIds
        val isMarked = trackId.isNotBlank() && trackId in markedForDeletionIds
        val isShuffled = mediaLibrarySession?.player?.shuffleModeEnabled ?: false
        return listOf(buildStarButton(isStarred), buildDeleteButton(isMarked), buildShuffleButton(isShuffled), buildRadioButton())
    }

    /**
     * Builds a fully-resolved MediaItem for a Track with stream URL, artwork, and metadata.
     * Used by onAddMediaItems when resolving browse-tree or mix items for playback.
     */
    private fun buildFullMediaItem(track: Track): MediaItem {
        val streamUrl = buildStreamUrlForTrack(track.id)
        val artUri = coverArtUri(track.coverArt)
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(streamUrl))
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(artUri)
                    .setTrackNumber(track.track)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun albumToMediaItem(album: Album): MediaItem =
        buildBrowsableItem(
            id = "$ALBUM_PREFIX${album.id}",
            title = album.name,
            subtitle = album.artist,
            artworkUri = coverArtUri(album.coverArt),
            extras = gridExtras()
        )

    private fun artistToMediaItem(artist: Artist): MediaItem =
        buildBrowsableItem(
            id = "$ARTIST_PREFIX${artist.id}",
            title = artist.name,
            subtitle = "${artist.albumCount} albums",
            artworkUri = coverArtUri(artist.coverArt)
        )

    private fun genreToMediaItem(genre: Genre): MediaItem =
        buildBrowsableItem(
            id = "$GENRE_PREFIX${genre.name}",
            title = genre.name,
            subtitle = "${genre.songCount} songs"
        )

    private fun playlistToMediaItem(playlist: Playlist): MediaItem =
        buildBrowsableItem(
            id = "$PLAYLIST_PREFIX${playlist.id}",
            title = playlist.name,
            subtitle = "${playlist.songCount} songs",
            artworkUri = coverArtUri(playlist.coverArt)
        )

    private fun trackToMediaItem(track: Track): MediaItem =
        buildPlayableItem(
            id = "$TRACK_PREFIX${track.id}",
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUri = coverArtUri(track.coverArt),
            trackNumber = track.track
        )

    // -- Callback --

    private inner class BrowseTreeCallback : MediaLibrarySession.Callback {

        /**
         * Playback resumption: when Android Auto starts and wants to resume music.
         * Restores the last queue, track, and position. Falls back to shuffle if no saved state.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            com.zonik.app.data.DebugLog.d("MediaService", "Playback resumption requested")
            return try {
                // Try to restore saved queue
                val savedTrackIds = runBlocking { settingsRepository.lastQueueTrackIds.first() }
                val savedIndex = runBlocking { settingsRepository.lastQueueIndex.first() }
                val savedPosition = runBlocking { settingsRepository.lastQueuePositionMs.first() }

                if (savedTrackIds.isNotEmpty()) {
                    // Batch lookup, then reorder to match saved queue order
                    val tracks = runBlocking {
                        val entities = database.trackDao().getByIds(savedTrackIds)
                        val entityMap = entities.associateBy { it.id }
                        savedTrackIds.mapNotNull { id -> entityMap[id]?.toDomain() }
                    }
                    if (tracks.isNotEmpty()) {
                        val startIndex = savedIndex.coerceIn(0, tracks.size - 1)
                        com.zonik.app.data.DebugLog.d("MediaService", "Resuming: ${tracks.size} tracks, index=$startIndex, position=${savedPosition}ms")
                        val mediaItems = tracks.map { buildFullMediaItem(it) }
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, savedPosition)
                        )
                    }
                }

                // Fallback: shuffle mix
                com.zonik.app.data.DebugLog.d("MediaService", "No saved queue — falling back to shuffle")
                val tracks = runBlocking { libraryRepository.getRandomSongs(count = 100) }
                if (tracks.isNotEmpty()) {
                    val mediaItems = tracks.map { buildFullMediaItem(it) }
                    Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(mediaItems, 0, 0L)
                    )
                } else {
                    Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                    )
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("MediaService", "Playback resumption failed: ${e.message}")
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }
        }

        /**
         * Handle setMediaItems calls — intercept Mix items from Android Auto.
         * Android Auto uses playFromMediaId() which maps to onSetMediaItems with INDEX_UNSET.
         * We must resolve the tracks AND set startIndex=0 explicitly.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Handle single-item mix lookups (Shuffle, Favorites, etc.)
            if (mediaItems.size == 1) {
                val id = mediaItems[0].mediaId
                val mixTracks = resolveMixTracks(id)
                if (mixTracks != null) {
                    com.zonik.app.data.DebugLog.d("MediaService", "onSetMediaItems: resolving mix $id")
                    if (mixTracks.isEmpty()) {
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(mutableListOf(), 0, 0L)
                        )
                    }
                    val resolved = mixTracks.map { buildFullMediaItem(it) }
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(resolved, 0, 0L)
                    )
                }
            }

            // Resolve ALL items here to avoid per-item onAddMediaItems IPC reordering.
            // Reconstruct URIs from requestMetadata.mediaUri (survives IPC).
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri != null) {
                    item.buildUpon().setUri(uri).build()
                } else {
                    val rawId = item.mediaId
                    val trackId = if (rawId.startsWith(TRACK_PREFIX)) rawId.removePrefix(TRACK_PREFIX) else rawId
                    val track = runBlocking { database.trackDao().getById(trackId)?.toDomain() }
                    if (track != null) {
                        buildFullMediaItem(track)
                    } else {
                        item.buildUpon().setUri(buildStreamUrlForTrack(trackId)).build()
                    }
                }
            }
            val actualStart = if (startIndex >= 0 && startIndex < resolved.size) startIndex else 0
            com.zonik.app.data.DebugLog.d("MediaService", "onSetMediaItems: resolved ${resolved.size} items, startIndex=$actualStart")
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, actualStart, startPositionMs.coerceAtLeast(0L))
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            if (mediaItems.size == 1) {
                val id = mediaItems[0].mediaId
                val mixTracks = resolveMixTracks(id)
                if (mixTracks != null) {
                    com.zonik.app.data.DebugLog.d("MediaService", "onAddMediaItems: resolving $id")
                    return Futures.immediateFuture(mixTracks.map { buildFullMediaItem(it) }.toMutableList())
                }
            }

            // Media3 strips localConfiguration (URI) during controller→service IPC.
            // Reconstruct the stream URL from requestMetadata.mediaUri (set by PlaybackManager).
            // If that's also null, the mediaId is a track ID — build the stream URL from it.
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                    ?: item.localConfiguration?.uri
                if (uri != null) {
                    item.buildUpon().setUri(uri).build()
                } else {
                    // Strip track: prefix if present (browse tree items use "track:{id}")
                    val rawId = item.mediaId
                    val trackId = if (rawId.startsWith(TRACK_PREFIX)) rawId.removePrefix(TRACK_PREFIX) else rawId
                    // Look up track in DB for full metadata (artwork, etc.)
                    val track = runBlocking { database.trackDao().getById(trackId)?.toDomain() }
                    if (track != null) {
                        com.zonik.app.data.DebugLog.d("MediaService", "onAddMediaItems: resolved track '${track.title}' from DB")
                        buildFullMediaItem(track)
                    } else {
                        val streamUrl = buildStreamUrlForTrack(trackId)
                        com.zonik.app.data.DebugLog.d("MediaService", "onAddMediaItems: built stream URL for $trackId (no DB match)")
                        item.buildUpon().setUri(streamUrl).build()
                    }
                }
            }.toMutableList()
            com.zonik.app.data.DebugLog.d("MediaService", "onAddMediaItems: ${resolved.size} items, first URI: ${resolved.firstOrNull()?.localConfiguration?.uri}")
            return Futures.immediateFuture(resolved)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            }
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Zonik")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(extras)
                        .build()
                )
                .build()
            val resultParams = LibraryParams.Builder().setExtras(extras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, resultParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return try {
                val children = resolveChildren(parentId, page, pageSize)
                Futures.immediateFuture(LibraryResult.ofItemList(children, params))
            } catch (e: Exception) {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO))
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return try {
                val item = resolveItem(mediaId)
                if (item != null) {
                    Futures.immediateFuture(LibraryResult.ofItem(item, null))
                } else {
                    Futures.immediateFuture(
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    )
                }
            } catch (e: Exception) {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO))
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            try {
                val lowerQuery = query.lowercase().trim()

                // Empty query → shuffle mix (auto-play)
                if (lowerQuery.isEmpty()) {
                    com.zonik.app.data.DebugLog.d("MediaService", "Voice search: empty query → shuffle mix")
                    val tracks = runBlocking { libraryRepository.getRandomSongs(count = 100) }
                    if (tracks.isNotEmpty()) {
                        val mediaItems = tracks.map { buildFullMediaItem(it) }
                        session.player.setMediaItems(mediaItems, 0, 0)
                        session.player.prepare()
                        session.player.play()
                    }
                    session.notifySearchResultChanged(browser, query, 0, params)
                    return Futures.immediateFuture(LibraryResult.ofVoid())
                }

                // Keyword detection: favorites/starred/liked
                if (lowerQuery in listOf("favorites", "favourites", "starred", "liked", "my favorites", "my favourites")) {
                    com.zonik.app.data.DebugLog.d("MediaService", "Voice search: '$query' → favorites")
                    val tracks = runBlocking { libraryRepository.getStarredTracks().shuffled() }
                    if (tracks.isNotEmpty()) {
                        val mediaItems = tracks.map { buildFullMediaItem(it) }
                        session.player.setMediaItems(mediaItems, 0, 0)
                        session.player.prepare()
                        session.player.play()
                    }
                    session.notifySearchResultChanged(browser, query, 0, params)
                    return Futures.immediateFuture(LibraryResult.ofVoid())
                }

                // Genre keywords: "play rock", "play jazz", etc.
                val genreKeywords = listOf("rock", "pop", "jazz", "electronic", "hip hop", "classical", "metal", "country", "r&b", "indie", "punk", "blues", "folk", "dance", "ambient", "reggae", "soul", "funk")
                val matchedGenre = genreKeywords.find { lowerQuery.contains(it) }
                if (matchedGenre != null) {
                    com.zonik.app.data.DebugLog.d("MediaService", "Voice search: '$query' → genre mix '$matchedGenre'")
                    val tracks = runBlocking { libraryRepository.getRandomSongs(count = 100, genre = matchedGenre) }
                    if (tracks.isNotEmpty()) {
                        val mediaItems = tracks.map { buildFullMediaItem(it) }
                        session.player.setMediaItems(mediaItems, 0, 0)
                        session.player.prepare()
                        session.player.play()
                        session.notifySearchResultChanged(browser, query, 0, params)
                        return Futures.immediateFuture(LibraryResult.ofVoid())
                    }
                }

                val (artists, albums, tracks) = runBlocking {
                    libraryRepository.search(query)
                }

                // Auto-play: if voice search matches an artist, play all their tracks
                if (artists.isNotEmpty()) {
                    val topArtist = artists.first()
                    if (topArtist.name.lowercase().contains(lowerQuery) || lowerQuery.contains(topArtist.name.lowercase())) {
                        com.zonik.app.data.DebugLog.d("MediaService", "Voice search: '$query' → auto-play artist '${topArtist.name}'")
                        val artistTracks = runBlocking {
                            val (_, artistAlbums) = libraryRepository.getArtistDetail(topArtist.id)
                            artistAlbums.flatMap { album ->
                                val (_, albumTracks) = libraryRepository.getAlbumDetail(album.id)
                                albumTracks
                            }.take(100)
                        }
                        if (artistTracks.isNotEmpty()) {
                            val mediaItems = artistTracks.map { buildFullMediaItem(it) }
                            session.player.setMediaItems(mediaItems, 0, 0)
                            session.player.prepare()
                            session.player.play()
                            session.notifySearchResultChanged(browser, query, 0, params)
                            return Futures.immediateFuture(LibraryResult.ofVoid())
                        }
                    }
                }

                // Auto-play: if voice search matches an album, play it
                if (albums.isNotEmpty()) {
                    val topAlbum = albums.first()
                    if (topAlbum.name.lowercase().contains(lowerQuery) || lowerQuery.contains(topAlbum.name.lowercase())) {
                        com.zonik.app.data.DebugLog.d("MediaService", "Voice search: '$query' → auto-play album '${topAlbum.name}'")
                        val albumTracks = runBlocking {
                            val (_, albumTracks) = libraryRepository.getAlbumDetail(topAlbum.id)
                            albumTracks
                        }
                        if (albumTracks.isNotEmpty()) {
                            val mediaItems = albumTracks.map { buildFullMediaItem(it) }
                            session.player.setMediaItems(mediaItems, 0, 0)
                            session.player.prepare()
                            session.player.play()
                            session.notifySearchResultChanged(browser, query, 0, params)
                            return Futures.immediateFuture(LibraryResult.ofVoid())
                        }
                    }
                }

                // Auto-play: if voice search matches a track, play it + related
                if (tracks.isNotEmpty()) {
                    val topTrack = tracks.first()
                    if (topTrack.title.lowercase().contains(lowerQuery) || lowerQuery.contains(topTrack.title.lowercase())) {
                        com.zonik.app.data.DebugLog.d("MediaService", "Voice search: '$query' → auto-play track '${topTrack.title}'")
                        val mediaItems = tracks.take(50).map { buildFullMediaItem(it) }
                        session.player.setMediaItems(mediaItems, 0, 0)
                        session.player.prepare()
                        session.player.play()
                        session.notifySearchResultChanged(browser, query, 0, params)
                        return Futures.immediateFuture(LibraryResult.ofVoid())
                    }
                }

                // Fallback: show search results without auto-play
                val results = mutableListOf<MediaItem>()
                albums.forEach { results.add(albumToMediaItem(it)) }
                artists.forEach { results.add(artistToMediaItem(it)) }
                tracks.forEach { results.add(trackToMediaItem(it)) }
                session.notifySearchResultChanged(browser, query, results.size, params)
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("MediaService", "Voice search failed: ${e.message}")
                session.notifySearchResultChanged(browser, query, 0, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return try {
                val (artists, albums, tracks) = runBlocking {
                    libraryRepository.search(query)
                }
                val results = mutableListOf<MediaItem>()
                albums.forEach { results.add(albumToMediaItem(it)) }
                artists.forEach { results.add(artistToMediaItem(it)) }
                tracks.forEach { results.add(trackToMediaItem(it)) }
                val start = page * pageSize
                val end = minOf(start + pageSize, results.size)
                val paged = if (start < results.size) results.subList(start, end) else emptyList()
                Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
                )
            } catch (e: Exception) {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO))
            }
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(toggleStarCommand)
                .add(toggleDeleteCommand)
                .add(playTracksCommand)
                .add(setEqCommand)
                .add(toggleShuffleCommand)
                .add(startRadioCommand)
                .add(getAudioSessionCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(buildCustomLayout(""))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_SHUFFLE) {
                val player = session.player
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                com.zonik.app.data.DebugLog.d("MediaService", "Shuffle toggled: ${player.shuffleModeEnabled}")
                val trackId = player.currentMediaItem?.mediaId?.removePrefix(TRACK_PREFIX) ?: ""
                session.setCustomLayout(buildCustomLayout(trackId))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            if (customCommand.customAction == ACTION_SET_EQ) {
                val enabled = args.getBoolean(EXTRA_EQ_ENABLED, false)
                val preset = args.getInt(EXTRA_EQ_PRESET, 0)
                val bandLevels = args.getString(EXTRA_EQ_BAND_LEVELS)
                val audioSessionId = (session.player as? ExoPlayer)?.audioSessionId ?: 0
                applyEqualizer(audioSessionId, enabled, preset, bandLevels)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            if (customCommand.customAction == ACTION_GET_AUDIO_SESSION) {
                val audioSessionId = (session.player as? ExoPlayer)?.audioSessionId ?: 0
                val result = Bundle().apply { putInt("audio_session_id", audioSessionId) }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, result))
            }

            if (customCommand.customAction == ACTION_START_RADIO) {
                val trackId = resolveCurrentTrackId(session) ?: return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                )
                com.zonik.app.data.DebugLog.d("MediaService", "START_RADIO for track $trackId")
                try {
                    val track = runBlocking { database.trackDao().getById(trackId)?.toDomain() }
                        ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    val radioTracks = runBlocking {
                        libraryRepository.startRadio(track.id, track.genre, track.artistId)
                    }
                    if (radioTracks.isEmpty()) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    }
                    val mediaItems = radioTracks.map { buildFullMediaItem(it) }
                    val player = session.player
                    player.setMediaItems(mediaItems, 0, 0)
                    player.prepare()
                    player.play()
                    com.zonik.app.data.DebugLog.d("MediaService", "START_RADIO: playing ${mediaItems.size} radio tracks")
                } catch (e: Exception) {
                    com.zonik.app.data.DebugLog.e("MediaService", "START_RADIO failed", e)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_IO))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            if (customCommand.customAction == ACTION_PLAY_TRACKS) {
                val trackIds = args.getStringArrayList(EXTRA_TRACK_IDS)
                val startIndex = args.getInt(EXTRA_START_INDEX, 0)
                if (trackIds.isNullOrEmpty()) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
                com.zonik.app.data.DebugLog.d("MediaService", "PLAY_TRACKS: ${trackIds.size} tracks, startIndex=$startIndex")
                try {
                    val tracks = runBlocking {
                        trackIds.mapNotNull { id -> database.trackDao().getById(id)?.toDomain() }
                    }
                    if (tracks.isEmpty()) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    }
                    val mediaItems = tracks.map { buildFullMediaItem(it) }
                    val startPaused = args.getBoolean("start_paused", false)
                    val player = session.player
                    if (startPaused) player.playWhenReady = false
                    player.setMediaItems(mediaItems, startIndex, 0)
                    player.prepare()
                    if (!startPaused) player.play()
                    com.zonik.app.data.DebugLog.d("MediaService", "PLAY_TRACKS: set ${mediaItems.size} items, playing from $startIndex${if (startPaused) " (paused)" else ""}")

                    // Pre-cache upcoming tracks in background after playback starts
                    val factory = cacheDataSourceFactory
                    if (factory != null && connectivityManager?.activeNetwork != null) {
                        preCacheJob?.cancel()
                        preCachingInProgress.clear()
                        preCacheJob = preCacheScope.launch {
                            val readAhead = settingsRepository.cacheReadAhead.first()
                            if (readAhead <= 0) return@launch
                            // Cache from startIndex+1 onward (current track streams via CacheDataSource which auto-caches)
                            val preCacheCount = minOf(readAhead, tracks.size - startIndex - 1)
                            for (i in 1..preCacheCount) {
                                if (isPlayerBuffering) break // Yield to active playback
                                val idx = startIndex + i
                                if (idx >= tracks.size) break
                                val track = tracks[idx]
                                if (simpleCache.getCachedBytes(track.id, 0, Long.MAX_VALUE) > 0) continue
                                if (!preCachingInProgress.add(track.id)) continue
                                try {
                                    com.zonik.app.data.DebugLog.d("MediaService", "PLAY_TRACKS: pre-caching track $i/$preCacheCount (${track.id})")
                                    val streamUrl = buildStreamUrlForTrack(track.id)
                                    val dataSpec = DataSpec(android.net.Uri.parse(streamUrl))
                                    val dataSource = factory.createDataSource()
                                    CacheWriter(dataSource, dataSpec, null, null).cache()
                                    com.zonik.app.data.DebugLog.d("MediaService", "PLAY_TRACKS: cached ${track.id}")
                                    preCachingInProgress.remove(track.id)
                                } catch (e: Exception) {
                                    preCachingInProgress.remove(track.id)
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    com.zonik.app.data.DebugLog.w("MediaService", "PLAY_TRACKS: pre-cache failed: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.zonik.app.data.DebugLog.e("MediaService", "PLAY_TRACKS failed", e)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_IO))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            val trackId = resolveCurrentTrackId(session) ?: return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
            )

            if (customCommand.customAction == ACTION_TOGGLE_STAR) {
                val isStarred = trackId in starredTrackIds
                try {
                    runBlocking {
                        if (isStarred) {
                            libraryRepository.unstar(trackId)
                            starredTrackIds.remove(trackId)
                        } else {
                            libraryRepository.star(trackId)
                            starredTrackIds.add(trackId)
                        }
                    }
                    com.zonik.app.data.DebugLog.d("MediaService", "Star toggled for $trackId: ${!isStarred}")
                    session.setCustomLayout(buildCustomLayout(trackId))
                } catch (e: Exception) {
                    com.zonik.app.data.DebugLog.e("MediaService", "Star toggle failed", e)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_IO))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            if (customCommand.customAction == ACTION_TOGGLE_DELETE) {
                val isMarked = trackId in markedForDeletionIds
                try {
                    runBlocking {
                        if (isMarked) {
                            libraryRepository.unmarkForDeletion(trackId)
                        } else {
                            libraryRepository.markForDeletion(trackId)
                        }
                    }
                    if (isMarked) markedForDeletionIds.remove(trackId) else markedForDeletionIds.add(trackId)
                    com.zonik.app.data.DebugLog.d("MediaService", "Delete mark toggled for $trackId: ${!isMarked}")
                    session.setCustomLayout(buildCustomLayout(trackId))
                } catch (e: Exception) {
                    com.zonik.app.data.DebugLog.e("MediaService", "Delete mark toggle failed", e)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_IO))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** Resolves the current track ID from the player, falling back to DB lookup by metadata */
    private fun resolveCurrentTrackId(session: MediaSession): String? {
        val player = session.player
        if (player.mediaItemCount == 0) return null
        val currentItem = player.currentMediaItem ?: return null
        var trackId = currentItem.mediaId.removePrefix(TRACK_PREFIX)
        if (trackId.isBlank()) {
            val title = currentItem.mediaMetadata.title?.toString()
            val artist = currentItem.mediaMetadata.artist?.toString()
            if (title != null) {
                val match = runBlocking {
                    if (artist != null) database.trackDao().findByTitleAndArtist(title, artist)
                    else database.trackDao().findByTitle(title)
                }
                trackId = match?.id ?: ""
            }
        }
        if (trackId.isBlank()) {
            com.zonik.app.data.DebugLog.w("MediaService", "Could not determine current track ID")
            return null
        }
        return trackId
    }

    private fun preCacheUpcoming(player: androidx.media3.common.Player) {
        preCacheJob?.cancel()
        if (isPlayerBuffering) return // Don't pre-cache while player is buffering
        if (connectivityManager?.activeNetwork == null) return // Don't pre-cache when offline
        val factory = cacheDataSourceFactory ?: return
        val allUpcoming = mutableListOf<Uri>()
        try {
            val currentIndex = player.currentMediaItemIndex
            val itemCount = player.mediaItemCount
            if (itemCount == 0) return
            val maxPossible = minOf(5, itemCount - currentIndex - 1) // Cap at 5 to avoid server transcode queue overload
            if (maxPossible <= 0) return
            for (i in 1..maxPossible) {
                val idx = currentIndex + i
                if (idx >= itemCount) break
                val item = player.getMediaItemAt(idx)
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri != null) allUpcoming.add(uri)
            }
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("MediaService", "Pre-cache setup failed: ${e.message}")
        }
        if (allUpcoming.isEmpty()) return

        preCacheJob = preCacheScope.launch {
            val readAhead = settingsRepository.cacheReadAhead.first()
            if (readAhead <= 0) return@launch
            val upcoming = allUpcoming.take(readAhead)
            for (uri in upcoming) {
                if (isPlayerBuffering) break // Yield to active playback
                try {
                    val trackId = uri.getQueryParameter("id") ?: continue
                    if (simpleCache.getCachedBytes(trackId, 0, Long.MAX_VALUE) > 0) continue
                    if (!preCachingInProgress.add(trackId)) continue // Already caching this track
                    com.zonik.app.data.DebugLog.d("MediaService", "Pre-caching track: $trackId")
                    val dataSpec = DataSpec(uri)
                    val dataSource = factory.createDataSource()
                    val writer = CacheWriter(dataSource, dataSpec, null, null)
                    writer.cache()
                    com.zonik.app.data.DebugLog.d("MediaService", "Pre-cached track: $trackId")
                    preCachingInProgress.remove(trackId)
                } catch (e: Exception) {
                    val trackId = uri.getQueryParameter("id")
                    if (trackId != null) preCachingInProgress.remove(trackId)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    com.zonik.app.data.DebugLog.w("MediaService", "Pre-cache failed: ${e.message}")
                }
            }
        }
    }

    // -- Browse tree resolution --

    private fun resolveChildren(parentId: String, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        val items: List<MediaItem> = when (parentId) {
            ROOT_ID -> rootChildren()
            RECENT_ID -> recentChildren()
            LIBRARY_ID -> libraryChildren()
            PLAYLISTS_ID -> playlistsChildren()
            MIX_ID -> mixChildren()
            ARTISTS_ID -> artistsChildren()
            ALBUMS_ID -> albumsChildren()
            GENRES_ID -> genresChildren()
            else -> dynamicChildren(parentId)
        }
        val start = page * pageSize
        val end = minOf(start + pageSize, items.size)
        if (start >= items.size) return ImmutableList.of()
        return ImmutableList.copyOf(items.subList(start, end))
    }

    private fun rootChildren(): List<MediaItem> {
        val tabOrder = runBlocking {
            settingsRepository.autoTabOrder.first()
        }
        val tabMap = mapOf(
            "mix" to buildBrowsableItem(id = MIX_ID, title = "Mix", extras = listExtras()),
            "recent" to buildBrowsableItem(id = RECENT_ID, title = "Recently Added", extras = gridExtras()),
            "library" to buildBrowsableItem(id = LIBRARY_ID, title = "Library", extras = listExtras()),
            "playlists" to buildBrowsableItem(id = PLAYLISTS_ID, title = "Playlists", extras = listExtras())
        )
        return tabOrder.mapNotNull { tabMap[it] }
    }

    private fun recentChildren(): List<MediaItem> {
        val albums = runBlocking {
            libraryRepository.getRecentAlbums(20).firstOrNull() ?: emptyList()
        }
        return albums.map { albumToMediaItem(it) }
    }

    private fun libraryChildren(): List<MediaItem> {
        return listOf(
            buildBrowsableItem(
                id = ARTISTS_ID,
                title = "Artists",
                extras = gridExtras()
            ),
            buildBrowsableItem(
                id = ALBUMS_ID,
                title = "Albums",
                extras = gridExtras()
            ),
            buildBrowsableItem(
                id = GENRES_ID,
                title = "Genres",
                extras = listExtras()
            )
        )
    }

    private fun artistsChildren(): List<MediaItem> {
        val artists = runBlocking {
            libraryRepository.getArtists().firstOrNull() ?: emptyList()
        }
        return artists.map { artistToMediaItem(it) }
    }

    private fun albumsChildren(): List<MediaItem> {
        val albums = runBlocking {
            libraryRepository.getAlbums().firstOrNull() ?: emptyList()
        }
        return albums.map { albumToMediaItem(it) }
    }

    private fun genresChildren(): List<MediaItem> {
        val genres = runBlocking {
            libraryRepository.getGenres()
        }
        return genres.map { genreToMediaItem(it) }
    }

    private fun playlistsChildren(): List<MediaItem> {
        val playlists = runBlocking {
            libraryRepository.getPlaylists()
        }
        return playlists.map { playlistToMediaItem(it) }
    }

    private fun mixChildren(): List<MediaItem> {
        return listOf(
            buildPlayableItem(id = SHUFFLE_MIX_ID, title = "Shuffle", subtitle = "Random songs"),
            buildPlayableItem(id = NEWLY_ADDED_ID, title = "Newly Added", subtitle = "Recently added tracks"),
            buildPlayableItem(id = FAVORITES_ID, title = "Favorites", subtitle = "Starred tracks"),
            buildPlayableItem(id = NON_FAVORITES_ID, title = "Non-Favorites", subtitle = "Unstarred tracks")
        )
    }

    /** Returns tracks for mix-type IDs, or null if not a mix ID */
    private fun resolveMixTracks(id: String): List<Track>? {
        return runBlocking {
            when (id) {
                SHUFFLE_MIX_ID -> libraryRepository.getRandomSongs(count = 100)
                NEWLY_ADDED_ID -> {
                    libraryRepository.getRecentTracks(limit = 100).first()
                }
                FAVORITES_ID -> {
                    val starred = libraryRepository.getStarredTracks()
                    starred.shuffled()
                }
                NON_FAVORITES_ID -> {
                    val unstarred = libraryRepository.getUnstarredTracks()
                    unstarred.shuffled().take(100)
                }
                else -> null
            }
        }
    }

    private fun dynamicChildren(parentId: String): List<MediaItem> {
        return when {
            parentId.startsWith(ARTIST_PREFIX) -> {
                val artistId = parentId.removePrefix(ARTIST_PREFIX)
                val (_, albums) = runBlocking {
                    libraryRepository.getArtistDetail(artistId)
                }
                albums.map { albumToMediaItem(it) }
            }

            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = parentId.removePrefix(ALBUM_PREFIX)
                val (_, tracks) = runBlocking {
                    libraryRepository.getAlbumDetail(albumId)
                }
                tracks.map { trackToMediaItem(it) }
            }

            parentId.startsWith(GENRE_PREFIX) -> {
                val genre = parentId.removePrefix(GENRE_PREFIX)
                val tracks = runBlocking {
                    libraryRepository.getRandomSongs(count = 50, genre = genre)
                }
                tracks.map { trackToMediaItem(it) }
            }

            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                val tracks = runBlocking {
                    libraryRepository.getPlaylistTracks(playlistId)
                }
                tracks.map { trackToMediaItem(it) }
            }

            else -> emptyList()
        }
    }

    private fun resolveItem(mediaId: String): MediaItem? {
        return when {
            mediaId == ROOT_ID -> buildBrowsableItem(id = ROOT_ID, title = "Zonik")
            mediaId == RECENT_ID -> buildBrowsableItem(id = RECENT_ID, title = "Recently Added")
            mediaId == LIBRARY_ID -> buildBrowsableItem(id = LIBRARY_ID, title = "Library")
            mediaId == PLAYLISTS_ID -> buildBrowsableItem(id = PLAYLISTS_ID, title = "Playlists")
            mediaId == MIX_ID -> buildBrowsableItem(id = MIX_ID, title = "Mix")
            mediaId == ARTISTS_ID -> buildBrowsableItem(id = ARTISTS_ID, title = "Artists")
            mediaId == ALBUMS_ID -> buildBrowsableItem(id = ALBUMS_ID, title = "Albums")
            mediaId == GENRES_ID -> buildBrowsableItem(id = GENRES_ID, title = "Genres")
            mediaId == SHUFFLE_MIX_ID -> buildPlayableItem(id = SHUFFLE_MIX_ID, title = "Shuffle")
            mediaId == NEWLY_ADDED_ID -> buildPlayableItem(id = NEWLY_ADDED_ID, title = "Newly Added")
            mediaId == FAVORITES_ID -> buildPlayableItem(id = FAVORITES_ID, title = "Favorites")
            mediaId == NON_FAVORITES_ID -> buildPlayableItem(id = NON_FAVORITES_ID, title = "Non-Favorites")
            mediaId.startsWith(TRACK_PREFIX) -> {
                // For individual tracks, return a minimal playable item
                buildPlayableItem(id = mediaId, title = mediaId.removePrefix(TRACK_PREFIX))
            }
            mediaId.startsWith(ALBUM_PREFIX) -> {
                buildBrowsableItem(id = mediaId, title = mediaId.removePrefix(ALBUM_PREFIX))
            }
            mediaId.startsWith(ARTIST_PREFIX) -> {
                buildBrowsableItem(id = mediaId, title = mediaId.removePrefix(ARTIST_PREFIX))
            }
            mediaId.startsWith(GENRE_PREFIX) -> {
                buildBrowsableItem(id = mediaId, title = mediaId.removePrefix(GENRE_PREFIX))
            }
            mediaId.startsWith(PLAYLIST_PREFIX) -> {
                buildBrowsableItem(id = mediaId, title = mediaId.removePrefix(PLAYLIST_PREFIX))
            }
            else -> null
        }
    }
}
