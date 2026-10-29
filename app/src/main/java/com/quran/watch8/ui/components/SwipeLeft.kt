package com.quran.watch8.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Runs [onSwipe] when a finger moves clearly to the left across the screen.
 *
 * It only watches: nothing is consumed, so the list under it still scrolls
 * and its chips still take taps. A swipe to the right is the system's back
 * and is left alone.
 */
fun Modifier.onSwipeLeft(onSwipe: () -> Unit): Modifier = pointerInput(Unit) {
    val far = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
            if (!change.pressed) break
        }
        if (dx < -far && abs(dx) > 2f * abs(dy)) onSwipe()
    }
}
