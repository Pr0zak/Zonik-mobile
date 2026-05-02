package com.zonik.app.data.repository

import androidx.room.withTransaction
import com.zonik.app.data.api.BulkDeleteTracksRequest
import com.zonik.app.data.api.SubsonicApi
import com.zonik.app.data.api.ZonikApi
import com.zonik.app.data.db.*
import com.zonik.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val api: SubsonicApi,
    private val zonikApi: ZonikApi,
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

    suspend fun getTracksByIds(ids: List<String>): List<Track> {
        val entities = database.trackDao().getByIds(ids)
        val entityMap = entities.associateBy { it.id }
        return ids.mapNotNull { id -> entityMap[id]?.toDomain() }
    }

    suspend fun getStarredTracks(): List<Track> =
        database.trackDao().getStarred().map { it.toDomain() }

    suspend fun getUnstarredTracks(): List<Track> =
        database.trackDao().getUnstarred().map { it.toDomain() }

    fun getAllTracks(): Flow<List<Track>> =
        database.trackDao().getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getAllTracksRecentFirst(): Flow<List<Track>> =
        database.trackDao().getAllRecentFirst().map { entities ->
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
        // markedForDeletion is derived from userRating == 1 in toDomain()
        val tracks = albumDetail.song.map { song -> song.toDomain() }

        val offlineIds = database.trackDao().getOfflineCachedIds().toSet()
        database.trackDao().upsertAll(tracks.map {
            val entity = TrackEntity.fromDomain(it)
            if (entity.id in offlineIds) entity.copy(offlineCached = true) else entity
        })

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

    suspend fun ping(): Boolean {
        return try {
            val response = api.ping()
            response.response.status == "ok"
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getRandomSongs(count: Int = 50, genre: String? = null): List<Track> {
        return try {
            val response = api.getRandomSongs(count, genre)
            response.response.randomSongs?.song?.map { it.toDomain() } ?: emptyList()
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("LibraryRepo", "getRandomSongs failed: ${e.message}")
            emptyList()
        }
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
        try {
            api.setRating(id, 1)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "Failed to set rating=1 for $id: ${e.message}")
        }
    }

    suspend fun unmarkForDeletion(id: String) {
        database.trackDao().setMarkedForDeletion(id, false)
        try {
            api.setRating(id, 0)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "Failed to clear rating for $id: ${e.message}")
        }
    }

    fun getTracksMarkedForDeletion(): Flow<List<Track>> =
        database.trackDao().getMarkedForDeletion().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getOfflineCachedTracks(): Flow<List<Track>> =
        database.trackDao().getOfflineCached().map { entities ->
            entities.map { it.toDomain() }
        }

    fun markedForDeletionCount(): Flow<Int> =
        database.trackDao().markedForDeletionCount()

    suspend fun deleteTracksFromServer(trackIds: List<String>) {
        zonikApi.bulkDeleteTracks(BulkDeleteTracksRequest(trackIds))
        // Remove from local DB after server confirms
        trackIds.chunked(900).forEach { chunk ->
            database.trackDao().deleteByIds(chunk)
        }
    }

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
        try {
            api.star(id = id)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "Star API failed for $id: ${e.message}")
        }
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
        try {
            api.unstar(id = id)
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "Unstar API failed for $id: ${e.message}")
        }
        database.trackDao().getById(id)?.let {
            database.trackDao().upsertAll(listOf(it.copy(starred = false)))
        }
        database.albumDao().getById(id)?.let {
            database.albumDao().upsertAll(listOf(it.copy(starred = false)))
        }
    }
    suspend fun scrobble(id: String, time: Long? = null) { api.scrobble(id, submission = true, time = time) }
    suspend fun scrobbleNowPlaying(id: String) { api.scrobble(id, submission = false) }
    suspend fun setRating(id: String, rating: Int) { api.setRating(id, rating) }

    suspend fun getNowPlaying(): List<com.zonik.app.model.NowPlayingEntry> {
        return try {
            api.getNowPlaying().response.nowPlaying?.entry ?: emptyList()
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "getNowPlaying failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSimilarSongs(id: String, count: Int = 50): List<Track> {
        val response = api.getSimilarSongs2(id, count)
        return response.response.randomSongs?.song?.map { it.toDomain() } ?: emptyList()
    }

    /**
     * Start radio from a track: server-first similar songs with local fallbacks.
     * 1. getSimilarSongs2 (server Last.fm data)
     * 2. Same-genre tracks from server
     * 3. Same-artist tracks from local DB
     * 4. Random songs fallback
     */
    suspend fun startRadio(trackId: String, genre: String?, artistId: String?): List<Track> {
        val seen = mutableSetOf(trackId)
        val result = mutableListOf<Track>()

        // 1. Try getSimilarSongs2
        try {
            val similar = getSimilarSongs(trackId, 75)
            for (t in similar) {
                if (t.id !in seen) { seen.add(t.id); result.add(t) }
            }
        } catch (e: Exception) {
            com.zonik.app.data.DebugLog.w("Library", "getSimilarSongs2 failed: ${e.message}")
        }

        // 2. If <10 results, add same-genre tracks from server
        if (result.size < 10 && !genre.isNullOrBlank()) {
            try {
                val genreTracks = api.getSongsByGenre(genre, 50).response.songsByGenre?.song?.map { it.toDomain() } ?: emptyList()
                for (t in genreTracks) {
                    if (t.id !in seen) { seen.add(t.id); result.add(t) }
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("Library", "getSongsByGenre fallback failed: ${e.message}")
            }
        }

        // 3. If still <10, add same-artist tracks from local DB
        if (result.size < 10 && !artistId.isNullOrBlank()) {
            try {
                val artistTracks = database.trackDao().getByArtistId(artistId, 50).map { it.toDomain() }
                for (t in artistTracks) {
                    if (t.id !in seen) { seen.add(t.id); result.add(t) }
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("Library", "Artist DB fallback failed: ${e.message}")
            }
        }

        // 4. Final fallback: random songs
        if (result.size < 10) {
            try {
                val random = getRandomSongs(50)
                for (t in random) {
                    if (t.id !in seen) { seen.add(t.id); result.add(t) }
                }
            } catch (e: Exception) {
                com.zonik.app.data.DebugLog.w("Library", "Random fallback failed: ${e.message}")
            }
        }

        com.zonik.app.data.DebugLog.d("Library", "startRadio: ${result.size} tracks (from track $trackId)")
        return result.shuffled()
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
        database.withTransaction {
            database.artistDao().upsertAll(entities)
            val newIds = entities.map { it.id }.toSet()
            val existingIds = database.artistDao().getAllIds()
            val toDelete = existingIds.filter { it !in newIds }
            toDelete.chunked(900).forEach { chunk ->
                database.artistDao().deleteByIds(chunk)
            }
        }
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
        database.withTransaction {
            database.albumDao().upsertAll(entities)
            val newIds = entities.map { it.id }.toSet()
            val existingIds = database.albumDao().getAllIds()
            val toDelete = existingIds.filter { it !in newIds }
            toDelete.chunked(900).forEach { chunk ->
                database.albumDao().deleteByIds(chunk)
            }
        }
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

        // markedForDeletion is now server-authoritative (userRating == 1); starred via getStarred2
        val serverMarkedCount = allTracks.count { it.userRating == 1 }
        com.zonik.app.data.DebugLog.d("Sync", "Server has ${serverStarredIds.size} starred, ${serverMarkedCount} flagged for deletion")
        // Preserve offlineCached status from existing DB rows (upsert would overwrite with false)
        val existingOfflineIds = database.trackDao().getOfflineCachedIds().toSet()
        val entities = allTracks.map { subsonicTrack ->
            var entity = TrackEntity.fromDomain(subsonicTrack.toDomain())
            if (entity.id in serverStarredIds) entity = entity.copy(starred = true)
            if (entity.id in existingOfflineIds) entity = entity.copy(offlineCached = true)
            entity
        }
        database.withTransaction {
            database.trackDao().upsertAll(entities)
            val newIds = entities.map { it.id }.toSet()
            val existingIds = database.trackDao().getAllIds()
            val toDelete = existingIds.filter { it !in newIds }
            toDelete.chunked(900).forEach { chunk ->
                database.trackDao().deleteByIds(chunk)
            }
        }
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
}
