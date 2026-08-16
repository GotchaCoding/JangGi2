package com.example.janggi2.domain.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move

/**
 * Interface for AI engine that calculates best moves for Janggi.
 * Implementations should wrap the native Fairy-Stockfish engine.
 */
interface AiEngine {
    /**
     * Initialize the AI engine.
     * Must be called before any other operations.
     * @throws IllegalStateException if initialization fails
     */
    suspend fun initialize()

    /**
     * Calculate the best move for the current game state.
     *
     * Skill level 은 탐색과 한 호출로 묶여 있습니다. 따로 두면 힌트(항상 최강)와
     * AI 착수(대국 난이도)가 서로의 설정 사이에 끼어들 수 있는데, Skill Level 은
     * 엔진 전역 옵션이라 그러면 잘못된 강도로 탐색하게 됩니다.
     *
     * @param gameState Current state of the game
     * @param thinkTimeMs Maximum time to think in milliseconds
     * @param skillLevel 1 (beginner) ~ 20 (full strength)
     * @return Best move calculated by the engine, or null if no legal moves available
     */
    suspend fun getBestMove(gameState: GameState, thinkTimeMs: Int = 2000, skillLevel: Int = 20): Move?

    /**
     * Evaluates the given position - AI 리뷰가 국면마다 점수를 매길 때 씁니다.
     * [getBestMove] 와 같은 탐색이지만 최선수뿐 아니라 점수도 돌려줍니다.
     *
     * @return null이면 합법수가 없는 국면(외통·궁이 없는 판 등)
     */
    suspend fun evaluate(gameState: GameState, thinkTimeMs: Int = 2000, skillLevel: Int = 20): Evaluation?

    /**
     * Clean up engine resources.
     * Should be called when the engine is no longer needed.
     */
    fun destroy()

    /**
     * Returns true if the engine is initialized and ready to use.
     */
    fun isReady(): Boolean
}

/**
 * 국면 하나의 엔진 평가. [scoreCp] 는 언제나 "이 국면에서 둘 차례인 쪽" 관점입니다 -
 * 양수면 그쪽이 유리, 음수면 불리. 졸(폰) 하나 ≈ 100.
 *
 * @param bestMove 엔진이 고른 최선수
 * @param scoreCp 메이트 국면은 [MATE_CLAMP_CP] 로 눌러 담습니다 - 손실 계산에 그대로 써도
 *   비정상적으로 큰 수가 나오지 않게 하려는 것입니다. 정확한 수는 [mateDistance] 로 압니다.
 * @param mateDistance 메이트까지 남은 수. 양수면 이쪽이 외통을 부르는 쪽, 음수면 당하는 쪽.
 */
data class Evaluation(
    val bestMove: Move?,
    val scoreCp: Int,
    val mateDistance: Int? = null
) {
    companion object {
        /** 메이트 점수를 손실 계산에 쓸 수 있는 범위로 눌러 담는 값. */
        const val MATE_CLAMP_CP = 3000
    }
}
