package com.zonik.wear.media

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionState { Disconnected, Connecting, Connected }

class WearMediaManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var browser: MediaBrowser? = null
    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var positionPollingJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var autoReconnect = true

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            updatePositionPolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                _duration.value = browser?.duration ?: 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _duration.value = browser?.duration ?: 0L
            updateQueueState()
        }

        override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
            // Refresh current item when metadata updates (e.g. artwork becomes available)
            _currentMediaItem.value = browser?.currentMediaItem
        }
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.Connecting) return
        _connectionState.value = ConnectionState.Connecting

        val sessionToken = SessionToken(
            context,
            ComponentName("com.zonik.app", "com.zonik.app.media.ZonikMediaService")
        )

        val future = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture = future
        future.addListener({
            try {
                val b = future.get()
                browser = b
                _connectionState.value = ConnectionState.Connected
                b.addListener(playerListener)
                syncState(b)
                updatePositionPolling()
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Disconnected
                scheduleReconnect()
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        autoReconnect = false
        positionPollingJob?.cancel()
        browser?.removeListener(playerListener)
        browserFuture?.let { MediaBrowser.releaseFuture(it) }
        browser = null
        browserFuture = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun syncState(b: MediaBrowser) {
        _isPlaying.value = b.isPlaying
        _isBuffering.value = b.playbackState == Player.STATE_BUFFERING
        _currentMediaItem.value = b.currentMediaItem
        _duration.value = b.duration.coerceAtLeast(0L)
        _position.value = b.currentPosition.coerceAtLeast(0L)
        updateQueueState()
    }

    private fun updateQueueState() {
        val b = browser ?: return
        val count = b.mediaItemCount
        val items = (0 until count).map { b.getMediaItemAt(it) }
        _queue.value = items
        _currentIndex.value = b.currentMediaItemIndex
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        scope.launch {
            delay(3000)
            if (_connectionState.value == ConnectionState.Disconnected && autoReconnect) {
                connect()
            }
        }
    }

    // -- Position polling --

    private var pollingIntervalMs = 200L

    fun setPollingInterval(ms: Long) {
        pollingIntervalMs = ms
        if (_isPlaying.value) {
            updatePositionPolling()
        }
    }

    fun stopPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun updatePositionPolling() {
        positionPollingJob?.cancel()
        if (_isPlaying.value && _connectionState.value == ConnectionState.Connected) {
            positionPollingJob = scope.launch {
                while (isActive) {
                    val b = browser
                    if (b != null) {
                        _position.value = b.currentPosition.coerceAtLeast(0L)
                        val dur = b.duration
                        if (dur > 0) _duration.value = dur
                    }
                    delay(pollingIntervalMs)
                }
            }
        }
    }

    // -- Playback controls --

    fun togglePlayPause() {
        val b = browser ?: return
        // Optimistic UI
        _isPlaying.value = !_isPlaying.value
        if (b.isPlaying) b.pause() else b.play()
    }

    fun skipNext() {
        val b = browser ?: return
        b.seekToNext()
    }

    fun skipPrevious() {
        val b = browser ?: return
        b.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val b = browser ?: return
        _position.value = positionMs // Optimistic
        b.seekTo(positionMs)
    }

    fun skipToIndex(index: Int) {
        val b = browser ?: return
        if (index in 0 until b.mediaItemCount) {
            b.seekTo(index, 0L)
        }
    }

    fun toggleStar() {
        val b = browser ?: return
        mainHandler.post {
            b.sendCustomCommand(
                SessionCommand("com.zonik.app.TOGGLE_STAR", Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    // -- Browse tree --

    suspend fun getChildren(parentId: String): List<MediaItem> {
        val b = browser ?: return emptyList()
        return try {
            val future = b.getChildren(parentId, 0, Int.MAX_VALUE, null)
            val result = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                future.addListener({
                    try {
                        cont.resume(future.get()) {}
                    } catch (e: Exception) {
                        cont.resume(null) {}
                    }
                }, MoreExecutors.directExecutor())
            }
            result?.value?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun playFromMediaId(mediaId: String) {
        val b = browser ?: return
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .build()
        b.setMediaItems(listOf(item), 0, 0L)
        b.prepare()
        b.play()
    }
}
