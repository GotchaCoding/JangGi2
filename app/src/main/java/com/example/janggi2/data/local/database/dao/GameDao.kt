package com.example.janggi2.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janggi2.data.local.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved games.
 */
@Dao
interface GameDao {

    @Query("SELECT * FROM saved_games ORDER BY savedDate DESC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM saved_games WHERE id = :gameId")
    suspend fun getGameById(gameId: Long): GameEntity?

    @Query("SELECT * FROM saved_games WHERE name = :name LIMIT 1")
    suspend fun getGameByName(name: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("DELETE FROM saved_games WHERE id = :gameId")
    suspend fun deleteGameById(gameId: Long)

    @Query("DELETE FROM saved_games")
    suspend fun deleteAllGames()

    @Query("SELECT COUNT(*) FROM saved_games")
    suspend fun getGameCount(): Int

    /**
     * Get or create an auto-save game slot.
     * Auto-save always uses id = 1 with name "auto_save"
     */
    @Query("SELECT * FROM saved_games WHERE name = 'auto_save' LIMIT 1")
    suspend fun getAutoSave(): GameEntity?
}
