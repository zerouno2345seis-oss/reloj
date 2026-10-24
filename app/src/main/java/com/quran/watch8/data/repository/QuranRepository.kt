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

        private val SPACES = Regex("\\s+")

        // Compiled once: building these per call recompiled them for each of the
        // 6,236 ayahs on every search keystroke.
        private val MARKS = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u08F0-\u08F2]")
        private val ALEF_VARIANTS = Regex("[أإآ\u0671]")
        private val HAMZA_SEATS = Regex("[ؤئ]")

        /**
         * Strips all Arabic diacritics (harakat, tanwin, shadda, sukun, tatweel, Quranic marks),
         * normalises alef variants (أ إ آ ٱ) → ا, alef maqsura ى → ي, teh marbuta ة → ه.
         * This makes search work correctly on Uthmani Hafs text which carries full tashkeel.
         */
        fun normalizeArabic(text: String): String = text
            // All standard harakat + Quranic extended marks (U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06ED, open tanween U+08F0–U+08F2)
            .replace(MARKS, "")
            // Alef variants → bare alef
            .replace(ALEF_VARIANTS, "ا")
            // Alef maqsura → ya
            .replace("ى", "ي")
            // Teh marbuta → ha
            .replace("ة", "ه")
            // Hamza on waw or ya → bare hamza, as dictation and quick typing write it
            .replace(HAMZA_SEATS, "ء")
            // Tatweel / kashida
            .replace("ـ", "")
            // Pause signs stood between spaces; one space is left where they were
            .replace(SPACES, " ")
            .trim()

        /**
         * Indexes of [texts] (already normalised) that match [query] (normalised),
         * best first: the phrase as written, the phrase with spaces ignored, then
         * every word of it anywhere in the verse.
         */
        fun rankMatches(texts: List<String>, query: String, limit: Int): List<Int> {
            val words = query.split(SPACES).filter { it.length >= 2 }
            if (query.length < 2 || words.isEmpty()) return emptyList()
            val joined = query.replace(SPACES, "")
            val exact = ArrayList<Int>()
            val spaced = ArrayList<Int>()
            val allWords = ArrayList<Int>()
            for (i in texts.indices) {
                val t = texts[i]
                when {
                    t.contains(query) -> exact += i
                    t.replace(SPACES, "").contains(joined) -> spaced += i
                    words.size > 1 && words.all { t.contains(it) } -> allWords += i
                }
                if (exact.size >= limit) break
            }
            return (exact + spaced + allWords).take(limit)
        }

        /** The basmala as Tanzil's simple text writes it. */
        const val SIMPLE_BASMALA = "بِسْمِ اللَّهِ الرَّحْمَـٰنِ الرَّحِيمِ"

        /**
         * Reads Tanzil's simple text ("surah|verse|text" lines, a "#" copyright
         * block at the end) into each surah's verses, in order. Tanzil starts
         * verse 1 of every surah but al-Fatiha with the basmala; the reader
         * draws the basmala itself, so it is left out here.
         */
        fun parseSimple(lines: Sequence<String>): Map<Int, List<String>> {
            val bySurah = HashMap<Int, MutableList<String>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) continue
                val surah = parts[0].toIntOrNull() ?: continue
                var text = parts[2].trim()
                if (surah != 1 && parts[1] == "1") {
                    // At-Tin and al-Qadr write its first letter with a shadda: "بِّسْمِ".
                    val rest = SIMPLE_BASMALA.substring(2) + " " // after "بِ"
                    for (start in listOf("\u0628\u0650", "\u0628\u0651\u0650", "\u0628\u0650\u0651")) {
                        if (text.startsWith(start + rest)) { text = text.substring(start.length + rest.length).trim(); break }
                    }
                }
                bySurah.getOrPut(surah) { ArrayList() } += text
            }
            return bySurah
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Arabic text normalization helpers
    // ──────────────────────────────────────────────────────────


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

    @Volatile private var simpleBySurah: Map<Int, List<String>> = emptyMap()
    private val simpleLock = Mutex()

    /**
     * The simple (imla'i) text, with full vowel marks: the fonts that stretch
     * with kashida read it, since the Uthmani letter forms cannot be stretched.
     * Loaded on first use only.
     */
    suspend fun loadSimple(): Boolean = withContext(Dispatchers.IO) {
        simpleLock.withLock {
            if (simpleBySurah.isNotEmpty()) return@withLock true
            try {
                context.assets.open("quran_simple.txt").bufferedReader(Charsets.UTF_8).useLines {
                    simpleBySurah = parseSimple(it)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /** One surah's verses in the simple text, verse 1 first; empty before [loadSimple]. */
    fun getSurahSimple(surahNumber: Int): List<String> = simpleBySurah[surahNumber] ?: emptyList()

    fun getAllSurahs() = SurahMetadata.surahs

    /**
     * Search by phrase, typed or spoken. It reads the simple text, which writes
     * the words as people type them ("يا أيها الذين آمنوا"); the Uthmani text
     * joins and spells some of them differently ("يَٰٓأَيُّهَا", "ءَامَنُوٓاْ"), so
     * a phrase of more than one word used to find nothing there.
     *
     * Best matches first: the exact phrase, then the phrase ignoring spaces,
     * then verses holding every word of it in any order.
     */
    suspend fun searchText(query: String, limit: Int = 40): List<Ayah> {
        if (query.isBlank()) return emptyList()
        loadSimple()
        val ayahs = allAyahs
        val texts = normalizedTexts ?: ayahs.map { a ->
            normalizeArabic(simpleBySurah[a.chapter]?.getOrNull(a.verse - 1) ?: a.text)
        }.also { normalizedTexts = it }
        return rankMatches(texts, normalizeArabic(query.trim()), limit).map { ayahs[it] }
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
