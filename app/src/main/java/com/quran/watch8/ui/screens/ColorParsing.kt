package com.quran.watch8.ui.screens

import androidx.compose.ui.graphics.Color

/** "#RRGGBB" or "#AARRGGBB" from settings; anything else falls back. */
fun parseHexColor(hexString: String?, fallback: Color): Color {
    if (hexString.isNullOrBlank() || !hexString.startsWith("#")) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hexString))
    } catch (e: Exception) {
        fallback
    }
}
