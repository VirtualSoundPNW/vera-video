package org.veraproject.veravideo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.veraproject.veravideo.data.local.ChannelSummary
import org.veraproject.veravideo.data.local.VideoDao
import org.veraproject.veravideo.data.local.VideoStatus
import org.veraproject.veravideo.data.prefs.SyncPrefs
import org.veraproject.veravideo.data.remote.CatalogApi
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.Video
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncResult {
    data class Updated(val added: Int, val removed: Int) : SyncResult
    data object NotModified : SyncResult
    data class Failed(val cause: Throwable) : SyncResult
}

/**
 * Owns the local catalog and its synchronization with vera-video-backend.
 *
 * Reads always come from Room, so the app works offline and search is instant;
 * the network only ever refreshes that local copy.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: CatalogApi,
    private val videoDao: VideoDao,
    private val syncPrefs: SyncPrefs,
) {

    fun observeVideos(query: SearchQuery): Flow<List<Video>> =
        videoDao.search(query).map { entities -> entities.map { it.toDomain() } }

    fun observeVideo(videoId: String): Flow<Video?> =
        videoDao.observeVideo(videoId).map { it?.toDomain() }

    fun observeVideoAvailability(videoId: String): Flow<Boolean?> =
        videoDao.observeVideo(videoId).map { it?.isAvailable }

    fun observeChannels(): Flow<List<ChannelSummary>> = videoDao.observeChannels()

    fun observeCatalogSize(): Flow<Int> = videoDao.observeActiveCount()

    val lastSyncedAt: Flow<Instant?> get() = syncPrefs.lastSyncedAt

    suspend fun getVideos(videoIds: List<String>): List<Video> =
        videoDao.getByIds(videoIds).map { it.toDomain() }

    /**
     * Pull changes from the backend.
     *
     * Uses the stored cursor for a delta and the stored ETag to short-circuit
     * on 304. Network failure is reported, not thrown: the local catalog is
     * still perfectly usable and callers decide whether to surface it.
     */
    suspend fun sync(): SyncResult = try {
        val cursor = syncPrefs.currentCursor()
        val response = api.getCatalog(since = cursor, ifNoneMatch = syncPrefs.currentEtag())

        when {
            response.code() == HTTP_NOT_MODIFIED -> {
                syncPrefs.recordSync(cursor, response.headers()[HEADER_ETAG], Instant.now())
                SyncResult.NotModified
            }

            !response.isSuccessful -> SyncResult.Failed(IOException("catalog sync failed: HTTP ${response.code()}"))

            else -> {
                val body = response.body() ?: return SyncResult.Failed(IOException("catalog sync returned no body"))
                val applied = apply(body.videos.mapNotNull { it.toEntity() }, isDelta = body.delta)
                syncPrefs.recordSync(body.cursor ?: cursor, response.headers()[HEADER_ETAG], Instant.now())
                applied
            }
        }
    } catch (e: IOException) {
        SyncResult.Failed(e)
    } catch (e: retrofit2.HttpException) {
        SyncResult.Failed(e)
    }

    private suspend fun apply(
        videos: List<org.veraproject.veravideo.data.local.VideoEntity>,
        isDelta: Boolean,
    ): SyncResult.Updated {
        videoDao.upsertAll(videos)

        // A full response lists only what is currently active, so anything
        // active locally but absent from it is gone. Reconciling this way —
        // rather than wiping the table — keeps rows that playlists point at.
        // Deltas carry their own 'removed' rows, so no reconciliation needed.
        var removed = 0
        if (!isDelta) {
            val returned = videos.mapTo(HashSet()) { it.videoId }
            val stale = videoDao.activeVideoIds().filterNot { it in returned }
            stale.chunked(SQLITE_VARIABLE_CHUNK).forEach { chunk ->
                videoDao.updateStatus(chunk, VideoStatus.REMOVED)
            }
            removed = stale.size
        } else {
            removed = videos.count { it.status == VideoStatus.REMOVED }
        }

        return SyncResult.Updated(added = videos.size, removed = removed)
    }

    /** Drops the cached catalog and forces the next sync to fetch everything. */
    suspend fun clearCache() {
        videoDao.clear()
        syncPrefs.reset()
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val HEADER_ETAG = "ETag"

        /**
         * SQLite caps host parameters per statement (999 on many Android
         * versions), so `IN (...)` lists are chunked well below it.
         */
        const val SQLITE_VARIABLE_CHUNK = 500
    }
}
