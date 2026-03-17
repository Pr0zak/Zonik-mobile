package com.zonik.app.media

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.zonik.app.model.ServerConfig
import com.zonik.app.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    val castManager: CastManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var controller: MediaController? = null

    private var _pendingStartIndex: Int = -1  // Legacy, kept for log compatibility
    // Tracks a pending seek after setMediaItems to correct per-item IPC reordering.
    // The resulting SEEK transition is ignored for UI updates.
    private var _pendingSeekIndex: Int = -1

    // Set by skipToIndex to prevent onMediaItemTransition from overriding
    // the correct track when manually seeking within the queue.
    private var _manualSeekIndex: Int = -1

    // After a manual seek, ExoPlayer fires two transitions: reason=2 (SEEK) then
    // reason=3 (AUTO). We must ignore the second one to avoid overriding the track.
    private var _ignoreNextAutoTransition: Boolean = false

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    private val _playbackRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playbackRequested: Flow<Unit> = _playbackRequested

    // Scrobble tracking — scrobble once when track plays >50%
    private var scrobbledTrackId: String? = null

    suspend fun connect() {
        if (controller != null) return
        DebugLog.d("Playback", "Connecting to MediaService...")
        val sessionToken = SessionToken(
            context,
            ComponentName(context, ZonikMediaService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controller = suspendCoroutine { cont ->
            future.addListener(
                { cont.resume(future.get()) },
                MoreExecutors.directExecutor()
            )
        }

        DebugLog.d("Playback", "Connected to MediaService")

        // Track Cast track changes and update currentTrack
        scope.launch {
            castManager.castTrackTitle.collect { title ->
                if (title != null && castManager.isCasting.value) {
                    val artist = castManager.castTrackArtist.value
                    val match = _queue.value.find { it.title == title && (artist == null || it.artist == artist) }
                    if (match != null && match.id != _currentTrack.value?.id) {
                        setCurrentTrack(match)
                        DebugLog.d("Playback", "Cast track update: ${match.title} by ${match.artist}")
                    }
                }
            }
        }

        // When Cast session starts, transfer current playback to Cast device
        scope.launch(Dispatchers.Main) {
            castManager.isCasting.collect { casting ->
                if (casting) {
                    val queue = _queue.value
                    val track = _currentTrack.value
                    if (queue.isNotEmpty() && track != null) {
                        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        val config = getServerConfig() ?: return@collect
                        val serverUrl = config.url

                        // Pause local playback
                        controller?.pause()

                        DebugLog.d("Playback", "Transferring ${queue.size} tracks to Cast (starting at $startIndex)")
                        castManager.loadQueue(
                            tracks = queue,
                            startIndex = startIndex,
                            buildStreamUrl = { t -> buildStreamUrl(t, serverUrl, config) },
                            buildArtUrl = { t -> buildArtUrl(t, serverUrl, config) }
                        )
                    }
                }
            }
        }

        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                DebugLog.d("Playback", "isPlaying changed: $playing")
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller?.currentMediaItemIndex ?: -1
                val metaTitle = mediaItem?.mediaMetadata?.title?.toString()
                val metaArtist = mediaItem?.mediaMetadata?.artist?.toString()
                DebugLog.d("Playback", "Track transition: title='$metaTitle' artist='$metaArtist' index=$index reason=$reason pendingStart=$_pendingStartIndex manualSeek=$_manualSeekIndex")

                // After a manual seek, ignore the follow-up AUTO transition
                if (_ignoreNextAutoTransition && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    _ignoreNextAutoTransition = false
                    DebugLog.d("Playback", "Ignoring post-seek AUTO transition")
                    return
                }

                // Manual seek from skipToIndex — trust our index, ignore ExoPlayer's
                if (_manualSeekIndex >= 0) {
                    val expected = _manualSeekIndex
                    _manualSeekIndex = -1
                    _ignoreNextAutoTransition = true
                    DebugLog.d("Playback", "Manual seek: using index $expected (ExoPlayer reported $index)")
                    updateCurrentTrackByIndex(expected)
                    return
                }

                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    // Media3 fires spurious PLAYLIST_CHANGED transitions during per-item IPC.
                    // Ignore them — the UI track was already set in playTracks().
                    DebugLog.d("Playback", "Ignoring PLAYLIST_CHANGED transition")
                    return
                }
                // Ignore the SEEK transition caused by our correction seekTo in playTracks()
                if (_pendingSeekIndex >= 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    DebugLog.d("Playback", "Ignoring correction seek transition (pendingSeek=$_pendingSeekIndex)")
                    _pendingSeekIndex = -1
                    return
                }
                // Match by metadata first (more reliable than index after shuffle/IPC)
                if (metaTitle != null) {
                    val match = findTrackByMetadata(metaTitle, metaArtist)
                    if (match != null) {
                        setCurrentTrack(match)
                        return
                    }
                }
                updateCurrentTrackByIndex(index)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DebugLog.e("Playback", "Player error: ${error.errorCodeName} - ${error.message}")
                DebugLog.e("Playback", "Error cause: ${error.cause?.message}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                DebugLog.d("Playback", "State: $state")
            }
        })
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val config = getServerConfig() ?: return
        val serverUrl = config.url
        // Cap track list to avoid TransactionTooLargeException (Binder 1MB limit)
        val maxTracks = 500
        val cappedTracks = if (tracks.size > maxTracks) {
            val start = maxOf(0, startIndex - 50) // keep some context before start
            val end = minOf(tracks.size, start + maxTracks)
            val adjustedIndex = startIndex - start
            DebugLog.d("Playback", "Capping ${tracks.size} tracks to $maxTracks (offset $start, adjusted index $adjustedIndex)")
            return playTracks(tracks.subList(start, end), adjustedIndex)
        } else tracks
        _queue.value = cappedTracks
        // Set current track immediately for instant UI update (don't wait for ExoPlayer callback)
        if (startIndex in tracks.indices) {
            _currentTrack.value = tracks[startIndex]
        }
        _playbackRequested.tryEmit(Unit)

        // Route to Cast if a Cast session is active
        if (castManager.isCasting.value) {
            DebugLog.d("Playback", "Casting ${tracks.size} tracks from index $startIndex")
            castManager.loadQueue(
                tracks = tracks,
                startIndex = startIndex,
                buildStreamUrl = { track -> buildStreamUrl(track, serverUrl, config) },
                buildArtUrl = { track -> buildArtUrl(track, serverUrl, config) }
            )
            return
        }

        val ctrl = controller
        if (ctrl == null) {
            DebugLog.e("Playback", "playTracks called but controller is null!")
            return
        }

        val mediaItems = tracks.map { track ->
            buildMediaItem(track, serverUrl, config)
        }

        _pendingSeekIndex = startIndex
        DebugLog.d("Playback", "Playing ${tracks.size} tracks from index $startIndex")
        DebugLog.d("Playback", "Stream URL: ${mediaItems.firstOrNull()?.localConfiguration?.uri}")
        ctrl.setMediaItems(mediaItems, startIndex, 0)
        ctrl.prepare()
        ctrl.play()
        // Media3 per-item IPC leaves the player at the wrong index.
        // seekTo corrects it. The transition handler ignores PLAYLIST_CHANGED
        // and the resulting SEEK transition for UI purposes.
        ctrl.seekTo(startIndex, 0)
    }

    fun playNext(track: Track) {
        val ctrl = controller ?: return
        val config = getServerConfig() ?: return
        val nextIndex = ctrl.currentMediaItemIndex + 1
        ctrl.addMediaItem(nextIndex, buildMediaItem(track, config.url, config))

        val updatedQueue = _queue.value.toMutableList()
        updatedQueue.add(nextIndex, track)
        _queue.value = updatedQueue
    }

    fun addToQueue(track: Track) {
        val ctrl = controller ?: return
        val config = getServerConfig() ?: return
        ctrl.addMediaItem(buildMediaItem(track, config.url, config))

        _queue.value = _queue.value + track
    }

    fun togglePlayPause() {
        if (castManager.isCasting.value) {
            castManager.togglePlayPause()
            _isPlaying.value = !_isPlaying.value
            return
        }
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) {
        if (castManager.isCasting.value) {
            castManager.seekTo(positionMs)
            return
        }
        controller?.seekTo(positionMs)
    }

    fun skipToIndex(index: Int) {
        val ctrl = controller ?: return
        val track = _queue.value.getOrNull(index) ?: return
        DebugLog.d("Playback", "skipToIndex: $index (mediaItemCount=${ctrl.mediaItemCount}, queueSize=${_queue.value.size})")

        // ExoPlayer's internal queue may differ from _queue due to IPC reordering.
        // Find the track by mediaId in ExoPlayer's actual queue.
        var exoIndex = -1
        for (i in 0 until ctrl.mediaItemCount) {
            if (ctrl.getMediaItemAt(i).mediaId == track.id) {
                exoIndex = i
                break
            }
        }
        if (exoIndex < 0) {
            DebugLog.w("Playback", "skipToIndex: track '${track.title}' (${track.id}) not found in ExoPlayer queue, falling back to index $index")
            exoIndex = index
        }
        if (exoIndex != index) {
            DebugLog.d("Playback", "skipToIndex: queue index $index maps to ExoPlayer index $exoIndex")
        }

        _pendingStartIndex = -1  // Clear any pending correction
        _manualSeekIndex = index  // Tell onMediaItemTransition to use our queue index
        _ignoreNextAutoTransition = false
        setCurrentTrack(track)  // Update UI immediately
        ctrl.seekTo(exoIndex, 0L)
    }

    fun skipNext() {
        if (castManager.isCasting.value) {
            castManager.skipNext()
            return
        }
        val ctrl = controller
        if (ctrl == null) {
            DebugLog.w("Playback", "skipNext: controller is null")
            return
        }
        DebugLog.d("Playback", "skipNext: index=${ctrl.currentMediaItemIndex}, count=${ctrl.mediaItemCount}")
        ctrl.seekToNext()
    }

    fun skipPrevious() {
        if (castManager.isCasting.value) {
            castManager.skipPrevious()
            return
        }
        val ctrl = controller
        if (ctrl == null) {
            DebugLog.w("Playback", "skipPrevious: controller is null")
            return
        }
        DebugLog.d("Playback", "skipPrevious: index=${ctrl.currentMediaItemIndex}, count=${ctrl.mediaItemCount}")
        ctrl.seekToPrevious()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    fun getCurrentPosition(): Long {
        if (castManager.isCasting.value) {
            val pos = castManager.getCurrentPosition()
            checkScrobble(pos)
            return pos
        }
        val pos = controller?.currentPosition ?: 0L
        checkScrobble(pos)
        return pos
    }

    fun getDuration(): Long {
        if (castManager.isCasting.value) return castManager.getDuration()
        return controller?.duration ?: 0L
    }

    private fun checkScrobble(positionMs: Long) {
        val track = _currentTrack.value ?: return
        if (track.id == scrobbledTrackId) return
        val duration = controller?.duration ?: 0L
        if (duration <= 0) return
        if (positionMs > duration / 2) {
            scrobbledTrackId = track.id
            scope.launch {
                try {
                    libraryRepository.scrobble(track.id)
                    DebugLog.d("Playback", "Scrobbled: ${track.title}")
                } catch (e: Exception) {
                    DebugLog.w("Playback", "Scrobble failed: ${e.message}")
                }
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun buildStreamUrl(track: Track, serverUrl: String, config: ServerConfig): String {
        val bitrate = getMaxBitRate()
        val bitrateParam = if (bitrate > 0) "&maxBitRate=$bitrate" else ""
        val authParams = buildAuthParamsFromConfig(config)
        return "${serverUrl.trimEnd('/')}/rest/stream.view?id=${track.id}${bitrateParam}&estimateContentLength=true$authParams"
    }

    private fun buildArtUrl(track: Track, serverUrl: String, config: ServerConfig): String? {
        val authParams = buildAuthParamsFromConfig(config)
        return track.coverArt?.let {
            "${serverUrl.trimEnd('/')}/rest/getCoverArt.view?id=$it&size=600$authParams"
        }
    }

    private fun buildMediaItem(track: Track, serverUrl: String, config: ServerConfig): MediaItem {
        val streamUrl = buildStreamUrl(track, serverUrl, config)
        // Use ContentProvider URI for artwork so Android Auto can fetch it
        val artUri = track.coverArt?.let {
            com.zonik.app.data.CoverArtProvider.buildUri(it, 600)
        }

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
                    .setTrackNumber(track.track)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun buildAuthParamsFromConfig(config: ServerConfig): String {
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        return "&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun findTrackByMetadata(title: String, artist: String?): Track? {
        val queue = _queue.value
        return queue.find { it.title == title && (artist == null || it.artist == artist) }
    }

    private fun setCurrentTrack(track: Track) {
        DebugLog.d("Playback", "Now playing: ${track.title} by ${track.artist}")
        _currentTrack.value = track
        scrobbledTrackId = null
        addToRecentlyPlayed(track)
    }

    private fun updateCurrentTrackByIndex(index: Int) {
        val queue = _queue.value
        if (index < 0 || index >= queue.size) {
            DebugLog.w("Playback", "Invalid track index: $index (queue size: ${queue.size})")
            _currentTrack.value = null
            return
        }
        setCurrentTrack(queue[index])
    }

    private fun getServerConfig(): ServerConfig? {
        return kotlinx.coroutines.runBlocking {
            settingsRepository.serverConfig.first()
        }
    }

    private fun getMaxBitRate(): Int {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val bitrate = kotlinx.coroutines.runBlocking {
            if (isWifi) settingsRepository.wifiBitrate.first()
            else settingsRepository.cellularBitrate.first()
        }
        return bitrate
    }

    private fun addToRecentlyPlayed(track: Track) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        if (current.size > 20) {
            _recentlyPlayed.value = current.take(20)
        } else {
            _recentlyPlayed.value = current
        }
    }
}
