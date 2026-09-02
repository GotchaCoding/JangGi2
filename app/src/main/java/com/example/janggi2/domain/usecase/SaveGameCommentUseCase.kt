package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for saving a comment on a tested move sequence branched off a saved AI review.
 */
class SaveGameCommentUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(reviewId: Long, message: String, branchStartIndex: Int, moves: List<Move>): Long {
        return repository.saveComment(reviewId, message, branchStartIndex, moves)
    }
}
