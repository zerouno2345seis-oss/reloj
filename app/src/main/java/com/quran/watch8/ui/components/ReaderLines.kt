package com.quran.watch8.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The reader's lines, shared by all four display styles.
 *
 * A surah becomes a run of words, each verse closed by its number mark. Lines
 * take whole words only, and every line but the surah's last is stretched to
 * its full width: first by drawing out letters with kashida (only in the fonts
 * that read the simple text -- the Uthmani letter forms cannot be stretched),
 * then by widening the spaces. So no line ends short of its edge, which is
 * what left the round screen looking half empty.
 *
 * Words are drawn one by one, right to left, so the platform shapes each word
 * whole and the stretch never lands inside a ligature it cannot draw.
 */
class ReaderLines(
    private val paint: TextPaint,
    private val markPaint: TextPaint,
    /** Kashida allowed: the font reads the simple text. */
    private val stretch: Boolean,
) {
    /** A word, or a verse's number mark; [ayahIndex] -1 is the basmala. */
    class Word(val text: String, val ayahIndex: Int, val isMark: Boolean)

    class Line(
        val texts: Array<String>,
        val widths: FloatArray,
        val marks: BooleanArray,
        val ayahs: IntArray,
        /** Space between neighbouring words after stretching. */
        val gap: Float,
        /** Drawn width: the full width given, or less for a surah's last line and the basmala. */
        val width: Float,
        /** Verses whose first word is on this line. */
        val begins: IntArray,
    ) {
        val firstAyah: Int get() = ayahs.firstOrNull { it >= 0 } ?: ayahs.firstOrNull() ?: 0
        fun holdsAyah(index: Int) = ayahs.any { it == index }

        /**
         * The verse this line stands for when saving the place: the first one
         * that begins on it, else the one it continues. Finding a verse goes by
         * where it begins too, so reopening lands on the same line every time.
         */
        val placeAyah: Int get() = begins.firstOrNull() ?: firstAyah

        /** The verse under [x], measured from the line's centre, before scaling. */
        fun ayahAt(x: Float): Int {
            var right = width / 2f
            for (i in texts.indices) {
                val left = right - widths[i]
                if (x >= left - gap / 2f) return ayahs[i]
                right = left - gap
            }
            return ayahs.lastOrNull() ?: 0
        }

        /**
         * The verse whose number mark is under [x] (measured as for [ayahAt]),
         * or -1 when [x] is on the words. The mark is small, so the hit reaches
         * half a mark beyond it on either side.
         */
        fun markAt(x: Float): Int {
            var right = width / 2f
            for (i in texts.indices) {
                val left = right - widths[i]
                if (marks[i]) {
                    val slop = widths[i] / 2f + gap / 2f
                    if (x >= left - slop && x <= right + slop) return ayahs[i]
                }
                right = left - gap
            }
            return -1
        }

        /**
         * Draws the line centred on ([cx], [cy]), scaled about that point. A
         * verse for which [checked] is true gets a red tick over its number.
         */
        fun draw(
            canvas: Canvas, cx: Float, cy: Float, scale: Float, alpha: Float, paint: Paint, markPaint: Paint,
            checked: (Int) -> Boolean = NONE_CHECKED,
        ) {
            val fm = paint.fontMetrics
            val baseline = -(fm.ascent + fm.descent) / 2f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale, scale)
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            var right = width / 2f
            for (i in texts.indices) {
                val p = if (marks[i]) markPaint else paint
                val old = p.alpha
                p.alpha = old * a / 255
                p.textAlign = Paint.Align.RIGHT
                canvas.drawText(texts[i], right, baseline, p)
                p.alpha = old
                if (marks[i] && checked(ayahs[i])) drawTick(canvas, right - widths[i] / 2f, paint.textSize, a)
                right -= widths[i] + gap
            }
            canvas.restore()
        }

        /** A red tick centred on [x], over the middle of the line. */
        private fun drawTick(canvas: Canvas, x: Float, textSize: Float, alpha: Int) {
            val h = textSize * 0.42f
            TICK_PAINT.strokeWidth = textSize * 0.11f
            TICK_PAINT.alpha = alpha
            val path = android.graphics.Path().apply {
                moveTo(x - h * 0.5f, 0f)
                lineTo(x - h * 0.12f, h * 0.38f)
                lineTo(x + h * 0.55f, -h * 0.45f)
            }
            canvas.drawPath(path, TICK_PAINT)
        }
    }

    private val space = paint.measureText(" ")

    private fun width(w: Word) = (if (w.isMark) markPaint else paint).measureText(w.text)

    /**
     * Lays [words] out from [start], line [i] being [widthOf] (i) wide, until
     * the words or [maxLines] run out. Returns the lines and where it stopped.
     */
    fun layout(words: List<Word>, start: Int, maxLines: Int, widthOf: (Int) -> Float): Pair<List<Line>, Int> {
        val lines = ArrayList<Line>()
        var i = start
        while (i < words.size && lines.size < maxLines) {
            val w = widthOf(lines.size)
            val row = ArrayList<Word>()
            val begins = ArrayList<Int>()
            var used = 0f
            var basmala = false
            while (i < words.size) {
                // The basmala keeps a line of its own: the first verse starts below it.
                if (row.isNotEmpty() && row.last().ayahIndex < 0 && words[i].ayahIndex >= 0) { basmala = true; break }
                // A verse mark goes with the word before it, never alone at a line's start.
                var j = i + 1
                var group = width(words[i])
                while (j < words.size && words[j].isMark) { group += space + width(words[j]); j++ }
                val add = if (row.isEmpty()) group else space + group
                if (row.isNotEmpty() && used + add > w) break
                for (k in i until j) {
                    val w0 = words[k]
                    if (!w0.isMark && w0.ayahIndex >= 0 && (k == 0 || words[k - 1].isMark || words[k - 1].ayahIndex != w0.ayahIndex)) begins += w0.ayahIndex
                    row += w0
                }
                used += add
                i = j
            }
            // The basmala is not stretched: it sits in the middle at its own width.
            lines += justify(row, w, last = basmala || i >= words.size, begins.toIntArray())
        }
        return lines to i
    }

    /** All of [words] in lines of one [width]. */
    fun layoutAll(words: List<Word>, width: Float): List<Line> = layout(words, 0, Int.MAX_VALUE) { width }.first

    private fun justify(row: List<Word>, w: Float, last: Boolean, begins: IntArray): Line {
        val texts = Array(row.size) { row[it].text }
        val widths = FloatArray(row.size) { width(row[it]) }
        val marks = BooleanArray(row.size) { row[it].isMark }
        val ayahs = IntArray(row.size) { row[it].ayahIndex }
        fun natural() = widths.sum() + space * (row.size - 1)
        if (last) return Line(texts, widths, marks, ayahs, space, natural(), begins)

        if (stretch && w > natural()) {
            // Up to three-quarters of the slack goes to kashida, longest words first.
            var budget = (w - natural()) * 0.75f
            val order = row.indices.filter { !marks[it] }.sortedByDescending { texts[it].length }
            val parts = HashMap<Int, Kashida.Word>()
            var progress = true
            while (progress && budget > 0f) {
                progress = false
                for (k in order) {
                    val word = parts.getOrPut(k) { Kashida.Word(row[k].text) }
                    val next = word.stretchedOnceMore() ?: continue
                    val nw = paint.measureText(next)
                    val dw = nw - widths[k]
                    if (dw <= 0f || dw > budget) continue
                    word.accept()
                    texts[k] = next
                    widths[k] = nw
                    budget -= dw
                    progress = true
                }
            }
        }
        val extra = max(0f, w - natural())
        val gap = if (row.size > 1) space + extra / (row.size - 1) else space
        return Line(texts, widths, marks, ayahs, gap, if (row.size > 1) w else natural(), begins)
    }

    companion object {
        private val LETTER = Regex("[ء-يٮ-ۓ]")
        internal val NONE_CHECKED: (Int) -> Boolean = { false }
        internal val TICK_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEF4444.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        /**
         * A surah as words: the basmala (unless [basmala] is null), then each
         * verse and its number mark. Pause signs standing alone between spaces
         * join the word before them, so a line never starts with one.
         */
        fun words(verses: List<String>, verseNumbers: List<Int>, basmala: String?, mark: (Int) -> String): List<Word> {
            val out = ArrayList<Word>()
            fun addText(text: String, ayah: Int) {
                for (token in text.split(' ')) {
                    if (token.isEmpty()) continue
                    val last = out.lastOrNull()
                    if (!LETTER.containsMatchIn(token) && last != null && !last.isMark && last.ayahIndex == ayah) {
                        out[out.size - 1] = Word(last.text + " " + token, ayah, false)
                    } else {
                        out += Word(token, ayah, false)
                    }
                }
            }
            if (basmala != null) addText(basmala, -1)
            verses.forEachIndexed { i, v ->
                addText(v, i)
                out += Word(mark(verseNumbers[i]), i, true)
            }
            return out
        }

        /** Width of the round screen at height [y], for a screen [diameter] across. */
        fun chord(y: Float, diameter: Float): Float {
            val r = diameter / 2f
            val d = abs(y - r)
            return if (d >= r) 0f else 2f * sqrt(r * r - d * d)
        }

        /** Width a line between [top] and [bottom] may take: the narrower of its edges. */
        fun chordBetween(top: Float, bottom: Float, diameter: Float): Float =
            min(chord(top, diameter), chord(bottom, diameter))
    }
}

/**
 * Kashida: drawing out a joining letter with tatweel (U+0640), as a printed
 * mushaf does to fill a line. Only after letters that join the next one, never
 * inside lam-alef or the lam-lam-ha of the divine name, at most three per word,
 * spread from the word's end towards its start.
 */
object Kashida {
    private const val TATWEEL = 'ـ'
    private const val JOINING = "بتثجحخسشصضطظعغفقكلمنهيئ"
    private val MARK = Regex("[ؐ-ًؚ-ٰٟۖ-ࣰۭ-ࣲ]")
    private val LETTER = Regex("[ء-ي]")
    const val MAX_PER_WORD = 3

    /** Where a tatweel may follow cluster [j] of [clusters]. */
    fun candidates(clusters: List<String>): List<Int> {
        val out = ArrayList<Int>()
        for (j in 0 until clusters.size - 1) {
            val a = clusters[j][0]
            val b = clusters[j + 1][0]
            if (a !in JOINING || !LETTER.matches(b.toString())) continue
            if (a == 'ل' && (b in "اأإآل" || b == 'ه')) continue
            out += j
        }
        return out
    }

    /** Letters with their vowel marks: a tatweel goes after the marks. */
    fun clusters(word: String): List<String> {
        val out = ArrayList<StringBuilder>()
        for (ch in word) {
            if (MARK.matches(ch.toString()) && out.isNotEmpty()) out.last().append(ch) else out += StringBuilder().append(ch)
        }
        return out.map { it.toString() }
    }

    class Word(text: String) {
        private val parts = clusters(text)
        private val slots = candidates(parts)
        private val adds = IntArray(parts.size)
        private var count = 0
        private var pending: Int = -1

        /** The word with one more tatweel, or null when it may take no more. */
        fun stretchedOnceMore(): String? {
            if (slots.isEmpty() || count >= MAX_PER_WORD) return null
            pending = slots[slots.size - 1 - count % slots.size]
            adds[pending]++
            val s = render()
            adds[pending]--
            return s
        }

        fun accept() { if (pending >= 0) { adds[pending]++; count++; pending = -1 } }

        private fun render() = buildString {
            parts.forEachIndexed { j, p -> append(p); repeat(adds[j]) { append(TATWEEL) } }
        }
    }
}
