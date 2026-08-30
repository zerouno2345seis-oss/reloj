package com.quran.watch8.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.launch

/**
 * Full support for the physical rotating bezel (الإطار الدوار)
 * on Galaxy Watch Classic models including Galaxy Watch 8 Classic.
 *
 * The bezel generates RotaryScrollEvent which we convert into list scrolling.
 * Focus must be requested so the system delivers bezel events to our composable.
 */

/**
 * Recommended way: returns a Modifier that makes any ScalingLazyColumn
 * fully controllable by the rotating bezel.
 *
 * Example:
 * ```
 * val listState = rememberScalingLazyListState()
 * val rotaryMod = rememberRotaryScrollModifier(listState)
 * ScalingLazyColumn(
 *     state = listState,
 *     modifier = Modifier.fillMaxSize().then(rotaryMod)
 * ) { ... }
 * ```
 */
@Composable
fun rememberRotaryScrollModifier(
    listState: ScalingLazyListState,
    enabled: Boolean = true
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Immediately claim focus so the physical bezel works as soon as the screen appears
    LaunchedEffect(Unit) {
        if (enabled) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    return Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            if (!enabled) return@onRotaryScrollEvent false
            // verticalScrollPixels > 0 usually means clockwise / "down" on most watches
            // We invert so that rotating clockwise scrolls the list down (natural feel)
            val delta = -event.verticalScrollPixels
            scope.launch {
                try {
                    // ScalingLazyListState implements ScrollableState → scrollBy works
                    listState.scrollBy(delta)
                } catch (_: Exception) {
                    // Fallback: animate by a fixed step if pixel scroll fails
                    val indexDelta = if (delta > 0) 1 else -1
                    val target = (listState.centerItemIndex + indexDelta)
                        .coerceIn(0, Int.MAX_VALUE)
                    listState.animateScrollToItem(target)
                }
            }
            true // consume so the system does not double-handle
        }
}

/**
 * Rotary support for any ScrollableState (Column + verticalScroll, etc.)
 */
@Composable
fun rememberRotaryScrollableModifier(
    scrollState: ScrollableState,
    enabled: Boolean = true
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (enabled) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    return Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            if (!enabled) return@onRotaryScrollEvent false
            scope.launch {
                scrollState.scrollBy(-event.verticalScrollPixels)
            }
            true
        }
}

/**
 * Special mode for Quran Reader: when font-adjust mode is active,
 * rotating the bezel changes font size instead of scrolling.
 */
@Composable
fun rememberRotaryFontSizeModifier(
    currentSize: Float,
    onSizeChange: (Float) -> Unit,
    enabled: Boolean = false,
    step: Float = 1.5f
): Modifier {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(enabled) {
        if (enabled) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    return Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            if (!enabled) return@onRotaryScrollEvent false
            val delta = if (event.verticalScrollPixels > 0) -step else step
            onSizeChange((currentSize + delta).coerceIn(12f, 34f))
            true
        }
}
