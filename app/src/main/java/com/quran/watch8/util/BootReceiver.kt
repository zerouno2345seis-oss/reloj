package com.quran.watch8.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedule prayer alarms after device reboot.
 * In a full implementation, re-calculate times and set AlarmManager again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: re-schedule prayer notifications from stored location + method
        }
    }
}
