package com.quran.watch8.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLinesTest {
    // A width per letter, as a font would give; counts the calls.
    private val seen = ArrayList<String>()
    private val measure: (String) -> Float = { seen += it; it.length * 7f }

    private val verses = listOf(
        "بسم الله الرحمن الرحيم الحمد لله رب العالمين",
        "الرحمن الرحيم مالك يوم الدين اياك نعبد واياك نستعين",
        "اهدنا الصراط المستقيم صراط الذين انعمت عليهم غير المغضوب عليهم ولا الضالين",
    )
    private fun words() = ReaderLines.words(verses, listOf(1, 2, 3), null) { "۝$it" }

    @Test
    fun measuresEachDistinctWordOnce() {
        val words = words()
        ReaderLines(measure, measure, stretch = true).layoutAll(words, 160f)
        val distinct = words.map { it.text }.toSet().size
        // Plain words (and the space) once each; stretched forms once each too.
        assertEquals(distinct + 1, seen.count { 'ـ' !in it })
        assertEquals(seen.size, seen.toSet().size)
    }

    @Test
    fun memoisingKeepsTheSameLines() {
        val words = words()
        val raw = ReaderLines(measure, measure, stretch = true).layoutAll(words, 160f)
        // Every width carried through is the word's own measure.
        raw.forEach { l -> l.texts.forEachIndexed { i, t -> assertEquals(t.length * 7f, l.widths[i], 0f) } }
        val again = ReaderLines(measure, measure, stretch = true).layoutAll(words, 160f)
        assertEquals(raw.size, again.size)
        raw.zip(again).forEach { (a, b) ->
            assertArrayEquals(a.texts, b.texts)
            assertArrayEquals(a.widths, b.widths, 0f)
            assertEquals(a.gap, b.gap, 0f)
        }
        // Words are whole and in order: joined again, they are the text.
        val drawn = raw.flatMap { it.texts.toList() }.filter { !it.startsWith("۝") }.map { it.replace("ـ", "") }
        assertEquals(verses.joinToString(" ").split(' '), drawn)
    }

    @Test
    fun findsTheLineHoldingAWordOfALongVerse() {
        val words = words()
        assertEquals(listOf(0, 1, 2, 3), words.filter { it.ayahIndex == 2 && !it.isMark }.map { it.ordinal }.take(4))
        val lines = ReaderLines(measure, measure, stretch = false).layoutAll(words, 120f)
        for (word in 0 until 12) {
            val l = ReaderLines.lineOfWord(lines, 2, word)
            val line = lines[l]
            assert(line.firstWordOf(2) <= word)
            // The next line, if it still holds the verse, starts after the word.
            lines.getOrNull(l + 1)?.firstWordOf(2)?.takeIf { it >= 0 }?.let { assert(it > word) }
        }
        assertEquals(-1, ReaderLines.lineOfWord(lines, 7, 0))
    }
}
