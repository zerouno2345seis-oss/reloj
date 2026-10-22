package com.quran.watch8.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.quran.watch8.MainActivity
import com.quran.watch8.QuranWatchApplication
import com.quran.watch8.R
import com.quran.watch8.data.model.ArgentinaLocations
import com.quran.watch8.ui.components.ReaderTypography
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.util.concurrent.TimeUnit

/*
 * The app as a source of complications: the small slots a watch face shows.
 * Any face that takes complications (Samsung's own included) can show these,
 * and a tap on one opens that part of the app directly. Since January 2026 a
 * watch face itself has to be a code-free Watch Face Format package, so this
 * is how the face and the app talk.
 */

/** Opens the app on [route]; the request code keeps one PendingIntent per route. */
internal fun Context.openRoute(route: String): PendingIntent =
    PendingIntent.getActivity(
        this,
        route.hashCode(),
        Intent(this, MainActivity::class.java)
            .putExtra("targetRoute", route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

private fun text(s: String) = PlainComplicationText.Builder(s).build()

/** Asks every face showing [service] to fetch it again. */
fun requestComplicationUpdate(context: Context, service: Class<*>) {
    ComplicationDataSourceUpdateRequester
        .create(context, ComponentName(context, service))
        .requestUpdateAll()
}

/** The next prayer: its name and a countdown the face keeps ticking itself. */
class NextPrayerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "العصر", "٤:١٥", Instant.now().plusSeconds(4_800), 0.6f)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = (applicationContext as QuranWatchApplication).prefsRepository
        val prayers = PrayerTimesHelper.calculate(
            latitude = prefs.selectedLat.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.latitude,
            longitude = prefs.selectedLng.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.longitude,
            methodName = prefs.calculationMethod.firstOrNull() ?: "ISNA",
            locationName = prefs.selectedLocationName.firstOrNull() ?: ""
        )
        val status = PrayerTimesHelper.status(prayers) ?: return null
        val total = status.minutesElapsed + status.minutesRemaining
        val progress = if (total > 0) status.minutesElapsed / total.toFloat() else 0f
        return build(request.complicationType, status.next.nameAr, status.next.formatted, status.next.time, progress)
    }

    private fun build(type: ComplicationType, name: String, at: String, time: Instant, progress: Float): ComplicationData? {
        val countdown = TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_DUAL_UNIT,
            CountDownTimeReference(time)
        ).setMinimumTimeUnit(TimeUnit.MINUTES).build()
        val tap = openRoute("prayer")
        val description = text("$name $at")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(countdown, description)
                    .setTitle(text(name)).setTapAction(tap).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text("$name $at"), description)
                    .setTitle(countdown).setTapAction(tap).build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(progress, 0f, 1f, description)
                    .setText(text(name)).setTitle(countdown).setTapAction(tap).build()
            else -> null
        }
    }
}

/** Where reading stopped; a tap opens the reader there. */
class ContinueReadingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "الكهف", 10, 18)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val position = (applicationContext as QuranWatchApplication)
            .databaseRepository.lastReadingPosition.firstOrNull()
        return if (position == null) build(request.complicationType, null, 0, 0)
        else build(request.complicationType, position.surahNameAr, position.surah, position.ayahNumber)
    }

    private fun build(type: ComplicationType, surahName: String?, surah: Int, ayah: Int): ComplicationData? {
        val tap = openRoute(if (surahName != null) "reader/$surah?startAyah=$ayah" else "quran")
        val label = if (surahName != null) "$surahName ${ReaderTypography.arabicDigits(ayah)}" else "المصحف"
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.img_quran)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(label), text("متابعة القراءة: $label"))
                    .setMonochromaticImage(icon).setTapAction(tap).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(label), text("متابعة القراءة: $label"))
                    .setTitle(text("متابعة القراءة")).setMonochromaticImage(icon).setTapAction(tap).build()
            else -> null
        }
    }
}

/** Today's Hijri date. */
class HijriDateComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    private fun build(type: ComplicationType): ComplicationData? {
        val full = HijriDate.arabic()
        // "١٩ صفر ١٤٤٨ هـ" -> day and month for a small slot.
        val short = full.split(' ').take(2).joinToString(" ")
        val tap = openRoute("prayer")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(short), text(full)).setTapAction(tap).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(full), text(full)).setTapAction(tap).build()
            else -> null
        }
    }
}

/** A plain shortcut into one part of the app. */
abstract class ShortcutComplicationService(
    private val route: String,
    private val label: String,
    private val iconRes: Int
) : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    private fun build(type: ComplicationType): ComplicationData? {
        val tap = openRoute(route)
        val icon = Icon.createWithResource(this, iconRes)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(label), text(label))
                    .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
                    .setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(SmallImage.Builder(icon, SmallImageType.ICON).build(), text(label))
                    .setTapAction(tap).build()
            else -> null
        }
    }
}

class QuranShortcutComplicationService : ShortcutComplicationService("quran", "المصحف", R.drawable.img_quran)
class BookmarksShortcutComplicationService : ShortcutComplicationService("bookmarks", "الإشارات", R.drawable.img_bookmark)
