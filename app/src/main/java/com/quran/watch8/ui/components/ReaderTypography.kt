package com.quran.watch8.ui.components

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.quran.watch8.R
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
    /**
     * A short, familiar verse (al-Isra 82) to judge a size and a face by. No
     * ornate brackets: the Madinah face drew them as broken marks; settings
     * closes it with [ayahEndMark] as the reader does.
     */
    const val SAMPLE_AYAH = "وَنُنَزِّلُ مِنَ ٱلْقُرْءَانِ مَا هُوَ شِفَآءٌ وَرَحْمَةٌ"

    fun coerceFontSize(value: Float): Float = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

    /**
     * Amiri Quran, bundled. The text carries Uthmani marks the watch's system
     * fonts cannot draw -- the small high sukun (U+06E1, ~37k times) and the
     * open tanween (U+08F0..U+08F2) -- so with a system font those letters came
     * out as empty boxes. Every Quranic choice maps here; only the explicit
     * "sansserif"/"serif" choices still use the system fonts.
     */
    val AmiriFont: FontFamily = FontFamily(Font(R.font.amiri_quran))

    /**
     * The Madinah mushaf face (KFGQPC Uthmanic Script HAFS), the one the
     * bundled text is encoded for. It ships unmodified in assets/fonts with
     * its licence, which forbids altering the font, so it is loaded as is;
     * without it every Quranic choice falls back to Amiri.
     */
    @Volatile private var madinah: Typeface? = null

    /** Loads the Madinah face once, at start-up. */
    fun load(context: Context) {
        if (madinah != null) return
        madinah = try {
            Typeface.createFromAsset(context.assets, MADINAH_ASSET)
        } catch (_: Exception) {
            null
        }
    }

    private const val MADINAH_ASSET = "fonts/kfgqpc_hafs.ttf"

    /** The default reading face: Madinah when bundled, otherwise Amiri. */
    val QuranFont: FontFamily
        get() = madinah?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) } ?: AmiriFont

    /**
     * Amiri's marks sit high above and below the line. 1.85 left visible gaps
     * between lines on the watch; 1.65 still keeps the marks of neighbouring
     * lines apart.
     */
    const val LINE_HEIGHT_FACTOR = 1.4f

    fun arabicDigits(n: Int): String = n.toString().map { '\u0660' + (it - '0') }.joinToString("")

    /** End-of-ayah sign with the number in Arabic-Indic digits, e.g. ۝١٢. */
    fun ayahEndMark(verse: Int): String = "\u06DD" + arabicDigits(verse)

    /** A reading font the settings offer: its id, its name, and which text it reads. */
    class ReadingFont(val id: String, val nameAr: String, val simpleText: Boolean)

    /**
     * The five reading fonts, Madinah first (the default). The first two draw
     * the Uthmani text, whose letter forms cannot be stretched; the other
     * three draw the simple text with full vowels and stretch with kashida,
     * so their lines fill to the edge.
     */
    val READING_FONTS = listOf(
        ReadingFont("default", "المدينة", simpleText = false),
        ReadingFont("amiri", "أميري قرآني", simpleText = false),
        ReadingFont("amiri_plain", "أميري", simpleText = true),
        ReadingFont("naskh", "نسخ", simpleText = true),
        ReadingFont("scheherazade", "شهرزاد", simpleText = true),
    )

    /** True when [id] reads the simple text and may stretch with kashida. */
    fun usesSimpleText(id: String): Boolean = READING_FONTS.firstOrNull { it.id == id }?.simpleText == true

    /** Al-Isra 82 in the simple text, for previewing the simple-text fonts. */
    const val SAMPLE_AYAH_SIMPLE = "وَنُنَزِّلُ مِنَ الْقُرْآنِ مَا هُوَ شِفَاءٌ وَرَحْمَةٌ"

    fun sampleAyah(id: String): String = if (usesSimpleText(id)) SAMPLE_AYAH_SIMPLE else SAMPLE_AYAH

    private val AmiriPlainFont = FontFamily(Font(R.font.amiri_regular))
    private val NaskhFont = FontFamily(Font(R.font.noto_naskh_arabic))
    private val ScheherazadeFont = FontFamily(Font(R.font.scheherazade_new))

    fun fontFamily(id: String): FontFamily = when (id) {
        "sansserif", "tajawal", "cairo" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "amiri" -> AmiriFont
        "amiri_plain" -> AmiriPlainFont
        "naskh" -> NaskhFont
        "scheherazade" -> ScheherazadeFont
        else        -> QuranFont
    }

    /** The same choice as [fontFamily], for text drawn by the platform. */
    fun typeface(context: Context, id: String): Typeface = when (id) {
        "sansserif", "tajawal", "cairo" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        "amiri" -> quranTypeface(context)
        "amiri_plain" -> context.resources.getFont(R.font.amiri_regular)
        "naskh" -> context.resources.getFont(R.font.noto_naskh_arabic)
        "scheherazade" -> context.resources.getFont(R.font.scheherazade_new)
        else -> madinah ?: quranTypeface(context)
    }

    /** Amiri Quran: also draws the ۝ verse-number ornament on every face. */
    fun quranTypeface(context: Context): Typeface = context.resources.getFont(R.font.amiri_quran)

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
