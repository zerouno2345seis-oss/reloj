package com.quran.watch8.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> PrayerAlarmScheduler.scheduleAll(context)
            // An update cancels the alarms and the taps the faces hold: both again.
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                PrayerAlarmScheduler.scheduleAll(context)
                com.quran.watch8.complication.refreshAllComplications(context)
            }
        }
    }
}
