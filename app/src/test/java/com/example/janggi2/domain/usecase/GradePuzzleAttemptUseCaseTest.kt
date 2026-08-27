package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.ai.Evaluation
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GradePuzzleAttemptUseCase] 는 저장된 리뷰를 불러온 경우([com.example.janggi2.domain.model.MoveReview.bestMove]
 * 가 null) 에도 채점해야 하므로, 절대 그 값을 참조하지 않고 매번 엔진에 새로 물어봅니다.
 * 이 테스트는 그 사실을 [FakeAiEngine] 호출 횟수/인자로 검증합니다.
 */
class GradePuzzleAttemptUseCaseTest {

    /** 호출 순서대로 [evaluations] 를 하나씩 돌려줍니다. */
    private class FakeAiEngine(private val evaluations: List<Evaluation?>) : AiEngine {
        var evaluateCallCount = 0
            private set

        override suspend fun initialize() {}
        override suspend fun getBestMove(gameState: GameState, thinkTimeMs: Int, skillLevel: Int): Move? = null

        override suspend fun evaluate(gameState: GameState, thinkTimeMs: Int, skillLevel: Int): Evaluation? {
            val result = evaluations.getOrNull(evaluateCallCount)
            evaluateCallCount++
            return result
        }

        override fun destroy() {}
        override fun isReady(): Boolean = true
    }

    private val choSoldierMove = Move(Position(0, 3), Position(0, 4))
    private val hanSoldierMove = Move(Position(0, 6), Position(0, 5))
    private val otherHanSoldierMove = Move(Position(2, 6), Position(2, 5))

    /** 초가 한 수 둔 뒤, 한이 둘 차례인 국면. */
    private val hanToMove = initialGameState().applyMove(choSoldierMove)

    @Test
    fun `엔진의 최선수를 그대로 두면 손실과 무관하게 최선수로, 정답으로 판정한다`() = runBlocking {
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = hanSoldierMove, scoreCp = 20),
                Evaluation(bestMove = null, scoreCp = 20)
            )
        )
        val useCase = GradePuzzleAttemptUseCase(engine)

        val result = useCase(hanToMove, hanSoldierMove)

        assertEquals(MoveQuality.BEST, result?.quality)
        assertEquals(true, result?.correct)
    }

    @Test
    fun `최선수와 다르고 손실이 크면 악수로, 오답으로 판정하며 엔진 최선수를 함께 돌려준다`() = runBlocking {
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = hanSoldierMove, scoreCp = 20),
                Evaluation(bestMove = null, scoreCp = 400)
            )
        )
        val useCase = GradePuzzleAttemptUseCase(engine)

        val result = useCase(hanToMove, otherHanSoldierMove)

        assertEquals(MoveQuality.BLUNDER, result?.quality)
        assertEquals(false, result?.correct)
        assertEquals(hanSoldierMove, result?.engineBestMove)
    }

    @Test
    fun `두기 전 국면을 평가하지 못하면 null 을 반환한다`() = runBlocking {
        val engine = FakeAiEngine(listOf(null))
        val useCase = GradePuzzleAttemptUseCase(engine)

        val result = useCase(hanToMove, hanSoldierMove)

        assertNull(result)
        assertEquals(1, engine.evaluateCallCount)
    }

    @Test
    fun `외통으로 끝나면 이후 국면은 평가하지 않고 최선수로 판정한다`() = runBlocking {
        // 상대 궁이 없는 국면 - evaluateGameStatus 가 두는 즉시 CHECKMATE 로 판정한다
        // (실제 외통 수순을 두지 않고도 체크메이트 지름길을 검증하는 표준 트릭,
        // OpeningPositionTest/PointAdjudicationTest 와 같은 방식).
        val noHanGeneral = initialGameState().let { s ->
            s.copy(board = s.board.filterValues { it !is Piece.General || it.player != Player.HAN })
        }

        val engine = FakeAiEngine(listOf(Evaluation(bestMove = choSoldierMove, scoreCp = 999)))
        val useCase = GradePuzzleAttemptUseCase(engine)

        val result = useCase(noHanGeneral, choSoldierMove)

        assertEquals(MoveQuality.BEST, result?.quality)
        assertEquals(0, result?.lossCp)
        assertEquals(true, result?.correct)
        assertEquals(1, engine.evaluateCallCount)
    }

    @Test
    fun `둔 뒤 국면을 평가하지 못하면 좋음으로, 정답으로 처리한다`() = runBlocking {
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = hanSoldierMove, scoreCp = 20),
                null
            )
        )
        val useCase = GradePuzzleAttemptUseCase(engine)

        val result = useCase(hanToMove, otherHanSoldierMove)

        assertEquals(MoveQuality.GOOD, result?.quality)
        assertEquals(0, result?.lossCp)
        assertTrue(result!!.correct)
    }

    @Test
    fun `불법수를 시도하면 null 을 반환한다`() = runBlocking {
        val engine = FakeAiEngine(listOf(Evaluation(bestMove = hanSoldierMove, scoreCp = 20)))
        val useCase = GradePuzzleAttemptUseCase(engine)

        // 초 기물을 한 차례에 옮기려는 시도 - 불법.
        val illegalMove = Move(choSoldierMove.to, Position(0, 5))
        val result = useCase(hanToMove, illegalMove)

        assertNull(result)
    }
}
