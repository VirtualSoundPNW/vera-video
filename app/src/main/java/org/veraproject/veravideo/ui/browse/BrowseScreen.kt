package org.veraproject.veravideo.ui.browse

import android.content.Intent
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.ui.components.AddToPlaylistSheet
import org.veraproject.veravideo.ui.components.EmptyState
import org.veraproject.veravideo.ui.components.NameDialog
import org.veraproject.veravideo.ui.components.VideoRow

/**
 * External Vera Project destinations offered in the overflow menu. Kept as data
 * so adding a link is one list entry, not another hand-written menu item. Each
 * builds its own Intent (a web page or a pre-addressed email); the screen only
 * has to launch it and report failure once, uniformly.
 */
private data class MenuLink(
    val labelRes: Int,
    val icon: ImageVector,
    val intent: () -> Intent,
)

private fun webIntent(url: String) = Intent(Intent.ACTION_VIEW, url.toUri())

// ACTION_SENDTO with a mailto: URI resolves only to email apps, so it opens a
// compose window addressed to `address` rather than a generic share sheet.
private fun emailIntent(address: String) = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri())

private val VERA_MENU_LINKS: List<MenuLink> = listOf(
    MenuLink(R.string.menu_about, Icons.Filled.Info) { webIntent("https://theveraproject.org/about/") },
    MenuLink(R.string.menu_classes, Icons.Filled.School) { webIntent("https://theveraproject.org/classes/") },
    MenuLink(R.string.menu_merch, Icons.Filled.Storefront) { webIntent("https://theveraproject.bigcartel.com/products") },
    MenuLink(R.string.menu_donate, Icons.Filled.Favorite) { webIntent("https://theveraproject.org/donate/") },
    MenuLink(R.string.menu_contact, Icons.Filled.ContactPage) { webIntent("https://theveraproject.org/about/contact/") },
    MenuLink(R.string.menu_email_info, Icons.Filled.Email) { emailIntent("info@theveraproject.org") },
    MenuLink(R.string.menu_booking, Icons.Filled.AlternateEmail) { emailIntent("booking@theveraproject.org") },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSaveSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showDateRangeSheet by rememberSaveable { mutableStateOf(false) }
    var addToPlaylistVideoId by rememberSaveable { mutableStateOf<String?>(null) }

    val syncFailedMessage = stringResource(R.string.sync_failed)
    LaunchedEffect(state.syncFailed) {
        if (state.syncFailed) {
            snackbarHostState.showSnackbar(syncFailedMessage)
            viewModel.dismissSyncError()
        }
    }

    // Read outside the click lambda so a locale change re-resolves it (LocalContext
    // reads inside lambdas aren't invalidated by configuration changes).
    val linkFailedMessage = stringResource(R.string.link_failed)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    OverflowMenu(
                        dateRangeActive = state.query.hasDateRange,
                        onToggleDateRange = {
                            // A genuine toggle: turning it on opens the picker;
                            // turning it off clears the window immediately.
                            if (state.query.hasDateRange) {
                                viewModel.onDateRangeChange(after = null, before = null)
                            } else {
                                showDateRangeSheet = true
                            }
                        },
                        onOpen = { intent ->
                            if (runCatching { context.startActivity(intent) }.isFailure) {
                                scope.launch { snackbarHostState.showSnackbar(linkFailedMessage) }
                            }
                        },
                    )
                },
            )
        },
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
                onEditDateRange = { showDateRangeSheet = true },
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

    if (showDateRangeSheet) {
        DateRangeSheet(
            after = state.query.publishedAfter,
            before = state.query.publishedBefore,
            onRangeChange = viewModel::onDateRangeChange,
            onClear = { viewModel.onDateRangeChange(after = null, before = null) },
            onDismiss = { showDateRangeSheet = false },
        )
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

/**
 * The Browse overflow menu: a date-range **toggle** (checked when a window is
 * active), then the Vera Project links from [VERA_MENU_LINKS]. Every link is
 * launched through [onOpen], which owns the "no app can handle it" fallback.
 */
@Composable
private fun OverflowMenu(
    dateRangeActive: Boolean,
    onToggleDateRange: () -> Unit,
    onOpen: (Intent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_date_range)) },
            leadingIcon = {
                Icon(
                    imageVector = if (dateRangeActive) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                )
            },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            onClick = {
                expanded = false
                onToggleDateRange()
            },
        )

        HorizontalDivider()

        VERA_MENU_LINKS.forEach { link ->
            DropdownMenuItem(
                text = { Text(stringResource(link.labelRes)) },
                leadingIcon = { Icon(link.icon, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpen(link.intent())
                },
            )
        }
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
