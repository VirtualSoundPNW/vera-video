package org.veraproject.veravideo.domain

import java.time.Instant

/** A named, persisted [SearchQuery]. */
data class SavedSearch(
    val id: Long = 0,
    val name: String,
    val query: SearchQuery,
    val createdAt: Instant,
)
