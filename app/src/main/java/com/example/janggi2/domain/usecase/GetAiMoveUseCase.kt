package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import javax.inject.Inject

/**
 * Use case for calculating AI move for the current game state.
 *
 * This use case:
 * 1. Takes the current game state
 * 2. Delegates to the AI engine to calculate the best move
 * 3. Returns the calculated move
 *
 * Thread Safety: This is a suspend function that should be called from a coroutine.
 */
class GetAiMoveUseCase @Inject constructor(
    private val aiEngine: AiEngine
) {
    /**
     * Calculate the best AI move for the given game state.
     *
     * @param gameState Current game state
     * @param thinkTimeMs Time limit for calculation in milliseconds
     * @return Best move calculated by AI, or null if no legal moves or error occurred
     * @throws IllegalStateException if AI engine is not initialized
     */
    suspend operator fun invoke(
        gameState: GameState,
        thinkTimeMs: Int = 2000
    ): Move? {
        require(aiEngine.isReady()) {
            "AI engine must be initialized before calculating moves"
        }

        return aiEngine.getBestMove(gameState, thinkTimeMs, skillLevel = gameState.aiDifficulty)
    }
}
