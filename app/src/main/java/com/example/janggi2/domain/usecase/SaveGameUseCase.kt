package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for saving a game.
 */
class SaveGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(
        gameState: GameState,
        name: String,
        choPlayerName: String? = null,
        hanPlayerName: String? = null,
        choRank: String? = null,
        hanRank: String? = null
    ): Long {
        return repository.saveGame(gameState, name, choPlayerName, hanPlayerName, choRank, hanRank)
    }
}
