package com.zonik.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("SELECT COUNT(*) FROM artists")
    fun count(): Flow<Int>

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    fun getByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums ORDER BY rowid DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<AlbumEntity>>

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT COUNT(*) FROM albums")
    fun count(): Flow<Int>

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun getAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY track")
    fun getByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY rowid DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE starred = 1 ORDER BY title COLLATE NOCASE")
    suspend fun getStarred(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE starred = 0 ORDER BY title COLLATE NOCASE")
    suspend fun getUnstarred(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    fun count(): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration), 0) FROM tracks")
    fun totalDuration(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM tracks")
    fun totalSize(): Flow<Long>

    @Query("SELECT COUNT(DISTINCT genre) FROM tracks WHERE genre IS NOT NULL")
    fun genreCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET markedForDeletion = :marked WHERE id = :id")
    suspend fun setMarkedForDeletion(id: String, marked: Boolean)

    @Query("SELECT * FROM tracks WHERE markedForDeletion = 1 ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, track")
    fun getMarkedForDeletion(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE markedForDeletion = 1")
    fun markedForDeletionCount(): Flow<Int>

    @Query("SELECT id FROM tracks WHERE markedForDeletion = 1")
    suspend fun getMarkedForDeletionIds(): List<String>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}

@Dao
interface PendingScrobbleDao {
    @Query("SELECT * FROM pending_scrobbles ORDER BY timestamp")
    suspend fun getAll(): List<PendingScrobbleEntity>

    @Insert
    suspend fun insert(scrobble: PendingScrobbleEntity)

    @Delete
    suspend fun delete(scrobble: PendingScrobbleEntity)

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    fun count(): Flow<Int>

    @Query("DELETE FROM pending_scrobbles")
    suspend fun deleteAll()
}
