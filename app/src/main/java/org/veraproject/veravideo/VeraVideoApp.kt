package org.veraproject.veravideo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import org.veraproject.veravideo.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class VeraVideoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    /**
     * Supplies WorkManager with Hilt's factory so workers can be injected.
     * The manifest removes WorkManager's default initializer to make room for
     * this; the two must stay in step.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        syncScheduler.schedulePeriodicSync()
    }
}
