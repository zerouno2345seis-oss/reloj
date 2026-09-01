package com.quran.watch8

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.quran.watch8.data.db.QuranDatabase
import com.quran.watch8.data.repository.DatabaseRepository
import com.quran.watch8.data.repository.PreferencesRepository
import com.quran.watch8.data.repository.QuranRepository

class QuranWatchApplication : Application() {

    lateinit var quranRepository: QuranRepository
        private set
    lateinit var prefsRepository: PreferencesRepository
        private set
    lateinit var databaseRepository: DatabaseRepository
        private set

    override fun onCreate() {
        super.onCreate()

        quranRepository = QuranRepository(this)
        prefsRepository = PreferencesRepository(this)

        // Room database (lazy singleton inside QuranDatabase.getInstance)
        val db = QuranDatabase.getInstance(this)
        databaseRepository = DatabaseRepository(
            bookmarkDao      = db.bookmarkDao(),
            locationDao      = db.savedLocationDao(),
            voiceNoteDao     = db.voiceNoteDao(),
            readingPositionDao = db.readingPositionDao()
        )

        // Notification channel for prayer alerts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "prayer_alerts",
                "تنبيهات الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات أوقات الصلاة"
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
