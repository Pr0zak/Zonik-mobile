package com.zonik.app.data.repository

import com.zonik.app.data.api.SubsonicApi
import com.zonik.app.data.db.*
import com.zonik.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val api: SubsonicApi,
    private val database: ZonikDatabase
) {
    fun getArtists(): Flow<List<Artist>> =
        database.artistDao().getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAlbums(): Flow<List<Album>> =
        database.albumDao().getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getRecentAlbums(limit: Int = 20): Flow<List<Album>> =
        database.albumDao().getRecent(limit).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAlbumsByArtist(artistId: String): Flow<List<Album>> =
        database.albumDao().getByArtist(artistId).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getTracksByAlbum(albumId: String): Flow<List<Track>> =
        database.trackDao().getByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAlbumDetail(albumId: String): Pair<Album, List<Track>> {
        val response = api.getAlbum(albumId)
        val albumDetail = response.response.album ?: throw Exception("Album not found")
        val album = Album(
            id = albumDetail.id,
            name = albumDetail.name,
            artist = albumDetail.artist,
            artistId = albumDetail.artistId,
            coverArt = albumDetail.coverArt,
            year = albumDetail.year,
            songCount = albumDetail.songCount,
            duration = albumDetail.duration,
            genre = albumDetail.genre
        )
        val tracks = albumDetail.song.map { it.toDomain() }

        // Cache tracks
        database.trackDao().upsertAll(tracks.map { TrackEntity.fromDomain(it) })

        return album to tracks
    }

    suspend fun getArtistDetail(artistId: String): Pair<Artist, List<Album>> {
        val response = api.getArtist(artistId)
        val detail = response.response.artist ?: throw Exception("Artist not found")
        val artist = Artist(
            id = detail.id,
            name = detail.name,
            albumCount = detail.albumCount,
            coverArt = detail.coverArt
        )
        val albums = detail.album.map { it.toDomain() }
        return artist to albums
    }

    suspend fun search(query: String): Triple<List<Artist>, List<Album>, List<Track>> {
        val response = api.search3(query)
        val result = response.response.searchResult3
        return Triple(
            result?.artist?.map { it.toDomain() } ?: emptyList(),
            result?.album?.map { it.toDomain() } ?: emptyList(),
            result?.song?.map { it.toDomain() } ?: emptyList()
        )
    }

    suspend fun getRandomSongs(count: Int = 50, genre: String? = null): List<Track> {
        val response = api.getRandomSongs(count, genre)
        return response.response.randomSongs?.song?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun getGenres(): List<Genre> {
        val response = api.getGenres()
        return response.response.genres?.genre?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun getPlaylists(): List<Playlist> {
        val response = api.getPlaylists()
        return response.response.playlists?.playlist?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        val response = api.getPlaylist(playlistId)
        return response.response.playlist?.entry?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun star(id: String) { api.star(id = id) }
    suspend fun unstar(id: String) { api.unstar(id = id) }
    suspend fun scrobble(id: String) { api.scrobble(id) }
    suspend fun setRating(id: String, rating: Int) { api.setRating(id, rating) }

    suspend fun getSimilarSongs(id: String, count: Int = 50): List<Track> {
        val response = api.getSimilarSongs2(id, count)
        return response.response.randomSongs?.song?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun syncArtists() {
        val response = api.getArtists()
        val artists = response.response.artists?.index?.flatMap { index ->
            index.artist.map { it.toDomain() }
        } ?: return

        val entities = artists.map { ArtistEntity.fromDomain(it) }
        database.artistDao().upsertAll(entities)
        database.artistDao().deleteNotIn(entities.map { it.id })
    }

    suspend fun syncAlbums() {
        val allAlbums = mutableListOf<SubsonicAlbum>()
        var offset = 0
        while (true) {
            val response = api.getAlbumList2(type = "alphabeticalByName", size = 500, offset = offset)
            val albums = response.response.albumList2?.album ?: break
            if (albums.isEmpty()) break
            allAlbums.addAll(albums)
            offset += albums.size
        }

        val entities = allAlbums.map { AlbumEntity.fromDomain(it.toDomain()) }
        database.albumDao().upsertAll(entities)
        database.albumDao().deleteNotIn(entities.map { it.id })
    }

    suspend fun fullSync() {
        syncArtists()
        syncAlbums()
    }

    fun buildStreamUrl(baseUrl: String, trackId: String, maxBitRate: Int = 0): String {
        return "${baseUrl.trimEnd('/')}/rest/stream.view?id=$trackId" +
            if (maxBitRate > 0) "&maxBitRate=$maxBitRate" else "" +
            "&estimateContentLength=true"
    }

    fun buildCoverArtUrl(baseUrl: String, coverArtId: String, size: Int = 300): String {
        return "${baseUrl.trimEnd('/')}/rest/getCoverArt.view?id=$coverArtId&size=$size"
    }
}
