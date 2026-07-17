package org.veraproject.veravideo.ui.searches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.veraproject.veravideo.data.CatalogRepository
import org.veraproject.veravideo.data.SavedSearchRepository
import org.veraproject.veravideo.domain.SavedSearch
import javax.inject.Inject

/** A saved search plus how many videos it currently matches. */
data class SavedSearchWithCount(
    val search: SavedSearch,
    val matchCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedSearchesViewModel @Inject constructor(
    private val savedSearchRepository: SavedSearchRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    /**
     * Each saved search's live match count.
     *
     * The counts are derived by running every saved search against the local
     * catalog and recomputing when either the list or the catalog changes.
     * That is only viable because searches are few and the catalog is local —
     * it would be untenable against the network.
     */
    val searches: StateFlow<List<SavedSearchWithCount>> = savedSearchRepository.observeAll()
        .flatMapLatest { searches ->
            if (searches.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    searches.map { search ->
                        catalogRepository.observeVideos(search.query).map { SavedSearchWithCount(search, it.size) }
                    },
                ) { it.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { savedSearchRepository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
