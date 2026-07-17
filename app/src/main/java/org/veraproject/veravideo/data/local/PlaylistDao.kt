package org.veraproject.veravideo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A playlist row plus the derived fields the list UI needs. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val videoCount: Int,
    val thumbnailUrl: String?,
)

@Dao
interface PlaylistDao {

    /**
     * Playlists with their size and cover image. The cover is the thumbnail of
     * the lowest-positioned item, resolved in SQL so the list does not have to
     * load every playlist's videos.
     */
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt,
               (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS videoCount,
               (SELECT v.thumbnailUrl
                  FROM playlist_items i
                  JOIN videos v ON v.videoId = i.videoId
                 WHERE i.playlistId = p.id
                 ORDER BY i.position ASC
                 LIMIT 1) AS thumbnailUrl
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    /**
     * The playlist's videos in order.
     *
     * An INNER JOIN would silently drop entries whose video is not in the local
     * catalog; the join is on `videos` because an entry without a known video
     * cannot be rendered at all. Videos that YouTube removed are still present
     * locally with status 'removed', so those entries survive and the UI marks
     * them unavailable.
     */
    @Query(
        """
        SELECT v.* FROM playlist_items i
        JOIN videos v ON v.videoId = i.videoId
        WHERE i.playlistId = :playlistId
        ORDER BY i.position ASC
        """,
    )
    fun observePlaylistVideos(playlistId: Long): Flow<List<VideoEntity>>

    @Query("SELECT playlistId FROM playlist_items WHERE videoId = :videoId")
    fun observePlaylistIdsContaining(videoId: String): Flow<List<Long>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, now: Instant)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, now: Instant)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteItem(playlistId: Long, videoId: String)

    @Query("SELECT videoId FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun orderedVideoIds(playlistId: Long): List<String>

    @Query("UPDATE playlist_items SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun setPosition(playlistId: Long, videoId: String, position: Int)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun countOf(playlistId: Long, videoId: String): Int

    /**
     * Append a video, ignoring a video already present so that adding twice is
     * harmless rather than a constraint crash.
     */
    @Transaction
    suspend fun addVideo(playlistId: Long, videoId: String, now: Instant) {
        if (countOf(playlistId, videoId) > 0) return
        insertItem(PlaylistItemEntity(playlistId, videoId, maxPosition(playlistId) + 1))
        touch(playlistId, now)
    }

    /** Remove a video and close the gap so positions stay dense. */
    @Transaction
    suspend fun removeVideo(playlistId: Long, videoId: String, now: Instant) {
        deleteItem(playlistId, videoId)
        renumber(playlistId)
        touch(playlistId, now)
    }

    /**
     * Move the item at [fromIndex] to [toIndex], shifting the rest.
     *
     * Reordering rewrites the whole playlist's positions inside one
     * transaction. That is O(n) writes, but n is a hand-curated playlist, and
     * it cannot leave positions duplicated or sparse the way pairwise swaps can.
     */
    @Transaction
    suspend fun moveVideo(playlistId: Long, fromIndex: Int, toIndex: Int, now: Instant) {
        val ids = orderedVideoIds(playlistId).toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return

        ids.add(toIndex, ids.removeAt(fromIndex))
        ids.forEachIndexed { index, videoId -> setPosition(playlistId, videoId, index) }
        touch(playlistId, now)
    }

    @Transaction
    suspend fun renumber(playlistId: Long) {
        orderedVideoIds(playlistId).forEachIndexed { index, videoId ->
            setPosition(playlistId, videoId, index)
        }
    }
}
