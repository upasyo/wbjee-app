package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val TAG = "WorkScheduler"
    private const val WORK_NAME = "wbjee_periodic_scan_work"

    fun schedulePeriodicScan(context: Context, intervalHours: Int = 1) {
        Log.d(TAG, "Scheduling periodic WBJEE scan every $intervalHours hours.")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<BackgroundScanWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
        .setConstraints(constraints)
        .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun forceSchedulePeriodicScan(context: Context, intervalHours: Int = 1) {
        Log.d(TAG, "Force rescheduling periodic WBJEE scan.")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<BackgroundScanWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
        .setConstraints(constraints)
        .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun cancelPeriodicScan(context: Context) {
        Log.d(TAG, "Cancelling periodic WBJEE scan.")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
