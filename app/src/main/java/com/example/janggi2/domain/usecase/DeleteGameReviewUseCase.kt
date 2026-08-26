package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for deleting a saved AI review.
 */
class DeleteGameReviewUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(reviewId: Long) {
        repository.deleteReview(reviewId)
    }
}
