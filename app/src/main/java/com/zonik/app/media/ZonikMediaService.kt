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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
import java.security.MessageDigest
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class ZonikMediaService : MediaLibraryService() {

    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var okHttpClient: OkHttpClient

    private var mediaLibrarySession: MediaLibrarySession? = null

    companion object {
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
        private const val TRUE_RANDOM_ID = "true_random"

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
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                com.zonik.app.data.DebugLog.d("MediaService", "ExoPlayer fetching: ${url.take(100)}...")
                val response = chain.proceed(chain.request())
                com.zonik.app.data.DebugLog.d("MediaService", "ExoPlayer response: ${response.code} ${response.header("Content-Type")} ${response.header("Content-Length") ?: "chunked"}")
                response
            }
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(streamClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
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

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                com.zonik.app.data.DebugLog.e("MediaService", "ExoPlayer error: ${error.errorCodeName} - ${error.message}")
                com.zonik.app.data.DebugLog.e("MediaService", "Cause: ${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")
                error.cause?.cause?.let {
                    com.zonik.app.data.DebugLog.e("MediaService", "Root cause: ${it.javaClass.simpleName}: ${it.message}")
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
            }
        })

        val callback = BrowseTreeCallback()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    // -- Helpers --

    private fun getServerUrl(): String? {
        return runBlocking {
            settingsRepository.serverConfig.firstOrNull()?.url?.trimEnd('/')
        }
    }

    private fun coverArtUri(coverArtId: String?): Uri? {
        if (coverArtId == null) return null
        val serverUrl = getServerUrl() ?: return null
        return Uri.parse(buildAuthenticatedUrl(
            "$serverUrl/rest/getCoverArt.view?id=$coverArtId&size=300"
        ))
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

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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
        if (artworkUri != null) metadata.setArtworkUri(artworkUri)
        if (trackNumber != null) metadata.setTrackNumber(trackNumber)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
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
                val (artists, albums, tracks) = runBlocking {
                    libraryRepository.search(query)
                }
                val results = mutableListOf<MediaItem>()
                albums.forEach { results.add(albumToMediaItem(it)) }
                artists.forEach { results.add(artistToMediaItem(it)) }
                tracks.forEach { results.add(trackToMediaItem(it)) }
                session.notifySearchResultChanged(browser, query, results.size, params)
            } catch (_: Exception) {
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
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
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
        return listOf(
            buildBrowsableItem(
                id = RECENT_ID,
                title = "Recently Added",
                extras = gridExtras()
            ),
            buildBrowsableItem(
                id = LIBRARY_ID,
                title = "Library",
                extras = listExtras()
            ),
            buildBrowsableItem(
                id = PLAYLISTS_ID,
                title = "Playlists",
                extras = listExtras()
            ),
            buildBrowsableItem(
                id = MIX_ID,
                title = "Mix",
                extras = listExtras()
            )
        )
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
            MediaItem.Builder()
                .setMediaId(SHUFFLE_MIX_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Shuffle Mix")
                        .setSubtitle("Shuffled random songs")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build(),
            MediaItem.Builder()
                .setMediaId(TRUE_RANDOM_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("True Random")
                        .setSubtitle("Completely random songs")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )
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
            mediaId == SHUFFLE_MIX_ID -> buildPlayableItem(id = SHUFFLE_MIX_ID, title = "Shuffle Mix")
            mediaId == TRUE_RANDOM_ID -> buildPlayableItem(id = TRUE_RANDOM_ID, title = "True Random")
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
