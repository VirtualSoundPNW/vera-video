package org.veraproject.veravideo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.veraproject.veravideo.ui.browse.BrowseScreen
import org.veraproject.veravideo.ui.navigation.Routes
import org.veraproject.veravideo.ui.navigation.TopLevelDestination
import org.veraproject.veravideo.ui.player.PlayerScreen
import org.veraproject.veravideo.ui.playlists.PlaylistDetailScreen
import org.veraproject.veravideo.ui.playlists.PlaylistsScreen
import org.veraproject.veravideo.ui.searches.SavedSearchesScreen

@Composable
fun VeraVideoAppUi(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar belongs to the tabs; the player and playlist detail are full-screen.
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination.navigateRoute) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSE,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(
                route = Routes.BROWSE,
                arguments = listOf(
                    navArgument(Routes.ARG_SAVED_SEARCH_ID) {
                        type = NavType.LongType
                        defaultValue = Routes.NO_SAVED_SEARCH
                    },
                ),
            ) {
                BrowseScreen(onVideoClick = { videoId -> navController.navigate(Routes.player(videoId)) })
            }

            composable(Routes.SEARCHES) {
                SavedSearchesScreen(
                    onSearchClick = { savedSearchId ->
                        // Hand the saved search to Browse, which applies it and
                        // becomes the active tab.
                        navController.navigate(Routes.browse(savedSearchId)) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onPlaylistClick = { playlistId -> navController.navigate(Routes.playlistDetail(playlistId)) },
                )
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
            ) { entry ->
                val playlistId = entry.arguments?.getLong(Routes.ARG_PLAYLIST_ID) ?: Routes.NO_PLAYLIST
                PlaylistDetailScreen(
                    onBack = navController::popBackStack,
                    onPlay = { videoId -> navController.navigate(Routes.player(videoId, playlistId)) },
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument(Routes.ARG_VIDEO_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_PLAYLIST_ID) {
                        type = NavType.LongType
                        defaultValue = Routes.NO_PLAYLIST
                    },
                ),
            ) {
                PlayerScreen(onBack = navController::popBackStack)
            }
        }
    }
}

/** Standard tab behaviour: single instance, state preserved, no back stack growth. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
