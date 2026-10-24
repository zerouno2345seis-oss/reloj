package com.quran.watch8.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KashidaTest {
    @Test
    fun stretchesAfterJoiningLettersAndKeepsTheirVowels() {
        val parts = Kashida.clusters("الْكِتَابَ")
        assertEquals(listOf("ا", "لْ", "كِ", "تَ", "ا", "بَ"), parts)
        // lam before kaf, kaf before ta, ta before alef; never after an alef.
        assertEquals(listOf(1, 2, 3), Kashida.candidates(parts))
    }

    @Test
    fun neverInsideTheDivineNameOrLamAlef() {
        assertEquals(emptyList<Int>(), Kashida.candidates(Kashida.clusters("لِلَّهِ")))
        assertEquals(emptyList<Int>(), Kashida.candidates(Kashida.clusters("لَا")))
    }

    @Test
    fun atMostThreePerWordFromTheEnd() {
        val word = Kashida.Word("الْكِتَابَ")
        val seen = ArrayList<String>()
        while (true) {
            val next = word.stretchedOnceMore() ?: break
            word.accept()
            seen += next
        }
        assertEquals(Kashida.MAX_PER_WORD, seen.size)
        assertEquals("الْكِتَـابَ", seen[0])
        assertEquals(3, seen.last().count { it == 'ـ' })
        assertNull(Kashida.Word("لِلَّهِ").stretchedOnceMore())
    }
}
