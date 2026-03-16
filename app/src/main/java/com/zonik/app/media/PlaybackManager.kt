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
import com.zonik.app.data.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository
) {
    private var controller: MediaController? = null

    // Tracks expected start index during playlist setup to work around Media3
    // calling onAddMediaItems individually per item, which causes a spurious
    // PLAYLIST_CHANGED transition to the wrong index.
    private var _pendingStartIndex: Int = -1

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

        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                DebugLog.d("Playback", "isPlaying changed: $playing")
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller?.currentMediaItemIndex ?: -1
                DebugLog.d("Playback", "Track transition: mediaId='${mediaItem?.mediaId}' index=$index reason=$reason pendingStart=$_pendingStartIndex")
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && _pendingStartIndex >= 0) {
                    // Media3 processes onAddMediaItems per-item, causing a spurious
                    // PLAYLIST_CHANGED transition to the wrong index. Correct it.
                    val expected = _pendingStartIndex
                    _pendingStartIndex = -1
                    if (index != expected) {
                        DebugLog.d("Playback", "Correcting playlist start: got index $index, seeking to $expected")
                        controller?.seekTo(expected, 0)
                        updateCurrentTrackByIndex(expected)
                    } else {
                        updateCurrentTrackByIndex(index)
                    }
                    return
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
        val ctrl = controller
        if (ctrl == null) {
            DebugLog.e("Playback", "playTracks called but controller is null!")
            return
        }
        val config = getServerConfig() ?: return
        val serverUrl = config.url
        _queue.value = tracks
        // Set current track immediately for instant UI update (don't wait for ExoPlayer callback)
        if (startIndex in tracks.indices) {
            _currentTrack.value = tracks[startIndex]
        }

        val mediaItems = tracks.map { track ->
            buildMediaItem(track, serverUrl, config)
        }

        _playbackRequested.tryEmit(Unit)
        _pendingStartIndex = startIndex
        DebugLog.d("Playback", "Playing ${tracks.size} tracks from index $startIndex")
        DebugLog.d("Playback", "Stream URL: ${mediaItems.firstOrNull()?.localConfiguration?.uri}")
        ctrl.setMediaItems(mediaItems, startIndex, 0)
        ctrl.prepare()
        ctrl.play()
        // Workaround: Media3 calls onAddMediaItems per-item during IPC, which can
        // leave the player at the wrong index. Explicit seekTo corrects it after
        // the playlist is fully built (IPC commands are processed in order).
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
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipNext() {
        val ctrl = controller
        if (ctrl == null) {
            DebugLog.w("Playback", "skipNext: controller is null")
            return
        }
        DebugLog.d("Playback", "skipNext: index=${ctrl.currentMediaItemIndex}, count=${ctrl.mediaItemCount}")
        ctrl.seekToNext()
    }

    fun skipPrevious() {
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

    fun getCurrentPosition(): Long = controller?.currentPosition ?: 0L
    fun getDuration(): Long = controller?.duration ?: 0L

    fun release() {
        controller?.release()
        controller = null
    }

    private fun buildMediaItem(track: Track, serverUrl: String, config: ServerConfig): MediaItem {
        val bitrate = getMaxBitRate()
        val bitrateParam = if (bitrate > 0) "&maxBitRate=$bitrate" else ""
        val authParams = buildAuthParamsFromConfig(config)
        val streamUrl = "${serverUrl.trimEnd('/')}/rest/stream.view?id=${track.id}${bitrateParam}&estimateContentLength=true$authParams"
        val artUrl = track.coverArt?.let {
            "${serverUrl.trimEnd('/')}/rest/getCoverArt.view?id=$it&size=600$authParams"
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
                    .setArtworkUri(artUrl?.let { Uri.parse(it) })
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

    private fun updateCurrentTrackByIndex(index: Int) {
        val queue = _queue.value
        if (index < 0 || index >= queue.size) {
            DebugLog.w("Playback", "Invalid track index: $index (queue size: ${queue.size})")
            _currentTrack.value = null
            return
        }
        val track = queue[index]
        DebugLog.d("Playback", "Now playing: ${track.title} by ${track.artist}")
        _currentTrack.value = track
        addToRecentlyPlayed(track)
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
