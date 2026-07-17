package org.veraproject.veravideo.ui.player

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import org.veraproject.veravideo.R
import org.veraproject.veravideo.ui.components.VideoRow
import org.veraproject.veravideo.util.ShareLinks
import org.veraproject.veravideo.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentVideo = state.currentVideo

    // Read through stringResource rather than context.getString inside the
    // click lambda: LocalContext reads are not invalidated by configuration
    // changes, so a locale switch would leave a stale label behind.
    val shareVideoLabel = stringResource(R.string.share_video)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    currentVideo?.let { video ->
                        IconButton(
                            onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, ShareLinks.videoShareText(video.title, video.videoId))
                                }
                                context.startActivity(Intent.createChooser(share, shareVideoLabel))
                            },
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_video))
                        }

                        IconButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, video.watchUrl.toUri()))
                            },
                        ) {
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.open_in_youtube),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            EmbeddedPlayer(
                videoId = currentVideo?.videoId,
                onVideoEnded = viewModel::onVideoEnded,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )

            currentVideo?.let { video ->
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = video.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${video.channelTitle} · ${formatDate(video.publishedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (state.isPlaylist) {
                PlaylistControls(
                    hasPrevious = state.hasPrevious,
                    hasNext = state.hasNext,
                    position = "${state.currentIndex + 1} / ${state.queue.size}",
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                )

                HorizontalDivider()

                UpNextList(
                    state = state,
                    onSelect = viewModel::playAt,
                )
            }
        }
    }
}

/**
 * The YouTube IFrame player, wrapped for Compose.
 *
 * Google's native YouTube Android Player API is defunct, and YouTube's terms
 * require playback through their player rather than extracting streams — so an
 * embedded IFrame is the compliant option, not merely a convenient one.
 */
@Composable
private fun EmbeddedPlayer(
    videoId: String?,
    onVideoEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { mutableStateOf<YouTubePlayer?>(null) }

    // The listener is created once with the view, so it must read the current
    // callback rather than capture the one from first composition.
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)

    val playerView = remember {
        // Held here so DisposableEffect can release the WebView it owns; leaking
        // it would keep an Activity-context WebView alive after navigating away.
        mutableStateOf<YouTubePlayerView?>(null)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                // Manual initialization: automatic init races with attaching a
                // listener, and onReady would be missed.
                enableAutomaticInitialization = false
                lifecycleOwner.lifecycle.addObserver(this)

                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            player.value = youTubePlayer
                        }

                        override fun onStateChange(
                            youTubePlayer: YouTubePlayer,
                            state: PlayerConstants.PlayerState,
                        ) {
                            if (state == PlayerConstants.PlayerState.ENDED) currentOnVideoEnded()
                        }
                    },
                )

                playerView.value = this
            }
        },
    )

    // Load whenever the video changes, and once the player becomes ready —
    // whichever happens last.
    LaunchedEffect(videoId, player.value) {
        val youTubePlayer = player.value ?: return@LaunchedEffect
        if (videoId != null) youTubePlayer.loadVideo(videoId, 0f)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.value?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
        }
    }
}

@Composable
private fun PlaylistControls(
    hasPrevious: Boolean,
    hasNext: Boolean,
    position: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_video))
        }
        Text(text = position, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_video))
        }
    }
}

@Composable
private fun UpNextList(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
) {
    if (state.upNext.isEmpty()) return

    Text(
        text = stringResource(R.string.up_next),
        style = MaterialTheme.typography.titleMedium,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.queue, key = { _, video -> video.videoId }) { index, video ->
            if (index > state.currentIndex) {
                VideoRow(video = video, onClick = { onSelect(index) })
            }
        }
    }
}
