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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.arturo254.opentune.extensions.toMediaItem
import com.arturo254.opentune.extensions.togglePlayPause
import com.arturo254.opentune.playback.queues.ListQueue
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.LocalMenuState
import com.arturo254.opentune.ui.component.NavigationTitle
import com.arturo254.opentune.ui.component.SongListItem
import com.arturo254.opentune.ui.menu.SongMenu
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.viewmodels.NavidromeAlbumViewModel

/**
 * Shows one Navidrome album: cover, play/shuffle buttons and the song list.
 * Songs are persisted in the app database, so playback, queue persistence
 * and the player metadata pipeline work like for any other song.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NavidromeAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NavidromeAlbumViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val error by viewModel.error.collectAsState()

    val songs = albumWithSongs?.songs.orEmpty()

    val play: (Int) -> Unit = { index ->
        val currentSongs = albumWithSongs?.songs.orEmpty()
        if (currentSongs.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = albumWithSongs?.album?.title.orEmpty(),
                    items = currentSongs.map { it.toMediaItem() },
                    startIndex = index,
                )
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues(),
    ) {
        item(key = "top_insets") {
            Spacer(
                Modifier.padding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Top)
                        .asPaddingValues()
                        .calculateTopPadding()
                )
            )
        }

        when {
            songs.isEmpty() && isFetching -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            songs.isEmpty() && error != null -> item(key = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.connection_failed) +
                            (error?.let { "\n$it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::retry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            songs.isNotEmpty() -> {
                // Header
                item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AsyncImage(
                        model = albumWithSongs?.album?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(128.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = albumWithSongs?.album?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        albumWithSongs?.artists?.firstOrNull()?.name?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { play(0) },
                                enabled = songs.isNotEmpty(),
                            ) {
                                Icon(painterResource(R.drawable.play), contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.play))
                            }
                            FilledTonalButton(
                                onClick = {
                                    val start = songs.indices.random()
                                    play(start)
                                },
                                enabled = songs.isNotEmpty(),
                            ) {
                                Icon(painterResource(R.drawable.shuffle), contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.shuffle))
                            }
                        }
                    }
                }
                }

                item(key = "songs_header") {
                    NavigationTitle(title = stringResource(R.string.songs))
                }

                // Lazy song list — only visible rows are composed.
                itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                ) { index, song ->
                    SongListItem(
                        song = song,
                        albumIndex = index + 1,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        play(index)
                                    }
                                },
                                onLongClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    )
                }

                item(key = "bottom_spacing") {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = albumWithSongs?.album?.title
                    ?: stringResource(R.string.navidrome),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
