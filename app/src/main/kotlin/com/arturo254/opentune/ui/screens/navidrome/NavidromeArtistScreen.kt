/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.arturo254.opentune.ui.screens.navidrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.CONTENT_TYPE_ALBUM
import com.arturo254.opentune.constants.CONTENT_TYPE_HEADER
import com.arturo254.opentune.constants.GridThumbnailHeight
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.LocalAlbumsGrid
import com.arturo254.opentune.ui.component.NavigationTitle
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.viewmodels.NavidromeArtistViewModel

/**
 * Shows one Navidrome artist: their picture and the grid of their albums.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavidromeArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NavidromeArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues(),
    ) {
        item(key = "artist_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
            Spacer(
                Modifier.padding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Top)
                        .asPaddingValues()
                        .calculateTopPadding()
                )
            )

            when (val state = uiState) {
                is NavidromeArtistViewModel.UiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is NavidromeArtistViewModel.UiState.NotConfigured -> CenteredMessage(
                    message = stringResource(R.string.navidrome_configure_prompt),
                    actionLabel = stringResource(R.string.navidrome_open_settings),
                    onAction = { navController.navigate("settings/navidrome") },
                )

                is NavidromeArtistViewModel.UiState.Error -> CenteredMessage(
                    message = stringResource(R.string.connection_failed) +
                        (state.message?.let { "\n$it" } ?: ""),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::retry,
                )

                is NavidromeArtistViewModel.UiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AsyncImage(
                            model = state.coverArtUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = state.artist.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.navidrome_artist_album_count, state.artist.album.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val state = uiState
        if (state is NavidromeArtistViewModel.UiState.Content && state.artist.album.isNotEmpty()) {
            item(key = "albums_header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                NavigationTitle(title = stringResource(R.string.albums))
            }

            items(
                items = state.artist.album,
                key = { "ndal_${it.id}" },
                contentType = { CONTENT_TYPE_ALBUM },
            ) { album ->
                LocalAlbumsGrid(
                    title = album.name,
                    subtitle = album.year?.toString() ?: "",
                    thumbnailUrl = state.albumCovers[album.id],
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("navidrome_album/${album.id}")
                        },
                )
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                text = (uiState as? NavidromeArtistViewModel.UiState.Content)?.artist?.name
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

@Composable
private fun CenteredMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
