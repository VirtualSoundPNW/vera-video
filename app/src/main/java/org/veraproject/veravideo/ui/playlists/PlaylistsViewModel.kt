package org.veraproject.veravideo.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.veraproject.veravideo.data.PlaylistRepository
import org.veraproject.veravideo.domain.Playlist
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun create(name: String) {
        viewModelScope.launch { playlistRepository.create(name) }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch { playlistRepository.rename(playlistId, name) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch { playlistRepository.delete(playlistId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
