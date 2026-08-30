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
     * Simple Arabic text search (contains).
     * For voice search we convert speech to text then search.
     */
    fun searchText(query: String, limit: Int = 30): List<Ayah> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return allAyahs.filter { it.text.contains(q) }.take(limit)
    }

    /**
     * Search by surah name (Arabic or English).
     */
    fun searchSurah(name: String): List<com.quran.watch8.data.model.SurahInfo> {
        val q = name.trim().lowercase()
        return SurahMetadata.surahs.filter {
            it.nameAr.contains(name) ||
            it.nameEn.lowercase().contains(q) ||
            it.number.toString() == q
        }
    }

    fun isLoaded() = allAyahs.isNotEmpty()
}
