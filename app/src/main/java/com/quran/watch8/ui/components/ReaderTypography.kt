package com.quran.watch8.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.quran.watch8.ui.screens.parseHexColor
import com.quran.watch8.ui.theme.AyahGreen
import com.quran.watch8.ui.theme.AyahYellow

/**
 * How a verse is drawn.
 *
 * The reader and the settings screen both read the same DataStore values, but
 * each used to translate them into a font and a colour with its own `when`
 * block -- so a sample rendered in settings could not be trusted to match the
 * page. One translation, used by both.
 */
object ReaderTypography {

    const val MIN_FONT_SIZE = 8f
    const val MAX_FONT_SIZE = 48f

    /** A short, familiar ayah: enough text to judge a size and a face by. */
    const val SAMPLE_AYAH = "﴿ وَنُنَزِّلُ مِنَ ٱلْقُرْءَانِ مَا هُوَ شِفَآءٌ وَرَحْمَةٌ ﴾"

    fun coerceFontSize(value: Float): Float = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

    fun fontFamily(id: String): FontFamily = when (id) {
        "sansserif" -> FontFamily.SansSerif
        "serif"     -> FontFamily.Serif
        "kufi"      -> FontFamily.Cursive
        "uthmani"   -> FontFamily.Serif
        "amiri", "naskh" -> FontFamily.Serif
        "tajawal", "cairo" -> FontFamily.SansSerif
        else        -> FontFamily.Default
    }

    fun backgroundColor(id: String, customHex: String): Color = when (id) {
        "navy"   -> Color(0xFF070F1E)
        "sepia"  -> Color(0xFF1B140B)
        "forest" -> Color(0xFF05170F)
        "slate"  -> Color(0xFF263341)
        "custom" -> parseHexColor(customHex, Color.Black)
        else     -> Color(0xFF000000)
    }

    fun textColor(id: String, customHex: String): Color = when (id) {
        "ivory"  -> Color(0xFFF6EADB)
        "mint"   -> Color(0xFFA7F3D0)
        "golden" -> Color(0xFFFEF08A)
        "cyan"   -> Color(0xFF9EE7FF)
        "custom" -> parseHexColor(customHex, Color.White)
        else     -> Color(0xFFFFFFFF)
    }

    fun ayahNumberColor(id: String, customHex: String): Color = when (id) {
        "green"  -> AyahGreen
        "cyan"   -> Color(0xFF5AC8FA)
        "rose"   -> Color(0xFFFF6B9A)
        "custom" -> parseHexColor(customHex, AyahYellow)
        else     -> AyahYellow
    }
}
