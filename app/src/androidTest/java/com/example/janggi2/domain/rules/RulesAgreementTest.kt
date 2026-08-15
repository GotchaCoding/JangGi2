package com.example.janggi2.domain.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.janggi2.data.ai.FairyStockfishEngine
import com.example.janggi2.data.ai.FenConverter
import com.example.janggi2.data.ai.NotationConverter
import com.example.janggi2.data.ai.UciProtocol
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * 코틀린 규칙과 엔진 규칙이 같은 답을 내는지 대조합니다.
 *
 * 이 앱은 이동 규칙을 두 벌 가지고 있습니다 - 화면이 쓰는 [PieceMovement] 와,
 * 힌트·AI 가 쓰는 Fairy-Stockfish 입니다. 하나로 합치는 대신 이 테스트로 묶기로 했고,
 * 이게 통과하는 한 "두 벌이라 어긋난다"는 위험은 없습니다.
 *
 * 네이티브가 필요해 계측 테스트입니다. 덤으로 [FenConverter]·[NotationConverter] 가
 * 실제 국면 수백 개에서 통째로 검증됩니다.
 */
@RunWith(AndroidJUnit4::class)
class RulesAgreementTest {

    private companion object {
        /** 한 판에서 둘 수 - 200수 판정에 걸리지 않는 선 */
        const val PLIES_PER_GAME = 120

        /** 시드마다 다른 대국이 나옵니다. 실패하면 시드로 그대로 재현됩니다. */
        val SEEDS = listOf(1L, 2L, 3L, 5L, 8L)
    }

    private lateinit var engine: FairyStockfishEngine
    private val rules = GameRules()
    private val checkDetector = CheckDetector()

    @Before
    fun setUp() = runBlocking {
        val notationConverter = NotationConverter()
        engine = FairyStockfishEngine(
            notationConverter,
            UciProtocol(FenConverter(), notationConverter)
        )
        engine.initialize()
    }

    @After
    fun tearDown() {
        if (engine.isReady()) engine.destroy()
    }

    @Test
    fun `both rule engines agree on every position of a random game`() {
        var positionsChecked = 0

        for (seed in SEEDS) {
            val random = Random(seed)
            var state = initialGameState()
            var ply = 0

            while (ply < PLIES_PER_GAME && !state.isGameOver()) {
                assertSameMoves(state, "seed=$seed ply=$ply")
                positionsChecked++

                val moves = kotlinMoves(state)
                if (moves.isEmpty()) break
                state = rules.applyMoveWithRules(moves.random(random), state) ?: break
                ply++
            }
        }

        // 서로 다른 국면을 실제로 훑었는지. 대국이 첫 수에서 조용히 멈춰도 통과하면
        // 안 되므로, 판이 바뀔 때만 세고 시드 하나당 최소 50수는 나왔어야 합니다.
        val floor = SEEDS.size * 50
        assertTrue("검사한 국면이 너무 적습니다: $positionsChecked (최소 $floor)", positionsChecked > floor)
    }

    @Test
    fun `both agree on whether a turn may be passed`() {
        // 엔진은 한 수 쉼을 궁 자리 제자리 수로 만들어 합법수에 넣습니다
        // (janggi 변형의 pass[WHITE|BLACK] = true). 코틀린은 기물별로 수를 만들어
        // 여기에 안 들어오므로 따로 맞춰 봅니다.
        var state = initialGameState()
        val random = Random(42)
        var checked = 0

        while (checked < 40 && !state.isGameOver()) {
            val enginePasses = engineMoves(state).filter { it.first == it.second }
            val kotlinCanPass = rules.canPass(state)

            assertEquals(
                "한 수 쉼 판정이 다릅니다 (${enginePasses.size}개): ${state.currentPlayer}",
                kotlinCanPass,
                enginePasses.isNotEmpty()
            )
            checked++

            val moves = kotlinMoves(state)
            if (moves.isEmpty()) break
            state = rules.applyMoveWithRules(moves.random(random), state) ?: break
        }

        assertEquals("40수를 다 확인하지 못했습니다", 40, checked)
    }

    /** 코틀린이 보는, 둘 차례인 쪽의 모든 합법 수 */
    private fun kotlinMoves(state: GameState): List<Move> =
        state.getPiecesForPlayer(state.currentPlayer).flatMap { piece ->
            checkDetector.getLegalMoves(piece, state).map { to ->
                Move(from = piece.position, to = to, movedPiece = piece)
            }
        }

    /** 엔진이 보는 합법 수. 한 수 쉼(출발 == 도착)도 들어 있습니다. */
    private fun engineMoves(state: GameState): List<Pair<Position, Position>> {
        val converter = NotationConverter()
        return engine.legalMoves(state).map { uci ->
            val (from, to) = converter.splitUciMove(uci)
            converter.uciToPosition(from) to converter.uciToPosition(to)
        }
    }

    private fun assertSameMoves(state: GameState, where: String) {
        val fromKotlin = kotlinMoves(state).map { it.from to it.to }.toSet()
        // 한 수 쉼은 따로 검사하므로 여기서는 뺍니다.
        val fromEngine = engineMoves(state).filter { it.first != it.second }.toSet()

        if (fromKotlin == fromEngine) return

        // 어긋나면 어느 쪽이 뭘 더/덜 봤는지 바로 알 수 있게 찍어 줍니다.
        val onlyKotlin = (fromKotlin - fromEngine).sortedBy { it.toString() }
        val onlyEngine = (fromEngine - fromKotlin).sortedBy { it.toString() }
        throw AssertionError(
            """
            합법 수가 다릅니다 ($where)
              차례: ${state.currentPlayer}
              코틀린에만: $onlyKotlin
              엔진에만:   $onlyEngine
              판: ${FenConverter().toFen(state)}
            """.trimIndent()
        )
    }
}
