package com.zonik.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val starred: Boolean = false
)

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val genre: String? = null,
    val starred: Boolean = false
)

@Serializable
data class Track(
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
    val transcodedSuffix: String? = null,
    val transcodedContentType: String? = null,
    val path: String? = null,
    val starred: Boolean = false,
    val markedForDeletion: Boolean = false
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val owner: String? = null
)

@Serializable
data class Genre(
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)

data class ServerConfig(
    val url: String,
    val username: String,
    val apiKey: String
)

enum class RepeatMode { OFF, ALL, ONE }
enum class ShuffleMode { OFF, SHUFFLE, TRUE_RANDOM }
