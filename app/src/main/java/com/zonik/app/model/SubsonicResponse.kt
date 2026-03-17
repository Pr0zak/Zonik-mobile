package com.zonik.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponse<T>(
    @SerialName("subsonic-response")
    val response: SubsonicEnvelope<T>
)

@Serializable
data class SubsonicEnvelope<T>(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null
) {
    val isOk: Boolean get() = status == "ok"
}

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String
)

@Serializable
data class PingResponse(
    @SerialName("subsonic-response")
    val response: SubsonicEnvelope<Unit>
)

@Serializable
data class ArtistsResponse(
    @SerialName("subsonic-response")
    val response: ArtistsEnvelope
)

@Serializable
data class ArtistsEnvelope(
    val status: String,
    val version: String,
    val artists: ArtistsData? = null
)

@Serializable
data class ArtistsData(
    val index: List<ArtistIndex> = emptyList()
)

@Serializable
data class ArtistIndex(
    val name: String,
    val artist: List<SubsonicArtist> = emptyList()
)

@Serializable
data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val starred: String? = null
) {
    fun toDomain() = Artist(
        id = id,
        name = name,
        albumCount = albumCount,
        coverArt = coverArt,
        starred = starred != null
    )
}

@Serializable
data class AlbumListResponse(
    @SerialName("subsonic-response")
    val response: AlbumListEnvelope
)

@Serializable
data class AlbumListEnvelope(
    val status: String,
    val version: String,
    val albumList2: AlbumListData? = null
)

@Serializable
data class AlbumListData(
    val album: List<SubsonicAlbum> = emptyList()
)

@Serializable
data class ArtistDetailResponse(
    @SerialName("subsonic-response")
    val response: ArtistDetailEnvelope
)

@Serializable
data class ArtistDetailEnvelope(
    val status: String,
    val version: String,
    val artist: SubsonicArtistDetail? = null
)

@Serializable
data class SubsonicArtistDetail(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val album: List<SubsonicAlbum> = emptyList()
)

@Serializable
data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val genre: String? = null,
    val starred: String? = null
) {
    fun toDomain() = Album(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        coverArt = coverArt,
        year = year,
        songCount = songCount,
        duration = duration,
        genre = genre,
        starred = starred != null
    )
}

@Serializable
data class AlbumDetailResponse(
    @SerialName("subsonic-response")
    val response: AlbumDetailEnvelope
)

@Serializable
data class AlbumDetailEnvelope(
    val status: String,
    val version: String,
    val album: SubsonicAlbumDetail? = null
)

@Serializable
data class SubsonicAlbumDetail(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val genre: String? = null,
    val song: List<SubsonicTrack> = emptyList()
)

@Serializable
data class SubsonicTrack(
    val id: String,
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
    val starred: String? = null
) {
    fun toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        coverArt = coverArt,
        duration = duration,
        track = track,
        year = year,
        genre = genre,
        bitRate = bitRate,
        size = size,
        suffix = suffix,
        contentType = contentType,
        path = path,
        starred = starred != null
    )
}

@Serializable
data class SearchResponse(
    @SerialName("subsonic-response")
    val response: SearchEnvelope
)

@Serializable
data class SearchEnvelope(
    val status: String,
    val version: String,
    val searchResult3: SearchResult? = null
)

@Serializable
data class SearchResult(
    val artist: List<SubsonicArtist> = emptyList(),
    val album: List<SubsonicAlbum> = emptyList(),
    val song: List<SubsonicTrack> = emptyList()
)

@Serializable
data class RandomSongsResponse(
    @SerialName("subsonic-response")
    val response: RandomSongsEnvelope
)

@Serializable
data class RandomSongsEnvelope(
    val status: String,
    val version: String,
    val randomSongs: RandomSongsData? = null
)

@Serializable
data class RandomSongsData(
    val song: List<SubsonicTrack> = emptyList()
)

@Serializable
data class GenresResponse(
    @SerialName("subsonic-response")
    val response: GenresEnvelope
)

@Serializable
data class GenresEnvelope(
    val status: String,
    val version: String,
    val genres: GenresData? = null
)

@Serializable
data class GenresData(
    val genre: List<SubsonicGenre> = emptyList()
)

@Serializable
data class SubsonicGenre(
    val value: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
) {
    fun toDomain() = Genre(
        name = value,
        songCount = songCount,
        albumCount = albumCount
    )
}

@Serializable
data class PlaylistsResponse(
    @SerialName("subsonic-response")
    val response: PlaylistsEnvelope
)

@Serializable
data class PlaylistsEnvelope(
    val status: String,
    val version: String,
    val playlists: PlaylistsData? = null
)

@Serializable
data class PlaylistsData(
    val playlist: List<SubsonicPlaylist> = emptyList()
)

@Serializable
data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val owner: String? = null
) {
    fun toDomain() = Playlist(
        id = id,
        name = name,
        songCount = songCount,
        duration = duration,
        coverArt = coverArt,
        owner = owner
    )
}

@Serializable
data class PlaylistDetailResponse(
    @SerialName("subsonic-response")
    val response: PlaylistDetailEnvelope
)

@Serializable
data class PlaylistDetailEnvelope(
    val status: String,
    val version: String,
    val playlist: SubsonicPlaylistDetail? = null
)

@Serializable
data class SubsonicPlaylistDetail(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<SubsonicTrack> = emptyList()
)

@Serializable
data class Starred2Data(
    val song: List<SubsonicTrack>? = null,
    val album: List<SubsonicAlbum>? = null,
    val artist: List<SubsonicArtist>? = null
)

@Serializable
data class Starred2Envelope(
    val status: String,
    val version: String,
    val starred2: Starred2Data? = null
)

@Serializable
data class Starred2Response(
    @SerialName("subsonic-response")
    val response: Starred2Envelope
)

@Serializable
data class StarResponse(
    @SerialName("subsonic-response")
    val response: SubsonicEnvelope<Unit>
)

@Serializable
data class ScrobbleResponse(
    @SerialName("subsonic-response")
    val response: SubsonicEnvelope<Unit>
)
