package com.example.janggi2.domain.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move

/**
 * Interface for AI engine that calculates best moves for Janggi.
 * Implementations should wrap the native Fairy-Stockfish engine.
 */
interface AiEngine {
    /**
     * Initialize the AI engine.
     * Must be called before any other operations.
     * @throws IllegalStateException if initialization fails
     */
    suspend fun initialize()

    /**
     * Set the AI difficulty level.
     * @param difficulty Level from 1 (beginner) to 20 (expert)
     * @throws IllegalArgumentException if difficulty is out of range
     */
    suspend fun setDifficulty(difficulty: Int)

    /**
     * Calculate the best move for the current game state.
     * @param gameState Current state of the game
     * @param thinkTimeMs Maximum time to think in milliseconds (default: 2000ms)
     * @return Best move calculated by the engine, or null if no legal moves available
     */
    suspend fun getBestMove(gameState: GameState, thinkTimeMs: Int = 2000): Move?

    /**
     * Clean up engine resources.
     * Should be called when the engine is no longer needed.
     */
    fun destroy()

    /**
     * Returns true if the engine is initialized and ready to use.
     */
    fun isReady(): Boolean
}
