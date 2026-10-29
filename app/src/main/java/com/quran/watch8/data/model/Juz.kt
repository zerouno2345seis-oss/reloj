package com.quran.watch8.data.model

/**
 * The thirty ajza' of the mushaf (Hafs, the Madinah mushaf's divisions), by
 * the surah and verse each begins at. Checked against assets/quran_info.json.
 */
object Juz {
    /** (surah, verse) where juz n + 1 begins. */
    val STARTS: List<Pair<Int, Int>> = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    )

    private fun before(a: Pair<Int, Int>, b: Pair<Int, Int>) = a.first < b.first || (a.first == b.first && a.second < b.second)

    /** The juz (1..30) that [verse] of [surah] is in. */
    fun of(surah: Int, verse: Int): Int {
        val at = surah to verse
        return STARTS.indexOfLast { !before(at, it) }.coerceAtLeast(0) + 1
    }

    /** Every (surah, verse) of [juz], in order. */
    fun verses(juz: Int): List<Pair<Int, Int>> {
        val n = juz.coerceIn(1, 30)
        val (s0, v0) = STARTS[n - 1]
        val end = STARTS.getOrNull(n) // the first verse after this juz, or null for the last
        val out = ArrayList<Pair<Int, Int>>()
        for (s in s0..114) {
            val count = SurahMetadata.getSurah(s)?.versesCount ?: continue
            for (v in (if (s == s0) v0 else 1)..count) {
                if (end != null && !before(s to v, end)) return out
                out += s to v
            }
        }
        return out
    }
}
