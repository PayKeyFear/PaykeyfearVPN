package com.paykeyfear.vpn.geo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the GeoIP + Geosite RU payloads from the configured source
 * every 12 hours. The worker is scheduled via [GeoUpdateScheduler] and
 * the repository it uses is supplied by a factory so the consumer app
 * (Hilt graph) can inject network/http implementations.
 */
class GeoUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = factory?.create(applicationContext) ?: run {
            val store = GeoStore(applicationContext)
            GeoRepository(HttpGeoSource(), store)
        }
        val report = repository.refresh()
        return if (report.allOk) Result.success() else Result.retry()
    }

    /** Supplies a [GeoRepository] bound to the app context. Set once at startup. */
    fun interface Factory {
        fun create(context: Context): GeoRepository
    }

    companion object {
        const val UNIQUE_NAME = "paykeyfear.geo.update"

        @Volatile
        internal var factory: Factory? = null

        fun installFactory(factory: Factory) {
            this.factory = factory
        }
    }
}

object GeoUpdateScheduler {
    private const val INTERVAL_HOURS = 12L
    private const val FLEX_HOURS = 1L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GeoUpdateWorker>(
            INTERVAL_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(GeoUpdateWorker.UNIQUE_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GeoUpdateWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(GeoUpdateWorker.UNIQUE_NAME)
    }
}
