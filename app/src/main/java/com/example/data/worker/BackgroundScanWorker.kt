package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.repository.NoticeRepository
import com.example.util.AlertNotificationManager

class BackgroundScanWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BackgroundScanWorker", "Starting periodic WBJEE web scan...")
        
        val db = AppDatabase.getDatabase(context)
        val repository = NoticeRepository(context, db.noticeDao())

        // Check if background scanning is enabled in user settings
        if (!repository.isBackgroundEnabled()) {
            Log.d("BackgroundScanWorker", "Background scan is disabled in settings. Skipping.")
            return Result.success()
        }

        val result = repository.refreshNotices { newNotices ->
            for (notice in newNotices) {
                Log.d("BackgroundScanWorker", "New announcement found: ${notice.title}. Triggering notification.")
                AlertNotificationManager.sendNewNoticeNotification(context, notice)
            }
        }

        return if (result.isSuccess) {
            Log.d("BackgroundScanWorker", "Periodic scan completed successfully.")
            Result.success()
        } else {
            Log.e("BackgroundScanWorker", "Periodic scan failed.", result.exceptionOrNull())
            Result.retry()
        }
    }
}
