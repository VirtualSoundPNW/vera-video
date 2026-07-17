package org.veraproject.veravideo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.Playlist

/**
 * Sheet for putting a video into a playlist, including creating one on the spot
 * so the first playlist does not require a detour to another tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.new_playlist)) },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true },
            )

            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.playlist_video_count,
                                    playlist.videoCount,
                                    playlist.videoCount,
                                ),
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(playlist.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = stringResource(R.string.new_playlist),
            label = stringResource(R.string.playlist_name),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                showCreateDialog = false
                onCreate(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

