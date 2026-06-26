package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Notice

object AlertNotificationManager {
    private const val CHANNEL_ID = "wbjee_high_priority_alerts"
    private const val CHANNEL_NAME = "WBJEE Board Alarms & Updates"
    private const val CHANNEL_DESC = "Urgent high-priority alarms for new WBJEE counselling notices and announcements."
    private const val NOTIFICATION_ID_BASE = 1000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800) // Urgent pulsing vibration
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNewNoticeNotification(context: Context, notice: Notice) {
        createNotificationChannel(context)

        // Intent to launch MainActivity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notice.id.hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open document directly in browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(notice.url))
        val browserPendingIntent = PendingIntent.getActivity(
            context,
            notice.id.hashCode() + 1,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // High visibility system icon or fallback
            .setContentTitle("🚨 New WBJEE ${notice.category} Alert!")
            .setContentText(notice.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${notice.title}\n\nCategory: ${notice.category}\nDate: ${notice.date}\n\nTap to open, or tap 'Download PDF' to download the notice directly."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .setFullScreenIntent(appPendingIntent, true) // High visibility full screen intent support
            .addAction(
                android.R.drawable.ic_menu_save,
                "Download / Open PDF",
                browserPendingIntent
            )

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Ensure permission check (calling will fail in Android 13+ if not granted, handled in UI)
            notificationManager.notify(notice.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun triggerTestAlarm(context: Context) {
        createNotificationChannel(context)

        val testNotice = Notice(
            id = "test_id",
            title = "Notification for WBJEE 2026 Seat Allotment (Mock Alarm Test)",
            url = "https://wbjeeb.nic.in/wbjee/",
            date = "25-06-2026",
            category = "Counselling",
            scannedAt = System.currentTimeMillis(),
            isNew = true,
            description = "This is a demonstration of the WBJEE high-priority alarm notification."
        )

        sendNewNoticeNotification(context, testNotice)
    }
}
