package com.quran.watch8.data.repository

import com.quran.watch8.data.db.BookmarkDao
import com.quran.watch8.data.db.ReadingPositionDao
import com.quran.watch8.data.db.SavedLocationDao
import com.quran.watch8.data.db.VoiceNoteDao
import com.quran.watch8.data.db.entities.BookmarkEntity
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.db.entities.SavedLocationEntity
import com.quran.watch8.data.db.entities.VoiceNoteEntity
import com.quran.watch8.data.model.Bookmark
import com.quran.watch8.data.model.LocationType
import com.quran.watch8.data.model.SavedLocation
import com.quran.watch8.data.model.VoiceNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository that wraps Room DAOs and converts entities ↔ domain models.
 * Replaces the JSON-in-DataStore approach for structured data.
 */
class DatabaseRepository(
    private val bookmarkDao: BookmarkDao,
    private val locationDao: SavedLocationDao,
    private val voiceNoteDao: VoiceNoteDao,
    private val readingPositionDao: ReadingPositionDao
) {

    // ─────────────────────────────── Bookmarks ───────────────────────────────

    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllFlow().map { list ->
        list.map { e ->
            Bookmark(
                id = e.id,
                surah = e.surah,
                ayah = e.ayah,
                textSnippet = e.textSnippet,
                timestamp = e.timestamp,
                note = e.note
            )
        }
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insert(
            BookmarkEntity(
                id = bookmark.id,
                surah = bookmark.surah,
                ayah = bookmark.ayah,
                textSnippet = bookmark.textSnippet,
                timestamp = bookmark.timestamp,
                note = bookmark.note
            )
        )
    }

    suspend fun removeBookmark(id: String) = bookmarkDao.deleteById(id)

    // ─────────────────────────────── Locations ───────────────────────────────

    val locations: Flow<List<SavedLocation>> = locationDao.getAllFlow().map { list ->
        list.map { e ->
            SavedLocation(
                id = e.id,
                name = e.name,
                address = e.address,
                latitude = e.latitude,
                longitude = e.longitude,
                timestamp = e.timestamp,
                type = runCatching { LocationType.valueOf(e.type) }.getOrDefault(LocationType.IMPORTANT)
            )
        }
    }

    suspend fun addLocation(location: SavedLocation) {
        if (location.type == LocationType.CAR) {
            locationDao.deleteCarLocations()
        }
        locationDao.insert(
            SavedLocationEntity(
                id = location.id,
                name = location.name,
                address = location.address,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = location.timestamp,
                type = location.type.name
            )
        )
    }

    suspend fun updateLocationName(id: String, newName: String) = locationDao.updateName(id, newName)

    suspend fun removeLocation(id: String) = locationDao.deleteById(id)

    // ─────────────────────────────── Voice Notes ─────────────────────────────

    val voiceNotes: Flow<List<VoiceNote>> = voiceNoteDao.getAllFlow().map { list ->
        list.map { e ->
            VoiceNote(
                id = e.id,
                title = e.title,
                filePath = e.filePath,
                transcription = e.transcription,
                durationMs = e.durationMs,
                timestamp = e.timestamp
            )
        }
    }

    suspend fun addVoiceNote(note: VoiceNote) {
        voiceNoteDao.insert(
            VoiceNoteEntity(
                id = note.id,
                title = note.title,
                filePath = note.filePath,
                transcription = note.transcription,
                durationMs = note.durationMs,
                timestamp = note.timestamp
            )
        )
    }

    suspend fun updateVoiceNoteTranscription(id: String, text: String) = voiceNoteDao.updateTranscription(id, text)

    suspend fun removeVoiceNote(id: String) = voiceNoteDao.deleteById(id)

    // ─────────────────────────── Reading Position ─────────────────────────────

    val lastReadingPosition: Flow<ReadingPositionEntity?> = readingPositionDao.getFlow()

    suspend fun saveReadingPosition(
        surah: Int,
        ayahIndex: Int,
        ayahNumber: Int,
        surahNameAr: String,
        ayahSnippet: String
    ) {
        readingPositionDao.save(
            ReadingPositionEntity(
                id = 1,
                surah = surah,
                ayahIndex = ayahIndex,
                ayahNumber = ayahNumber,
                surahNameAr = surahNameAr,
                ayahSnippet = ayahSnippet,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getLastReadingPosition(): ReadingPositionEntity? = readingPositionDao.get()
}
