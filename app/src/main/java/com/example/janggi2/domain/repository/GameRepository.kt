package com.example.janggi2.domain.repository

import com.example.janggi2.domain.model.GameState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for game persistence operations.
 */
interface GameRepository {

    /**
     * Saves a game with the given name.
     * @param gameState The game state to save
     * @param name The name for this saved game
     * @return The ID of the saved game
     */
    suspend fun saveGame(gameState: GameState, name: String): Long

    /**
     * Auto-saves the current game state.
     * This overwrites the auto-save slot.
     */
    suspend fun autoSave(gameState: GameState)

    /**
     * Loads a game by ID.
     * @return The game state, or null if not found
     */
    suspend fun loadGame(gameId: Long): GameState?

    /**
     * Loads the auto-saved game.
     * @return The auto-saved game state, or null if none exists
     */
    suspend fun loadAutoSave(): GameState?

    /**
     * Gets all saved games as a Flow.
     */
    fun getAllGames(): Flow<List<SavedGameInfo>>

    /**
     * Deletes a saved game by ID.
     */
    suspend fun deleteGame(gameId: Long)

    /**
     * Deletes all saved games (except auto-save).
     */
    suspend fun deleteAllGames()
}

/**
 * Information about a saved game for display in lists.
 */
data class SavedGameInfo(
    val id: Long,
    val name: String,
    val savedDate: Long,
    val moveCount: Int,
    val currentPlayer: String,
    val gameStatus: String
)
