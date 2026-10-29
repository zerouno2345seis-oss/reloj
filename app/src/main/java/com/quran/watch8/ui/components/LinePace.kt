package com.quran.watch8.ui.components

/**
 * «سطر في الوسط»: how fast the line moves on, and how the middle line and its
 * neighbours show. Plain numbers, kept apart from the fonts so they can be
 * tested off the watch.
 */
object LinePace {
    const val MIN_LINE_SECONDS = 1f
    const val MAX_LINE_SECONDS = 20f
    const val LINE_SECONDS_STEP = 0.5f

    /** Seconds per line, to a tenth, within the range. */
    fun coerceLineSeconds(value: Float): Float =
        (Math.round(value * 10f) / 10f).coerceIn(MIN_LINE_SECONDS, MAX_LINE_SECONDS)

    private fun digits(n: Int): String = n.toString().map { '\u0660' + (it - '0') }.joinToString("")

    /** "٤ ث", "٢٫٥ ث". */
    fun secondsText(value: Float): String {
        val tenths = Math.round(value * 10f)
        val whole = digits(tenths / 10)
        return (if (tenths % 10 == 0) whole else whole + "٫" + digits(tenths % 10)) + " ث"
    }

    /**
     * Minutes to read a juz of [juzLetters] letters, where a line holds
     * [lettersPerLine] and rests [seconds] in the middle.
     */
    fun juzMinutes(juzLetters: Int, lettersPerLine: Float, seconds: Float): Int =
        if (lettersPerLine <= 0f) 0 else Math.round(juzLetters / lettersPerLine * seconds / 60f)

    /** The seconds per line that read that juz in [minutes]. */
    fun secondsForJuz(juzLetters: Int, lettersPerLine: Float, minutes: Int): Float =
        if (juzLetters <= 0) MIN_LINE_SECONDS else coerceLineSeconds(minutes * 60f * lettersPerLine / juzLetters)

    /** Opacity of the lines above and below the middle one. */
    const val DEFAULT_SIDE_ALPHA = 0.45f
    const val MIN_SIDE_ALPHA = 0.1f
    const val MAX_SIDE_ALPHA = 0.9f

    /** How clear the middle line is, 1..[MAX_CLARITY]. */
    const val DEFAULT_CLARITY = 3
    const val MAX_CLARITY = 5

    /**
     * The middle line at clarity [level]: its opacity, and the width of the
     * stroke drawn around its letters, as a share of the text size.
     * 1 and 2 soften it, 3 is the plain full line, 4 and 5 thicken it.
     */
    fun lineCentre(level: Int): Pair<Float, Float> = when (level) {
        1 -> 0.7f to 0f
        2 -> 0.85f to 0f
        4 -> 1f to 0.025f
        5 -> 1f to 0.05f
        else -> 1f to 0f
    }

    fun clarityName(level: Int): String = when (level) {
        1 -> "خافت"
        2 -> "أخفّ"
        4 -> "أعرض"
        5 -> "الأعرض"
        else -> "عادي"
    }
}
