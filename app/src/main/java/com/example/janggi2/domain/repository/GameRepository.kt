package com.example.janggi2.domain.repository

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.ReviewComment
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for game persistence operations.
 */
interface GameRepository {

    /**
     * Saves a game with the given name and optional player info.
     * @param gameState The game state to save
     * @param name The name for this saved game
     * @param choPlayerName 초쪽 기사 이름
     * @param hanPlayerName 한쪽 기사 이름
     * @param choRank 초쪽 기사 급수
     * @param hanRank 한쪽 기사 급수
     * @return The ID of the saved game
     */
    suspend fun saveGame(
        gameState: GameState,
        name: String,
        choPlayerName: String? = null,
        hanPlayerName: String? = null,
        choRank: String? = null,
        hanRank: String? = null
    ): Long

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

    /**
     * Saves an AI review, independent of whether the underlying game was itself saved.
     * @return The ID of the saved review
     */
    suspend fun saveReview(gameState: GameState, review: GameReview, name: String): Long

    /**
     * Loads a saved review by ID, along with the game it was computed on.
     * @return The saved review, or null if not found
     */
    suspend fun loadReview(reviewId: Long): SavedReview?

    /**
     * Gets all saved AI reviews as a Flow.
     */
    fun getAllReviews(): Flow<List<SavedReviewInfo>>

    /**
     * Deletes a saved review by ID.
     */
    suspend fun deleteReview(reviewId: Long)

    /**
     * "검토"로 갈라져 나가 둬 본 수순에 댓글을 남깁니다.
     * @return The ID of the saved comment
     */
    suspend fun saveComment(reviewId: Long, message: String, branchStartIndex: Int, moves: List<Move>): Long

    /**
     * 특정 리뷰에 달린 댓글을 시간순으로 Flow 로 냅니다.
     */
    fun getCommentsForReview(reviewId: Long): Flow<List<ReviewComment>>
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
    val gameStatus: String,
    val choPlayerName: String? = null,
    val hanPlayerName: String? = null,
    val choRank: String? = null,
    val hanRank: String? = null
)

/**
 * Information about a saved AI review for display in lists.
 */
data class SavedReviewInfo(
    val id: Long,
    val name: String,
    val savedDate: Long,
    val moveCount: Int
)

/** A saved AI review together with the game it was computed on. */
data class SavedReview(
    val gameState: GameState,
    val review: GameReview
)
