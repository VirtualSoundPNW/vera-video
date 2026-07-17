package org.veraproject.veravideo.ui.playlists

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.veraproject.veravideo.R
import org.veraproject.veravideo.ui.components.EmptyState
import org.veraproject.veravideo.ui.components.VideoRow
import org.veraproject.veravideo.util.ShareLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlay: (videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Read through stringResource rather than context.getString inside the
    // click lambda: LocalContext reads are not invalidated by configuration
    // changes, so a locale switch would leave a stale label behind.
    val sharePlaylistLabel = stringResource(R.string.share_playlist)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (state.canPlay) {
                        IconButton(
                            onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, state.name)
                                    putExtra(Intent.EXTRA_TEXT, viewModel.buildShareText())
                                }
                                context.startActivity(Intent.createChooser(share, sharePlaylistLabel))
                            },
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_playlist))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canPlay) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.play_all)) },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = { state.playableVideos.firstOrNull()?.let { onPlay(it.videoId) } },
                )
            }
        },
    ) { padding ->
        if (state.videos.isEmpty() && !state.isLoading) {
            EmptyState(
                title = state.name,
                body = stringResource(R.string.empty_playlist_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            if (state.shareTruncated) {
                Text(
                    text = stringResource(R.string.share_truncated_notice, ShareLinks.MAX_PLAYLIST_VIDEOS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.videos, key = { _, video -> video.videoId }) { index, video ->
                    VideoRow(
                        video = video,
                        unavailable = video.videoId in state.unavailableVideoIds,
                        onClick = { onPlay(video.videoId) },
                        trailingContent = {
                            ReorderControls(
                                canMoveUp = index > 0,
                                canMoveDown = index < state.videos.lastIndex,
                                onMoveUp = { viewModel.moveVideo(index, index - 1) },
                                onMoveDown = { viewModel.moveVideo(index, index + 1) },
                                onRemove = { viewModel.removeVideo(video.videoId) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Explicit up/down/remove buttons rather than drag-and-drop: they are reachable
 * with one hand, work with TalkBack, and need no gesture discovery.
 */
@Composable
private fun ReorderControls(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.move_down))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_from_playlist))
        }
    }
}
