package org.veraproject.veravideo.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Mirrors `status` in the backend catalog. */
object VideoStatus {
    const val ACTIVE = "active"

    /** Gone from YouTube. Kept locally so playlists can say so instead of silently losing an entry. */
    const val REMOVED = "removed"
}

@Entity(
    tableName = "videos",
    indices = [
        Index("publishedAt"),
        Index("channelId"),
        Index("status"),
    ],
)
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val description: String,
    val channelId: String,
    val channelTitle: String,
    val publishedAt: Instant,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val tags: List<String>,
    val status: String,
    val updatedAt: Instant,
)

/**
 * Full-text index over the searchable columns of [VideoEntity].
 *
 * `contentEntity` makes this an external-content FTS table: it stores no copy
 * of the data, and Room generates triggers that keep it in sync with `videos`.
 * Fields must mirror the source entity's names and types exactly.
 */
@Fts4(contentEntity = VideoEntity::class)
@Entity(tableName = "videos_fts")
data class VideoFtsEntity(
    val videoId: String,
    val title: String,
    val description: String,
    val channelTitle: String,
    val tags: List<String>,
)

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val text: String,
    val channelId: String?,
    val minDurationSeconds: Int?,
    val maxDurationSeconds: Int?,
    val publishedAfter: Instant?,
    val publishedBefore: Instant?,
    val sortOrder: String,
    val createdAt: Instant,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Membership of a video in a playlist. `position` is dense and 0-based within a
 * playlist; [PlaylistDao] rewrites it on reorder.
 *
 * Deleting a playlist cascades. There is deliberately no foreign key to
 * `videos`: the catalog is a cache that re-syncs, and a playlist entry must
 * survive its video temporarily vanishing from it.
 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "position")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
)
