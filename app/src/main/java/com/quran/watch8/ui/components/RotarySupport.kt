package com.quran.watch8.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.launch

/**
 * Ultra-Responsive Rotary Bezel Support for Samsung Galaxy Watch (Wear OS 5 / 6)
 *
 * Provides immediate physical bezel feedback with zero latency, optimized
 * specifically for prayer / continuous Quran reading.
 */
const val BEZEL_SENSITIVITY_DEFAULT = 3.5f
const val BEZEL_SENSITIVITY_PRAYER_READING = 4.5f

@Composable
fun rememberRotaryScrollModifier(
    listState: ScalingLazyListState,
    enabled: Boolean = true,
    sensitivity: Float = BEZEL_SENSITIVITY_DEFAULT
): Modifier {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    return remember(listState, enabled, sensitivity) {
        if (!enabled) Modifier
        else {
            Modifier.onRotaryScrollEvent { event ->
                val delta = event.verticalScrollPixels * sensitivity
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scope.launch {
                    listState.scrollBy(delta)
                }
                true
            }
        }
    }
}

@Composable
fun rememberUltraRotaryScrollModifier(
    listState: ScalingLazyListState,
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    sensitivity: Float = BEZEL_SENSITIVITY_PRAYER_READING
): Modifier {
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    return remember(listState, enabled, sensitivity) {
        if (!enabled) Modifier
        else {
            Modifier
                .focusRequester(focusRequester)
                .onRotaryScrollEvent { event ->
                    val delta = event.verticalScrollPixels * sensitivity
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    scope.launch {
                        listState.scrollBy(delta)
                    }
                    true
                }
        }
    }
}
