package com.quran.watch8.data.repository

import com.quran.watch8.data.repository.QuranRepository.Companion.normalizeArabic
import com.quran.watch8.data.repository.QuranRepository.Companion.rankMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuranSearchTest {
    private val simple = File("src/main/assets/quran_simple.txt").useLines { QuranRepository.parseSimple(it) }
    private val keys = (1..114).flatMap { s -> simple.getValue(s).indices.map { s to it + 1 } }
    private val texts = (1..114).flatMap { s -> simple.getValue(s).map(::normalizeArabic) }

    private fun search(q: String, limit: Int = 2000) = rankMatches(texts, normalizeArabic(q), limit).map { keys[it] }

    @Test
    fun findsAPhraseAsItIsTypedOrSpoken() {
        val hits = search("يا أيها الذين آمنوا")
        // "O you who believe" opens 89 verses.
        assertTrue("found ${hits.size}", hits.size >= 89)
        assertEquals(2 to 104, hits.first())
    }

    @Test
    fun findsAlFatihasSecondVerse() {
        assertEquals(1 to 2, search("الحمد لله رب العالمين").first())
    }

    @Test
    fun oneWordStillFindsEveryVerseWithIt() {
        val hits = search("الكهف")
        assertTrue(hits.containsAll(listOf(18 to 9, 18 to 10, 18 to 11)))
    }

    @Test
    fun ignoresVowelsSpacesAndAlefForms() {
        assertEquals(search("يا أيها الذين آمنوا").take(5), search("يَا أَيُّهَا الَّذِينَ آمَنُوا").take(5))
        assertTrue(search("ياايها الذين امنوا").contains(2 to 104))
    }

    @Test
    fun findsEveryWordInAnyOrderAfterThePhrase() {
        val hits = search("الصبر الصلاة")
        assertTrue(hits.contains(2 to 45))
        assertTrue(hits.contains(2 to 153))
    }

    @Test
    fun theWholePhraseComesFirstAndCoversTheKnownVerses() {
        val hits = search("يا أيها الذين آمنوا")
        assertTrue(hits.containsAll(listOf(2 to 104, 2 to 153, 2 to 183)))
    }

    @Test
    fun aPhraseAcrossAPauseSignStillMatches() {
        // 18:1 ends "عِوَجًا ۜ"; the sign must not break the phrase.
        assertTrue(search("ولم يجعل له عوجا").contains(18 to 1))
    }
}
