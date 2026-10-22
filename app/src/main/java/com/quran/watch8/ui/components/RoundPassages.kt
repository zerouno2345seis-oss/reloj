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
 * whole units as fit at the reader's font size, so it starts where a
 * reciter would start whenever it can. When the next unit does not fit whole, the screen is
 * filled with as many of its words as fit, ending in "…", and the next screen
 * goes on from there, so no screen is left half empty.
 *
 * Each line is as wide as the circle at its height (StaticLayout per-line
 * indents), and the block is centred where the circle is widest.
 *
 * Reading glides rather than flips: each screen opens with about the last
 * quarter of the one before, dimmed, so the eye picks up where it left off.
 * The carried text is whole pause units where they fit, otherwise the last
 * words of the unit that ended the screen.
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
        /** Pieces before this index are carried over from the screen before. */
        private val firstNew: Int,
    ) {
        /** The verse this screen stands for: the first new one on it. */
        val firstAyah: Int get() =
            pieceAyahs.drop(firstNew).firstOrNull { it >= 0 } ?: pieceAyahs.firstOrNull { it >= 0 } ?: 0
        fun ayahAt(offset: Int): Int {
            var found = -1
            for (i in pieceStarts.indices) if (pieceStarts[i] <= offset) found = i
            return if (found < 0) firstAyah else pieceAyahs[found]
        }
        fun holdsAyah(index: Int): Boolean = pieceAyahs.drop(firstNew).contains(index)
    }

    private fun compose(pieces: List<Piece>, carried: Int = 0): Triple<CharSequence, IntArray, IntArray> {
        val sb = SpannableStringBuilder()
        var carriedEnd = 0
        val starts = IntArray(pieces.size)
        val ayahs = IntArray(pieces.size)
        pieces.forEachIndexed { i, p ->
            if (i > 0) sb.append(if (pieces[i - 1].unit.ayahIndex < 0) '\n' else ' ')
            starts[i] = sb.length
            ayahs[i] = p.unit.ayahIndex
            sb.append(p.unit.words.subList(p.from, p.to).joinToString(" "))
            val next = pieces.getOrNull(i + 1)
            val resumedNext = next != null && next.unit === p.unit && next.from == p.to
            if (resumedNext) {
                // The same stretch goes on right after: no "…" between.
            } else if (p.continues) {
                sb.append(" …")
            } else if (p.unit.endsAyah) {
                // No-break space: the number stays on its verse's last line.
                sb.append(' ')
                val markStart = sb.length
                sb.append(ReaderTypography.ayahEndMark(p.unit.verse))
                sb.setSpan(ForegroundColorSpan(numberColor), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(TypefaceSpan(numberTypeface), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (i == carried - 1) carriedEnd = sb.length
        }
        if (carriedEnd > 0) {
            // Set last, so it also dims the carried verse numbers.
            sb.setSpan(ForegroundColorSpan(dimColor), 0, carriedEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Every line exactly one band tall, so the indents line up with the circle.
        sb.setSpan(LineHeightSpan.Standard(lineHeightPx), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        return Triple(sb, starts, ayahs)
    }

    private val dimColor = (paint.color and 0x00FFFFFF) or (0x70 shl 24)

    private fun screenOf(pieces: List<Piece>, carried: Int, fit: Fit, text: Triple<CharSequence, IntArray, IntArray>) =
        Screen(text.first, fit, text.second, text.third, carried)

    private fun width(p: Piece) = Layout.getDesiredWidth(p.unit.words.subList(p.from, p.to).joinToString(" "), paint)

    /** About a quarter of a screen from the end of [pieces], to open the next one with. */
    private fun carryOf(pieces: List<Piece>): List<Piece> {
        val budget = capacity(maxLines) * CARRY_SHARE
        val out = ArrayDeque<Piece>()
        var used = 0f
        for (p in pieces.asReversed()) {
            val w = width(p)
            if (used + w <= budget) { out.addFirst(p); used += w; continue }
            if (out.isEmpty()) {
                // The last stretch alone is too long: its last words only.
                var from = p.to - 1
                while (from > p.from && used + width(Piece(p.unit, from - 1, p.to)) <= budget) from--
                if (used + width(Piece(p.unit, from, p.to)) <= budget) out.addFirst(Piece(p.unit, from, p.to))
            }
            break
        }
        return out.toList()
    }

    /** The surah's screens, cut one by one so the first can show at once. */
    fun screens(units: List<PauseUnit>): Sequence<Screen> = sequence {
        var i = 0
        var from = 0
        var carry = emptyList<Piece>()
        while (i < units.size) {
            val head = Piece(units[i], from, units[i].words.size)
            // Open with the carried quarter if the next stretch still fits with it.
            var base = carry
            var headText = compose(base + head, base.size)
            var headFit = fit(headText.first)
            if (headFit == null && base.isNotEmpty()) {
                base = emptyList()
                headText = compose(listOf(head))
                headFit = fit(headText.first)
            }
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
                yield(screenOf(best.first, 0, best.second, best.third))
                carry = carryOf(best.first)
                from = best.first.last().to
                if (from >= units[i].words.size) { i++; from = 0 }
                continue
            }
            var pieces = base + head
            var placed: Fit = headFit
            var text = headText
            var j = i + 1
            while (j < units.size) {
                val candidate = pieces + Piece(units[j], 0, units[j].words.size)
                val t = compose(candidate, base.size)
                val f = fit(t.first) ?: break
                pieces = candidate; placed = f; text = t; j++
            }
            // The next stretch does not fit whole: fill the rest of the screen
            // with as many of its words as fit, and go on from there. A screen
            // left half empty to keep a stretch together read as wasted space.
            var resumeAt = 0
            if (j < units.size) {
                // Every try extends the whole units placed above, not the last
                // try: extending `pieces` itself appended the same words twice.
                val whole = pieces
                var lo = 1
                var hi = units[j].words.size - 1
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val candidate = whole + Piece(units[j], 0, mid)
                    val t = compose(candidate, base.size)
                    val f = fit(t.first)
                    if (f != null) { pieces = candidate; placed = f; text = t; resumeAt = mid; lo = mid + 1 } else hi = mid - 1
                }
            }
            yield(screenOf(pieces, base.size, placed, text))
            carry = carryOf(pieces.drop(base.size))
            i = j
            from = resumeAt
        }
    }

    companion object {
        /** Share of a screen carried over to open the next. */
        private const val CARRY_SHARE = 0.25f

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
