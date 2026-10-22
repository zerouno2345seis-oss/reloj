package com.quran.watch8.data.repository

import android.content.Context
import com.google.gson.Gson
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.QuranResponse
import com.quran.watch8.data.model.SurahMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Offline Quran repository.
 * Loads the full Uthmani Hafs text from assets (1.6 MB).
 * Supports fast surah/ayah lookup and simple text search.
 */
class QuranRepository(private val context: Context) {

    companion object {
        // The bundled text puts a space between an open tanween (and any shadda
        // after it) and the alef or alef maqsura that carries it -- "إِذࣰ ا",
        // "هُدࣰ ى" -- about 2,400 times. Drawn as is, that alef stood apart
        // from its word, and a line could even break between them.
        private val OPEN_TANWEEN_SPACE = Regex("([\u08F0-\u08F2]\u0651?) (?=[\u0627\u0649])")

        fun joinOpenTanween(text: String): String = text.replace(OPEN_TANWEEN_SPACE, "$1")
    }

    // ──────────────────────────────────────────────────────────
    //  Arabic text normalization helpers
    // ──────────────────────────────────────────────────────────

    // Compiled once: building these per call recompiled them for each of the
    // 6,236 ayahs on every search keystroke.
    private val MARKS = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u08F0-\u08F2]")
    private val ALEF_VARIANTS = Regex("[أإآ\u0671]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Strips all Arabic diacritics (harakat, tanwin, shadda, sukun, tatweel, Quranic marks),
     * normalises alef variants (أ إ آ ٱ) → ا, alef maqsura ى → ي, teh marbuta ة → ه.
     * This makes search work correctly on Uthmani Hafs text which carries full tashkeel.
     */
    private fun normalizeArabic(text: String): String = text
        // All standard harakat + Quranic extended marks (U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06ED, open tanween U+08F0–U+08F2)
        .replace(MARKS, "")
        // Alef variants → bare alef
        .replace(ALEF_VARIANTS, "ا")
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

    @Volatile private var allAyahs: List<Ayah> = emptyList()
    @Volatile private var ayahsBySurah: Map<Int, List<Ayah>> = emptyMap()
    /** Search form of each ayah, same order as [allAyahs]; built on first search. */
    @Volatile private var normalizedTexts: List<String>? = null
    private val gson = Gson()
    // The view model and MainActivity both ask for the text at start-up; without
    // this they parsed the 1.6 MB file twice, side by side.
    private val loadLock = Mutex()

    suspend fun loadQuran(): Boolean = withContext(Dispatchers.IO) {
        loadLock.withLock {
            try {
                if (allAyahs.isNotEmpty()) return@withLock true
                context.assets.open("quran_uthmani.min.json").use { input ->
                    val reader = InputStreamReader(input, Charsets.UTF_8)
                    val response = gson.fromJson(reader, QuranResponse::class.java)
                    val ayahs = response.quran.map { it.copy(text = joinOpenTanween(it.text)) }
                    ayahsBySurah = ayahs.groupBy { it.chapter }
                    allAyahs = ayahs
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun getSurahAyahs(surahNumber: Int): List<Ayah> {
        return ayahsBySurah[surahNumber] ?: emptyList()
    }

    fun getAyah(surah: Int, ayah: Int): Ayah? {
        val list = ayahsBySurah[surah] ?: return null
        // Verses are stored in order, so the index is the verse number - 1.
        return list.getOrNull(ayah - 1)?.takeIf { it.verse == ayah } ?: list.find { it.verse == ayah }
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

        val ayahs = allAyahs
        val texts = normalizedTexts ?: ayahs.map { normalizeArabic(it.text) }.also { normalizedTexts = it }
        return ayahs.indices.asSequence().filter { i ->
            val normalizedText = texts[i]

            // Tier 1: exact phrase match after normalization
            if (normalizedText.contains(normalizedQuery)) return@filter true

            // Tier 2 & 3: per-word root-like match
            normalizedText.split(WHITESPACE).any { word ->
                val stripped = stripArabicPrefixes(word)
                // The normalised query appears anywhere inside the stripped word
                stripped.contains(normalizedQuery) ||
                // Or inside the original (normalised) word
                word.contains(normalizedQuery)
            }
        }.map { ayahs[it] }.take(limit).toList()
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
