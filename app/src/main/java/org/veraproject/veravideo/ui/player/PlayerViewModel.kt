package org.veraproject.veravideo.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.veraproject.veravideo.data.CatalogRepository
import org.veraproject.veravideo.data.PlaylistRepository
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.ui.navigation.Routes
import javax.inject.Inject

data class PlayerUiState(
    val queue: List<Video> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaylist: Boolean = false,
    val isLoading: Boolean = true,
) {
    val currentVideo: Video? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < queue.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
    val upNext: List<Video> get() = if (currentIndex < queue.lastIndex) queue.drop(currentIndex + 1) else emptyList()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val videoId: String = checkNotNull(savedStateHandle[Routes.ARG_VIDEO_ID])
    private val playlistId: Long = savedStateHandle[Routes.ARG_PLAYLIST_ID] ?: Routes.NO_PLAYLIST

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { buildQueue() }
    }

    /**
     * The queue is captured once rather than observed.
     *
     * A live queue would be hostile during playback: editing the playlist in
     * another tab, or a background sync marking a video removed, would reorder
     * or yank what is currently on screen. A snapshot keeps the session stable.
     */
    private suspend fun buildQueue() {
        val videos = if (playlistId != Routes.NO_PLAYLIST) {
            val unavailable = playlistRepository.observeUnavailableVideoIds(playlistId).first()
            playlistRepository.observePlaylistVideos(playlistId).first()
                .filterNot { it.videoId in unavailable }
        } else {
            listOfNotNull(catalogRepository.observeVideo(videoId).first())
        }

        // Start from the tapped video; if it is not in the playlist (or is
        // unavailable), fall back to the top rather than showing nothing.
        val startIndex = videos.indexOfFirst { it.videoId == videoId }.takeIf { it >= 0 } ?: 0

        _uiState.value = PlayerUiState(
            queue = videos,
            currentIndex = startIndex,
            isPlaylist = playlistId != Routes.NO_PLAYLIST && videos.size > 1,
            isLoading = false,
        )
    }

    fun next() = _uiState.update {
        if (it.hasNext) it.copy(currentIndex = it.currentIndex + 1) else it
    }

    fun previous() = _uiState.update {
        if (it.hasPrevious) it.copy(currentIndex = it.currentIndex - 1) else it
    }

    fun playAt(index: Int) = _uiState.update {
        if (index in it.queue.indices) it.copy(currentIndex = index) else it
    }

    /** Called when the player reports a video finished; advances within a playlist only. */
    fun onVideoEnded() = _uiState.update {
        if (it.isPlaylist && it.hasNext) it.copy(currentIndex = it.currentIndex + 1) else it
    }
}
