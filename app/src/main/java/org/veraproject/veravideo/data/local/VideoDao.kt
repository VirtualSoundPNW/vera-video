package org.veraproject.veravideo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder

@Dao
interface VideoDao {

    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE videoId = :videoId")
    fun observeVideo(videoId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE videoId IN (:videoIds)")
    suspend fun getByIds(videoIds: List<String>): List<VideoEntity>

    @Query("SELECT COUNT(*) FROM videos WHERE status = '${VideoStatus.ACTIVE}'")
    fun observeActiveCount(): Flow<Int>

    /** Distinct channels present in the catalog, for the channel filter. */
    @Query(
        """
        SELECT channelId, channelTitle, COUNT(*) AS videoCount
        FROM videos
        WHERE status = '${VideoStatus.ACTIVE}'
        GROUP BY channelId, channelTitle
        ORDER BY channelTitle COLLATE NOCASE ASC
        """,
    )
    fun observeChannels(): Flow<List<ChannelSummary>>

    @Query("SELECT videoId FROM videos WHERE status = '${VideoStatus.ACTIVE}'")
    suspend fun activeVideoIds(): List<String>

    @Query("UPDATE videos SET status = :status WHERE videoId IN (:videoIds)")
    suspend fun updateStatus(videoIds: List<String>, status: String)

    @Query("DELETE FROM videos")
    suspend fun clear()

    /**
     * Search is a raw query because the filters and sort are chosen at runtime;
     * expressing every combination as a static @Query is not practical.
     * [observedEntities] keeps the Flow reactive to writes on both tables.
     */
    @RawQuery(observedEntities = [VideoEntity::class, VideoFtsEntity::class])
    fun searchRaw(query: SupportSQLiteQuery): Flow<List<VideoEntity>>

    fun search(query: SearchQuery): Flow<List<VideoEntity>> = searchRaw(buildSearchQuery(query))

    @Transaction
    suspend fun replaceAll(videos: List<VideoEntity>) {
        clear()
        upsertAll(videos)
    }

    companion object {
        /**
         * Builds the search SQL. Values are always bound, never interpolated.
         *
         * The FTS table is joined only when there is search text: an
         * external-content FTS table cannot be scanned without a MATCH, and
         * browsing with no query must still list everything.
         */
        internal fun buildSearchQuery(query: SearchQuery): SimpleSQLiteQuery {
            val args = mutableListOf<Any>()
            val where = mutableListOf<String>()

            where += "status = ?"
            args += VideoStatus.ACTIVE

            FtsQuery.sanitize(query.text)?.let { match ->
                where += "videoId IN (SELECT videoId FROM videos_fts WHERE videos_fts MATCH ?)"
                args += match
            }

            query.channelId?.let {
                where += "channelId = ?"
                args += it
            }
            query.minDurationSeconds?.let {
                where += "durationSeconds >= ?"
                args += it
            }
            query.maxDurationSeconds?.let {
                where += "durationSeconds <= ?"
                args += it
            }
            query.publishedAfter?.let {
                where += "publishedAt >= ?"
                args += it.toEpochMilli()
            }
            query.publishedBefore?.let {
                where += "publishedAt <= ?"
                args += it.toEpochMilli()
            }

            // Sort is a fixed expression chosen by enum, never user input.
            val orderBy = when (query.sortOrder) {
                SortOrder.NEWEST -> "publishedAt DESC"
                SortOrder.OLDEST -> "publishedAt ASC"
                SortOrder.LONGEST -> "durationSeconds DESC"
                SortOrder.TITLE -> "title COLLATE NOCASE ASC"
            }

            val sql = "SELECT * FROM videos WHERE ${where.joinToString(" AND ")} ORDER BY $orderBy"
            return SimpleSQLiteQuery(sql, args.toTypedArray())
        }
    }
}

data class ChannelSummary(
    val channelId: String,
    val channelTitle: String,
    val videoCount: Int,
)
