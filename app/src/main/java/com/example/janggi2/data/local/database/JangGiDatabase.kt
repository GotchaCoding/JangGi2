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
    version = 3,
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

        /**
         * Migration from version 2 to 3: Add startBoardJson column.
         *
         * 이게 없어서 마·상 배치를 기본값과 다르게 고른 대국을 복기하거나 수 기록을
         * 눌러 이동하면 항상 기본 배치로 되감아 실제와 다른 기물이 나오는 버그가
         * 있었습니다. 옛 대국은 이 값이 없어 여전히 기본 배치로 되감기지만, 새로
         * 저장되는 대국부터는 바로잡힙니다.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_games ADD COLUMN startBoardJson TEXT")
            }
        }
    }
}
