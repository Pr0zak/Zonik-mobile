package com.zonik.wear.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.zonik.core.api.ServerConfigProvider
import com.zonik.core.api.SubsonicApi
import com.zonik.core.api.SubsonicAuthInterceptor
import com.zonik.core.model.Track
import com.zonik.wear.WearApp
import com.zonik.wear.data.repository.WearLibraryRepository
import com.zonik.wear.data.repository.WearSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Standalone Wear OS media service. Owns its own ExoPlayer, OkHttp-backed
 * streaming cache, and MediaLibrarySession. Talks Subsonic directly to the
 * Zonik server — no phone tether.
 *
 * Browse tree (root → leaves):
 *   ROOT
 *     ├── recent        (list of 50 newest albums; each album is browsable to tracks)
 *     ├── library
 *     │     └── artists (list of artists; each artist → albums → tracks)
 *     └── playlists     (list of playlists; each → tracks)
 */
@OptIn(UnstableApi::class)
class ZonikWearMediaService : MediaLibraryService() {

    private lateinit var settings: WearSettingsRepository
    private lateinit var library: WearLibraryRepository
    private lateinit var configProvider: ServerConfigProvider

    private var simpleCache: SimpleCache? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Scrobble tracking — fires regardless of UI state so the watch shows up
    // on the server's /api/live page just like the phone does.
    private val scrobbleMainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val scrobbleIoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var scrobbledTrackId: String? = null
    @Volatile private var nowPlayingPostedFor: String? = null
    private var scrobblePollJob: kotlinx.coroutines.Job? = null
    private val pendingScrobbles = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Long>>()

    override fun onCreate() {
        super.onCreate()

        val app = application as WearApp
        settings = app.settings
        library = app.library
        configProvider = ServerConfigProvider { settings.current() }

        val streamClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(SubsonicAuthInterceptor(configProvider, clientName = "ZonikWear"))
            .build()
        val upstreamFactory = OkHttpDataSource.Factory(streamClient)

        val cacheDir = File(cacheDir, "exoplayer_audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024) // 200 MB
        val cache = SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(this))
        simpleCache = cache
        val cachedFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedFactory))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, BrowseTreeCallback())
            .build()

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (mediaItem != null) {
                    // New track → ready to scrobble it once it crosses 50%.
                    scrobbledTrackId = null
                    postNowPlaying()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startScrobblePoll() else stopScrobblePoll()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopScrobblePoll()
        scrobbleMainScope.cancel()
        scrobbleIoScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        simpleCache?.release()
        simpleCache = null
        super.onDestroy()
    }

    // --- Scrobble ---------------------------------------------------------

    private fun startScrobblePoll() {
        if (scrobblePollJob?.isActive == true) return
        scrobblePollJob = scrobbleMainScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000L)
                checkScrobbleThreshold()
            }
        }
    }

    private fun stopScrobblePoll() {
        scrobblePollJob?.cancel()
        scrobblePollJob = null
    }

    /** Reads player state on the main thread; fires the scrobble on IO. */
    private fun checkScrobbleThreshold() {
        val player = mediaLibrarySession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId.isEmpty() || mediaId == scrobbledTrackId) return
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || position <= duration / 2) return
        scrobbledTrackId = mediaId
        val capturedTime = System.currentTimeMillis()
        scrobbleIoScope.launch {
            flushPendingScrobbles()
            try {
                library.scrobble(mediaId, capturedTime)
                android.util.Log.d("WearScrobble", "Scrobbled: $mediaId")
            } catch (e: Exception) {
                pendingScrobbles.add(mediaId to capturedTime)
                android.util.Log.w("WearScrobble", "Scrobble queued offline: ${e.message}")
            }
        }
    }

    private fun postNowPlaying() {
        val player = mediaLibrarySession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId.isEmpty() || mediaId == nowPlayingPostedFor) return
        nowPlayingPostedFor = mediaId
        scrobbleIoScope.launch {
            flushPendingScrobbles()
            try {
                library.scrobbleNowPlaying(mediaId)
                android.util.Log.d("WearScrobble", "Now-playing posted: $mediaId")
            } catch (_: Exception) { /* fire-and-forget */ }
        }
    }

    private suspend fun flushPendingScrobbles() {
        while (pendingScrobbles.isNotEmpty()) {
            val entry = pendingScrobbles.peek() ?: break
            try {
                library.scrobble(entry.first, entry.second)
                pendingScrobbles.poll()
            } catch (_: Exception) {
                return
            }
        }
    }

    // --- Browse tree -------------------------------------------------------

    private inner class BrowseTreeCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            ioScope.launch {
                try {
                    val items: List<MediaItem> = when {
                        parentId == ROOT_ID -> rootChildren()
                        parentId == RECENT_ID -> library.listRecentAlbums(size = 50)
                            .map { albumNode(it.id, it.name, it.coverArt) }
                        parentId == LIBRARY_ID -> libraryChildren()
                        parentId == ARTISTS_ID -> library.listArtists()
                            .map { artistNode(it.id, it.name, it.coverArt) }
                        parentId == PLAYLISTS_ID -> library.listPlaylists()
                            .map { playlistNode(it.id, it.name, it.coverArt) }
                        parentId.startsWith(ARTIST_PREFIX) -> {
                            val id = parentId.removePrefix(ARTIST_PREFIX)
                            library.getArtistAlbums(id).map { albumNode(it.id, it.name, it.coverArt) }
                        }
                        parentId.startsWith(ALBUM_PREFIX) -> {
                            val id = parentId.removePrefix(ALBUM_PREFIX)
                            library.getAlbumTracks(id).map { trackLeaf(it) }
                        }
                        parentId.startsWith(PLAYLIST_PREFIX) -> {
                            val id = parentId.removePrefix(PLAYLIST_PREFIX)
                            library.getPlaylistTracks(id).map { trackLeaf(it) }
                        }
                        else -> emptyList()
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), null))
                } catch (e: Exception) {
                    android.util.Log.w("WearMediaService", "Browse $parentId failed: ${e.message}")
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                when (mediaId) {
                    ROOT_ID -> LibraryResult.ofItem(rootItem(), null)
                    else -> LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            )
        }
    }

    // --- MediaItem builders ------------------------------------------------

    private fun rootItem(): MediaItem = browsable(ROOT_ID, "Zonik")

    private fun rootChildren(): List<MediaItem> = listOf(
        browsable(RECENT_ID, "Recently added"),
        browsable(LIBRARY_ID, "Library"),
        browsable(PLAYLISTS_ID, "Playlists"),
    )

    private fun libraryChildren(): List<MediaItem> = listOf(
        browsable(ARTISTS_ID, "Artists"),
    )

    private fun artistNode(id: String, name: String, coverArt: String?): MediaItem =
        browsable("$ARTIST_PREFIX$id", name, coverArtUri(coverArt))

    private fun albumNode(id: String, name: String, coverArt: String?): MediaItem =
        browsable("$ALBUM_PREFIX$id", name, coverArtUri(coverArt))

    private fun playlistNode(id: String, name: String, coverArt: String?): MediaItem =
        browsable("$PLAYLIST_PREFIX$id", name, coverArtUri(coverArt))

    private fun browsable(id: String, title: String, artwork: Uri? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(artwork)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun trackLeaf(track: Track): MediaItem {
        val streamUrl = runBlocking { library.buildStreamUrl(track.id) }
            ?: return MediaItem.EMPTY
        val artworkUri = coverArtUri(track.coverArt)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setTrackNumber(track.track)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(streamUrl))
                    .build()
            )
            .setMediaMetadata(metadata)
            .build()
    }

    private fun coverArtUri(coverArtId: String?): Uri? {
        if (coverArtId.isNullOrBlank()) return null
        val url = runBlocking { library.buildCoverArtUrl(coverArtId, size = 200) } ?: return null
        return Uri.parse(url)
    }

    private companion object {
        const val ROOT_ID = "root"
        const val RECENT_ID = "recent"
        const val LIBRARY_ID = "library"
        const val PLAYLISTS_ID = "playlists"
        const val ARTISTS_ID = "artists"
        const val ARTIST_PREFIX = "artist:"
        const val ALBUM_PREFIX = "album:"
        const val PLAYLIST_PREFIX = "playlist:"
    }
}
