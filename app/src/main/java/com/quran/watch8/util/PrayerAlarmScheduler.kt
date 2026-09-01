package com.quran.watch8.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.quran.watch8.data.model.ArgentinaLocations
import com.quran.watch8.data.model.PrayerReminderConfig
import com.quran.watch8.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

object PrayerAlarmScheduler {

    private const val TAG = "PrayerAlarmScheduler"

    fun scheduleAll(context: Context) {
        val prefs = PreferencesRepository(context)

        val (enabled, lat, lng, methodName, locName, remindersJson) = runBlocking {
            val en = prefs.notificationsEnabled.firstOrNull() ?: true
            val la = prefs.selectedLat.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.latitude
            val ln = prefs.selectedLng.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.longitude
            val m = prefs.calculationMethod.firstOrNull() ?: "ISNA"
            val n = prefs.selectedLocationName.firstOrNull() ?: "بوينس آيرس"
            val rj = prefs.prayerRemindersJson.firstOrNull() ?: ""
            Tuple6(en, la, ln, m, n, rj)
        }

        if (!enabled) {
            cancelAll(context)
            Log.d(TAG, "Prayer notifications are disabled in settings")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reminderConfig = PrayerReminderConfig.fromJson(remindersJson)

        val prayers = PrayerTimesHelper.calculate(
            latitude = lat,
            longitude = lng,
            methodName = methodName,
            locationName = locName
        )

        val prayerList = listOf(
            PrayerScheduleItem(1000, "fajr", "الفجر", prayers.fajr.time, prayers.fajr.formatted),
            PrayerScheduleItem(2000, "dhuhr", "الظهر", prayers.dhuhr.time, prayers.dhuhr.formatted),
            PrayerScheduleItem(3000, "asr", "العصر", prayers.asr.time, prayers.asr.formatted),
            PrayerScheduleItem(4000, "maghrib", "المغرب", prayers.maghrib.time, prayers.maghrib.formatted),
            PrayerScheduleItem(5000, "isha", "العشاء", prayers.isha.time, prayers.isha.formatted)
        )

        val now = Instant.now()

        for (item in prayerList) {
            // Get pre-reminders for this prayer (e.g. [15, 5]) plus on-time (0)
            val offsets = (reminderConfig.getMinutesForPrayer(item.key) + listOf(0)).distinct().sortedDescending()

            for (minsBefore in offsets) {
                val triggerInstant = item.time.minus(minsBefore.toLong(), ChronoUnit.MINUTES)
                val triggerMillis = triggerInstant.toEpochMilli()
                val reqCode = item.baseCode + minsBefore

                if (triggerInstant.isAfter(now)) {
                    val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        putExtra("prayer_name", item.nameAr)
                        putExtra("minutes_before", minsBefore)
                        putExtra("prayer_time_formatted", item.formattedTime)
                        putExtra("prayer_code", reqCode)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerMillis,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerMillis,
                                    pendingIntent
                                )
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerMillis,
                                pendingIntent
                            )
                        }
                        Log.d(TAG, "Scheduled alarm for ${item.nameAr} (-$minsBefore min) at $triggerInstant (Req: $reqCode)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule alarm for ${item.nameAr}: ${e.message}")
                    }
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val baseCodes = listOf(1000, 2000, 3000, 4000, 5000)
        for (base in baseCodes) {
            for (offset in 0..60) {
                val reqCode = base + offset
                val intent = Intent(context, PrayerAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    private data class PrayerScheduleItem(
        val baseCode: Int,
        val key: String,
        val nameAr: String,
        val time: Instant,
        val formattedTime: String
    )

    private data class Tuple6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
}
