/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.navidrome

import com.arturo254.opentune.navidrome.models.Album
import com.arturo254.opentune.navidrome.models.AlbumWithSongs
import com.arturo254.opentune.navidrome.models.ArtistAndAlbums
import com.arturo254.opentune.navidrome.models.Artists
import com.arturo254.opentune.navidrome.models.PlaylistRef
import com.arturo254.opentune.navidrome.models.PlaylistWithSongs
import com.arturo254.opentune.navidrome.models.SearchResult3
import com.arturo254.opentune.navidrome.models.SubsonicEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Client for Navidrome and other Subsonic-compatible servers.
 *
 * Authentication follows the Subsonic API 1.13+ token scheme:
 * `t = md5(password + s)` where `s` is a random salt sent alongside the token.
 */
object Navidrome {
    const val API_VERSION = "1.16.1"
    const val CLIENT_NAME = "OpenTune"

    // ID prefixes separating Navidrome entities from YouTube ids in the app database.
    const val SONG_ID_PREFIX = "nd_"
    const val ALBUM_ID_PREFIX = "nda_"
    const val ARTIST_ID_PREFIX = "ndar_"

    class SubsonicException(val code: Int, override val message: String) : Exception(message) {
        override fun toString(): String = "SubsonicException(code=$code, message=$message)"
    }

    data class ServerInfo(
        val apiVersion: String?,
        val serverType: String?,
        val serverVersion: String?,
        val openSubsonic: Boolean?,
    ) {
        val displayName: String
            get() = buildString {
                val type = serverType?.replaceFirstChar { it.uppercase() } ?: "Subsonic"
                append(type)
                serverVersion?.let { append(" $it") }
            }
    }

    enum class AlbumListType(val value: String) {
        NEWEST("newest"),
        RECENT("recent"),
        FREQUENT("frequent"),
        RANDOM("random"),
        ALPHABETICAL_BY_NAME("alphabeticalByName"),
        ALPHABETICAL_BY_ARTIST("alphabeticalByArtist"),
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            expectSuccess = false
        }
    }

    private val random = SecureRandom()

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun randomSalt(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Generates a salt that can be stored and reused for stable cover-art URLs. */
    fun generateSalt(): String = randomSalt()

    /**
     * Normalizes a user-entered server address: trims whitespace and trailing
     * slashes, and assumes plain http when no scheme is given.
     */
    fun normalizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun buildUrl(
        serverUrl: String,
        username: String,
        password: String,
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
        salt: String? = null,
    ): String {
        val saltValue = salt ?: randomSalt()
        return URLBuilder(normalizeServerUrl(serverUrl)).apply {
            // Append instead of replacing: Navidrome may live behind a reverse
            // proxy sub-path (e.g. https://host/navidrome -> /navidrome/rest/...).
            appendPathSegments("rest", endpoint)
            parameters.append("u", username)
            parameters.append("t", md5(password + saltValue))
            parameters.append("s", saltValue)
            parameters.append("v", API_VERSION)
            parameters.append("c", CLIENT_NAME)
            parameters.append("f", "json")
            extra.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()
    }

    private suspend fun request(
        serverUrl: String,
        username: String,
        password: String,
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
    ) = runCatching {
        val response = client.get(
            buildUrl(serverUrl, username, password, endpoint, extra)
        )
        val responseText = response.bodyAsText()
        val body = try {
            json.decodeFromString<SubsonicEnvelope>(responseText).subsonicResponse
        } catch (e: SerializationException) {
            val hint = if (responseText.trimStart().startsWith("<")) {
                // An HTML page from a reverse proxy: wrong address, missing
                // /navidrome sub-path, or not a Subsonic-compatible server.
                "The server did not return a Subsonic response. Check the address (it must " +
                    "be the URL where Navidrome's web UI answers, including any sub-path)."
            } else {
                // Valid JSON that doesn't fit the model — surface the exact
                // field mismatch so it can be fixed quickly.
                "Invalid Subsonic response: ${e.message?.take(200)}"
            }
            throw SubsonicException(code = ERROR_UNKNOWN, message = hint)
        }
        if (body.status != "ok") {
            throw SubsonicException(
                code = body.error?.code ?: ERROR_UNKNOWN,
                message = body.error?.message ?: "Request failed",
            )
        }
        body
    }

    /** Verifies the server address and credentials, returning server metadata on success. */
    suspend fun ping(serverUrl: String, username: String, password: String): Result<ServerInfo> =
        request(serverUrl, username, password, "ping.view").map { body ->
            ServerInfo(
                apiVersion = body.version,
                serverType = body.type,
                serverVersion = body.serverVersion,
                openSubsonic = body.openSubsonic,
            )
        }

    suspend fun getArtists(serverUrl: String, username: String, password: String): Result<Artists> =
        request(serverUrl, username, password, "getArtists.view").map { it.artists ?: Artists() }

    suspend fun getArtist(
        serverUrl: String,
        username: String,
        password: String,
        id: String,
    ): Result<ArtistAndAlbums> = request(
        serverUrl,
        username,
        password,
        "getArtist.view",
        mapOf("id" to id),
    ).map { it.artist ?: ArtistAndAlbums(id = id) }

    suspend fun getAlbum(
        serverUrl: String,
        username: String,
        password: String,
        id: String,
    ): Result<AlbumWithSongs> = request(
        serverUrl,
        username,
        password,
        "getAlbum.view",
        mapOf("id" to id),
    ).map { it.album ?: AlbumWithSongs(id = id) }

    suspend fun getAlbumList2(
        serverUrl: String,
        username: String,
        password: String,
        type: AlbumListType = AlbumListType.NEWEST,
        size: Int = 20,
        offset: Int = 0,
    ): Result<List<Album>> = request(
        serverUrl,
        username,
        password,
        "getAlbumList2.view",
        mapOf(
            "type" to type.value,
            "size" to size.toString(),
            "offset" to offset.toString(),
        ),
    ).map { it.albumList2?.album ?: emptyList() }

    suspend fun search3(
        serverUrl: String,
        username: String,
        password: String,
        query: String,
        artistCount: Int = 10,
        albumCount: Int = 10,
        songCount: Int = 20,
    ): Result<SearchResult3> = request(
        serverUrl,
        username,
        password,
        "search3.view",
        mapOf(
            "query" to query,
            "artistCount" to artistCount.toString(),
            "albumCount" to albumCount.toString(),
            "songCount" to songCount.toString(),
        ),
    ).map { it.searchResult3 ?: SearchResult3() }

    /** Lists the server's playlists (e.g. the one imported from playlist.m3u). */
    suspend fun getPlaylists(serverUrl: String, username: String, password: String): Result<List<PlaylistRef>> =
        request(serverUrl, username, password, "getPlaylists.view").map {
            it.playlists?.playlist ?: emptyList()
        }

    /** Fetches one playlist with its songs, in playlist order. */
    suspend fun getPlaylist(
        serverUrl: String,
        username: String,
        password: String,
        id: String,
    ): Result<PlaylistWithSongs> = request(
        serverUrl,
        username,
        password,
        "getPlaylist.view",
        mapOf("id" to id),
    ).map { it.playlist ?: PlaylistWithSongs(id = id) }

    /** Stars (favorites) an entity on the server — song, album or artist id. */
    suspend fun star(serverUrl: String, username: String, password: String, id: String): Result<Unit> =
        request(serverUrl, username, password, "star.view", mapOf("id" to id)).map { }

    /** Removes the star from an entity on the server. */
    suspend fun unstar(serverUrl: String, username: String, password: String, id: String): Result<Unit> =
        request(serverUrl, username, password, "unstar.view", mapOf("id" to id)).map { }

    /** Direct streaming URL for a song, suitable for media players. */
    fun streamUrl(
        serverUrl: String,
        username: String,
        password: String,
        id: String,
        maxBitRate: Int? = null,
        format: String? = null,
        salt: String? = null,
    ): String = buildUrl(
        serverUrl,
        username,
        password,
        "stream.view",
        buildMap {
            put("id", id)
            maxBitRate?.let { put("maxBitRate", it.toString()) }
            format?.let { put("format", it) }
        },
        salt = salt,
    )

    /** Cover art URL for an entity id (album, artist or song). */
    fun coverArtUrl(
        serverUrl: String,
        username: String,
        password: String,
        id: String,
        size: Int? = null,
        salt: String? = null,
    ): String = buildUrl(
        serverUrl,
        username,
        password,
        "getCoverArt.view",
        buildMap {
            put("id", id)
            size?.let { put("size", it.toString()) }
        },
        salt = salt,
    )

    // Subsonic error codes of interest
    const val ERROR_GENERIC = 0
    const val ERROR_MISSING_PARAM = 10
    const val ERROR_CLIENT_TOO_OLD = 20
    const val ERROR_SERVER_TOO_OLD = 30
    const val ERROR_WRONG_USERNAME_OR_PASSWORD = 40
    const val ERROR_TOKEN_AUTH_NOT_SUPPORTED = 41
    const val ERROR_NOT_AUTHORIZED = 50
    const val ERROR_DATA_NOT_FOUND = 70
    const val ERROR_UNKNOWN = -1
}
