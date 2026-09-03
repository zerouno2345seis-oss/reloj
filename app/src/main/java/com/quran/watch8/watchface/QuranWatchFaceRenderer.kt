package com.quran.watch8.watchface

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.quran.watch8.MainActivity
import com.quran.watch8.data.model.SlotItem
import com.quran.watch8.data.model.TileActionCatalog
import com.quran.watch8.data.model.TileConfig
import com.quran.watch8.data.repository.PreferencesRepository
import com.quran.watch8.util.HijriDate
import com.quran.watch8.util.PrayerTimesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class QuranWatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<QuranWatchFaceRenderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    // A digital face only ever changes once a second, so redrawing at 60fps
    // burned ~60x the power for no visible difference.
    1000L,
    true
), WatchFace.TapListener {

    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = PreferencesRepository(context)

    // Read on the render thread, written from the collectors below.
    @Volatile private var currentConfig: TileConfig = TileConfig()
    @Volatile private var lastPrayerResult: PrayerTimesHelper.DayPrayers? = null

    private val clickableTiles = mutableListOf<ClickableTile>()

    data class ClickableTile(
        val rect: RectF,
        val actionId: String
    )

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subTextPaint = Paint().apply {
        color = Color.parseColor("#E2E8F0")
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        scope.launch {
            prefs.tilesConfigJson.collect { json ->
                runCatching {
                    currentConfig = TileConfig.fromJson(json)
                }
            }
        }
        // Prayer times now follow the location the user actually picked, and
        // recompute whenever it changes or the day rolls over. The old code
        // pinned one hardcoded point in Buenos Aires, so on any other location
        // the countdown drifted (e.g. "0m to Fajr" late at night).
        scope.launch {
            combine(
                prefs.selectedLat,
                prefs.selectedLng,
                prefs.selectedLocationName,
                prefs.calculationMethod,
            ) { lat, lng, name, method -> LocationParams(lat, lng, name, method) }
                .collectLatest { p ->
                    // collectLatest cancels this block on a new location or on
                    // scope shutdown; delay() is the cancellation point.
                    while (true) {
                        runCatching {
                            lastPrayerResult = PrayerTimesHelper.calculate(
                                latitude = p.lat,
                                longitude = p.lng,
                                methodName = p.method,
                                locationName = p.name,
                            )
                        }
                        // Refresh across midnight so tomorrow's Fajr is used.
                        delay(15 * 60 * 1000L)
                    }
                }
        }
    }

    private data class LocationParams(
        val lat: Double,
        val lng: Double,
        val name: String,
        val method: String,
    )

    override fun onDestroy() {
        scope.cancel()
    }

    override suspend fun createSharedAssets(): SharedAssets {
        return SharedAssets()
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val isAmbient = watchState.isAmbient.value ?: false
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        clickableTiles.clear()

        // The persisted studio appearance is applied by both the renderer and Compose home screen.
        canvas.drawColor(patternColor(currentConfig.appearance.pattern))

        if (isAmbient) {
            drawAmbientMode(canvas, width, height, zonedDateTime)
            return
        }

        currentConfig.tiles.forEachIndexed { index, slot ->
            val circular = currentConfig.appearance.tileShape == "circle"
            val ringCount = (currentConfig.tiles.size - 1).coerceAtLeast(1)
            val centerSide = if (ringCount > 7) 26f else 31f
            val ringSide = if (ringCount > 8) 15f else if (ringCount > 5) 17f else 20f
            val radius = if (ringCount > 8) 34f else if (ringCount > 5) 31f else 29f
            val angle = (-Math.PI / 2.0) + (2.0 * Math.PI * (index - 1) / ringCount)
            val layoutWidth = if (circular) if (index == 0) centerSide else ringSide else slot.width
            val layoutHeight = if (circular) layoutWidth else slot.height
            val layoutX = when {
                !circular -> slot.x
                index == 0 -> 50f - centerSide / 2f
                else -> (50f + kotlin.math.cos(angle).toFloat() * radius - ringSide / 2f).coerceIn(2f, 98f - ringSide)
            }
            val layoutY = when {
                !circular -> slot.y
                index == 0 -> 50f - centerSide / 2f
                else -> (50f + kotlin.math.sin(angle).toFloat() * radius - ringSide / 2f).coerceIn(2f, 98f - ringSide)
            }
            val tileRect = RectF(
                layoutX / 100f * width,
                layoutY / 100f * height,
                (layoutX + layoutWidth) / 100f * width,
                (layoutY + layoutHeight) / 100f * height
            )
            val drawableRect = if (currentConfig.appearance.tileShape == "circle") {
                val side = minOf(tileRect.width(), tileRect.height())
                RectF(tileRect.centerX() - side / 2f, tileRect.centerY() - side / 2f, tileRect.centerX() + side / 2f, tileRect.centerY() + side / 2f)
            } else tileRect
            clickableTiles.add(ClickableTile(drawableRect, if (slot.tapAction.isNotBlank()) slot.tapAction else slot.id))
            bgPaint.color = paletteColor(slot.colorHex, currentConfig.appearance.iconPalette)
            drawTileBackground(canvas, drawableRect, currentConfig.appearance.tileShape)
            drawTileContent(canvas, drawableRect, slot, zonedDateTime)
        }
    }

    private fun patternColor(pattern: String): Int = when (pattern) {
        "andalusian", "moroccan" -> Color.rgb(24, 49, 59)
        "damascene", "arabesque" -> Color.rgb(23, 32, 48)
        "egyptian", "african" -> Color.rgb(44, 29, 20)
        "ottoman", "persian" -> Color.rgb(27, 23, 48)
        "central-asian", "indonesian", "indian" -> Color.rgb(16, 42, 39)
        "geometric", "global" -> Color.rgb(11, 24, 33)
        "none" -> Color.rgb(16, 18, 22)
        else -> Color.rgb(16, 27, 43)
    }

    private fun paletteColor(colorHex: String, palette: String): Int = when (palette) {
        "monochrome" -> Color.rgb(52, 66, 83)
        "night" -> Color.rgb(23, 38, 58)
        "warm" -> Color.rgb(140, 89, 29)
        else -> runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.rgb(30, 41, 59))
    }

    private fun drawTileBackground(canvas: Canvas, rect: RectF, shape: String) {
        when (shape) {
            "circle" -> canvas.drawOval(rect, bgPaint)
            "oval" -> canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, bgPaint)
            "square-gapped" -> canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
            else -> canvas.drawRect(rect, bgPaint)
        }
    }

    private fun drawRowTiles(
        canvas: Canvas,
        slots: List<SlotItem>,
        rowY: Float,
        rowHeight: Float,
        screenWidth: Float,
        zonedDateTime: ZonedDateTime
    ) {
        var currentX = 0f

        slots.forEach { slot ->
            val tileWidth = screenWidth * slot.weight
            val tileRect = RectF(currentX, rowY, currentX + tileWidth, rowY + rowHeight)

            clickableTiles.add(ClickableTile(tileRect, slot.id))

            bgPaint.color = runCatching { Color.parseColor(slot.colorHex) }.getOrDefault(Color.parseColor("#1E293B"))
            canvas.drawRect(tileRect, bgPaint)

            drawTileContent(canvas, tileRect, slot, zonedDateTime)

            currentX += tileWidth
        }
    }

    private fun drawTileContent(
        canvas: Canvas,
        rect: RectF,
        slot: SlotItem,
        zonedDateTime: ZonedDateTime
    ) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val isNarrow = rect.width() < 120f
        val isLarge = rect.width() > 210f

        val actionDef = TileActionCatalog.getDef(slot.id)

        when (slot.id) {
            "clock_big" -> {
                val timeStr = zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
                val secStr = zonedDateTime.format(DateTimeFormatter.ofPattern("ss", Locale.US))
                textPaint.textSize = if (isLarge) 46f else 32f
                textPaint.color = Color.WHITE
                canvas.drawText(timeStr, centerX, centerY + (textPaint.textSize / 3f) - 4f, textPaint)
                
                subTextPaint.textSize = 14f
                subTextPaint.color = Color.parseColor("#FDE047")
                canvas.drawText(":$secStr", centerX, centerY + 24f, subTextPaint)
            }
            "date_big" -> {
                val dayName = when (zonedDateTime.dayOfWeek) {
                    java.time.DayOfWeek.FRIDAY -> "الجمعة"
                    java.time.DayOfWeek.SATURDAY -> "السبت"
                    java.time.DayOfWeek.SUNDAY -> "الأحد"
                    java.time.DayOfWeek.MONDAY -> "الاثنين"
                    java.time.DayOfWeek.TUESDAY -> "الثلاثاء"
                    java.time.DayOfWeek.WEDNESDAY -> "الأربعاء"
                    java.time.DayOfWeek.THURSDAY -> "الخميس"
                    null -> "الأحد"
                }
                textPaint.textSize = if (isLarge) 24f else 18f
                textPaint.color = Color.WHITE
                canvas.drawText("$dayName ${zonedDateTime.dayOfMonth}", centerX, centerY - 2f, textPaint)

                subTextPaint.textSize = if (isLarge) 16f else 12f
                subTextPaint.color = Color.parseColor("#FEF08A")
                canvas.drawText(HijriDate.shortArabic(), centerX, centerY + 20f, subTextPaint)
            }
            "prayer_countdown" -> {
                val nextPrayer = lastPrayerResult?.nextPrayer
                val countdown = lastPrayerResult?.timeUntilNext ?: "باقٍ 1 س"
                textPaint.textSize = if (isNarrow) 17f else 21f
                textPaint.color = Color.WHITE
                canvas.drawText(nextPrayer?.nameAr ?: "الصلاة", centerX, centerY - 4f, textPaint)

                subTextPaint.textSize = if (isNarrow) 12f else 15f
                subTextPaint.color = Color.WHITE
                canvas.drawText(countdown, centerX, centerY + 20f, subTextPaint)
            }
            "quran_resume" -> {
                textPaint.textSize = if (isLarge) 26f else 18f
                textPaint.color = Color.WHITE
                canvas.drawText("📜 المصحف", centerX, centerY - 4f, textPaint)
                subTextPaint.textSize = 13f
                subTextPaint.color = Color.parseColor("#BAE6FD")
                canvas.drawText("موضع القراءة", centerX, centerY + 20f, subTextPaint)
            }
            else -> {
                val icon = when (slot.id) {
                    "prayer" -> "🕌"
                    "tasbih" -> "📿"
                    "qibla" -> "🕋"
                    "bookmarks" -> "🔖"
                    "locations" -> "📍"
                    "settings" -> "⚙️"
                    "battery" -> "🔋"
                    "weather" -> "⛅"
                    "folder_islamic", "folder_tools", "folder_custom" -> "📁"
                    else -> "📖"
                }
                val title = actionDef.title.split(" ").firstOrNull()?.replace("📁", "") ?: "تطبيق"
                
                textPaint.textSize = if (isNarrow) 22f else 28f
                textPaint.color = Color.WHITE
                canvas.drawText(icon, centerX, centerY - 6f, textPaint)

                subTextPaint.textSize = if (isNarrow) 11.5f else 15f
                subTextPaint.color = Color.WHITE
                canvas.drawText(title, centerX, centerY + 24f, subTextPaint)
            }
        }
    }

    private fun drawAmbientMode(
        canvas: Canvas,
        width: Float,
        height: Float,
        zonedDateTime: ZonedDateTime
    ) {
        val centerX = width / 2f
        val centerY = height / 2f

        val timeStr = zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        textPaint.textSize = 58f
        textPaint.color = Color.WHITE
        canvas.drawText(timeStr, centerX, centerY - 10f, textPaint)

        subTextPaint.textSize = 17f
        subTextPaint.color = Color.parseColor("#94A3B8")
        canvas.drawText(HijriDate.arabic(), centerX, centerY + 30f, subTextPaint)

        val next = lastPrayerResult?.nextPrayer
        if (next != null) {
            subTextPaint.textSize = 15f
            subTextPaint.color = Color.parseColor("#10B981")
            canvas.drawText("${next.nameAr}: ${lastPrayerResult?.timeUntilNext ?: ""}", centerX, centerY + 65f, subTextPaint)
        }
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {}

    override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        if (tapType != TapType.UP) return

        val touchX = tapEvent.xPos.toFloat()
        val touchY = tapEvent.yPos.toFloat()

        val clicked = clickableTiles.firstOrNull { it.rect.contains(touchX, touchY) }
        val actionId = clicked?.actionId ?: "quran"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_ACTION_ID", actionId)
        }
        context.startActivity(intent)
    }
}
