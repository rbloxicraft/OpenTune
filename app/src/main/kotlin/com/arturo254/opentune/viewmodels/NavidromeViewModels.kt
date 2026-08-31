/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.arturo254.opentune.constants.NavidromePlaylistIdKey
import com.arturo254.opentune.db.MusicDatabase
import com.arturo254.opentune.navidrome.Navidrome
import com.arturo254.opentune.navidrome.models.Album
import com.arturo254.opentune.navidrome.models.Artist
import com.arturo254.opentune.navidrome.models.ArtistAndAlbums
import com.arturo254.opentune.utils.NavidromeAccess
import com.arturo254.opentune.utils.dataStore
import com.arturo254.opentune.utils.insertNavidromeAlbum
import com.arturo254.opentune.utils.insertNavidromeSongs
import com.arturo254.opentune.utils.navidromeAccess
import com.arturo254.opentune.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the dedicated Navidrome tab: the server's main playlist (imported
 * from the user's playlist.m3u) shown as a flat, directly playable song list
 * in its exact order, plus debounced server-side search.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class NavidromeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object NotConfigured : UiState
        data object NoPlaylist : UiState
        data class Content(
            val playlistName: String,
            val songs: List<Pair<com.arturo254.opentune.navidrome.models.Song, String?>>,
            /** Server playlists, for the selector (more than one → show it). */
            val playlists: List<com.arturo254.opentune.navidrome.models.PlaylistRef> = emptyList(),
            val selectedPlaylistId: String = "",
        ) : UiState

        data class Error(val message: String?) : UiState
    }

    data class SearchState(
        val songs: List<Pair<com.arturo254.opentune.navidrome.models.Song, String?>>,
        val albums: List<Pair<Album, String?>>,
        val artists: List<Pair<Artist, String?>>,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchState = MutableStateFlow<SearchState?>(null)
    val searchState: StateFlow<SearchState?> = _searchState

    init {
        refresh()

        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.length < 2) {
                        _searchState.value = null
                        return@collectLatest
                    }
                    val access = navidromeAccess(context) ?: return@collectLatest
                    Navidrome.search3(
                        access.serverUrl,
                        access.username,
                        access.password,
                        query = q,
                        artistCount = 12,
                        albumCount = 12,
                        songCount = 20,
                    ).onSuccess { result ->
                        _searchState.value = SearchState(
                            songs = result.song.map { it to access.coverArtUrl(it.coverArt) },
                            albums = result.album.map { it to access.coverArtUrl(it.coverArt) },
                            artists = result.artist.map { it to access.coverArtUrl(it.coverArt) },
                        )
                    }.onFailure {
                        reportException(it)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * Loads the server's playlist (the one built from playlist.m3u) with its
     * songs in the playlist's exact order. The chosen playlist is remembered;
     * default falls back to the largest one.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val access = navidromeAccess(context)
            if (access == null) {
                _uiState.value = UiState.NotConfigured
                return@launch
            }

            Navidrome.getPlaylists(access.serverUrl, access.username, access.password)
                .onSuccess { playlists ->
                    if (playlists.isEmpty()) {
                        _uiState.value = UiState.NoPlaylist
                        return@launch
                    }
                    val savedId = context.dataStore.data.first()[NavidromePlaylistIdKey]
                    val target = playlists.firstOrNull { it.id == savedId }
                        ?: playlists.maxByOrNull { it.songCount }
                        ?: return@launch
                    Navidrome.getPlaylist(access.serverUrl, access.username, access.password, target.id)
                        .onSuccess { playlist ->
                            _uiState.value = UiState.Content(
                                playlistName = playlist.name.ifBlank { target.name },
                                songs = playlist.entry.map { it to access.coverArtUrl(it.coverArt) },
                                playlists = playlists,
                                selectedPlaylistId = target.id,
                            )
                        }
                        .onFailure {
                            reportException(it)
                            _uiState.value = UiState.Error(it.message)
                        }
                }
                .onFailure {
                    reportException(it)
                    _uiState.value = UiState.Error(it.message)
                }
        }
    }

    /** Switches the tab to another server playlist and remembers the choice. */
    fun selectPlaylist(id: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[NavidromePlaylistIdKey] = id }
            refresh()
        }
    }

    /**
     * Plays the loaded playlist starting at [index]. Media items are built
     * straight from Subsonic data — the DB fills in lazily as songs play.
     */
    fun playFrom(
        index: Int,
        onReady: (title: String, items: List<MediaItem>, startIndex: Int) -> Unit,
    ) {
        val state = _uiState.value
        if (state !is UiState.Content) return
        viewModelScope.launch(Dispatchers.IO) {
            val access = navidromeAccess(context) ?: return@launch
            val items = state.songs.map { (song, _) -> access.toMediaItem(song) }
            withContext(Dispatchers.Main) {
                onReady(state.playlistName, items, index)
            }
        }
    }

    /**
     * Plays the current search results starting at [index] — media items are
     * built from Subsonic data without touching the DB.
     */
    fun playSearchResults(
        index: Int,
        onReady: (title: String, items: List<MediaItem>, startIndex: Int) -> Unit,
    ) {
        val results = _searchState.value?.songs.orEmpty()
        if (results.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val access = navidromeAccess(context) ?: return@launch
            val items = results.map { (song, _) -> access.toMediaItem(song) }
            withContext(Dispatchers.Main) {
                onReady(_query.value, items, index)
            }
        }
    }

    /**
     * Persists one song so it can be handed to SongMenu (add to playlist,
     * play next, add to queue…).
     */
    fun persistSearchSong(
        song: com.arturo254.opentune.navidrome.models.Song,
        onReady: (com.arturo254.opentune.db.entities.Song) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val access = navidromeAccess(context) ?: return@launch
            database.insertNavidromeSongs(listOf(song), access)
            val dbSong = database.song(Navidrome.SONG_ID_PREFIX + song.id).first()
            if (dbSong != null) {
                withContext(Dispatchers.Main) {
                    onReady(dbSong)
                }
            }
        }
    }
}

/**
 * Fetches a single Navidrome album, persists it into the app database, and
 * exposes it as a database AlbumWithSongs flow (same shape as AlbumViewModel,
 * so queue persistence and player metadata work unchanged).
 */
@HiltViewModel
class NavidromeAlbumViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Raw Subsonic album id (without the app-side "nda_" prefix). */
    val albumId = savedStateHandle.get<String>("albumId")!!

    val albumWithSongs =
        database
            .albumWithSongs(Navidrome.ALBUM_ID_PREFIX + albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _isFetching.value = true
            _error.value = null

            val access = navidromeAccess(context)
            if (access == null) {
                _isFetching.value = false
                _error.value = "NOT_CONFIGURED"
                return@launch
            }

            Navidrome.getAlbum(access.serverUrl, access.username, access.password, albumId)
                .onSuccess { album ->
                    database.insertNavidromeAlbum(album, access)
                    _isFetching.value = false
                }
                .onFailure {
                    reportException(it)
                    _isFetching.value = false
                    if (albumWithSongs.value == null) {
                        _error.value = it.message
                    }
                }
        }
    }
}

/** Fetches a Navidrome artist and their albums. */
@HiltViewModel
class NavidromeArtistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object NotConfigured : UiState
        data class Content(
            val artist: ArtistAndAlbums,
            val coverArtUrl: String?,
            val albumCovers: Map<String, String?>,
        ) : UiState

        data class Error(val message: String?) : UiState
    }

    /** Raw Subsonic artist id (without the app-side "ndar_" prefix). */
    val artistId = savedStateHandle.get<String>("artistId")!!

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val access: NavidromeAccess = navidromeAccess(context) ?: run {
                _uiState.value = UiState.NotConfigured
                return@launch
            }

            Navidrome.getArtist(access.serverUrl, access.username, access.password, artistId)
                .onSuccess { artist ->
                    _uiState.value = UiState.Content(
                        artist = artist,
                        coverArtUrl = access.coverArtUrl(artist.coverArt),
                        albumCovers = artist.album.associate { it.id to access.coverArtUrl(it.coverArt) },
                    )
                }
                .onFailure {
                    reportException(it)
                    _uiState.value = UiState.Error(it.message)
                }
        }
    }
}
