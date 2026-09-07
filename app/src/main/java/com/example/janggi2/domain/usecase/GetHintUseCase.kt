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

        /**
         * 힌트는 시간이 아니라 깊이로 끊습니다.
         *
         * 시간으로 끊으면 같은 국면이라도 그때그때 도달하는 깊이가 달라서, 힌트를
         * 누를 때마다 다른 수가 나옵니다. 실제로 공짜로 차를 잡는 수(상x차)를 찾는
         * 실행과 못 찾고 궁을 옮기라는 실행이 번갈아 나왔습니다. 배우려고 보는
         * 기능이 매번 다른 답을 내놓으면 신뢰할 수 없습니다.
         */
        const val HINT_DEPTH = 16

        /**
         * 깊이 탐색이 느린 기기에서 화면을 오래 붙잡지 않도록 거는 상한입니다.
         * 여기에 걸리면 그 실행만 얕게 끝나므로 넉넉히 잡습니다.
         */
        const val HINT_TIME_CAP_MS = 10_000
    }

    /**
     * @return 추천 수, 합법수가 없거나 오류면 null
     * @throws IllegalStateException if the AI engine is not initialized
     */
    suspend operator fun invoke(
        gameState: GameState,
        depth: Int = HINT_DEPTH
    ): Move? {
        check(aiEngine.isReady()) {
            "AI engine must be initialized before requesting a hint"
        }

        return aiEngine.getBestMove(
            gameState,
            thinkTimeMs = HINT_TIME_CAP_MS,
            skillLevel = HINT_SKILL_LEVEL,
            depth = depth
        )
    }
}
