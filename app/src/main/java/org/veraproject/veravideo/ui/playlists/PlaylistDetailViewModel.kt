package org.veraproject.veravideo.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.veraproject.veravideo.data.PlaylistRepository
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.ui.navigation.Routes
import org.veraproject.veravideo.util.ShareLinks
import javax.inject.Inject

data class PlaylistDetailUiState(
    val name: String = "",
    val videos: List<Video> = emptyList(),
    val unavailableVideoIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
) {
    /** Videos that can actually be played, in order. */
    val playableVideos: List<Video> get() = videos.filterNot { it.videoId in unavailableVideoIds }

    val canPlay: Boolean get() = playableVideos.isNotEmpty()

    /** True when the playlist is longer than a single share link can carry. */
    val shareTruncated: Boolean get() = ShareLinks.isTruncated(playableVideos.map { it.videoId })
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle[Routes.ARG_PLAYLIST_ID])

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.observePlaylistWithVideos(playlistId),
        playlistRepository.observeUnavailableVideoIds(playlistId),
    ) { playlist, unavailable ->
        PlaylistDetailUiState(
            name = playlist?.playlist?.name.orEmpty(),
            videos = playlist?.videos.orEmpty(),
            unavailableVideoIds = unavailable,
            isLoading = playlist == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistDetailUiState())

    fun removeVideo(videoId: String) {
        viewModelScope.launch { playlistRepository.removeVideo(playlistId, videoId) }
    }

    fun moveVideo(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepository.moveVideo(playlistId, fromIndex, toIndex) }
    }

    /** Share text for the current playlist; unavailable videos are left out. */
    fun buildShareText(): String {
        val state = uiState.value
        return ShareLinks.playlistShareText(
            playlistName = state.name,
            videos = state.playableVideos.map { it.videoId to it.title },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
