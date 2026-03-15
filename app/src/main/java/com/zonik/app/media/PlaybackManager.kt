package com.zonik.app.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.*
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

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    suspend fun connect() {
        if (controller != null) return
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

        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentTrack(mediaItem)
            }
        })
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val ctrl = controller ?: return
        val serverUrl = getServerUrl()
        _queue.value = tracks

        val mediaItems = tracks.map { track ->
            buildMediaItem(track, serverUrl)
        }

        ctrl.setMediaItems(mediaItems, startIndex, 0)
        ctrl.prepare()
        ctrl.play()
    }

    fun playNext(track: Track) {
        val ctrl = controller ?: return
        val serverUrl = getServerUrl()
        val nextIndex = ctrl.currentMediaItemIndex + 1
        ctrl.addMediaItem(nextIndex, buildMediaItem(track, serverUrl))

        val updatedQueue = _queue.value.toMutableList()
        updatedQueue.add(nextIndex, track)
        _queue.value = updatedQueue
    }

    fun addToQueue(track: Track) {
        val ctrl = controller ?: return
        val serverUrl = getServerUrl()
        ctrl.addMediaItem(buildMediaItem(track, serverUrl))

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
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
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

    private fun buildMediaItem(track: Track, serverUrl: String): MediaItem {
        val streamUrl = "${serverUrl.trimEnd('/')}/rest/stream.view?id=${track.id}&estimateContentLength=true"
        val artUrl = track.coverArt?.let {
            "${serverUrl.trimEnd('/')}/rest/getCoverArt.view?id=$it&size=600"
        }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl)
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

    private fun updateCurrentTrack(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentTrack.value = null
            return
        }
        _currentTrack.value = _queue.value.find { it.id == mediaItem.mediaId }
    }

    private fun getServerUrl(): String {
        return kotlinx.coroutines.runBlocking {
            settingsRepository.serverConfig.first()?.url ?: ""
        }
    }
}
