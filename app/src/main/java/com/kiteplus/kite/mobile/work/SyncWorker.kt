package com.kiteplus.kite.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiteplus.kite.mobile.KiteApp

/**
 * The background half of syncing: same engine as the pull-to-refresh path, run
 * by WorkManager so it happens even when the app is closed.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KiteApp ?: return Result.success()
        val report = app.container.syncEngine.sync()

        return when {
            // Nothing to do until an address or a session exists; retrying
            // would only burn battery.
            report.blocked != null -> Result.success()
            report.error == null -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val NAME = "kite-sync"
        private const val MAX_ATTEMPTS = 3
    }
}
