package com.zonik.app.media

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.zonik.app.data.DebugLog
import com.zonik.app.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CastManager"
    }

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            DebugLog.d(TAG, "Cast session starting")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            DebugLog.d(TAG, "Cast session started: ${session.castDevice?.friendlyName}")
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            DebugLog.e(TAG, "Cast session start failed: $error")
            _isCasting.value = false
            _castDeviceName.value = null
        }

        override fun onSessionEnding(session: CastSession) {
            DebugLog.d(TAG, "Cast session ending")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            DebugLog.d(TAG, "Cast session ended")
            _isCasting.value = false
            _castDeviceName.value = null
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            DebugLog.d(TAG, "Cast session resumed")
            _isCasting.value = true
            _castDeviceName.value = session.castDevice?.friendlyName
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _isCasting.value = false
            _castDeviceName.value = null
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            DebugLog.d(TAG, "Cast SDK initialized")
        } catch (e: Exception) {
            DebugLog.w(TAG, "Cast SDK not available: ${e.message}")
        }
    }

    fun getCastContext(): CastContext? = castContext

    fun loadMedia(track: Track, streamUrl: String, artUrl: String?) {
        val session = sessionManager?.currentCastSession ?: return
        val remoteMediaClient = session.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.title)
            putString(MediaMetadata.KEY_ARTIST, track.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
            track.track?.let { putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }
            artUrl?.let { addImage(WebImage(android.net.Uri.parse(it))) }
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(track.contentType ?: "audio/mpeg")
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteMediaClient.load(loadRequest)
        DebugLog.d(TAG, "Loading on Cast: ${track.title}")
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int, buildStreamUrl: (Track) -> String, buildArtUrl: (Track) -> String?) {
        val session = sessionManager?.currentCastSession ?: return
        val remoteMediaClient = session.remoteMediaClient ?: return

        if (tracks.isEmpty()) return

        // Load the first track, then queue the rest
        val startTrack = tracks[startIndex]
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, startTrack.title)
            putString(MediaMetadata.KEY_ARTIST, startTrack.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, startTrack.album)
            startTrack.track?.let { putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }
            buildArtUrl(startTrack)?.let { addImage(WebImage(android.net.Uri.parse(it))) }
        }

        val mediaInfo = MediaInfo.Builder(buildStreamUrl(startTrack))
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(startTrack.contentType ?: "audio/mpeg")
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteMediaClient.load(loadRequest).setResultCallback { result ->
            if (result.status.isSuccess) {
                // Queue remaining tracks after the start index
                val remaining = tracks.filterIndexed { i, _ -> i != startIndex }
                for (track in remaining) {
                    val trackMeta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                        putString(MediaMetadata.KEY_TITLE, track.title)
                        putString(MediaMetadata.KEY_ARTIST, track.artist)
                        putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
                        track.track?.let { putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }
                        buildArtUrl(track)?.let { addImage(WebImage(android.net.Uri.parse(it))) }
                    }
                    val queueItem = com.google.android.gms.cast.MediaQueueItem.Builder(
                        MediaInfo.Builder(buildStreamUrl(track))
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setContentType(track.contentType ?: "audio/mpeg")
                            .setMetadata(trackMeta)
                            .build()
                    ).build()
                    remoteMediaClient.queueAppendItem(queueItem, null)
                }
                DebugLog.d(TAG, "Queued ${remaining.size} additional tracks on Cast")
            }
        }
    }

    fun play() {
        sessionManager?.currentCastSession?.remoteMediaClient?.play()
    }

    fun pause() {
        sessionManager?.currentCastSession?.remoteMediaClient?.pause()
    }

    fun togglePlayPause() {
        val client = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        if (client.isPlaying) client.pause() else client.play()
    }

    fun skipNext() {
        sessionManager?.currentCastSession?.remoteMediaClient?.queueNext(null)
    }

    fun skipPrevious() {
        sessionManager?.currentCastSession?.remoteMediaClient?.queuePrev(null)
    }

    fun seekTo(positionMs: Long) {
        val client = sessionManager?.currentCastSession?.remoteMediaClient ?: return
        @Suppress("DEPRECATION")
        client.seek(positionMs)
    }

    fun getCurrentPosition(): Long {
        return sessionManager?.currentCastSession?.remoteMediaClient?.approximateStreamPosition ?: 0L
    }

    fun getDuration(): Long {
        return sessionManager?.currentCastSession?.remoteMediaClient?.streamDuration ?: 0L
    }

    fun isPlaying(): Boolean {
        return sessionManager?.currentCastSession?.remoteMediaClient?.isPlaying == true
    }

    fun disconnect() {
        sessionManager?.endCurrentSession(true)
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }
}
