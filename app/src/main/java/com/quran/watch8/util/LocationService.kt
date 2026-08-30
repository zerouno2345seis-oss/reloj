package com.quran.watch8.util

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Optional foreground service for continuous location if needed for car tracking.
 * Currently not started by default to save battery.
 */
class LocationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
