package com.quran.watch8.data.repository

import android.content.Context
import com.google.gson.stream.JsonReader
import com.quran.watch8.data.model.Ayah
import com.quran.watch8.data.model.SurahMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile

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

        // Most verses have no open tanween; the check spares them the regex.
        fun joinOpenTanween(text: String): String =
            if (text.none { it in '\u08F0'..'\u08F2' }) text else text.replace(OPEN_TANWEEN_SPACE, "$1")

        /**
         * The Uthmani text, read as a stream: Gson's reflection over 6,236
         * objects was most of the wait before the reader could open.
         */
        fun parseUthmani(input: InputStream): List<Ayah> {
            val out = ArrayList<Ayah>(6236)
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() != "quran") { r.skipValue(); continue }
                    r.beginArray()
                    while (r.hasNext()) {
                        var chapter = 0; var verse = 0; var text = ""
                        r.beginObject()
                        while (r.hasNext()) when (r.nextName()) {
                            "chapter" -> chapter = r.nextInt()
                            "verse" -> verse = r.nextInt()
                            "text" -> text = r.nextString()
                            else -> r.skipValue()
                        }
                        r.endObject()
                        out += Ayah(chapter, verse, joinOpenTanween(text))
                    }
                    r.endArray()
                }
                r.endObject()
            }
            return out
        }

        private const val CACHE_MAGIC = 0x51524331 // "QRC1"
        private const val SURAHS = 114
        private const val CACHE_HEADER = 4 + (SURAHS + 1) * 4

        /**
         * Each surah's verses in one file, with the offsets of all 114 up front,
         * so the reader can take the one surah it opens on without the rest.
         */
        fun writeSurahCache(file: File, bySurah: Map<Int, List<String>>) {
            val body = ByteArrayOutputStream(2_000_000)
            val offsets = IntArray(SURAHS + 1)
            DataOutputStream(body).use { out ->
                for (n in 1..SURAHS) {
                    offsets[n - 1] = out.size()
                    val verses = bySurah[n].orEmpty()
                    out.writeInt(verses.size)
                    verses.forEach { out.writeUTF(it) }
                }
                offsets[SURAHS] = out.size()
            }
            // Written aside and renamed, so a half-written file is never read.
            val tmp = File(file.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(CACHE_MAGIC)
                offsets.forEach { out.writeInt(it) }
                body.writeTo(out)
            }
            if (!tmp.renameTo(file)) tmp.delete()
        }

        /** One surah from [writeSurahCache]'s file, or null when it is missing or not that file. */
        fun readSurahCache(file: File, surah: Int): List<String>? {
            if (surah !in 1..SURAHS || !file.isFile) return null
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    if (raf.readInt() != CACHE_MAGIC) return null
                    raf.seek(4L + (surah - 1) * 4L)
                    val start = raf.readInt()
                    val end = raf.readInt()
                    if (end < start || CACHE_HEADER + end > raf.length()) return null
                    val bytes = ByteArray(end - start)
                    raf.seek((CACHE_HEADER + start).toLong())
                    raf.readFully(bytes)
                    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                        List(input.readInt()) { input.readUTF() }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        private const val UTHMANI_CACHE = "surahs-uthmani"
        private const val SIMPLE_CACHE = "surahs-simple"

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
    // The view model and MainActivity both ask for the text at start-up; without
    // this they parsed the 1.6 MB file twice, side by side.
    private val loadLock = Mutex()

    suspend fun loadQuran(): Boolean = withContext(Dispatchers.IO) {
        loadLock.withLock {
            try {
                if (allAyahs.isNotEmpty()) return@withLock true
                val ayahs = context.assets.open("quran_uthmani.min.json").use { parseUthmani(it) }
                val bySurah = ayahs.groupBy { it.chapter }
                ayahsBySurah = bySurah
                allAyahs = ayahs
                saveCache(UTHMANI_CACHE) { bySurah.mapValues { (_, list) -> list.map { it.text } } }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Named by the app's version, so an update with a corrected text never
    // reads the old one; older files are removed when a new one is written.
    private val version: Long by lazy {
        try {
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        } catch (e: Exception) { 0L }
    }
    private fun cacheFile(name: String) = File(context.filesDir, "$name-v$version.bin")

    private fun saveCache(name: String, data: () -> Map<Int, List<String>>) {
        val file = cacheFile(name)
        if (file.isFile) return
        background.launch {
            try {
                context.filesDir.listFiles { f -> f.name.startsWith("$name-v") }?.forEach { it.delete() }
                writeSurahCache(file, data())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * One surah, as soon as possible: from the loaded text, else from the
     * per-surah file of an earlier start, else after the whole text loads.
     * The reader opens with this, so a cold start reads ~10 KB, not 1.6 MB.
     */
    suspend fun surahAyahsFast(surahNumber: Int): List<Ayah> {
        ayahsBySurah[surahNumber]?.let { return it }
        val cached = withContext(Dispatchers.IO) { readSurahCache(cacheFile(UTHMANI_CACHE), surahNumber) }
        if (!cached.isNullOrEmpty()) return cached.mapIndexed { i, t -> Ayah(surahNumber, i + 1, t) }
        loadQuran()
        return getSurahAyahs(surahNumber)
    }

    /** As [surahAyahsFast], for the simple text the stretching fonts read. */
    suspend fun surahSimpleFast(surahNumber: Int): List<String> {
        simpleBySurah[surahNumber]?.let { return it }
        val cached = withContext(Dispatchers.IO) { readSurahCache(cacheFile(SIMPLE_CACHE), surahNumber) }
        if (!cached.isNullOrEmpty()) return cached
        loadSimple()
        return getSurahSimple(surahNumber)
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
                val bySurah = simpleBySurah
                saveCache(SIMPLE_CACHE) { bySurah }
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
