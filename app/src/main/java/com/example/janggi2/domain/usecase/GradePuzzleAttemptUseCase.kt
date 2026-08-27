package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.MoveQualityClassifier
import com.example.janggi2.domain.rules.GameRules
import javax.inject.Inject

/** 퍼즐 채점 결과. [engineBestMove]는 오답일 때 힌트 화살표로 씁니다. */
data class PuzzleAttemptResult(
    val quality: MoveQuality,
    val lossCp: Int,
    val correct: Boolean,
    val engineBestMove: Move?
)

/**
 * 퍼즐에서 둔 수를 채점합니다. 저장된 리뷰를 불러온 경우 [com.example.janggi2.domain.model.MoveReview.bestMove]가
 * null이라 절대 참조하지 않고, 매번 엔진에 새로 물어봅니다 - [ReviewGameUseCase]와 같은 방식입니다.
 */
class GradePuzzleAttemptUseCase @Inject constructor(
    private val aiEngine: AiEngine
) {
    companion object {
        private const val THINK_TIME_MS = ReviewGameUseCase.REVIEW_THINK_TIME_MS
        private const val SKILL_LEVEL = 20
    }

    private val gameRules = GameRules()

    suspend operator fun invoke(position: GameState, attempted: Move): PuzzleAttemptResult? {
        val evalBefore = aiEngine.evaluate(position, THINK_TIME_MS, SKILL_LEVEL) ?: return null
        val afterState = gameRules.applyMoveWithRules(attempted, position) ?: return null

        if (afterState.status == GameStatus.CHECKMATE) {
            return PuzzleAttemptResult(
                quality = MoveQuality.BEST,
                lossCp = 0,
                correct = true,
                engineBestMove = evalBefore.bestMove
            )
        }

        val evalAfter = aiEngine.evaluate(afterState, THINK_TIME_MS, SKILL_LEVEL)
        if (evalAfter == null) {
            return PuzzleAttemptResult(
                quality = MoveQuality.GOOD,
                lossCp = 0,
                correct = true,
                engineBestMove = evalBefore.bestMove
            )
        }

        val lossCp = (evalBefore.scoreCp + evalAfter.scoreCp).coerceAtLeast(0)
        val quality = MoveQualityClassifier.classify(lossCp, attempted, evalBefore.bestMove)
        return PuzzleAttemptResult(
            quality = quality,
            lossCp = lossCp,
            correct = quality == MoveQuality.BEST || quality == MoveQuality.GOOD,
            engineBestMove = evalBefore.bestMove
        )
    }
}
