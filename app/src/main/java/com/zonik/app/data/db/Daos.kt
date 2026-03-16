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

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

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
