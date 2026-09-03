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

    @Composable
    fun Car(modifier: Modifier = Modifier.size(24.dp), color: Color = Color.White) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val roof = Path().apply {
                moveTo(w * 0.28f, h * 0.48f)
                lineTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.70f, h * 0.28f)
                lineTo(w * 0.82f, h * 0.48f)
                close()
            }
            drawPath(roof, color = color, style = Fill)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, h * 0.46f),
                size = Size(w * 0.76f, h * 0.28f),
                cornerRadius = CornerRadius(w * 0.08f),
            )
            drawCircle(Color(0xFF0F172A), w * 0.09f, Offset(w * 0.28f, h * 0.75f))
            drawCircle(Color(0xFF0F172A), w * 0.09f, Offset(w * 0.72f, h * 0.75f))
        }
    }

    @Composable
    fun Star(modifier: Modifier = Modifier.size(24.dp), color: Color = Color.White) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val star = Path().apply {
                moveTo(w * 0.50f, h * 0.08f)
                lineTo(w * 0.61f, h * 0.38f)
                lineTo(w * 0.92f, h * 0.39f)
                lineTo(w * 0.67f, h * 0.58f)
                lineTo(w * 0.76f, h * 0.90f)
                lineTo(w * 0.50f, h * 0.71f)
                lineTo(w * 0.24f, h * 0.90f)
                lineTo(w * 0.33f, h * 0.58f)
                lineTo(w * 0.08f, h * 0.39f)
                lineTo(w * 0.39f, h * 0.38f)
                close()
            }
            drawPath(star, color = color, style = Fill)
        }
    }
}
