package com.zonik.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zonik.app.model.Album
import com.zonik.app.model.Artist
import com.zonik.app.model.Track

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val starred: Boolean = false
) {
    fun toDomain() = Artist(id, name, albumCount, coverArt, starred)

    companion object {
        fun fromDomain(artist: Artist) = ArtistEntity(
            id = artist.id,
            name = artist.name,
            albumCount = artist.albumCount,
            coverArt = artist.coverArt,
            starred = artist.starred
        )
    }
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val genre: String? = null,
    val starred: Boolean = false
) {
    fun toDomain() = Album(id, name, artist, artistId, coverArt, year, songCount, duration, genre, starred)

    companion object {
        fun fromDomain(album: Album) = AlbumEntity(
            id = album.id,
            name = album.name,
            artist = album.artist,
            artistId = album.artistId,
            coverArt = album.coverArt,
            year = album.year,
            songCount = album.songCount,
            duration = album.duration,
            genre = album.genre,
            starred = album.starred
        )
    }
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String? = null,
    val album: String = "",
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRate: Int? = null,
    val size: Long? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val path: String? = null,
    val starred: Boolean = false,
    val markedForDeletion: Boolean = false
) {
    fun toDomain() = Track(id, title, artist, artistId, album, albumId, coverArt, duration, track, year, genre, bitRate, size, suffix, contentType, path, starred, markedForDeletion)

    companion object {
        fun fromDomain(track: Track) = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            artistId = track.artistId,
            album = track.album,
            albumId = track.albumId,
            coverArt = track.coverArt,
            duration = track.duration,
            track = track.track,
            year = track.year,
            genre = track.genre,
            bitRate = track.bitRate,
            size = track.size,
            suffix = track.suffix,
            contentType = track.contentType,
            path = track.path,
            starred = track.starred,
            markedForDeletion = track.markedForDeletion
        )
    }
}

@Entity(tableName = "pending_scrobbles")
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackTitle: String,
    val trackArtist: String,
    val trackAlbum: String,
    val timestamp: Long,
    val duration: Int
)
