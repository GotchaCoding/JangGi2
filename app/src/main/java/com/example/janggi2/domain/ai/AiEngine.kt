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
     * Calculate the best move for the current game state.
     *
     * Skill level 은 탐색과 한 호출로 묶여 있습니다. 따로 두면 힌트(항상 최강)와
     * AI 착수(대국 난이도)가 서로의 설정 사이에 끼어들 수 있는데, Skill Level 은
     * 엔진 전역 옵션이라 그러면 잘못된 강도로 탐색하게 됩니다.
     *
     * @param gameState Current state of the game
     * @param thinkTimeMs Maximum time to think in milliseconds
     * @param skillLevel 1 (beginner) ~ 20 (full strength)
     * @return Best move calculated by the engine, or null if no legal moves available
     */
    suspend fun getBestMove(gameState: GameState, thinkTimeMs: Int = 2000, skillLevel: Int = 20): Move?

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
