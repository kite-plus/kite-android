package com.kiteplus.kite.mobile

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kiteplus.kite.mobile.data.ContentRepository
import com.kiteplus.kite.mobile.data.MediaUploader
import com.kiteplus.kite.mobile.data.ServerRender
import com.kiteplus.kite.mobile.data.SettingsStore
import com.kiteplus.kite.mobile.data.local.KiteDatabase
import com.kiteplus.kite.mobile.domain.SyncEngine
import com.kiteplus.kite.mobile.work.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * One place where the app's objects are built, so that the screens, the worker
 * and the sync engine all talk to the same database and the same session.
 */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database = KiteDatabase.open(context)
    val repository = ContentRepository(
        dao = database.contents(),
        kinds = { settings.kinds },
        defaultKind = { settings.defaultKind ?: "post" },
    )
    val syncEngine = SyncEngine(database.contents(), settings)
    val uploader = MediaUploader(settings)
    val render = ServerRender(settings)
}

/**
 * The process: crash logging first, then the object graph, then the periodic sync.
 */
class KiteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so that a failure in anything below lands in the log file.
        CrashLog.install(this)
        container = AppContainer(this)
        scheduleSync()
    }

    /**
     * A periodic run is a safety net, not the primary path: the app syncs when
     * it opens, when the list is pulled down, and after an edit has settled.
     */
    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.NAME,
            // KEEP, because replacing the request on every launch would reset
            // the interval and a phone that is opened often would never sync.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
