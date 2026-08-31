/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.navidrome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.LocalPlayerConnection
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.CONTENT_TYPE_ALBUM
import com.arturo254.opentune.constants.CONTENT_TYPE_ARTIST
import com.arturo254.opentune.constants.CONTENT_TYPE_HEADER
import com.arturo254.opentune.constants.GridThumbnailHeight
import com.arturo254.opentune.playback.queues.ListQueue
import com.arturo254.opentune.ui.component.ChipsRow
import com.arturo254.opentune.ui.component.LocalAlbumsGrid
import com.arturo254.opentune.ui.component.LocalArtistsGrid
import com.arturo254.opentune.ui.component.LocalMenuState
import com.arturo254.opentune.ui.component.LocalSongsGrid
import com.arturo254.opentune.ui.component.NavigationTitle
import com.arturo254.opentune.ui.menu.SongMenu
import com.arturo254.opentune.navidrome.Navidrome
import com.arturo254.opentune.viewmodels.NavidromeViewModel

/**
 * Dedicated Navidrome tab: search the server's library and browse its
 * recently added albums and artists. Album/artist cards navigate to their
 * detail screens; tapping a search-result song plays it (and the rest of
 * the results as queue).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavidromeHomeScreen(
    navController: NavController,
    viewModel: NavidromeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val albumListType by viewModel.albumListType.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues(),
    ) {
        item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.navidrome_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (searchState != null) {
            val state = searchState!!

            if (state.songs.isNotEmpty()) {
                item(key = "search_songs_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.songs))
                }
                items(
                    items = state.songs,
                    key = { "ndsr_${it.first.id}" },
                    contentType = { CONTENT_TYPE_ALBUM },
                ) { (song, coverUrl) ->
                    LocalSongsGrid(
                        title = song.title,
                        subtitle = song.artist ?: song.album ?: "",
                        thumbnailUrl = coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    val index = state.songs.indexOfFirst { it.first.id == song.id }
                                    viewModel.playSearchResults(index) { mediaItems ->
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = query,
                                                items = mediaItems,
                                                startIndex = index,
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
                                    // Persist then open the standard song menu:
                                    // add to playlist, play next, add to queue…
                                    viewModel.persistSearchSong(song) { dbSong ->
                                        menuState.show {
                                            SongMenu(
                                                originalSong = dbSong,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }
                                },
                            ),
                    )
                }
            }

            if (state.albums.isNotEmpty()) {
                item(key = "search_albums_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.albums))
                }
                items(
                    items = state.albums,
                    key = { "ndsa_${it.first.id}" },
                    contentType = { CONTENT_TYPE_ALBUM },
                ) { (album, coverUrl) ->
                    LocalAlbumsGrid(
                        title = album.name,
                        subtitle = album.artist ?: album.year?.toString() ?: "",
                        thumbnailUrl = coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("navidrome_album/${album.id}") },
                    )
                }
            }

            if (state.artists.isNotEmpty()) {
                item(key = "search_artists_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.artists))
                }
                items(
                    items = state.artists,
                    key = { "ndsr2_${it.first.id}" },
                    contentType = { CONTENT_TYPE_ARTIST },
                ) { (artist, coverUrl) ->
                    LocalArtistsGrid(
                        title = artist.name,
                        subtitle = "",
                        thumbnailUrl = coverUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("navidrome_artist/${artist.id}") },
                    )
                }
            }

            if (state.songs.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty()) {
                item(key = "search_empty", span = { GridItemSpan(maxLineSpan) }) {
                    CenteredMessage(message = stringResource(R.string.navidrome_no_results))
                }
            }
        } else {
            when (val state = uiState) {
                is NavidromeViewModel.UiState.Loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is NavidromeViewModel.UiState.NotConfigured -> item(key = "not_configured", span = { GridItemSpan(maxLineSpan) }) {
                    CenteredMessage(
                        message = stringResource(R.string.navidrome_configure_prompt),
                        actionLabel = stringResource(R.string.navidrome_open_settings),
                        onAction = { navController.navigate("settings/navidrome") },
                    )
                }

                is NavidromeViewModel.UiState.Error -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    CenteredMessage(
                        message = stringResource(R.string.connection_failed) +
                            (state.message?.let { "\n$it" } ?: ""),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::refresh,
                    )
                }

                is NavidromeViewModel.UiState.Content -> {
                    // Server-side album ordering (getAlbumList2 type).
                    item(key = "sort_chips", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                        ChipsRow(
                            chips = listOf(
                                Navidrome.AlbumListType.NEWEST to stringResource(R.string.sort_by_create_date),
                                Navidrome.AlbumListType.RECENT to stringResource(R.string.navidrome_sort_recently_played),
                                Navidrome.AlbumListType.FREQUENT to stringResource(R.string.navidrome_sort_frequently_played),
                                Navidrome.AlbumListType.RANDOM to stringResource(R.string.navidrome_sort_random),
                                Navidrome.AlbumListType.ALPHABETICAL_BY_NAME to stringResource(R.string.sort_by_name),
                                Navidrome.AlbumListType.ALPHABETICAL_BY_ARTIST to stringResource(R.string.sort_by_artist),
                            ),
                            currentValue = albumListType,
                            onValueUpdate = viewModel::setAlbumListType,
                        )
                    }

                    if (state.albums.isNotEmpty()) {
                        item(key = "albums_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(title = stringResource(R.string.navidrome_recent_albums))
                        }
                        items(
                            items = state.albums,
                            key = { "ndal_${it.first.id}" },
                            contentType = { CONTENT_TYPE_ALBUM },
                        ) { (album, coverUrl) ->
                            LocalAlbumsGrid(
                                title = album.name,
                                subtitle = album.artist ?: album.year?.toString() ?: "",
                                thumbnailUrl = coverUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("navidrome_album/${album.id}") },
                            )
                        }
                    }

                    if (state.artists.isNotEmpty()) {
                        item(key = "artists_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                            NavigationTitle(title = stringResource(R.string.artists))
                        }
                        items(
                            items = state.artists,
                            key = { "ndar_${it.first.id}" },
                            contentType = { CONTENT_TYPE_ARTIST },
                        ) { (artist, coverUrl) ->
                            LocalArtistsGrid(
                                title = artist.name,
                                subtitle = "",
                                thumbnailUrl = coverUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("navidrome_artist/${artist.id}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
