package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for deleting a saved game.
 */
class DeleteGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(gameId: Long) {
        repository.deleteGame(gameId)
    }
}
