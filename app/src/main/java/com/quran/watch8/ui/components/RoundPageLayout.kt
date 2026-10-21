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
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import com.quran.watch8.data.model.Ayah
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lays a surah out as pages shaped to a round screen.
 *
 * A scrolling list cannot fill a circle: a line's width would have to change
 * as it moves up and down the glass, so it has to be as narrow as the circle
 * near the rim and wastes the wide middle, or as wide as the middle and loses
 * its ends at the rim. A page does not move, so each of its lines can be cut
 * to the width of the circle at its own height: long lines across the middle,
 * short ones towards the top and bottom. StaticLayout takes a left and right
 * indent per line, which is exactly that shape.
 *
 * Breaking is greedy (BREAK_STRATEGY_SIMPLE): a line's breaks then depend only
 * on the text before it, so laying out just one page's text gives the same
 * lines it had when the page was cut from the longer run.
 */
class RoundPageLayout(
    private val diameterPx: Int,
    private val lineHeightPx: Int,
    private val marginPx: Int,
    private val minLineWidthPx: Int,
    private val textPaint: TextPaint,
) {
    /** Lines on every page, centred on the middle of the screen. */
    val linesPerPage: Int
    /** Top of the first line, from the top of the screen. */
    val firstLineTop: Int
    private val leftIndents: IntArray
    private val rightIndents: IntArray

    init {
        val r = diameterPx / 2f
        // Most lines whose outermost edge still leaves a usable width.
        var n = max(1, (diameterPx / lineHeightPx))
        while (n > 1 && chordAt(r, r - n * lineHeightPx / 2f) < minLineWidthPx) n--
        linesPerPage = n
        firstLineTop = ((diameterPx - n * lineHeightPx) / 2f).toInt()
        leftIndents = IntArray(n)
        rightIndents = IntArray(n)
        for (i in 0 until n) {
            val top = firstLineTop + i * lineHeightPx
            val bottom = top + lineHeightPx
            // The narrower of the line's two edges decides its width.
            val edge = if (abs(top - r) > abs(bottom - r)) top.toFloat() else bottom.toFloat()
            val chord = chordAt(r, edge)
            val inset = ceil((diameterPx - chord) / 2f).toInt() + marginPx
            leftIndents[i] = inset
            rightIndents[i] = inset
        }
    }

    private fun chordAt(r: Float, y: Float): Float {
        val d = abs(y - r)
        return if (d >= r) 0f else 2f * sqrt(r * r - d * d)
    }

    fun build(text: CharSequence, start: Int, end: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, start, end, textPaint, diameterPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            .setIncludePad(false)
            .setIndents(leftIndents, rightIndents)
            .build()

    /**
     * Start offsets of every page of [text], lazily, so the first pages can be
     * shown while the rest are still being cut. Offsets are into [text] itself:
     * StaticLayout reports line starts in the coordinates of its source.
     */
    fun pageStarts(text: CharSequence): Sequence<Int> = sequence {
        var start = 0
        // A first guess at a page's length; refined from the pages already cut.
        var window = 400
        while (start < text.length) {
            yield(start)
            var end = minOf(text.length, start + window)
            var layout = build(text, start, end)
            // Not enough text to fill the page: widen until it is, or the text ends.
            while (layout.lineCount <= linesPerPage && end < text.length) {
                end = minOf(text.length, end + window)
                layout = build(text, start, end)
            }
            if (layout.lineCount <= linesPerPage) break
            val next = layout.getLineStart(linesPerPage)
            if (next <= start) break
            window = max(200, (next - start) * 3 / 2)
            start = next
        }
    }

    companion object {
        /**
         * The surah as one run of text: the name and the basmala as their own
         * centred paragraphs, then the verses running on like a printed page,
         * each closed by its ۝ number. Returns the text and where each verse
         * starts in it. The verse colour comes from the paint; a span over the
         * whole run would paint over the numbers' colour.
         */
        fun surahText(
            surahName: String,
            withBasmala: Boolean,
            ayahs: List<Ayah>,
            lineHeightPx: Int,
            nameColor: Int,
            numberColor: Int,
            numberTypeface: Typeface,
        ): Pair<CharSequence, IntArray> {
            val sb = SpannableStringBuilder()
            val nameStart = sb.length
            sb.append(surahName)
            sb.setSpan(ForegroundColorSpan(nameColor), nameStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.85f), nameStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append('\n')
            if (withBasmala) {
                sb.append("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ")
                sb.append('\n')
            }
            val starts = IntArray(ayahs.size)
            ayahs.forEachIndexed { i, ayah ->
                starts[i] = sb.length
                sb.append(ayah.text)
                sb.append('\u00A0') // keep the mark on the verse's last line
                val markStart = sb.length
                sb.append(ReaderTypography.ayahEndMark(ayah.verse))
                sb.setSpan(ForegroundColorSpan(numberColor), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(TypefaceSpan(numberTypeface), markStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (i < ayahs.size - 1) sb.append(' ')
            }
            // Every line exactly one band tall, so the indents line up with the circle.
            sb.setSpan(LineHeightSpan.Standard(lineHeightPx), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            return sb to starts
        }
    }
}
