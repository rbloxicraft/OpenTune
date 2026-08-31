/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.navidrome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.LocalPlayerConnection
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.CONTENT_TYPE_HEADER
import com.arturo254.opentune.constants.CONTENT_TYPE_SONG
import com.arturo254.opentune.constants.ListThumbnailSize
import com.arturo254.opentune.playback.queues.ListQueue
import com.arturo254.opentune.ui.component.ListItem
import com.arturo254.opentune.ui.component.LocalMenuState
import com.arturo254.opentune.ui.component.NavigationTitle
import com.arturo254.opentune.ui.menu.SongMenu
import com.arturo254.opentune.utils.makeTimeString
import com.arturo254.opentune.viewmodels.NavidromeViewModel

/**
 * Dedicated Navidrome tab: the server's playlist (imported from the user's
 * playlist.m3u) as a flat, directly playable song list in its exact order,
 * plus a server-side search field. One tap plays — no album hop.
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

    fun playQueue(title: String, items: List<androidx.media3.common.MediaItem>, startIndex: Int) {
        playerConnection?.playQueue(
            ListQueue(
                title = title,
                items = items,
                startIndex = startIndex,
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues(),
    ) {
        item(key = "search", contentType = CONTENT_TYPE_HEADER) {
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
                item(key = "search_songs_header", contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.songs))
                }
                itemsIndexed(
                    items = state.songs,
                    key = { _, (song, _) -> "ndsr_${song.id}" },
                    contentType = { _, _ -> CONTENT_TYPE_SONG },
                ) { index, (song, coverUrl) ->
                    SongRow(
                        title = song.title,
                        artist = song.artist ?: song.album ?: "",
                        duration = song.duration,
                        coverUrl = coverUrl,
                        onClick = {
                            viewModel.playSearchResults(index) { title, items, start ->
                                playQueue(title, items, start)
                            }
                        },
                        onLongClick = {
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
                    )
                }
            }

            if (state.albums.isNotEmpty()) {
                item(key = "search_albums_header", contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.albums))
                }
                itemsIndexed(
                    items = state.albums,
                    key = { _, (album, _) -> "ndsa_${album.id}" },
                    contentType = { _, _ -> CONTENT_TYPE_HEADER },
                ) { _, (album, coverUrl) ->
                    BrowseRow(
                        title = album.name,
                        subtitle = album.artist ?: album.year?.toString() ?: "",
                        coverUrl = coverUrl,
                        onClick = { navController.navigate("navidrome_album/${album.id}") },
                    )
                }
            }

            if (state.artists.isNotEmpty()) {
                item(key = "search_artists_header", contentType = CONTENT_TYPE_HEADER) {
                    NavigationTitle(title = stringResource(R.string.artists))
                }
                itemsIndexed(
                    items = state.artists,
                    key = { _, (artist, _) -> "ndsr2_${artist.id}" },
                    contentType = { _, _ -> CONTENT_TYPE_HEADER },
                ) { _, (artist, coverUrl) ->
                    BrowseRow(
                        title = artist.name,
                        subtitle = "",
                        coverUrl = coverUrl,
                        circleShape = true,
                        onClick = { navController.navigate("navidrome_artist/${artist.id}") },
                    )
                }
            }

            if (state.songs.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty()) {
                item(key = "search_empty", contentType = CONTENT_TYPE_HEADER) {
                    CenteredMessage(message = stringResource(R.string.navidrome_no_results))
                }
            }
        } else {
            when (val state = uiState) {
                is NavidromeViewModel.UiState.Loading -> item(key = "loading", contentType = CONTENT_TYPE_HEADER) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is NavidromeViewModel.UiState.NotConfigured -> item(key = "not_configured", contentType = CONTENT_TYPE_HEADER) {
                    CenteredMessage(
                        message = stringResource(R.string.navidrome_configure_prompt),
                        actionLabel = stringResource(R.string.navidrome_open_settings),
                        onAction = { navController.navigate("settings/navidrome") },
                    )
                }

                is NavidromeViewModel.UiState.NoPlaylist -> item(key = "no_playlist", contentType = CONTENT_TYPE_HEADER) {
                    CenteredMessage(message = stringResource(R.string.navidrome_no_playlist))
                }

                is NavidromeViewModel.UiState.Error -> item(key = "error", contentType = CONTENT_TYPE_HEADER) {
                    CenteredMessage(
                        message = stringResource(R.string.connection_failed) +
                            (state.message?.let { "\n$it" } ?: ""),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::refresh,
                    )
                }

                is NavidromeViewModel.UiState.Content -> {
                    item(key = "playlist_header", contentType = CONTENT_TYPE_HEADER) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = state.playlistName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${state.songs.size} " + stringResource(R.string.songs).lowercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Button(onClick = {
                                    viewModel.playFrom(0) { title, items, start ->
                                        playQueue(title, items, start)
                                    }
                                }) {
                                    Icon(painterResource(R.drawable.play), contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.play))
                                }
                                FilledTonalButton(onClick = {
                                    val start = state.songs.indices.random()
                                    viewModel.playFrom(start) { title, items, startIndex ->
                                        playQueue(title, items, startIndex)
                                    }
                                }) {
                                    Icon(painterResource(R.drawable.shuffle), contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.shuffle))
                                }
                            }
                        }
                    }

                    // The flat m3u-ordered song list — one tap plays.
                    itemsIndexed(
                        items = state.songs,
                        key = { _, (song, _) -> "ndm3u_${song.id}" },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, (song, coverUrl) ->
                        SongRow(
                            title = song.title,
                            artist = song.artist ?: song.album ?: "",
                            duration = song.duration,
                            coverUrl = coverUrl,
                            onClick = {
                                viewModel.playFrom(index) { title, items, start ->
                                    playQueue(title, items, start)
                                }
                            },
                            onLongClick = {
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
                        )
                    }

                    item(key = "bottom_spacing", contentType = CONTENT_TYPE_HEADER) {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    title: String,
    artist: String,
    duration: Int?,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val subtitleText = listOfNotNull(
        artist.takeIf { it.isNotBlank() },
        duration?.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
    ).joinToString(" · ")

    ListItem(
        title = title,
        subtitle = {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        thumbnailContent = {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(8.dp)),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

@Composable
private fun BrowseRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    circleShape: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitleContent: (@Composable RowScope.() -> Unit)? =
        if (subtitle.isBlank()) null else {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

    ListItem(
        title = title,
        subtitle = subtitleContent,
        thumbnailContent = {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(if (circleShape) CircleShape else RoundedCornerShape(8.dp)),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
    )
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
