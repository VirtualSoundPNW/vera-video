package org.veraproject.veravideo.domain

import java.time.Instant

/** How to order results. */
enum class SortOrder {
    NEWEST,
    OLDEST,
    LONGEST,
    TITLE,
}

/**
 * A search over the local catalog: free text plus optional filters.
 *
 * This is exactly what a saved search stores, so saving one is just persisting
 * the current [SearchQuery].
 */
data class SearchQuery(
    val text: String = "",
    val channelId: String? = null,
    val minDurationSeconds: Int? = null,
    val maxDurationSeconds: Int? = null,
    val publishedAfter: Instant? = null,
    val publishedBefore: Instant? = null,
    val sortOrder: SortOrder = SortOrder.NEWEST,
) {
    val hasDateRange: Boolean
        get() = publishedAfter != null || publishedBefore != null

    val hasFilters: Boolean
        get() = channelId != null ||
            minDurationSeconds != null ||
            maxDurationSeconds != null ||
            hasDateRange

    val isEmpty: Boolean
        get() = text.isBlank() && !hasFilters
}
