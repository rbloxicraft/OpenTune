/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.navidrome.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response")
    val subsonicResponse: SubsonicResponse = SubsonicResponse(),
)

@Serializable
data class SubsonicResponse(
    val status: String = "failed",
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,
    val artists: Artists? = null,
    val albumList2: AlbumList? = null,
    val searchResult3: SearchResult3? = null,
    val artist: ArtistAndAlbums? = null,
    val album: AlbumWithSongs? = null,
)

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class Artists(
    val lastModified: Long? = null,
    val ignoredArticles: String? = null,
    val index: List<ArtistIndex> = emptyList(),
) {
    val allArtists: List<Artist>
        get() = index.flatMap { it.artist }
}

@Serializable
data class ArtistIndex(
    val name: String? = null,
    val artist: List<Artist> = emptyList(),
)

@Serializable
data class Artist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int? = null,
    // Subsonic sends ISO-8601 datetimes ("2024-01-15T10:30:00.000Z") here.
    val starred: String? = null,
)

/** Response of getArtist: the artist with their albums. */
@Serializable
data class ArtistAndAlbums(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val album: List<Album> = emptyList(),
)

@Serializable
data class AlbumList(
    val album: List<Album> = emptyList(),
)

@Serializable
data class Album(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val playCount: Long? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
)

/** Response of getAlbum: the album with its songs. */
@Serializable
data class AlbumWithSongs(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val playCount: Long? = null,
    val created: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Song(
    val id: String = "",
    val parent: String? = null,
    val isDir: Boolean? = null,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val path: String? = null,
    val playCount: Long? = null,
    val discNumber: Int? = null,
    val created: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val type: String? = null,
)
