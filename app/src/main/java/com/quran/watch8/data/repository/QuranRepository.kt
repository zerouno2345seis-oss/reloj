package com.quran.watch8.data.repository

import android.content.Context
import com.google.gson.Gson
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.QuranResponse
import com.quran.watch8.data.model.SurahMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Offline Quran repository.
 * Loads the full Uthmani Hafs text from assets (1.6 MB).
 * Supports fast surah/ayah lookup and simple text search.
 */
class QuranRepository(private val context: Context) {

    // ──────────────────────────────────────────────────────────
    //  Arabic text normalization helpers
    // ──────────────────────────────────────────────────────────

    /**
     * Strips all Arabic diacritics (harakat, tanwin, shadda, sukun, tatweel, Quranic marks),
     * normalises alef variants (أ إ آ ٱ) → ا, alef maqsura ى → ي, teh marbuta ة → ه.
     * This makes search work correctly on Uthmani Hafs text which carries full tashkeel.
     */
    private fun normalizeArabic(text: String): String = text
        // All standard harakat + Quranic extended marks (U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06ED)
        .replace(Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
        // Alef variants → bare alef
        .replace(Regex("[أإآ\u0671]"), "ا")
        // Alef maqsura → ya
        .replace("ى", "ي")
        // Teh marbuta → ha
        .replace("ة", "ه")
        // Tatweel / kashida
        .replace("ـ", "")
        .trim()

    /**
     * Common Arabic connective/preposition prefixes that attach to words.
     * Ordered longest-first to match greedily.
     */
    private val ARABIC_PREFIXES = listOf(
        "وال", "فال", "بال", "كال",   // particle + definite article
        "لل",                           // la + definite article
        "ال",                           // definite article alone
        "و", "ف", "ب", "ك", "ل"       // standalone particles
    )

    /**
     * Strips the longest matching Arabic prefix so we can compare roots.
     * Only strips if the resulting stem has at least 2 characters.
     */
    private fun stripArabicPrefixes(word: String): String {
        for (prefix in ARABIC_PREFIXES) {
            if (word.startsWith(prefix) && word.length > prefix.length + 1) {
                return word.removePrefix(prefix)
            }
        }
        return word
    }

    private var allAyahs: List<Ayah> = emptyList()
    private var ayahsBySurah: Map<Int, List<Ayah>> = emptyMap()
    private val gson = Gson()

    suspend fun loadQuran(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (allAyahs.isNotEmpty()) return@withContext true
            context.assets.open("quran_uthmani.min.json").use { input ->
                val reader = InputStreamReader(input, Charsets.UTF_8)
                val response = gson.fromJson(reader, QuranResponse::class.java)
                allAyahs = response.quran
                ayahsBySurah = allAyahs.groupBy { it.chapter }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getSurahAyahs(surahNumber: Int): List<Ayah> {
        return ayahsBySurah[surahNumber] ?: emptyList()
    }

    fun getAyah(surah: Int, ayah: Int): Ayah? {
        return allAyahs.find { it.chapter == surah && it.verse == ayah }
    }

    fun getAllSurahs() = SurahMetadata.surahs

    /**
     * Arabic-aware text search with three tiers:
     *  1. Direct normalised-string contains (fastest — catches exact + diacritic mismatches).
     *  2. Word-level match on every token of the ayah with prefix stripping
     *     (root-like: query "رحم" finds "الرحمن", "رحيم", "يرحم" …).
     *  3. Partial-word match: any word in the text whose stripped form starts with the query.
     *
     * Both query and text are normalised before comparison.
     */
    fun searchText(query: String, limit: Int = 40): List<Ayah> {
        if (query.isBlank()) return emptyList()
        val normalizedQuery = normalizeArabic(query.trim())
        if (normalizedQuery.length < 2) return emptyList()

        return allAyahs.filter { ayah ->
            val normalizedText = normalizeArabic(ayah.text)

            // Tier 1: exact phrase match after normalization
            if (normalizedText.contains(normalizedQuery)) return@filter true

            // Tier 2 & 3: per-word root-like match
            normalizedText.split(Regex("\\s+")).any { word ->
                val stripped = stripArabicPrefixes(word)
                // The normalised query appears anywhere inside the stripped word
                stripped.contains(normalizedQuery) ||
                // Or inside the original (normalised) word
                word.contains(normalizedQuery)
            }
        }.take(limit)
    }

    /**
     * Search surah by Arabic name (normalised), English name, or number.
     */
    fun searchSurah(name: String): List<com.quran.watch8.data.model.SurahInfo> {
        val raw = name.trim()
        val normalizedQuery = normalizeArabic(raw)
        val lowerEn = raw.lowercase()
        return SurahMetadata.surahs.filter {
            normalizeArabic(it.nameAr).contains(normalizedQuery) ||
            it.nameEn.lowercase().contains(lowerEn) ||
            it.number.toString() == raw
        }
    }

    fun isLoaded() = allAyahs.isNotEmpty()
}
