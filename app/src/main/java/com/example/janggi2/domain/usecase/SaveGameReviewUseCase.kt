package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for saving an AI review.
 */
class SaveGameReviewUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(gameState: GameState, review: GameReview, name: String): Long {
        return repository.saveReview(gameState, review, name)
    }
}
