package com.example.janggi2.domain.model

/**
 * 손실(centipawn loss) 기준으로 수의 등급을 매깁니다. [ReviewGameUseCase]의 대국 전체
 * 리뷰와 블런더 퍼즐 채점이 같은 기준을 쓰도록 공유합니다.
 */
object MoveQualityClassifier {
    const val BEST_MAX_LOSS_CP = 10
    const val GOOD_MAX_LOSS_CP = 50
    const val INACCURACY_MAX_LOSS_CP = 100
    const val MISTAKE_MAX_LOSS_CP = 300

    fun classify(lossCp: Int, played: Move, engineBest: Move?): MoveQuality {
        val matchesBest = engineBest != null && engineBest.from == played.from && engineBest.to == played.to
        return when {
            matchesBest || lossCp <= BEST_MAX_LOSS_CP -> MoveQuality.BEST
            lossCp <= GOOD_MAX_LOSS_CP -> MoveQuality.GOOD
            lossCp <= INACCURACY_MAX_LOSS_CP -> MoveQuality.INACCURACY
            lossCp <= MISTAKE_MAX_LOSS_CP -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
    }
}
