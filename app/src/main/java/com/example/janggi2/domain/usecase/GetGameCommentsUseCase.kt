package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing the comments left on a saved AI review's tested move sequences.
 */
class GetGameCommentsUseCase @Inject constructor(
    private val repository: GameRepository
) {
    operator fun invoke(reviewId: Long): Flow<List<ReviewComment>> {
        return repository.getCommentsForReview(reviewId)
    }
}
