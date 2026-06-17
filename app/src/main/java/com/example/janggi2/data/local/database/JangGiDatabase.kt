package com.example.janggi2.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.local.database.entity.GameEntity

/**
 * Room database for JangGi2 app.
 */
@Database(
    entities = [GameEntity::class],
    version = 2,
    exportSchema = false
)
abstract class JangGiDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        /**
         * Migration from version 1 to 2: Add moveHistoryJson column
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_games ADD COLUMN moveHistoryJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
