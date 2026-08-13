package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import javax.inject.Inject

/**
 * Use case for suggesting the best move for the side to move, without playing it.
 *
 * 대국 난이도와 무관하게 항상 최강으로 탐색합니다. Skill Level 을 낮추면 엔진이
 * rootMoves[0] 에 의도적으로 무작위성을 섞기 때문에, 학습용 힌트로는 부적합합니다.
 */
class GetHintUseCase @Inject constructor(
    private val aiEngine: AiEngine
) {
    companion object {
        const val HINT_SKILL_LEVEL = 20
        const val HINT_THINK_TIME_MS = 1500
    }

    /**
     * @return 추천 수, 합법수가 없거나 오류면 null
     * @throws IllegalStateException if the AI engine is not initialized
     */
    suspend operator fun invoke(
        gameState: GameState,
        thinkTimeMs: Int = HINT_THINK_TIME_MS
    ): Move? {
        check(aiEngine.isReady()) {
            "AI engine must be initialized before requesting a hint"
        }

        return aiEngine.getBestMove(gameState, thinkTimeMs, skillLevel = HINT_SKILL_LEVEL)
    }
}
