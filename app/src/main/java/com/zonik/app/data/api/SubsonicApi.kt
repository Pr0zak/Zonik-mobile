package com.zonik.app.data.api

import com.zonik.app.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    @GET("rest/ping.view")
    suspend fun ping(): PingResponse

    @GET("rest/getArtists.view")
    suspend fun getArtists(): ArtistsResponse

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): ArtistDetailResponse

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): AlbumDetailResponse

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0
    ): AlbumListResponse

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("artistOffset") artistOffset: Int = 0,
        @Query("albumCount") albumCount: Int = 20,
        @Query("albumOffset") albumOffset: Int = 0,
        @Query("songCount") songCount: Int = 50,
        @Query("songOffset") songOffset: Int = 0
    ): SearchResponse

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 50,
        @Query("genre") genre: String? = null
    ): RandomSongsResponse

    @GET("rest/getGenres.view")
    suspend fun getGenres(): GenresResponse

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(): PlaylistsResponse

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): PlaylistDetailResponse

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(): com.zonik.app.model.Starred2Response

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): StarResponse

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): StarResponse

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean = true
    ): ScrobbleResponse

    @GET("rest/setRating.view")
    suspend fun setRating(
        @Query("id") id: String,
        @Query("rating") rating: Int
    ): StarResponse

    @GET("rest/getSimilarSongs2.view")
    suspend fun getSimilarSongs2(
        @Query("id") id: String,
        @Query("count") count: Int = 50
    ): RandomSongsResponse

    @GET("rest/getSongsByGenre.view")
    suspend fun getSongsByGenre(
        @Query("genre") genre: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0
    ): SongsByGenreResponse
}
