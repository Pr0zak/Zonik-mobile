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
    fun artistCount(): Flow<Int> = database.artistDao().count()
    fun albumCount(): Flow<Int> = database.albumDao().count()
    fun trackCount(): Flow<Int> = database.trackDao().count()
    fun totalDuration(): Flow<Long> = database.trackDao().totalDuration()
    fun totalSize(): Flow<Long> = database.trackDao().totalSize()
    fun genreCount(): Flow<Int> = database.trackDao().genreCount()

    suspend fun getServerInfo(): Triple<String, String?, String?> {
        val response = api.ping()
        val envelope = response.response
        return Triple(envelope.version, envelope.serverVersion, envelope.type)
    }

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

    fun getRecentTracks(limit: Int = 20): Flow<List<Track>> =
        database.trackDao().getRecent(limit).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getTrackById(id: String): Track? =
        database.trackDao().getById(id)?.toDomain()

    suspend fun getStarredTracks(): List<Track> =
        database.trackDao().getStarred().map { it.toDomain() }

    suspend fun getUnstarredTracks(): List<Track> =
        database.trackDao().getUnstarred().map { it.toDomain() }

    fun getAllTracks(): Flow<List<Track>> =
        database.trackDao().getAll().map { entities ->
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
        // Preserve markedForDeletion flags when caching
        val existingMarked = database.trackDao().getMarkedForDeletionIds().toSet()
        val tracks = albumDetail.song.map { song ->
            val track = song.toDomain()
            if (track.id in existingMarked) track.copy(markedForDeletion = true) else track
        }

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

    suspend fun markForDeletion(id: String) {
        database.trackDao().setMarkedForDeletion(id, true)
    }

    suspend fun unmarkForDeletion(id: String) {
        database.trackDao().setMarkedForDeletion(id, false)
    }

    fun getTracksMarkedForDeletion(): Flow<List<Track>> =
        database.trackDao().getMarkedForDeletion().map { entities ->
            entities.map { it.toDomain() }
        }

    fun markedForDeletionCount(): Flow<Int> =
        database.trackDao().markedForDeletionCount()

    // Stats
    suspend fun getFormatDistribution() = database.trackDao().getFormatDistribution()
    suspend fun getBitrateDistribution() = database.trackDao().getBitrateDistribution()
    suspend fun getTopGenres(limit: Int = 15) = database.trackDao().getTopGenres(limit)
    suspend fun getYearDistribution() = database.trackDao().getYearDistribution()
    suspend fun getStarredTrackCount() = database.trackDao().starredCount()
    suspend fun getStarredAlbumCount() = database.albumDao().starredCount()
    suspend fun getMarkedForDeletionCount() = database.trackDao().markedCount()
    suspend fun getTopArtists(limit: Int = 10) = database.artistDao().getTopByAlbumCount(limit).map { it.toDomain() }

    suspend fun getMostPlayedAlbums(count: Int = 10): List<Album> {
        return try {
            val response = api.getAlbumList2("frequent", count)
            response.response.albumList2?.album?.map { it.toDomain() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getRecentlyPlayedAlbums(count: Int = 10): List<Album> {
        return try {
            val response = api.getAlbumList2("recent", count)
            response.response.albumList2?.album?.map { it.toDomain() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun star(id: String) {
        api.star(id = id)
        val track = database.trackDao().getById(id)
        if (track != null) {
            database.trackDao().upsertAll(listOf(track.copy(starred = true)))
            com.zonik.app.data.DebugLog.d("Library", "Starred track in DB: $id (was ${track.starred})")
        } else {
            com.zonik.app.data.DebugLog.w("Library", "Star: track $id not found in DB")
        }
        database.albumDao().getById(id)?.let {
            database.albumDao().upsertAll(listOf(it.copy(starred = true)))
        }
    }

    suspend fun unstar(id: String) {
        api.unstar(id = id)
        database.trackDao().getById(id)?.let {
            database.trackDao().upsertAll(listOf(it.copy(starred = false)))
        }
        database.albumDao().getById(id)?.let {
            database.albumDao().upsertAll(listOf(it.copy(starred = false)))
        }
    }
    suspend fun scrobble(id: String) { api.scrobble(id) }
    suspend fun setRating(id: String, rating: Int) { api.setRating(id, rating) }

    suspend fun getSimilarSongs(id: String, count: Int = 50): List<Track> {
        val response = api.getSimilarSongs2(id, count)
        return response.response.randomSongs?.song?.map { it.toDomain() } ?: emptyList()
    }

    /**
     * Fast sync using search3 with empty query (Symfonium approach).
     * Fetches all artists, albums, and tracks in bulk via paginated search3 calls.
     */
    suspend fun syncArtists(onProgress: (fetched: Int) -> Unit = {}): Int {
        val allArtists = mutableListOf<SubsonicArtist>()
        var offset = 0
        while (true) {
            val response = api.search3(
                query = "",
                artistCount = 500, artistOffset = offset,
                albumCount = 0, albumOffset = 0,
                songCount = 0, songOffset = 0
            )
            val artists = response.response.searchResult3?.artist ?: break
            if (artists.isEmpty()) break
            allArtists.addAll(artists)
            offset += artists.size
            onProgress(allArtists.size)
        }

        val entities = allArtists.map { ArtistEntity.fromDomain(it.toDomain()) }
        database.artistDao().upsertAll(entities)
        database.artistDao().deleteNotIn(entities.map { it.id })
        return entities.size
    }

    suspend fun syncAlbums(onProgress: (fetched: Int) -> Unit = {}): Int {
        val allAlbums = mutableListOf<SubsonicAlbum>()
        var offset = 0
        while (true) {
            val response = api.search3(
                query = "",
                artistCount = 0, artistOffset = 0,
                albumCount = 500, albumOffset = offset,
                songCount = 0, songOffset = 0
            )
            val albums = response.response.searchResult3?.album ?: break
            if (albums.isEmpty()) break
            allAlbums.addAll(albums)
            offset += albums.size
            onProgress(allAlbums.size)
        }

        val entities = allAlbums.map { AlbumEntity.fromDomain(it.toDomain()) }
        database.albumDao().upsertAll(entities)
        database.albumDao().deleteNotIn(entities.map { it.id })
        return entities.size
    }

    suspend fun syncAllTracks(onProgress: (fetched: Int) -> Unit = {}): Int {
        val allTracks = mutableListOf<SubsonicTrack>()
        var offset = 0
        while (true) {
            val response = api.search3(
                query = "",
                artistCount = 0, artistOffset = 0,
                albumCount = 0, albumOffset = 0,
                songCount = 500, songOffset = offset
            )
            val tracks = response.response.searchResult3?.song ?: break
            if (tracks.isEmpty()) break
            allTracks.addAll(tracks)
            offset += tracks.size
            onProgress(allTracks.size)
        }

        // Fetch server-side starred tracks for authoritative starred status
        val serverStarredIds = try {
            val starred2 = api.getStarred2()
            val ids = mutableSetOf<String>()
            starred2.response.starred2?.song?.forEach { ids.add(it.id) }
            com.zonik.app.data.DebugLog.d("Sync", "Server has ${ids.size} starred tracks")
            ids
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Sync", "Failed to fetch starred2: ${e.message}")
            emptySet()
        }

        // Preserve local flags (markedForDeletion, starred) from existing tracks
        val existingMarked = database.trackDao().getMarkedForDeletionIds()
        val existingStarredIds = database.trackDao().getStarred().map { it.id }.toSet()
        // Merge: starred if server OR local DB says so
        val allStarredIds = serverStarredIds + existingStarredIds
        com.zonik.app.data.DebugLog.d("Sync", "Preserving ${allStarredIds.size} starred (${serverStarredIds.size} server + ${existingStarredIds.size} local), ${existingMarked.size} marked for deletion")
        val entities = allTracks.map { subsonicTrack ->
            var entity = TrackEntity.fromDomain(subsonicTrack.toDomain())
            if (entity.id in existingMarked) entity = entity.copy(markedForDeletion = true)
            if (entity.id in allStarredIds) entity = entity.copy(starred = true)
            entity
        }
        database.trackDao().upsertAll(entities)
        database.trackDao().deleteNotIn(entities.map { it.id })
        return entities.size
    }

    suspend fun syncPlaylists(): Int {
        val playlists = getPlaylists()
        return playlists.size
    }

    suspend fun fullSync() {
        syncArtists()
        syncAlbums()
        syncAllTracks()
    }

    fun buildStreamUrl(baseUrl: String, trackId: String, maxBitRate: Int = 0): String {
        return "${baseUrl.trimEnd('/')}/rest/stream.view?id=$trackId" +
            (if (maxBitRate > 0) "&maxBitRate=$maxBitRate" else "") +
            "&estimateContentLength=true"
    }

    fun buildCoverArtUrl(baseUrl: String, coverArtId: String, size: Int = 300): String {
        return "${baseUrl.trimEnd('/')}/rest/getCoverArt.view?id=$coverArtId&size=$size"
    }
}
