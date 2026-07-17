package org.veraproject.veravideo.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.Playlist
import org.veraproject.veravideo.ui.components.ConfirmDialog
import org.veraproject.veravideo.ui.components.EmptyState
import org.veraproject.veravideo.ui.components.NameDialog

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_playlist))
            }
        },
    ) { padding ->
        if (playlists.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_playlists_title),
                body = stringResource(R.string.empty_playlists_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                    onRename = { renaming = playlist.id },
                    onDelete = { pendingDelete = playlist.id },
                )
                HorizontalDivider()
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = stringResource(R.string.new_playlist),
            label = stringResource(R.string.playlist_name),
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                viewModel.create(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renaming?.let { id ->
        NameDialog(
            title = stringResource(R.string.rename),
            label = stringResource(R.string.playlist_name),
            initialValue = playlists.firstOrNull { it.id == id }?.name.orEmpty(),
            confirmLabel = stringResource(R.string.save),
            onConfirm = {
                viewModel.rename(id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    pendingDelete?.let { id ->
        val name = playlists.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            body = stringResource(R.string.delete_playlist_body, name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(playlist.name) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.playlist_video_count, playlist.videoCount, playlist.videoCount))
        },
        leadingContent = { PlaylistCover(playlist) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PlaylistCover(playlist: Playlist) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (playlist.thumbnailUrl != null) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
        }
    }
}
