/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.arturo254.opentune.constants.NavidromePasswordKey
import com.arturo254.opentune.constants.NavidromeSaltKey
import com.arturo254.opentune.constants.NavidromeServerUrlKey
import com.arturo254.opentune.constants.NavidromeUsernameKey
import com.arturo254.opentune.db.MusicDatabase
import com.arturo254.opentune.db.entities.AlbumArtistMap
import com.arturo254.opentune.db.entities.AlbumEntity
import com.arturo254.opentune.db.entities.ArtistEntity
import com.arturo254.opentune.db.entities.FormatEntity
import com.arturo254.opentune.db.entities.SongAlbumMap
import com.arturo254.opentune.db.entities.SongArtistMap
import com.arturo254.opentune.db.entities.SongEntity
import com.arturo254.opentune.navidrome.Navidrome
import com.arturo254.opentune.navidrome.models.AlbumWithSongs
import com.arturo254.opentune.navidrome.models.Song
import kotlinx.coroutines.flow.first

/**
 * Credentials for the user's Navidrome server, loaded from DataStore.
 * A persistent salt is generated once so cover-art URLs stay stable
 * (important for Coil's disk cache and DB-stored thumbnails).
 */
data class NavidromeAccess(
    val serverUrl: String,
    val username: String,
    val password: String,
    val salt: String,
) {
    fun coverArtUrl(coverArtId: String?, size: Int = 600): String? =
        coverArtId?.let {
            Navidrome.coverArtUrl(serverUrl, username, password, it, size, salt)
        }

    fun streamUrl(songId: String): String =
        Navidrome.streamUrl(serverUrl, username, password, songId, salt = salt)
}

/** Returns the stored Navidrome credentials, or null when not configured. */
suspend fun navidromeAccess(context: Context): NavidromeAccess? {
    val prefs = context.dataStore.data.first()
    val serverUrl = prefs[NavidromeServerUrlKey]?.trim().orEmpty()
    val username = prefs[NavidromeUsernameKey]?.trim().orEmpty()
    val password = prefs[NavidromePasswordKey].orEmpty()
    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) return null

    var salt = prefs[NavidromeSaltKey].orEmpty()
    if (salt.isBlank()) {
        salt = Navidrome.generateSalt()
        context.dataStore.edit { it[NavidromeSaltKey] = salt }
    }
    return NavidromeAccess(serverUrl, username, password, salt)
}

/**
 * Maps a Subsonic artist reference to a stable app-side artist id.
 * Subsonic song/album payloads sometimes omit artistId, so the name is
 * used as a deterministic fallback.
 */
fun navidromeArtistId(subsonicArtistId: String?, name: String?): String =
    Navidrome.ARTIST_ID_PREFIX + (
        subsonicArtistId?.takeIf { it.isNotBlank() }
            ?: "n_" + name?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty().ifBlank { "unknown" }
        )

private fun NavidromeAccess.toSongEntity(song: Song, album: AlbumWithSongs): SongEntity =
    SongEntity(
        id = Navidrome.SONG_ID_PREFIX + song.id,
        title = song.title,
        duration = song.duration ?: -1,
        thumbnailUrl = coverArtUrl(song.coverArt ?: album.coverArt),
        albumId = Navidrome.ALBUM_ID_PREFIX + album.id,
        albumName = album.name,
        year = album.year ?: song.year,
        // inLibrary stays null: songs are browsable from the Navidrome tab but
        // don't pollute the main local library lists.
        inLibrary = null,
    )

/**
 * Subsonic songs already know their technical metadata — store it in the
 * format table so the standard "Details" sheet shows format/bitrate/size.
 * Subsonic bitRate is in kbps while the format table stores bps.
 */
private fun Song.toFormatEntity(): FormatEntity =
    FormatEntity(
        id = Navidrome.SONG_ID_PREFIX + id,
        itag = 0,
        mimeType = contentType ?: "audio/${suffix ?: "unknown"}",
        codecs = suffix ?: "",
        bitrate = (bitRate ?: 0) * 1000,
        sampleRate = samplingRate,
        contentLength = size ?: 0L,
        loudnessDb = null,
        playbackUrl = null,
    )

/**
 * Persists a Navidrome album (and its songs/artists) into the app database,
 * mirroring what AlbumViewModel does for YouTube albums. Inserting makes the
 * rows available to the queue persistence and the player metadata pipeline.
 */
suspend fun MusicDatabase.insertNavidromeAlbum(
    album: AlbumWithSongs,
    access: NavidromeAccess,
) = withTransaction {
    val albumId = Navidrome.ALBUM_ID_PREFIX + album.id
    val artistId = navidromeArtistId(album.artistId, album.artist)

    insert(
        AlbumEntity(
            id = albumId,
            title = album.name,
            songCount = album.songCount ?: album.song.size,
            duration = album.duration ?: album.song.sumOf { it.duration ?: 0 },
            year = album.year,
            thumbnailUrl = access.coverArtUrl(album.coverArt),
            inLibrary = null,
        )
    )

    album.artist?.let { artistName ->
        insert(ArtistEntity(id = artistId, name = artistName))
        insert(AlbumArtistMap(albumId = albumId, artistId = artistId, order = 0))
    }

    album.song.forEachIndexed { index, song ->
        val songId = Navidrome.SONG_ID_PREFIX + song.id
        insert(access.toSongEntity(song, album))
        upsert(song.toFormatEntity())
        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
        upsert(SongAlbumMap(songId = songId, albumId = albumId, index = index))
    }
}

/**
 * Persists standalone Navidrome songs (e.g. search results) so they can be
 * played and survive queue restoration. Album links are derived from the
 * per-song Subsonic fields when present.
 */
suspend fun MusicDatabase.insertNavidromeSongs(
    songs: List<Song>,
    access: NavidromeAccess,
) = withTransaction {
    songs.forEach { song ->
        val songId = Navidrome.SONG_ID_PREFIX + song.id
        val artistId = navidromeArtistId(song.artistId, song.artist)

        song.artist?.let { artistName ->
            insert(ArtistEntity(id = artistId, name = artistName))
        }

        insert(
            SongEntity(
                id = songId,
                title = song.title,
                duration = song.duration ?: -1,
                thumbnailUrl = access.coverArtUrl(song.coverArt),
                albumId = song.albumId?.let { Navidrome.ALBUM_ID_PREFIX + it },
                albumName = song.album,
                year = song.year,
                inLibrary = null,
            )
        )
        upsert(song.toFormatEntity())
        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
    }
}
