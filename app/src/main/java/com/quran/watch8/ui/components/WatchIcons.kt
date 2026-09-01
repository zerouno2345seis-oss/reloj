package com.quran.watch8.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-definition, clean, unified Vector Icons designed specifically for Samsung Galaxy Watch.
 * Replaces generic Emojis with crisp, modern Vector Art.
 */
object WatchIcons {

    /**
     * 1. Holy Quran Open Book Vector Icon
     */
    @Composable
    fun QuranBook(
        modifier: Modifier = Modifier.size(28.dp),
        color: Color = Color.White,
        accentColor: Color = Color(0xFFFDE047)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Stand (Rehal) Base
            val standPath = Path().apply {
                moveTo(w * 0.20f, h * 0.88f)
                lineTo(w * 0.50f, h * 0.65f)
                lineTo(w * 0.80f, h * 0.88f)
                lineTo(w * 0.74f, h * 0.94f)
                lineTo(w * 0.50f, h * 0.75f)
                lineTo(w * 0.26f, h * 0.94f)
                close()
            }
            drawPath(standPath, color = color.copy(alpha = 0.55f), style = Fill)

            // Left Page
            val leftPage = Path().apply {
                moveTo(w * 0.50f, h * 0.62f)
                cubicTo(w * 0.38f, h * 0.66f, w * 0.24f, h * 0.63f, w * 0.12f, h * 0.55f)
                lineTo(w * 0.12f, h * 0.18f)
                cubicTo(w * 0.24f, h * 0.26f, w * 0.38f, h * 0.29f, w * 0.50f, h * 0.25f)
                close()
            }
            drawPath(leftPage, color = color, style = Fill)

            // Right Page
            val rightPage = Path().apply {
                moveTo(w * 0.50f, h * 0.62f)
                cubicTo(w * 0.62f, h * 0.66f, w * 0.76f, h * 0.63f, w * 0.88f, h * 0.55f)
                lineTo(w * 0.88f, h * 0.18f)
                cubicTo(w * 0.76f, h * 0.26f, w * 0.62f, h * 0.29f, w * 0.50f, h * 0.25f)
                close()
            }
            drawPath(rightPage, color = color, style = Fill)

            // Page Spine & Ribbons
            drawLine(
                color = accentColor,
                start = Offset(w * 0.50f, h * 0.24f),
                end = Offset(w * 0.50f, h * 0.70f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )

            // Page Lines / Script indications
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                start = Offset(w * 0.22f, h * 0.32f),
                end = Offset(w * 0.42f, h * 0.35f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                start = Offset(w * 0.20f, h * 0.42f),
                end = Offset(w * 0.42f, h * 0.45f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                start = Offset(w * 0.58f, h * 0.35f),
                end = Offset(w * 0.78f, h * 0.32f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                start = Offset(w * 0.58f, h * 0.45f),
                end = Offset(w * 0.80f, h * 0.42f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 2. Prayer Times (Mosque Dome / Minaret + Clock Indicator)
     */
    @Composable
    fun PrayerMosque(
        modifier: Modifier = Modifier.size(26.dp),
        color: Color = Color.White,
        accentColor: Color = Color(0xFFFDE047)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Minaret / Dome Left
            val domePath = Path().apply {
                moveTo(w * 0.12f, h * 0.85f)
                lineTo(w * 0.12f, h * 0.48f)
                cubicTo(w * 0.12f, h * 0.28f, w * 0.28f, h * 0.20f, w * 0.34f, h * 0.08f)
                cubicTo(w * 0.40f, h * 0.20f, w * 0.56f, h * 0.28f, w * 0.56f, h * 0.48f)
                lineTo(w * 0.56f, h * 0.85f)
                close()
            }
            drawPath(domePath, color = color, style = Fill)

            // Crescent finial on dome top
            drawCircle(
                color = accentColor,
                radius = w * 0.05f,
                center = Offset(w * 0.34f, h * 0.07f)
            )

            // Arch Doorway
            val archPath = Path().apply {
                moveTo(w * 0.24f, h * 0.85f)
                lineTo(w * 0.24f, h * 0.62f)
                cubicTo(w * 0.24f, h * 0.52f, w * 0.44f, h * 0.52f, w * 0.44f, h * 0.62f)
                lineTo(w * 0.44f, h * 0.85f)
                close()
            }
            drawPath(archPath, color = Color(0xFF0F172A).copy(alpha = 0.7f), style = Fill)

            // Clock / Time Disc on Right
            val clockCenter = Offset(w * 0.75f, h * 0.55f)
            val clockRadius = w * 0.22f

            drawCircle(
                color = color,
                radius = clockRadius,
                center = clockCenter,
                style = Fill
            )
            drawCircle(
                color = Color(0xFF0F172A),
                radius = clockRadius * 0.82f,
                center = clockCenter,
                style = Fill
            )

            // Clock Hands pointing to prayer time
            drawLine(
                color = accentColor,
                start = clockCenter,
                end = Offset(clockCenter.x, clockCenter.y - clockRadius * 0.55f),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accentColor,
                start = clockCenter,
                end = Offset(clockCenter.x + clockRadius * 0.45f, clockCenter.y),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 3. Audio / Recording Studio Microphone
     */
    @Composable
    fun MicRecording(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Mic Capsule (Pill)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.33f, h * 0.10f),
                size = Size(w * 0.34f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.17f, w * 0.17f)
            )

            // Arc Cradle
            val cradlePath = Path().apply {
                moveTo(w * 0.20f, h * 0.38f)
                lineTo(w * 0.20f, h * 0.48f)
                cubicTo(w * 0.20f, h * 0.74f, w * 0.80f, h * 0.74f, w * 0.80f, h * 0.48f)
                lineTo(w * 0.80f, h * 0.38f)
            }
            drawPath(
                path = cradlePath,
                color = color,
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Stand Stem
            drawLine(
                color = color,
                start = Offset(w * 0.50f, h * 0.72f),
                end = Offset(w * 0.50f, h * 0.88f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )

            // Stand Base
            drawLine(
                color = color,
                start = Offset(w * 0.28f, h * 0.88f),
                end = Offset(w * 0.72f, h * 0.88f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 4. Bookmark Ribbon
     */
    @Composable
    fun Bookmark(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White,
        starColor: Color = Color(0xFFFDE047)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            val ribbon = Path().apply {
                moveTo(w * 0.22f, h * 0.08f)
                lineTo(w * 0.78f, h * 0.08f)
                lineTo(w * 0.78f, h * 0.90f)
                lineTo(w * 0.50f, h * 0.70f)
                lineTo(w * 0.22f, h * 0.90f)
                close()
            }
            drawPath(ribbon, color = color, style = Fill)

            // Star emblem inside
            drawCircle(
                color = starColor,
                radius = w * 0.12f,
                center = Offset(w * 0.50f, h * 0.38f)
            )
        }
    }

    /**
     * 5. Modern Settings Gear
     */
    @Composable
    fun SettingsGear(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.50f, h * 0.50f)
            val outerR = w * 0.40f
            val innerR = w * 0.26f

            // Outer Ring with 6 Teeth
            for (i in 0 until 6) {
                val angle = (i * 60) * (Math.PI / 180.0)
                val tx = center.x + (outerR * 0.95f * Math.cos(angle)).toFloat()
                val ty = center.y + (outerR * 0.95f * Math.sin(angle)).toFloat()
                drawCircle(
                    color = color,
                    radius = w * 0.10f,
                    center = Offset(tx, ty)
                )
            }

            drawCircle(
                color = color,
                radius = innerR * 1.3f,
                center = center,
                style = Fill
            )

            // Hole in center
            drawCircle(
                color = Color(0xFF0F172A),
                radius = innerR * 0.55f,
                center = center,
                style = Fill
            )
        }
    }

    /**
     * 6. Location GPS Pin
     */
    @Composable
    fun LocationPin(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White,
        dotColor: Color = Color(0xFFFDE047)
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            val pinPath = Path().apply {
                moveTo(w * 0.50f, h * 0.92f)
                cubicTo(w * 0.35f, h * 0.70f, w * 0.16f, h * 0.52f, w * 0.16f, h * 0.36f)
                cubicTo(w * 0.16f, h * 0.16f, w * 0.31f, h * 0.06f, w * 0.50f, h * 0.06f)
                cubicTo(w * 0.69f, h * 0.06f, w * 0.84f, h * 0.16f, w * 0.84f, h * 0.36f)
                cubicTo(w * 0.84f, h * 0.52f, w * 0.65f, h * 0.70f, w * 0.50f, h * 0.92f)
                close()
            }
            drawPath(pinPath, color = color, style = Fill)

            // Inner dot
            drawCircle(
                color = dotColor,
                radius = w * 0.14f,
                center = Offset(w * 0.50f, h * 0.36f)
            )
        }
    }

    /**
     * 7. Watch Battery Indicator
     */
    @Composable
    fun Battery(
        percentage: Int,
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Battery Outline
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.10f, h * 0.28f),
                size = Size(w * 0.72f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                style = Stroke(width = w * 0.06f)
            )

            // Battery Cap
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.84f, h * 0.40f),
                size = Size(w * 0.07f, h * 0.20f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )

            // Fill Level
            val fillW = (w * 0.58f) * (percentage.coerceIn(0, 100) / 100f)
            val fillColor = if (percentage > 20) Color(0xFF10B981) else Color(0xFFEF4444)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(w * 0.17f, h * 0.35f),
                size = Size(fillW, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
    }

    /**
     * 8. Weather Sun & Cloud
     */
    @Composable
    fun Weather(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Sun behind
            drawCircle(
                color = Color(0xFFFBBF24),
                radius = w * 0.18f,
                center = Offset(w * 0.65f, h * 0.32f)
            )

            // Cloud front
            val cloud = Path().apply {
                moveTo(w * 0.20f, h * 0.75f)
                lineTo(w * 0.75f, h * 0.75f)
                cubicTo(w * 0.88f, h * 0.75f, w * 0.88f, h * 0.55f, w * 0.76f, h * 0.52f)
                cubicTo(w * 0.76f, h * 0.36f, w * 0.55f, h * 0.34f, w * 0.46f, h * 0.44f)
                cubicTo(w * 0.38f, h * 0.42f, w * 0.20f, h * 0.48f, w * 0.20f, h * 0.62f)
                close()
            }
            drawPath(cloud, color = color, style = Fill)
        }
    }

    /**
     * 9. Calendar / Date Icon
     */
    @Composable
    fun CalendarDate(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Body
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.20f),
                size = Size(w * 0.70f, h * 0.70f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Fill
            )

            // Header Banner
            drawRoundRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(w * 0.15f, h * 0.20f),
                size = Size(w * 0.70f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Fill
            )

            // Binder Rings
            drawLine(
                color = Color(0xFF0F172A),
                start = Offset(w * 0.32f, h * 0.12f),
                end = Offset(w * 0.32f, h * 0.26f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0F172A),
                start = Offset(w * 0.68f, h * 0.12f),
                end = Offset(w * 0.68f, h * 0.26f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )

            // Date grid lines
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.7f),
                start = Offset(w * 0.30f, h * 0.58f),
                end = Offset(w * 0.70f, h * 0.58f),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0F172A).copy(alpha = 0.7f),
                start = Offset(w * 0.30f, h * 0.72f),
                end = Offset(w * 0.55f, h * 0.72f),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 10. Navigation Direction Arrow Icon
     */
    @Composable
    fun NavigationArrow(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.82f)
                lineTo(w * 0.50f, h * 0.65f)
                lineTo(w * 0.12f, h * 0.82f)
                close()
            }
            drawPath(path, color = color, style = Fill)
        }
    }

    /**
     * 11. Edit Pencil Icon
     */
    @Composable
    fun EditPencil(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.75f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.25f)
                lineTo(w * 0.35f, h * 0.75f)
                lineTo(w * 0.15f, h * 0.85f)
                lineTo(w * 0.25f, h * 0.65f)
                close()
            }
            drawPath(path, color = color, style = Fill)
            drawLine(
                color = Color(0xFF0F172A),
                start = Offset(w * 0.65f, h * 0.25f),
                end = Offset(w * 0.75f, h * 0.35f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * 12. Delete Trash Can Icon
     */
    @Composable
    fun DeleteTrash(
        modifier: Modifier = Modifier.size(24.dp),
        color: Color = Color.White
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Lid
            drawLine(
                color = color,
                start = Offset(w * 0.18f, h * 0.28f),
                end = Offset(w * 0.82f, h * 0.28f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
            // Handle
            drawLine(
                color = color,
                start = Offset(w * 0.40f, h * 0.18f),
                end = Offset(w * 0.60f, h * 0.18f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
            // Bin Body
            val bodyPath = Path().apply {
                moveTo(w * 0.26f, h * 0.32f)
                lineTo(w * 0.32f, h * 0.86f)
                lineTo(w * 0.68f, h * 0.86f)
                lineTo(w * 0.74f, h * 0.32f)
                close()
            }
            drawPath(bodyPath, color = color, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Inner Ribs
            drawLine(
                color = color,
                start = Offset(w * 0.42f, h * 0.44f),
                end = Offset(w * 0.42f, h * 0.74f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.58f, h * 0.44f),
                end = Offset(w * 0.58f, h * 0.74f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )
        }
    }
}
