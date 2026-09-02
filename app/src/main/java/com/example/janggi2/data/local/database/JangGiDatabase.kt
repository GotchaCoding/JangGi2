package com.example.janggi2.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.janggi2.data.local.database.dao.GameCommentDao
import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.local.database.dao.GameReviewDao
import com.example.janggi2.data.local.database.entity.GameCommentEntity
import com.example.janggi2.data.local.database.entity.GameEntity
import com.example.janggi2.data.local.database.entity.GameReviewEntity

/**
 * Room database for JangGi2 app.
 */
@Database(
    entities = [GameEntity::class, GameReviewEntity::class, GameCommentEntity::class],
    version = 7,
    exportSchema = false
)
abstract class JangGiDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun gameReviewDao(): GameReviewDao
    abstract fun gameCommentDao(): GameCommentDao

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

        /**
         * Migration from version 3 to 4: Add choPlayerName, hanPlayerName, choRank,
         * hanRank columns so a saved 기보 can record who played and their 급수.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_games ADD COLUMN choPlayerName TEXT")
                db.execSQL("ALTER TABLE saved_games ADD COLUMN hanPlayerName TEXT")
                db.execSQL("ALTER TABLE saved_games ADD COLUMN choRank TEXT")
                db.execSQL("ALTER TABLE saved_games ADD COLUMN hanRank TEXT")
            }
        }

        /**
         * Migration from version 4 to 5: Add the game_reviews table so AI 리뷰 결과가
         * 기보 저장과 별개로, 리뷰를 돌릴 때마다 자동으로 저장됩니다.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `game_reviews` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `savedDate` INTEGER NOT NULL,
                        `boardStateJson` TEXT NOT NULL,
                        `currentPlayer` TEXT NOT NULL,
                        `moveCount` INTEGER NOT NULL,
                        `gameStatus` TEXT NOT NULL,
                        `winner` TEXT,
                        `moveHistoryJson` TEXT NOT NULL,
                        `startBoardJson` TEXT,
                        `reviewJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from version 5 to 6: Add the game_comments table so a 검토(테스트
         * 수순)에 댓글을 남기면 그 메시지와 둬 본 수순이 같이 저장됩니다.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `game_comments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reviewId` INTEGER NOT NULL,
                        `message` TEXT NOT NULL,
                        `movesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from version 6 to 7: Add branchStartIndex column to game_comments,
         * so 댓글을 다시 열면 원래 기보의 그 지점부터 재생한 뒤 둬 본 수순을 이어
         * 붙여 그대로 되살릴 수 있습니다.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_comments ADD COLUMN branchStartIndex INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
