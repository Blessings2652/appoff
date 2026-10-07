package com.theblacksheep.appoff.service

import android.content.Context
import androidx.work.*
import com.theblacksheep.appoff.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            
            // 1. Identify targets
            val apps = AppRepository.getCleanableApps(context, includeSystem = false, extraWhitelist = emptySet())
                .map { it.packageName }
            
            if (apps.isNotEmpty()) {
                // 2. Perform Standard Clean (safe for automated maintenance)
                StandardCleaner.clean(context, apps)
            }

            // 3. Automated Junk Purge
            val junk = JunkRepository.scanJunk(context) {}
            if (junk.isNotEmpty()) {
                JunkRepository.cleanJunk(junk)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "deep_maintenance_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
