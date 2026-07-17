package org.veraproject.veravideo.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.veraproject.veravideo.data.CatalogRepository
import org.veraproject.veravideo.data.PlaylistRepository
import org.veraproject.veravideo.data.SavedSearchRepository
import org.veraproject.veravideo.data.SyncResult
import org.veraproject.veravideo.data.local.ChannelSummary
import org.veraproject.veravideo.domain.Playlist
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.ui.navigation.Routes
import javax.inject.Inject

data class BrowseUiState(
    val query: SearchQuery = SearchQuery(),
    val videos: List<Video> = emptyList(),
    val channels: List<ChannelSummary> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isSyncing: Boolean = false,
    val syncFailed: Boolean = false,
    /** True until the first query result arrives, to avoid flashing "no matches". */
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val savedSearchRepository: SavedSearchRepository,
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val savedSearchId: Long = savedStateHandle[Routes.ARG_SAVED_SEARCH_ID] ?: Routes.NO_SAVED_SEARCH

    private val query = MutableStateFlow(SearchQuery())
    private val isSyncing = MutableStateFlow(false)
    private val syncFailed = MutableStateFlow(false)

    /**
     * Typing debounces; changing a filter or sort does not. Re-running the
     * query on every keystroke is wasteful, but a filter tap is a deliberate
     * action and should feel immediate — so the two halves are debounced
     * separately and recombined rather than debouncing the whole query.
     */
    private val debouncedText = query
        .map { it.text }
        .distinctUntilChanged()
        .debounce { text -> if (text.isBlank()) 0L else TEXT_DEBOUNCE_MS }

    private val filters = query
        .map { it.copy(text = "") }
        .distinctUntilChanged()

    private val effectiveQuery = combine(debouncedText, filters) { text, filters -> filters.copy(text = text) }

    private val videos: StateFlow<List<Video>?> = effectiveQuery
        .flatMapLatest { catalogRepository.observeVideos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<BrowseUiState> = combine(
        query,
        videos,
        catalogRepository.observeChannels(),
        playlistRepository.observePlaylists(),
        combine(isSyncing, syncFailed) { syncing, failed -> syncing to failed },
    ) { query, videos, channels, playlists, (syncing, failed) ->
        BrowseUiState(
            query = query,
            videos = videos.orEmpty(),
            channels = channels,
            playlists = playlists,
            isSyncing = syncing,
            syncFailed = failed,
            isLoading = videos == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrowseUiState())

    init {
        // Opened from the Searches tab: start with that search applied.
        if (savedSearchId != Routes.NO_SAVED_SEARCH) {
            viewModelScope.launch {
                savedSearchRepository.get(savedSearchId)?.let { saved -> query.value = saved.query }
            }
        }

        // Refresh on open so a returning user sees new videos without pulling.
        refresh()
    }

    fun onQueryTextChange(text: String) = query.update { it.copy(text = text) }

    fun onChannelFilterChange(channelId: String?) = query.update { it.copy(channelId = channelId) }

    fun onSortOrderChange(sortOrder: SortOrder) = query.update { it.copy(sortOrder = sortOrder) }

    fun onDurationFilterChange(min: Int?, max: Int?) =
        query.update { it.copy(minDurationSeconds = min, maxDurationSeconds = max) }

    fun clearFilters() = query.update { SearchQuery(text = it.text) }

    fun applyQuery(newQuery: SearchQuery) = query.update { newQuery }

    fun refresh() {
        viewModelScope.launch {
            isSyncing.value = true
            val result = catalogRepository.sync()
            syncFailed.value = result is SyncResult.Failed
            isSyncing.value = false
        }
    }

    fun dismissSyncError() {
        syncFailed.value = false
    }

    fun saveCurrentSearch(name: String) {
        viewModelScope.launch { savedSearchRepository.save(name, query.value) }
    }

    fun addToPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch { playlistRepository.addVideo(playlistId, videoId) }
    }

    fun createPlaylistAndAdd(name: String, videoId: String) {
        viewModelScope.launch {
            val playlistId = playlistRepository.create(name)
            playlistRepository.addVideo(playlistId, videoId)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TEXT_DEBOUNCE_MS = 250L
    }
}
