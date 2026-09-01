package com.quran.watch8.data.db

import androidx.room.*
import com.quran.watch8.data.db.entities.BookmarkEntity
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.db.entities.SavedLocationEntity
import com.quran.watch8.data.db.entities.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────
//  Bookmark DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────────────────────
//  Saved Locations DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<SavedLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE saved_locations SET name = :newName WHERE id = :id")
    suspend fun updateName(id: String, newName: String)

    /** Keep only one CAR-type location — delete old before inserting new. */
    @Query("DELETE FROM saved_locations WHERE type = 'CAR'")
    suspend fun deleteCarLocations()
}

// ─────────────────────────────────────────────────────────────
//  Voice Notes DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: VoiceNoteEntity)

    @Query("UPDATE voice_notes SET transcription = :text WHERE id = :id")
    suspend fun updateTranscription(id: String, text: String)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ─────────────────────────────────────────────────────────────
//  Reading Position DAO  (single-row table — upsert by fixed id=1)
// ─────────────────────────────────────────────────────────────
@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_position WHERE id = 1 LIMIT 1")
    fun getFlow(): Flow<ReadingPositionEntity?>

    @Query("SELECT * FROM reading_position WHERE id = 1 LIMIT 1")
    suspend fun get(): ReadingPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(position: ReadingPositionEntity)
}
