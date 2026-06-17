package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for loading a saved game in replay mode.
 * If the game has no move history, returns the game in normal mode.
 */
class LoadGameForReplayUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(gameId: Long): GameState? {
        val gameState = repository.loadGame(gameId) ?: return null

        // Only enter replay mode if there's move history
        return if (gameState.moveHistory.isNotEmpty()) {
            gameState.enterReplayMode()
        } else {
            gameState
        }
    }
}
