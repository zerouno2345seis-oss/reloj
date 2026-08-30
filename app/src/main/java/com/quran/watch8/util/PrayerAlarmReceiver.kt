package com.quran.watch8.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.quran.watch8.MainActivity
import com.quran.watch8.R

/**
 * Receives prayer time alarms and shows high-priority notification + vibration.
 * Schedule alarms using AlarmManager when prayer times are calculated.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "prayer_alerts")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت $prayerName")
            .setContentText("حان الآن موعد صلاة $prayerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        nm.notify(prayerName.hashCode(), notification)
    }
}
