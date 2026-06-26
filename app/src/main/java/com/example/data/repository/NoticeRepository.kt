package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.NoticeDao
import com.example.data.model.Notice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NoticeRepository(
    private val context: Context,
    private val noticeDao: NoticeDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wbjee_alert_prefs", Context.MODE_PRIVATE)

    val allNotices: Flow<List<Notice>> = noticeDao.getAllNotices()

    companion object {
        private const val TAG = "NoticeRepository"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
        private const val KEY_IS_FIRST_RUN = "is_first_run"
        private const val KEY_BACKGROUND_ENABLED = "background_scan_enabled"
        private const val KEY_NOTIFICATION_CATEGORIES = "notification_categories"
        private const val KEY_CHECK_INTERVAL_HOURS = "check_interval_hours"
    }

    fun getLastCheckedTime(): Long {
        return prefs.getLong(KEY_LAST_CHECKED, 0L)
    }

    fun setLastCheckedTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_CHECKED, timestamp).apply()
    }

    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_RUN, true)
    }

    fun setFirstRunCompleted() {
        prefs.edit().putBoolean(KEY_IS_FIRST_RUN, false).apply()
    }

    fun isBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_ENABLED, true) // Enable by default
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_ENABLED, enabled).apply()
    }

    fun getNotificationCategories(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFICATION_CATEGORIES, setOf("Counselling", "Schedule", "Notice")) ?: emptySet()
    }

    fun setNotificationCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFICATION_CATEGORIES, categories).apply()
    }

    fun getCheckIntervalHours(): Int {
        return prefs.getInt(KEY_CHECK_INTERVAL_HOURS, 1) // Default to 1 hour
    }

    fun setCheckIntervalHours(hours: Int) {
        prefs.edit().putInt(KEY_CHECK_INTERVAL_HOURS, hours).apply()
    }

    /**
     * Refreshes notices from the WBJEE webpage.
     * Detects if there are any new items, and triggers an alarm notification immediately.
     * Returns a list of notices that are NEW in this fetch session.
     */
    suspend fun refreshNotices(onNewNoticeDetected: (List<Notice>) -> Unit): Result<List<Notice>> {
        return try {
            Log.d(TAG, "Starting refreshNotices...")
            val fetched = Scraper.fetchNotices()
            if (fetched.isEmpty()) {
                Log.d(TAG, "No notices fetched from website.")
                setLastCheckedTime(System.currentTimeMillis())
                return Result.success(emptyList())
            }

            val isFirst = isFirstRun()
            val existingList = allNotices.first()
            val dbIsEmpty = existingList.isEmpty()

            // Prepare notices
            val noticesToInsert = fetched.map { notice ->
                // If it is first run or the database was completely empty, we save them as read (isNew = false)
                // so we don't trigger 20 alarms on the very first install check.
                if (isFirst || dbIsEmpty) {
                    notice.copy(isNew = false)
                } else {
                    notice
                }
            }

            // Insert into DB. insertNotices returns list of row IDs. Newly inserted = row ID > 0. Ignored (duplicate) = -1.
            val insertResults = noticeDao.insertNotices(noticesToInsert)
            setLastCheckedTime(System.currentTimeMillis())

            val newlyInsertedNotices = mutableListOf<Notice>()
            for (i in insertResults.indices) {
                if (insertResults[i] > 0) {
                    newlyInsertedNotices.add(noticesToInsert[i])
                }
            }

            if (isFirst || dbIsEmpty) {
                setFirstRunCompleted()
                Log.d(TAG, "Initial run complete. Saved ${noticesToInsert.size} notices as archive (silently).")
            } else if (newlyInsertedNotices.isNotEmpty()) {
                val enabledCategories = getNotificationCategories()
                val noticesToAlert = newlyInsertedNotices.filter { it.category in enabledCategories }
                if (noticesToAlert.isNotEmpty()) {
                    Log.d(TAG, "Detected ${noticesToAlert.size} new notices in enabled categories! Alerting user.")
                    onNewNoticeDetected(noticesToAlert)
                } else {
                    Log.d(TAG, "Detected ${newlyInsertedNotices.size} new notices, but none in enabled categories.")
                }
            } else {
                Log.d(TAG, "Sync complete. No new notices found.")
            }

            Result.success(newlyInsertedNotices)
        } catch (e: Exception) {
            Log.e(TAG, "Error in refreshNotices", e)
            Result.failure(e)
        }
    }

    suspend fun markAsRead(id: String) {
        noticeDao.markAsRead(id)
    }

    suspend fun markAllAsRead() {
        noticeDao.markAllAsRead()
    }

    suspend fun clearAll() {
        noticeDao.clearAll()
        prefs.edit().putBoolean(KEY_IS_FIRST_RUN, true).apply()
    }
}
