package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.ai.Evaluation
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ReviewGameUseCase.review] 는 국면마다 엔진 탐색이 한 번 필요해 실제 엔진 없이는
 * 느리고 결정론적이지 않으므로, 스크립트로 짠 [Evaluation] 시퀀스를 돌려주는
 * [FakeAiEngine] 으로 손실 계산·등급 분류·체크메이트 특수 케이스만 검증합니다.
 */
class ReviewGameUseCaseTest {

    /** 호출 순서대로(=국면 0, 1, 2, ...) [evaluations] 를 하나씩 돌려줍니다. */
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
    private val elsewhereMove = Move(Position(0, 6), Position(2, 6))

    private fun twoMoveGame(): GameState =
        initialGameState()
            .applyMove(choSoldierMove)
            .applyMove(hanSoldierMove)

    @Test
    fun `엔진의 최선수와 같은 수는 손실이 크더라도 최선수로 판정한다`() = runBlocking {
        // eval0(초 관점)=20, eval1(한 관점)=20 -> 손실 40, 임계값(10)은 넘지만
        // eval0.bestMove 가 실제 둔 수와 같으므로 최선수여야 한다.
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = choSoldierMove, scoreCp = 20),
                Evaluation(bestMove = elsewhereMove, scoreCp = 20),
                Evaluation(bestMove = null, scoreCp = 0)
            )
        )
        val useCase = ReviewGameUseCase(engine)

        val review = (useCase.review(twoMoveGame()).toList().last() as ReviewProgress.Finished).review

        assertEquals(MoveQuality.BEST, review.moveReviews[0].quality)
    }

    @Test
    fun `손실이 임계값을 넘으면 실수로 판정한다`() = runBlocking {
        // 수 2(한)의 손실 = eval1.scoreCp(20) + eval2.scoreCp(150) = 170 -> MISTAKE(100 초과 300 이하)
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = choSoldierMove, scoreCp = 20),
                Evaluation(bestMove = elsewhereMove, scoreCp = 20),
                Evaluation(bestMove = null, scoreCp = 150)
            )
        )
        val useCase = ReviewGameUseCase(engine)

        val review = (useCase.review(twoMoveGame()).toList().last() as ReviewProgress.Finished).review

        assertEquals(170, review.moveReviews[1].lossCp)
        assertEquals(MoveQuality.MISTAKE, review.moveReviews[1].quality)
        assertEquals(Player.HAN, review.moveReviews[1].player)
    }

    @Test
    fun `손실이 아주 크면 악수로 판정한다`() = runBlocking {
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = choSoldierMove, scoreCp = 20),
                Evaluation(bestMove = elsewhereMove, scoreCp = 400),
                Evaluation(bestMove = null, scoreCp = 0)
            )
        )
        val useCase = ReviewGameUseCase(engine)

        val review = (useCase.review(twoMoveGame()).toList().last() as ReviewProgress.Finished).review

        assertEquals(MoveQuality.BLUNDER, review.moveReviews[1].quality)
    }

    @Test
    fun `외통으로 끝난 마지막 수는 종국 국면을 엔진에 묻지 않고 최선수로 판정한다`() = runBlocking {
        val checkmateGame = twoMoveGame().withStatus(GameStatus.CHECKMATE, newWinner = Player.HAN)
        // 국면은 0,1,2 세 개지만 마지막(체크메이트)은 건너뛰므로 평가는 두 번만 나가야 한다.
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = choSoldierMove, scoreCp = 20),
                Evaluation(bestMove = elsewhereMove, scoreCp = 20)
            )
        )
        val useCase = ReviewGameUseCase(engine)

        val review = (useCase.review(checkmateGame).toList().last() as ReviewProgress.Finished).review

        assertEquals(2, engine.evaluateCallCount)
        assertEquals(MoveQuality.BEST, review.moveReviews[1].quality)
        assertEquals(0, review.moveReviews[1].lossCp)
    }

    @Test
    fun `국면 개수만큼 진행률을 알리고 마지막에 결과를 낸다`() = runBlocking {
        val engine = FakeAiEngine(
            listOf(
                Evaluation(bestMove = choSoldierMove, scoreCp = 0),
                Evaluation(bestMove = hanSoldierMove, scoreCp = 0),
                Evaluation(bestMove = null, scoreCp = 0)
            )
        )
        val useCase = ReviewGameUseCase(engine)

        val progress = useCase.review(twoMoveGame()).toList()

        val analyzing = progress.filterIsInstance<ReviewProgress.Analyzing>()
        assertEquals(3, analyzing.size)
        assertEquals(3 to 3, analyzing.last().completed to analyzing.last().total)
        assertEquals(1, progress.filterIsInstance<ReviewProgress.Finished>().size)
        assertEquals(2, (progress.last() as ReviewProgress.Finished).review.moveReviews.size)
    }
}
