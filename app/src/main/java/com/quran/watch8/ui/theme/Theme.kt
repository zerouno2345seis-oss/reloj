package com.quran.watch8.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Dark theme optimized for watch AMOLED: pure black bg, white text, yellow/green ayah numbers
val QuranBlack = Color(0xFF000000)
val QuranWhite = Color(0xFFFFFFFF)
val AyahYellow = Color(0xFFFFD700) // Gold yellow
val AyahGreen = Color(0xFF4CAF50)  // Soft green
val AccentGold = Color(0xFFC9A227)
val CardDark = Color(0xFF121212)
val SubtleGray = Color(0xFFB0B0B0)

private val DarkColorPalette = Colors(
    primary = AccentGold,
    primaryVariant = Color(0xFF8B7500),
    secondary = AyahYellow,
    secondaryVariant = AyahGreen,
    background = QuranBlack,
    surface = CardDark,
    error = Color(0xFFCF6679),
    onPrimary = QuranBlack,
    onSecondary = QuranBlack,
    onBackground = QuranWhite,
    onSurface = QuranWhite,
    onError = QuranBlack
)

@Composable
fun QuranWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColorPalette,
        content = content
    )
}
