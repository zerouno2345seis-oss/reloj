package com.quran.watch8.ui.components

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.TypefaceSpan
import com.quran.watch8.data.model.Ayah
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The reader's screens: passages cut at the Quran's own pauses, laid out in
 * the shape of a round screen.
 *
 * A surah is split into pause units -- each verse, and within a long verse
 * each stretch ending at a waqf sign (ۖ ۗ ۘ ۙ ۚ ۛ). A screen holds as many
 * whole units as fit at the reader's font size, so it always starts where a
 * reciter would start and nothing from the next screen leaks onto it. Only a
 * unit too long for a whole screen is cut, between words, ending in "…".
 *
 * Each line is as wide as the circle at its height (StaticLayout per-line
 * indents), and the block is centred where the circle is widest. The thin caps
 * above and below the text are left to the caller for the bridge words and the
 * surah and verse.
 *
 * Every screen is laid out from its own text, never as a slice of a longer
 * run, so shaping never sees a cut word.
 */
class RoundPassages(
    private val diameterPx: Int,
    private val lineHeightPx: Int,
    private val marginPx: Int,
    /** Height kept free at the top and at the bottom for the caps. */
    capPx: Int,
    /** Narrowest line worth using; anything narrower is left to the caps. */
    minLineWidthPx: Int,
    private val paint: TextPaint,
    private val numberColor: Int,
    private val numberTypeface: Typeface,
) {
    /** Most lines a screen holds between the caps. */
    private val maxLines: Int
    private val radius = diameterPx / 2f

    init {
        var n = max(1, (diameterPx - 2 * capPx) / lineHeightPx)
        while (n > 1 && lineWidth(n, 0) < minLineWidthPx) n--
        maxLines = n
    }

    private fun top(k: Int): Int = ((diameterPx - k * lineHeightPx) / 2f).toInt()

    private fun inset(k: Int, i: Int): Int {
        val lineTop = top(k) + i * lineHeightPx
        val lineBottom = lineTop + lineHeightPx
        // The narrower of the line's two edges decides its width.
        val d = max(abs(lineTop - radius), abs(lineBottom - radius))
        val chord = if (d >= radius) 0f else 2f * sqrt(radius * radius - d * d)
        return ceil((diameterPx - chord) / 2f).toInt() + marginPx
    }

    private fun lineWidth(k: Int, i: Int): Int = diameterPx - 2 * inset(k, i)

    private fun capacity(k: Int): Float = (0 until k).sumOf { lineWidth(k, it).toDouble() }.toFloat()

    private fun build(text: CharSequence, k: Int): StaticLayout {
        val indents = IntArray(k) { inset(k, it) }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, diameterPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setIncludePad(false)
            .setIndents(indents, indents)
            .build()
    }

    /** A line ending anywhere but a space means a word was split to fit. */
    private fun splitsAWord(layout: StaticLayout, text: CharSequence): Boolean {
        for (i in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(i)
            if (end <= 0 || end > text.length) continue
            val c = text[end - 1]
            if (c != ' ' && c != '\n') return true
        }
        return false
    }

    class Fit(val layout: StaticLayout, val top: Int)

    /** Lays [text] out in the fewest centred lines that hold it whole, or null. */
    fun fit(text: CharSequence): Fit? {
        val total = Layout.getDesiredWidth(text, paint)
        if (total > capacity(maxLines)) return null
        var k = 1
        while (k < maxLines && capacity(k) * 0.9f < total) k++
        while (k <= maxLines) {
            val layout = build(text, k)
            if (layout.lineCount <= k && !splitsAWord(layout, text)) {
                // Fewer lines than planned: move them towards the middle,
                // where every line is at least as wide as it was planned.
                return Fit(layout, top(k) + (k - layout.lineCount) * lineHeightPx / 2)
            }
            k++
        }
        return null
    }

    /** A stretch of one verse ending at a pause; [ayahIndex] -1 is the basmala. */
    class PauseUnit(val ayahIndex: Int, val verse: Int, val words: List<String>, val endsAyah: Boolean)

    /** Part of a unit placed on a screen: words [from, to). */
    private class Piece(val unit: PauseUnit, val from: Int, val to: Int) {
        val continues get() = to < unit.words.size
    }

    class Screen(
        val text: CharSequence,
        val fit: Fit,
        /** Where each verse's text starts on this screen, with its index. */
        val pieceStarts: IntArray,
        val pieceAyahs: IntArray,
        /** The last words, shown faintly above the next screen. */
        val tail: String,
    ) {
        /** The verse this screen stands for: the first one on it. */
        val firstAyah: Int get() = pieceAyahs.firstOrNull { it >= 0 } ?: 0
        fun ayahAt(offset: Int): Int {
            var found = -1
            for (i in pieceStarts.indices) if (pieceStarts[i] <= offset) found = i
            return if (found < 0) firstAyah else pieceAyahs[found]
        }
        fun holdsAyah(index: Int): Boolean = pieceAyahs.contains(index)
    }

    private fun compose(pieces: List<Piece>): Triple<CharSequence, IntArray, IntArray> {
        val sb = SpannableStringBuilder()
        val starts = IntArray(pieces.size)
        val ayahs = IntArray(pieces.size)
        pieces.forEachIndexed { i, p ->
            if (i > 0) sb.append(if (pieces[i - 1].unit.ayahIndex < 0) '\n' else ' ')
            starts[i] = sb.length
            ayahs[i] = p.unit.ayahIndex
            sb.append(p.unit.words.subList(p.from, p.to).joinToString(" "))
            if (p.continues) {
                sb.append(" …")
            } else if (p.unit.endsAyah) {
                // No-break space: the number stays on its verse's last line.
                sb.append(' ')
                val markStart = sb.length
                sb.append(ReaderTypography.ayahEndMark(p.unit.verse))
                sb.setSpan(ForegroundColorSpan(numberColor), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(TypefaceSpan(numberTypeface), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // Every line exactly one band tall, so the indents line up with the circle.
        sb.setSpan(LineHeightSpan.Standard(lineHeightPx), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        return Triple(sb, starts, ayahs)
    }

    private fun screenOf(pieces: List<Piece>, fit: Fit, text: Triple<CharSequence, IntArray, IntArray>): Screen {
        val last = pieces.last()
        val tail = last.unit.words.subList(max(last.from, last.to - 2), last.to).joinToString(" ")
        return Screen(text.first, fit, text.second, text.third, tail)
    }

    /** The surah's screens, cut one by one so the first can show at once. */
    fun screens(units: List<PauseUnit>): Sequence<Screen> = sequence {
        var i = 0
        var from = 0
        while (i < units.size) {
            val head = Piece(units[i], from, units[i].words.size)
            val headText = compose(listOf(head))
            val headFit = fit(headText.first)
            if (headFit == null) {
                // One unit longer than a screen: as many words as fit, then "…".
                var lo = 1
                var hi = units[i].words.size - from - 1
                var best: Triple<List<Piece>, Fit, Triple<CharSequence, IntArray, IntArray>>? = null
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val pieces = listOf(Piece(units[i], from, from + mid))
                    val t = compose(pieces)
                    val f = fit(t.first)
                    if (f != null) { best = Triple(pieces, f, t); lo = mid + 1 } else hi = mid - 1
                }
                if (best == null) {
                    // Not even one word fits a line: show it anyway rather than stall.
                    val pieces = listOf(Piece(units[i], from, from + 1))
                    val t = compose(pieces)
                    best = Triple(pieces, Fit(build(t.first, maxLines), top(maxLines)), t)
                }
                yield(screenOf(best.first, best.second, best.third))
                from = best.first.last().to
                if (from >= units[i].words.size) { i++; from = 0 }
                continue
            }
            var pieces = listOf(head)
            var placed: Fit = headFit
            var text = headText
            var j = i + 1
            while (j < units.size) {
                val candidate = pieces + Piece(units[j], 0, units[j].words.size)
                val t = compose(candidate)
                val f = fit(t.first) ?: break
                pieces = candidate; placed = f; text = t; j++
            }
            yield(screenOf(pieces, placed, text))
            i = j
            from = 0
        }
    }

    companion object {
        private fun isWaqf(c: Char) = c in 'ۖ'..'ۛ'

        /**
         * Splits the surah into pause units: the basmala, then each verse,
         * cut after every word that carries a waqf sign.
         */
        fun pauseUnits(ayahs: List<Ayah>, withBasmala: Boolean): List<PauseUnit> {
            val units = ArrayList<PauseUnit>()
            if (withBasmala) {
                units += PauseUnit(-1, 0, "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ".split(' '), endsAyah = false)
            }
            ayahs.forEachIndexed { index, ayah ->
                val words = ayah.text.replace("‏", "").trim().split(' ').filter { it.isNotEmpty() }
                var start = 0
                for (w in words.indices) {
                    val last = w == words.size - 1
                    if (last || words[w].any(::isWaqf)) {
                        units += PauseUnit(index, ayah.verse, words.subList(start, w + 1), endsAyah = last)
                        start = w + 1
                    }
                }
            }
            return units
        }
    }
}
