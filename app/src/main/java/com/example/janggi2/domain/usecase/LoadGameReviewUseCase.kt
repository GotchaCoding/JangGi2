package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedReview
import javax.inject.Inject

/**
 * Use case for loading a saved AI review in replay mode, so its move-by-move
 * annotations can be browsed the same way a saved game's 복기 is.
 */
class LoadGameReviewUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(reviewId: Long): SavedReview? {
        val saved = repository.loadReview(reviewId) ?: return null
        return saved.copy(gameState = saved.gameState.enterReplayMode())
    }
}
