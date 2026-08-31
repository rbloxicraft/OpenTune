/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.arturo254.opentune.db.MusicDatabase
import com.arturo254.opentune.extensions.toMediaItem
import com.arturo254.opentune.navidrome.Navidrome
import com.arturo254.opentune.navidrome.models.Album
import com.arturo254.opentune.navidrome.models.Artist
import com.arturo254.opentune.navidrome.models.ArtistAndAlbums
import com.arturo254.opentune.utils.NavidromeAccess
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
 * Backs the dedicated Navidrome tab: library overview (recent albums,
 * artists) plus debounced server-side search over songs/albums/artists.
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
        data class Content(
            val albums: List<Pair<Album, String?>>,
            val artists: List<Pair<Artist, String?>>,
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

    private val _albumListType = MutableStateFlow(Navidrome.AlbumListType.NEWEST)
    val albumListType: StateFlow<Navidrome.AlbumListType> = _albumListType

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

    /** Changes the album ordering (server-side via getAlbumList2) and reloads. */
    fun setAlbumListType(type: Navidrome.AlbumListType) {
        if (_albumListType.value == type) return
        _albumListType.value = type
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val access = navidromeAccess(context)
            if (access == null) {
                _uiState.value = UiState.NotConfigured
                return@launch
            }

            val albumsResult = Navidrome.getAlbumList2(
                access.serverUrl,
                access.username,
                access.password,
                type = _albumListType.value,
                size = 30,
            )
            val artistsResult = Navidrome.getArtists(
                access.serverUrl,
                access.username,
                access.password,
            )

            albumsResult.onSuccess { albums ->
                _uiState.value = artistsResult.fold(
                    onSuccess = { artists ->
                        UiState.Content(
                            albums = albums.map { it to access.coverArtUrl(it.coverArt) },
                            artists = artists.allArtists.map { it to access.coverArtUrl(it.coverArt) },
                        )
                    },
                    onFailure = {
                        reportException(it)
                        UiState.Error(it.message)
                    },
                )
            }.onFailure {
                reportException(it)
                _uiState.value = UiState.Error(it.message)
            }
        }
    }

    /**
     * Persists the current search-result songs and hands the resulting
     * media items back to the UI, which builds the playing queue.
     */
    fun playSearchResults(
        index: Int,
        onReady: (List<MediaItem>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = _searchState.value?.songs.orEmpty()
            if (songs.isEmpty()) return@launch

            val access = navidromeAccess(context) ?: return@launch
            database.insertNavidromeSongs(songs.map { it.first }, access)

            val items = songs.mapNotNull { (song, _) ->
                database.song(Navidrome.SONG_ID_PREFIX + song.id).first()?.toMediaItem()
            }
            withContext(Dispatchers.Main) {
                onReady(items)
            }
        }
    }

    /**
     * Persists one search-result song so it can be handed to SongMenu
     * (add to playlist, play next, add to queue…).
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
