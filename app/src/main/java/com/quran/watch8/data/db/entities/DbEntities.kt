package com.quran.watch8.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────
//  Bookmark
// ─────────────────────────────────────────────────────────────
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val surah: Int,
    val ayah: Int,
    val textSnippet: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

// ─────────────────────────────────────────────────────────────
//  Saved Location
// ─────────────────────────────────────────────────────────────
@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "IMPORTANT"   // CAR / MOSQUE / IMPORTANT / OTHER
)

// ─────────────────────────────────────────────────────────────
//  Voice Note
// ─────────────────────────────────────────────────────────────
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val transcription: String = "",
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────
//  Reading Position  (only one row, id always = 1)
// ─────────────────────────────────────────────────────────────
@Entity(tableName = "reading_position")
data class ReadingPositionEntity(
    @PrimaryKey val id: Int = 1,
    val surah: Int,
    val ayahIndex: Int,        // 0-based index inside ScalingLazyColumn
    val ayahNumber: Int = 1,   // 1-based ayah number in surah
    val surahNameAr: String,   // cached name for the Resume button label
    val ayahSnippet: String = "", // first 2-3 words of the ayah
    val timestamp: Long = System.currentTimeMillis()
)
