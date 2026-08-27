package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Puzzle
import javax.inject.Inject

/**
 * AI 리뷰 결과에서 악수(BLUNDER)만 골라 다시 풀어볼 [Puzzle] 목록을 만듭니다.
 * 엔진을 부르지 않는 순수 함수입니다 - 국면 재구성뿐입니다.
 */
class ExtractBlunderPuzzlesUseCase @Inject constructor() {
    operator fun invoke(gameState: GameState, review: GameReview): List<Puzzle> {
        return review.moveReviews
            .filter { it.quality == MoveQuality.BLUNDER }
            .map { moveReview ->
                val position = gameState.reconstructStateAtPosition(moveReview.moveIndex).copy(
                    moveHistory = emptyList(),
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    isReplayMode = false,
                    replayPosition = 0
                )
                Puzzle(
                    position = position,
                    moveIndex = moveReview.moveIndex,
                    player = moveReview.player,
                    blunderMove = moveReview.move
                )
            }
    }
}
