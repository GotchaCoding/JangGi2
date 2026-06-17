package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for loading a saved game.
 */
class LoadGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(gameId: Long): GameState? {
        return repository.loadGame(gameId)
    }
}
