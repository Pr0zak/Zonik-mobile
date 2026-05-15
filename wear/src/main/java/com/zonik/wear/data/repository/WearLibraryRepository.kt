package com.zonik.wear.data.repository

import com.zonik.core.api.SubsonicApi
import com.zonik.core.model.Album
import com.zonik.core.model.Artist
import com.zonik.core.model.Playlist
import com.zonik.core.model.ServerConfig
import com.zonik.core.model.Track

/**
 * Thin Subsonic facade for the wear UI. Only exposes the subset of endpoints
 * the watch actually needs — keeps the call sites obvious and avoids dragging
 * over the phone's much larger LibraryRepository.
 */
class WearLibraryRepository(
    private val api: SubsonicApi,
    private val settings: WearSettingsRepository,
) {

    suspend fun listArtists(): List<Artist> {
        val env = api.getArtists().response
        return env.artists?.index.orEmpty()
            .flatMap { it.artist }
            .map { it.toDomain() }
    }

    suspend fun listRecentAlbums(size: Int = 50, offset: Int = 0): List<Album> {
        val env = api.getAlbumList2(type = "newest", size = size, offset = offset).response
        return env.albumList2?.album.orEmpty().map { it.toDomain() }
    }

    suspend fun getArtistAlbums(artistId: String): List<Album> {
        val env = api.getArtist(artistId).response
        return env.artist?.album.orEmpty().map { it.toDomain() }
    }

    suspend fun getAlbumTracks(albumId: String): List<Track> {
        val env = api.getAlbum(albumId).response
        return env.album?.song.orEmpty().map { it.toDomain() }
    }

    suspend fun listPlaylists(): List<Playlist> {
        val env = api.getPlaylists().response
        return env.playlists?.playlist.orEmpty().map { it.toDomain() }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        val env = api.getPlaylist(playlistId).response
        return env.playlist?.entry.orEmpty().map { it.toDomain() }
    }

    /** Build a Subsonic stream URL the caller can hand to ExoPlayer or fetch directly. */
    suspend fun buildStreamUrl(trackId: String, maxBitRate: Int? = null): String? {
        val cfg = settings.current() ?: return null
        val bitrate = if (maxBitRate != null && maxBitRate > 0) "&maxBitRate=$maxBitRate" else ""
        return "${cfg.url.trimEnd('/')}/rest/stream.view?id=$trackId&estimateContentLength=true$bitrate${buildAuthParams(cfg)}"
    }

    /** Build a Subsonic getCoverArt URL for use with Coil. */
    suspend fun buildCoverArtUrl(coverArtId: String, size: Int = 200): String? {
        val cfg = settings.current() ?: return null
        return "${cfg.url.trimEnd('/')}/rest/getCoverArt.view?id=$coverArtId&size=$size${buildAuthParams(cfg)}"
    }

    private fun buildAuthParams(cfg: ServerConfig): String {
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = com.zonik.core.util.md5("${cfg.apiKey}$salt")
        return "&u=${cfg.username}&t=$token&s=$salt&v=1.16.1&c=ZonikWear"
    }
}
