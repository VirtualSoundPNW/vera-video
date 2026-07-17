package org.veraproject.veravideo.data

import org.veraproject.veravideo.data.local.PlaylistSummary
import org.veraproject.veravideo.data.local.SavedSearchEntity
import org.veraproject.veravideo.data.local.VideoEntity
import org.veraproject.veravideo.data.local.VideoStatus
import org.veraproject.veravideo.data.remote.CatalogVideoDto
import org.veraproject.veravideo.domain.Playlist
import org.veraproject.veravideo.domain.SavedSearch
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder
import org.veraproject.veravideo.domain.Video
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A video whose timestamps the server sent malformed is dropped rather than
 * defaulted: silently mapping it to the epoch would park it at the bottom of
 * every "newest first" list forever, which is harder to notice than it missing.
 */
fun CatalogVideoDto.toEntity(): VideoEntity? {
    val published = parseInstantOrNull(publishedAt) ?: return null
    val updated = parseInstantOrNull(updatedAt) ?: return null

    return VideoEntity(
        videoId = videoId,
        title = title,
        description = description,
        channelId = channelId,
        channelTitle = channelTitle,
        publishedAt = published,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
        tags = tags,
        status = status,
        updatedAt = updated,
    )
}

private fun parseInstantOrNull(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

fun VideoEntity.toDomain(): Video = Video(
    videoId = videoId,
    title = title,
    description = description,
    channelId = channelId,
    channelTitle = channelTitle,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    tags = tags,
)

val VideoEntity.isAvailable: Boolean get() = status == VideoStatus.ACTIVE

fun SavedSearchEntity.toDomain(): SavedSearch = SavedSearch(
    id = id,
    name = name,
    query = SearchQuery(
        text = text,
        channelId = channelId,
        minDurationSeconds = minDurationSeconds,
        maxDurationSeconds = maxDurationSeconds,
        publishedAfter = publishedAfter,
        publishedBefore = publishedBefore,
        sortOrder = sortOrder.toSortOrder(),
    ),
    createdAt = createdAt,
)

fun SavedSearch.toEntity(): SavedSearchEntity = SavedSearchEntity(
    id = id,
    name = name,
    text = query.text,
    channelId = query.channelId,
    minDurationSeconds = query.minDurationSeconds,
    maxDurationSeconds = query.maxDurationSeconds,
    publishedAfter = query.publishedAfter,
    publishedBefore = query.publishedBefore,
    sortOrder = query.sortOrder.name,
    createdAt = createdAt,
)

/** Tolerates an unknown stored value (e.g. an enum removed in a later version). */
private fun String.toSortOrder(): SortOrder =
    SortOrder.entries.firstOrNull { it.name == this } ?: SortOrder.NEWEST

fun PlaylistSummary.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    videoCount = videoCount,
    thumbnailUrl = thumbnailUrl,
)
