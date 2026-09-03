package com.quran.watch8.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Shared safe bounds for the 438 px circular watch display. */
object WatchSafeInsets {
    const val contentWidthFraction = 0.76f
    val listContentPadding = PaddingValues(
        top = 26.dp,
        bottom = 32.dp,
        start = 12.dp,
        end = 12.dp,
    )
}
