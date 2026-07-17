package org.veraproject.veravideo.domain

import java.time.Instant

data class Playlist(
    val id: Long = 0,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val videoCount: Int = 0,
    /** Thumbnail of the first video, for the list card. */
    val thumbnailUrl: String? = null,
)

/** A playlist with its videos resolved, in order. */
data class PlaylistWithVideos(
    val playlist: Playlist,
    val videos: List<Video>,
)
