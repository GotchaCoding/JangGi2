package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.ai.Evaluation
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.MoveReview
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** [ReviewGameUseCase.review] 의 진행 상황. */
sealed class ReviewProgress {
    data class Analyzing(val completed: Int, val total: Int) : ReviewProgress()
    data class Finished(val review: GameReview) : ReviewProgress()
}

/**
 * 대국의 모든 수를 최선수/좋음/부정확/실수/악수로 판정합니다.
 *
 * 국면은 시작(0)부터 마지막 수까지 N+1개이고, 각 국면을 딱 한 번만 평가합니다. 평가는
 * 언제나 "그 국면에서 둘 차례인 쪽" 관점 점수라서, 수 i(국면 i-1 -> i)의 손실은
 * `eval(i-1) + eval(i)` 로 구합니다 - eval(i) 가 다음 차례(상대) 관점이라 부호가 이미
 * 뒤집혀 있기 때문입니다. 최선수를 뒀다면 0에 가깝고, 나빴다면 양수로 커집니다.
 */
class ReviewGameUseCase @Inject constructor(
    private val aiEngine: AiEngine
) {
    companion object {
        const val REVIEW_THINK_TIME_MS = 400
        private const val REVIEW_SKILL_LEVEL = 20

        private const val BEST_MAX_LOSS_CP = 10
        private const val GOOD_MAX_LOSS_CP = 50
        private const val INACCURACY_MAX_LOSS_CP = 100
        private const val MISTAKE_MAX_LOSS_CP = 300
    }

    fun review(gameState: GameState, thinkTimeMs: Int = REVIEW_THINK_TIME_MS): Flow<ReviewProgress> = flow {
        val moves = gameState.moveHistory
        val positionCount = moves.size + 1
        val positions = Array(positionCount) { gameState.reconstructStateAtPosition(it) }
        val evaluations = arrayOfNulls<Evaluation>(positionCount)

        for (k in 0 until positionCount) {
            // 대국이 외통으로 끝났으면 마지막 국면은 합법수가 없어 엔진에 물어볼 수
            // 없습니다 - 그 직전 수는 아래에서 바로 최선수로 판정합니다.
            val isFinalCheckmate = k == positionCount - 1 && gameState.status == GameStatus.CHECKMATE
            if (!isFinalCheckmate) {
                evaluations[k] = aiEngine.evaluate(positions[k], thinkTimeMs, REVIEW_SKILL_LEVEL)
            }
            emit(ReviewProgress.Analyzing(k + 1, positionCount))
        }

        val isCheckmateFinish = gameState.status == GameStatus.CHECKMATE
        val moveReviews = moves.indices.map { i ->
            val move = moves[i]
            val mover = positions[i].currentPlayer
            val evalBefore = evaluations[i]
            val evalAfter = evaluations.getOrNull(i + 1)

            val quality: MoveQuality
            val lossCp: Int
            when {
                i == moves.lastIndex && isCheckmateFinish -> {
                    quality = MoveQuality.BEST
                    lossCp = 0
                }
                evalBefore == null || evalAfter == null -> {
                    quality = MoveQuality.GOOD
                    lossCp = 0
                }
                else -> {
                    lossCp = (evalBefore.scoreCp + evalAfter.scoreCp).coerceAtLeast(0)
                    quality = classify(lossCp, move, evalBefore.bestMove)
                }
            }

            MoveReview(
                moveIndex = i,
                move = move,
                player = mover,
                quality = quality,
                lossCp = lossCp,
                bestMove = evalBefore?.bestMove
            )
        }

        emit(ReviewProgress.Finished(GameReview(moveReviews)))
    }

    private fun classify(lossCp: Int, played: Move, engineBest: Move?): MoveQuality {
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
