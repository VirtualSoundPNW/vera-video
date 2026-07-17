package org.veraproject.veravideo.ui.searches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.ui.components.ConfirmDialog
import org.veraproject.veravideo.ui.components.EmptyState

@Composable
fun SavedSearchesScreen(
    onSearchClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedSearchesViewModel = hiltViewModel(),
) {
    val searches by viewModel.searches.collectAsStateWithLifecycle()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    if (searches.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_searches_title),
            body = stringResource(R.string.empty_searches_body),
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(searches, key = { it.search.id }) { item ->
            ListItem(
                headlineContent = { Text(item.search.name) },
                supportingContent = { Text(item.search.query.describe()) },
                leadingContent = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pluralStringResource(R.plurals.saved_search_count, item.matchCount, item.matchCount))
                        IconButton(onClick = { pendingDelete = item.search.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick(item.search.id) },
            )
            HorizontalDivider()
        }
    }

    pendingDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.delete_search_title),
            body = searches.firstOrNull { it.search.id == id }?.search?.name.orEmpty(),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Human-readable summary of what a saved search actually filters on. */
private fun SearchQuery.describe(): String {
    val parts = buildList {
        if (text.isNotBlank()) add("“$text”")
        if (channelId != null) add("one channel")
        if (minDurationSeconds != null || maxDurationSeconds != null) add("duration filtered")
        if (publishedAfter != null || publishedBefore != null) add("date filtered")
    }
    return if (parts.isEmpty()) "All videos" else parts.joinToString(" · ")
}
