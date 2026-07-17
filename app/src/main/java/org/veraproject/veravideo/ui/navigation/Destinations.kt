package org.veraproject.veravideo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.graphics.vector.ImageVector
import org.veraproject.veravideo.R

object Routes {
    /** Browse optionally opens with a saved search already applied. */
    const val BROWSE = "browse?savedSearchId={savedSearchId}"
    fun browse(savedSearchId: Long? = null) =
        "browse" + if (savedSearchId != null) "?savedSearchId=$savedSearchId" else ""

    const val SEARCHES = "searches"
    const val PLAYLISTS = "playlists"

    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"

    /**
     * The player takes an optional playlist so it can auto-advance through one;
     * with no playlist it plays a single video.
     */
    const val PLAYER = "player/{videoId}?playlistId={playlistId}"
    fun player(videoId: String, playlistId: Long? = null) =
        "player/$videoId" + if (playlistId != null) "?playlistId=$playlistId" else ""

    const val ARG_PLAYLIST_ID = "playlistId"
    const val ARG_VIDEO_ID = "videoId"
    const val ARG_SAVED_SEARCH_ID = "savedSearchId"

    /** Sentinels, since nav arguments cannot be null Longs. */
    const val NO_PLAYLIST = -1L
    const val NO_SAVED_SEARCH = -1L
}

enum class TopLevelDestination(
    /** Route pattern, used to match the current destination for tab selection. */
    val route: String,
    /** Concrete route to navigate to — a pattern with placeholders is not navigable. */
    val navigateRoute: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    BROWSE(Routes.BROWSE, "browse", Icons.Filled.VideoLibrary, R.string.nav_browse),
    SEARCHES(Routes.SEARCHES, Routes.SEARCHES, Icons.Outlined.BookmarkBorder, R.string.nav_searches),
    PLAYLISTS(Routes.PLAYLISTS, Routes.PLAYLISTS, Icons.Filled.PlaylistPlay, R.string.nav_playlists),
}
