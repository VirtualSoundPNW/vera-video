package org.veraproject.veravideo.domain

import java.time.Instant

/** A video in the catalog, as the UI sees it. */
data class Video(
    val videoId: String,
    val title: String,
    val description: String,
    val channelId: String,
    val channelTitle: String,
    val publishedAt: Instant,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val tags: List<String>,
) {
    /** Canonical watch URL, used for sharing and for the "open in YouTube" action. */
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}
