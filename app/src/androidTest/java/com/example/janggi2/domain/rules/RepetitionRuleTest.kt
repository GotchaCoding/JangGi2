package com.example.janggi2.domain.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.janggi2.data.ai.FairyStockfishEngine
import com.example.janggi2.data.ai.FenConverter
import com.example.janggi2.data.ai.NotationConverter
import com.example.janggi2.data.ai.UciProtocol
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 되풀이하는 수가 실제로 막히는지 확인합니다.
 *
 * 코틀린 규칙에는 이 개념이 없어서 엔진에 맡긴 유일한 판정입니다
 * (`janggimodern` 변형의 `perpetualCheckIllegal`·`moveRepetitionIllegal`).
 * 판 하나로는 알 수 없고 수순을 재생해야 하므로 네이티브가 필요합니다.
 */
@RunWith(AndroidJUnit4::class)
class RepetitionRuleTest {

    private lateinit var engine: FairyStockfishEngine
    private lateinit var rules: GameRules

    @Before
    fun setUp() = runBlocking {
        val notationConverter = NotationConverter()
        engine = FairyStockfishEngine(
            notationConverter,
            UciProtocol(FenConverter(), notationConverter)
        )
        engine.initialize()
        rules = GameRules(engine)
    }

    @After
    fun tearDown() {
        if (engine.isReady()) engine.destroy()
    }

    /** 초 궁 d2↔e2, 한 차 d6↔e6. 차는 옮길 때마다 장군을 부릅니다. */
    private val cycle = listOf(
        Move(Position(4, 1), Position(3, 1)),  // 초 궁 e2 - d2 (장군 피함)
        Move(Position(4, 5), Position(3, 5)),  // 한 차 e6 - d6 (다시 장군)
        Move(Position(3, 1), Position(4, 1)),  // 초 궁 d2 - e2
        Move(Position(3, 5), Position(4, 5))   // 한 차 d6 - e6 (다시 장군)
    )

    /**
     * 같은 국면을 되풀이하는 수는 언젠가 막힙니다 - 지는 게 아니라 못 두는 것입니다.
     *
     * 궁 e2, 궁 e9, 차 e6 만 있는 판에서 양쪽이 제자리를 오갑니다.
     */
    @Test
    fun `a repeating move eventually becomes unplayable`() {
        var state = perpetualCheckOpening()
        var blockedAt = -1

        for (ply in 0 until cycle.size * 6) {
            val move = cycle[ply % cycle.size]

            if (rules.wouldRepeat(move, state)) {
                blockedAt = ply
                break
            }

            val next = rules.applyMoveWithRules(move, state)
            assertNotNull("합법이어야 할 수가 거부됐습니다 (ply=$ply)", next)
            state = next!!
        }

        assertTrue("되풀이하는데도 끝까지 막히지 않았습니다", blockedAt >= 0)
        // 막힐 뿐 대국은 계속됩니다.
        assertFalse("반복은 승부를 가르지 않아야 합니다", state.isGameOver())
        assertNotEquals(GameStatus.FOUL_LOSS, state.status)
    }

    @Test
    fun `blocking a repetition never leaves the player stuck`() {
        // 막는 게 규칙이 되려면 둘 수 있는 다른 수가 남아야 합니다. 전부 막히면
        // 대국자가 아무것도 못 하게 됩니다.
        var state = perpetualCheckOpening()

        for (ply in 0 until cycle.size * 6) {
            val move = cycle[ply % cycle.size]

            if (rules.wouldRepeat(move, state)) {
                val playable = state.getPiecesForPlayer(state.currentPlayer)
                    .flatMap { piece ->
                        rules.getLegalMoves(piece.position, state)
                            .map { Move(piece.position, it, movedPiece = piece) }
                    }
                    .filterNot { rules.wouldRepeat(it, state) }

                assertTrue("한 수가 막혔는데 둘 수 있는 다른 수가 없습니다", playable.isNotEmpty())
                return
            }

            state = rules.applyMoveWithRules(move, state) ?: break
        }

        fail("되풀이하는데도 끝까지 막히지 않았습니다")
    }

    @Test
    fun `nothing is ever blocked without a judge`() {
        // 위 결과가 엔진에서 나온 것임을 못 박습니다 - 코틀린 규칙만으로는 안 막힙니다.
        val plain = GameRules()
        var state = perpetualCheckOpening()

        for (ply in 0 until cycle.size * 6) {
            val move = cycle[ply % cycle.size]
            assertFalse("판정자 없이 막혔습니다 (ply=$ply)", plain.wouldRepeat(move, state))
            state = plain.applyMoveWithRules(move, state) ?: break
        }

        assertFalse("판정자 없이 끝났다면 이 테스트의 전제가 틀린 것입니다", state.isGameOver())
    }

    /** 초 궁 e2, 한 궁 e9, 한 차 e6. 초가 둘 차례이며 장군을 맞은 상태입니다. */
    private fun perpetualCheckOpening(): GameState {
        val board = mapOf(
            Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
            Position(4, 8) to Piece.General(Player.HAN, Position(4, 8)),
            Position(4, 5) to Piece.Chariot(Player.HAN, Position(4, 5))
        )
        return GameState(
            board = board,
            currentPlayer = Player.CHO,
            // 표준 배치가 아니므로 엔진이 여기서부터 수순을 재생하도록 알려 줍니다.
            startBoard = board
        )
    }
}
