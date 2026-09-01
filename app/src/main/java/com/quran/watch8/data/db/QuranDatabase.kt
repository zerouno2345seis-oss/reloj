package com.quran.watch8.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.quran.watch8.data.db.entities.BookmarkEntity
import com.quran.watch8.data.db.entities.ReadingPositionEntity
import com.quran.watch8.data.db.entities.SavedLocationEntity
import com.quran.watch8.data.db.entities.VoiceNoteEntity

@Database(
    entities = [
        BookmarkEntity::class,
        SavedLocationEntity::class,
        VoiceNoteEntity::class,
        ReadingPositionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun readingPositionDao(): ReadingPositionDao

    companion object {
        @Volatile private var INSTANCE: QuranDatabase? = null

        fun getInstance(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_watch.db"
                )
                    .fallbackToDestructiveMigration()   // acceptable for v1
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
