package com.example.janggi2.domain.model

/**
 * AI 리뷰가 매기는 수의 등급. 엔진 평가 기준 손실(centipawn loss)이 작을수록 좋은 등급입니다.
 */
enum class MoveQuality {
    BEST,
    GOOD,
    INACCURACY,
    MISTAKE,
    BLUNDER
}

/**
 * 수 하나에 대한 AI 리뷰 결과.
 *
 * @param moveIndex [GameState.moveHistory] 안에서의 0부터 시작하는 순서
 * @param player 이 수를 둔 쪽
 * @param lossCp 엔진 기준 손실(centipawn). 0 이상이며, 최선수에 가까울수록 작습니다.
 * @param bestMove 그 자리에서 엔진이 골랐던 수. [move] 와 같으면 최선수를 그대로 둔 것입니다.
 */
data class MoveReview(
    val moveIndex: Int,
    val move: Move,
    val player: Player,
    val quality: MoveQuality,
    val lossCp: Int,
    val bestMove: Move?
)

/** 대국 전체의 AI 리뷰 결과. */
data class GameReview(
    val moveReviews: List<MoveReview>
)
