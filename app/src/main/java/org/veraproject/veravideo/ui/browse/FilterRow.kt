package org.veraproject.veravideo.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder
import org.veraproject.veravideo.util.formatDate

/** Duration buckets offered as one-tap filters. */
enum class DurationFilter(val labelRes: Int, val min: Int?, val max: Int?) {
    SHORT(R.string.filter_duration_short, null, 5 * 60),
    MEDIUM(R.string.filter_duration_medium, 5 * 60, 20 * 60),
    LONG(R.string.filter_duration_long, 20 * 60, null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(
    state: BrowseUiState,
    onChannelSelected: (String?) -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onDurationSelected: (Int?, Int?) -> Unit,
    onEditDateRange: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SortChip(selected = state.query.sortOrder, onSelected = onSortSelected)
        }

        item {
            ChannelChip(state = state, onChannelSelected = onChannelSelected)
        }

        if (state.query.hasDateRange) {
            item {
                DateRangeChip(query = state.query, onClick = onEditDateRange)
            }
        }

        items(DurationFilter.entries.toList()) { filter ->
            val selected = state.query.minDurationSeconds == filter.min &&
                state.query.maxDurationSeconds == filter.max
            FilterChip(
                selected = selected,
                // Tapping the active bucket clears it, so a filter is never a trap.
                onClick = {
                    if (selected) onDurationSelected(null, null) else onDurationSelected(filter.min, filter.max)
                },
                label = { Text(stringResource(filter.labelRes)) },
            )
        }

        if (state.query.hasFilters) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onClearFilters,
                    label = { Text(stringResource(R.string.filter_clear)) },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(selected: SortOrder, onSelected: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    FilterChip(
        selected = false,
        onClick = { expanded = true },
        label = { Text(stringResource(selected.labelRes())) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(stringResource(order.labelRes())) },
                onClick = {
                    onSelected(order)
                    expanded = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelChip(state: BrowseUiState, onChannelSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = state.channels.firstOrNull { it.channelId == state.query.channelId }?.channelTitle

    FilterChip(
        selected = state.query.channelId != null,
        onClick = { expanded = true },
        label = { Text(selectedTitle ?: stringResource(R.string.filter_all_channels)) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.filter_all_channels)) },
            onClick = {
                onChannelSelected(null)
                expanded = false
            },
        )
        state.channels.forEach { channel ->
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.filter_channel_with_count, channel.channelTitle, channel.videoCount))
                },
                onClick = {
                    onChannelSelected(channel.channelId)
                    expanded = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeChip(query: SearchQuery, onClick: () -> Unit) {
    val after = query.publishedAfter
    val before = query.publishedBefore
    val label = when {
        after != null && before != null ->
            stringResource(R.string.date_range_chip_between, formatDate(after), formatDate(before))
        after != null -> stringResource(R.string.date_range_chip_after, formatDate(after))
        else -> stringResource(R.string.date_range_chip_before, formatDate(before!!))
    }

    FilterChip(
        selected = true,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.NEWEST -> R.string.sort_newest
    SortOrder.OLDEST -> R.string.sort_oldest
    SortOrder.LONGEST -> R.string.sort_longest
    SortOrder.TITLE -> R.string.sort_title
}
