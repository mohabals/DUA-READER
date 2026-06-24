package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.*

@Database(
    entities = [
        StoryGroup::class,
        Story::class,
        SentenceCard::class,
        ActivityLog::class,
        UserProgress::class,
        Achievement::class,
        MonthlyChallenge::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun sentenceDao(): SentenceDao

    companion object {
        @Volatile
        private var INSTANCE: LocalDatabase? = null

        fun getDatabase(context: Context): LocalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalDatabase::class.java,
                    "bilingual_reader_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
