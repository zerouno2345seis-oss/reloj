package com.quran.watch8.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.quran.watch8.MainActivity
import com.quran.watch8.data.model.ArgentinaLocations
import com.quran.watch8.data.repository.PreferencesRepository
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The prayer tile: the next prayer and how long until it, then the day's six
 * times. Location and method are the ones the app saved. Tapping opens the app.
 * The system asks again once a minute so the countdown stays current.
 */
class QuranPrayerTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val result = SettableFuture.create<TileBuilders.Tile>()
        scope.launch {
            try {
                val prefs = PreferencesRepository(this@QuranPrayerTileService)
                val prayers = PrayerTimesHelper.calculate(
                    latitude = prefs.selectedLat.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.latitude,
                    longitude = prefs.selectedLng.firstOrNull() ?: ArgentinaLocations.BUENOS_AIRES_CABA.longitude,
                    methodName = prefs.calculationMethod.firstOrNull() ?: PrayerTimesHelper.DEFAULT_METHOD,
                    locationName = prefs.selectedLocationName.firstOrNull() ?: "بوينس آيرس"
                )
                result.set(buildTile(prayers))
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun buildTile(prayers: PrayerTimesHelper.DayPrayers): TileBuilders.Tile {
        val status = PrayerTimesHelper.status(prayers, Instant.now())
        val root = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openApp())
                    .setPadding(ModifiersBuilders.Padding.Builder().setTop(dp(22f)).build())
                    .build()
            )

        if (status != null) {
            root.addContent(text("${status.next.nameAr} ${status.next.formatted}", 18f, GOLD, bold = true))
            root.addContent(text("بعد ${status.remainingText}", 13f, WHITE))
        }
        root.addContent(Spacer.Builder().setHeight(dp(8f)).build())

        val nextName = status?.next?.nameAr
        PrayerTimesHelper.schedule(prayers).forEach { p ->
            val isNext = p.nameAr == nextName
            root.addContent(
                text("${p.nameAr}  ${p.formatted}", 12f, if (isNext) GOLD else GRAY, bold = isNext)
            )
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(Layout.Builder().setRoot(root.build()).build())
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): LayoutElement =
        Text.Builder()
            .setText(value)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) FONT_WEIGHT_BOLD else FONT_WEIGHT_NORMAL)
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setBottom(dp(1f)).build())
                    .build()
            )
            .build()

    private fun openApp(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "2"
        const val GOLD = 0xFFC9A227.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GRAY = 0xFFB0B0B0.toInt()
    }
}
