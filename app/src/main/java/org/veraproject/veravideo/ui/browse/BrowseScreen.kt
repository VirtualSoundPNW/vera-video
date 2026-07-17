package org.veraproject.veravideo.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.ui.components.AddToPlaylistSheet
import org.veraproject.veravideo.ui.components.EmptyState
import org.veraproject.veravideo.ui.components.NameDialog
import org.veraproject.veravideo.ui.components.VideoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSaveSearchDialog by rememberSaveable { mutableStateOf(false) }
    var addToPlaylistVideoId by rememberSaveable { mutableStateOf<String?>(null) }

    val syncFailedMessage = stringResource(R.string.sync_failed)
    LaunchedEffect(state.syncFailed) {
        if (state.syncFailed) {
            snackbarHostState.showSnackbar(syncFailedMessage)
            viewModel.dismissSyncError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SearchBar(
                text = state.query.text,
                canSave = !state.query.isEmpty,
                onTextChange = viewModel::onQueryTextChange,
                onSaveClick = { showSaveSearchDialog = true },
            )

            FilterRow(
                state = state,
                onChannelSelected = viewModel::onChannelFilterChange,
                onSortSelected = viewModel::onSortOrderChange,
                onDurationSelected = viewModel::onDurationFilterChange,
                onClearFilters = viewModel::clearFilters,
            )

            PullToRefreshBox(
                isRefreshing = state.isSyncing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()

                    state.videos.isEmpty() && state.query.isEmpty -> EmptyState(
                        title = stringResource(R.string.empty_catalog_title),
                        body = stringResource(R.string.empty_catalog_body),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = viewModel::refresh,
                    )

                    state.videos.isEmpty() -> EmptyState(
                        title = stringResource(R.string.empty_results_title),
                        body = stringResource(R.string.empty_results_body),
                    )

                    else -> VideoList(
                        videos = state.videos,
                        onVideoClick = onVideoClick,
                        onAddToPlaylist = { addToPlaylistVideoId = it },
                    )
                }
            }
        }
    }

    if (showSaveSearchDialog) {
        NameDialog(
            title = stringResource(R.string.save_search_title),
            label = stringResource(R.string.search_name),
            initialValue = state.query.text,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                viewModel.saveCurrentSearch(name)
                showSaveSearchDialog = false
            },
            onDismiss = { showSaveSearchDialog = false },
        )
    }

    addToPlaylistVideoId?.let { videoId ->
        AddToPlaylistSheet(
            playlists = state.playlists,
            onSelect = { playlistId ->
                viewModel.addToPlaylist(playlistId, videoId)
                addToPlaylistVideoId = null
            },
            onCreate = { name ->
                viewModel.createPlaylistAndAdd(name, videoId)
                addToPlaylistVideoId = null
            },
            onDismiss = { addToPlaylistVideoId = null },
        )
    }
}

@Composable
private fun SearchBar(
    text: String,
    canSave: Boolean,
    onTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
        )

        if (canSave) {
            IconButton(onClick = onSaveClick) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = stringResource(R.string.save_search))
            }
        }
    }
}

@Composable
private fun VideoList(
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(videos, key = { it.videoId }) { video ->
            VideoRow(
                video = video,
                onClick = { onVideoClick(video.videoId) },
                onOverflowClick = { onAddToPlaylist(video.videoId) },
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
