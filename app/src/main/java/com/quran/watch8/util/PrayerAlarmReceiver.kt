package com.quran.watch8.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quran.watch8.ui.screens.FullScreenReminderActivity
import com.quran.watch8.data.model.PrayerReminderConfig
import com.quran.watch8.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Receives prayer time alarms (both on-time and pre-prayer offsets).
 * Launches FullScreenReminderActivity and posts high-priority Wear OS notification.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val minutesBefore = intent.getIntExtra("minutes_before", 0)
        val prayerTimeFormatted = intent.getStringExtra("prayer_time_formatted") ?: ""
        val reminderConfig = runBlocking {
            PrayerReminderConfig.fromJson(PreferencesRepository(context).prayerRemindersJson.firstOrNull() ?: "")
        }

        // 1. Launch Full Screen Reminder Activity
        val fullScreenIntent = Intent(context, FullScreenReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PRAYER_NAME", prayerName)
            putExtra("EXTRA_MINUTES_BEFORE", minutesBefore)
            putExtra("EXTRA_PRAYER_TIME", prayerTimeFormatted)
        }

        if (reminderConfig.isFullScreenEnabled) {
            try { context.startActivity(fullScreenIntent) } catch (_: Exception) {}
        }

        // 2. Also Post High-Priority Notification Channel on Wear OS
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "prayer_alerts",
                "تنبيهات أوقات الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات تنبيه دخول أوقات الصلاة والتذكيرات المسبقة"
                enableVibration(reminderConfig.isVibrationEnabled)
                if (reminderConfig.isVibrationEnabled) vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            nm.createNotificationChannel(channel)
        }

        val fullScreenPending = PendingIntent.getActivity(
            context,
            (prayerName.hashCode() + minutesBefore),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (minutesBefore > 0) "🕌 اقترب موعد $prayerName" else "🕌 حان موعد $prayerName"
        val text = if (minutesBefore > 0) "متبقي $minutesBefore دقيقة على الأذان" else "حان الآن موعد صلاة $prayerName"

        val notification = NotificationCompat.Builder(context, "prayer_alerts")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPending, reminderConfig.isFullScreenEnabled)
            .setContentIntent(fullScreenPending)
            .setVibrate(if (reminderConfig.isVibrationEnabled) longArrayOf(0, 600, 250, 600, 250, 600) else longArrayOf(0))
            .build()

        nm.notify(prayerName.hashCode() + minutesBefore, notification)

        // Reschedule next prayer cycle
        PrayerAlarmScheduler.scheduleAll(context)
    }
}
