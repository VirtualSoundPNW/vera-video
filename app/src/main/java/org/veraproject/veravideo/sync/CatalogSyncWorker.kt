package org.veraproject.veravideo.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.veraproject.veravideo.data.CatalogRepository
import org.veraproject.veravideo.data.SyncResult

/**
 * Periodic catalog refresh.
 *
 * The catalog changes a few times a day at most (the backend crawls on a 6-hour
 * cadence), so this runs daily and is cheap when nothing changed — the stored
 * ETag turns an unchanged catalog into a 304.
 */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val catalogRepository: CatalogRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val result = catalogRepository.sync()) {
        is SyncResult.Updated -> {
            Log.i(TAG, "catalog synced: ${result.added} upserted, ${result.removed} removed")
            Result.success()
        }

        SyncResult.NotModified -> Result.success()

        // Retry rather than fail: the usual cause is no connectivity, and
        // WorkManager backs off on our behalf.
        is SyncResult.Failed -> {
            Log.w(TAG, "catalog sync failed, will retry", result.cause)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CatalogSyncWorker"
    }
}
