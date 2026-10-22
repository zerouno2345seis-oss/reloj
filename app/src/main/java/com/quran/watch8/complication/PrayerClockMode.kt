package com.quran.watch8.complication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Which of the three prayer readings the face's line shows: 0 time left to the
 * next prayer, 1 time since the last one, 2 the next prayer's time. A tap on
 * the line moves to the next reading.
 */
object PrayerClockMode {
    private const val PREFS = "watch_face"
    private const val KEY = "prayer_clock_mode"
    const val COUNT = 3

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0).mod(COUNT)

    fun advance(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY, (get(context) + 1) % COUNT).commit()
    }

    fun nextModeIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, PrayerClockModeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Refreshes the face just after [atMillis], when the next prayer comes in.
     * Without it the face would count towards a prayer already passed until
     * the five-minute update.
     */
    fun refreshAt(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val refresh = PendingIntent.getBroadcast(
            context, 1, Intent(context, PrayerClockModeReceiver::class.java).putExtra(EXTRA_REFRESH_ONLY, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, refresh)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, refresh)
        }
    }

    const val EXTRA_REFRESH_ONLY = "refresh_only"
}

class PrayerClockModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(PrayerClockMode.EXTRA_REFRESH_ONLY, false)) PrayerClockMode.advance(context)
        requestComplicationUpdate(context, PrayerClockComplicationService::class.java)
    }
}
