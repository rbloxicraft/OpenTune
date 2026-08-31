/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.navidrome

import com.arturo254.opentune.navidrome.models.SubsonicEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding tests against realistic Navidrome (OpenSubsonic) payloads,
 * including fields the app doesn't model and ISO-8601 starred dates.
 */
class SubsonicParsingTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `getArtists decodes with starred dates and empty indexes`() {
        val payload = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome",
            "serverVersion":"dev","openSubsonic":true,"artists":{
            "lastModified":1719878651000,"ignoredArticles":"The El La Los Las Le Les",
            "index":[{"name":"A","artist":[{"id":"ar_1","name":"Artist A","coverArt":"ar_1",
            "albumCount":3,"starred":"2024-02-01T08:00:00.000Z","userRating":5,
            "musicBrainzId":"mb-1"}]},{"name":"#","artist":[]}]}}}
        """.trimIndent()

        val body = json.decodeFromString<SubsonicEnvelope>(payload).subsonicResponse

        assertEquals("ok", body.status)
        assertNotNull(body.artists)
        val artists = body.artists!!
        assertEquals(2, artists.index.size)
        val all = artists.allArtists
        assertEquals(1, all.size)
        assertEquals("Artist A", all[0].name)
        assertEquals("2024-02-01T08:00:00.000Z", all[0].starred)
    }

    @Test
    fun `getAlbumList2 decodes with unknown OpenSubsonic fields`() {
        val payload = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome",
            "serverVersion":"dev","openSubsonic":true,"albumList2":{"album":[{
            "id":"al_7dc0c97a","name":"Some Album","artist":"Some Artist","artistId":"ar_123",
            "coverArt":"al_7dc0c97a","songCount":10,"duration":2134,"playCount":42,
            "created":"2024-01-15T10:30:00.000Z","year":2024,"genre":"Rock",
            "billingType":"NONE","price":0.0,"starred":"2024-02-01T08:00:00.000Z",
            "userRating":3,"sortName":"some album","albumStatus":"COMPLETE"}]}}}
        """.trimIndent()

        val body = json.decodeFromString<SubsonicEnvelope>(payload).subsonicResponse

        assertNotNull(body.albumList2?.album)
        val albums = body.albumList2!!.album
        assertEquals(1, albums.size)
        assertEquals("Some Album", albums[0].name)
        assertEquals(2024, albums[0].year)
        assertEquals(2134, albums[0].duration)
    }

    @Test
    fun `getAlbum decodes nested songs with numeric and boolean extras`() {
        val payload = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome",
            "serverVersion":"dev","openSubsonic":true,"album":{"id":"al_1","name":"Album",
            "artist":"Artist","artistId":"ar_1","coverArt":"al_1","songCount":2,"duration":400,
            "playCount":1,"created":"2024-01-15T10:30:00.000Z","year":2024,"genre":"Rock",
            "song":[{"id":"f1","parent":"al_1","isDir":false,"title":"Track One","album":"Album",
            "artist":"Artist","track":1,"year":2024,"genre":"Rock","coverArt":"f1",
            "size":12345678,"contentType":"audio/mpeg","suffix":"mp3","duration":200,
            "bitRate":320,"path":"Artist/Album/01.mp3","playCount":3,"discNumber":1,
            "created":"2024-01-15T10:30:00.000Z","albumId":"al_1","artistId":"ar_1",
            "type":"music","isVideo":false,"samplingRate":44100,"channelCount":2}]}}}
        """.trimIndent()

        val body = json.decodeFromString<SubsonicEnvelope>(payload).subsonicResponse

        assertNotNull(body.album)
        val album = body.album!!
        assertEquals("Album", album.name)
        assertEquals(1, album.song.size)
        assertEquals("Track One", album.song[0].title)
        assertEquals(200, album.song[0].duration)
        assertEquals("audio/mpeg", album.song[0].contentType)
    }

    @Test
    fun `error response decodes with failure status`() {
        val payload = """
            {"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome",
            "serverVersion":"dev","openSubsonic":true,
            "error":{"code":40,"message":"Bad credentials"}}}
        """.trimIndent()

        val body = json.decodeFromString<SubsonicEnvelope>(payload).subsonicResponse

        assertEquals("failed", body.status)
        assertEquals(40, body.error?.code)
        assertEquals("Bad credentials", body.error?.message)
    }

    @Test
    fun `url builder keeps sub-path`() {
        val url = Navidrome.streamUrl("https://home.appolon.dev/navidrome/", "u", "p", "song1")
        assertTrue(url.startsWith("https://home.appolon.dev/navidrome/rest/stream.view"))
        assertTrue(url.contains("u=u"))
        assertTrue(url.contains("id=song1"))
    }
}
