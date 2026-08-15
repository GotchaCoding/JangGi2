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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 장군 반복·수 반복이 실제로 승부를 가르는지 확인합니다.
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

    /**
     * 한이 차로 장군을 계속 부르고 초의 궁은 궁성 안을 오갑니다.
     *
     * 궁 e2, 궁 e9, 차 e6 만 있는 판입니다. 차가 d6↔e6 을 오가며 매번 장군을 부르고
     * 초의 궁은 d2↔e2 로 피합니다. 장군을 반복해 부른 한이 반칙패해야 합니다.
     */
    @Test
    fun `perpetual check loses the game for the side giving it`() {
        var state = perpetualCheckOpening()
        val cycle = listOf(
            Move(Position(4, 1), Position(3, 1)),  // 초 궁 e2 - d2 (장군 피함)
            Move(Position(4, 5), Position(3, 5)),  // 한 차 e6 - d6 (다시 장군)
            Move(Position(3, 1), Position(4, 1)),  // 초 궁 d2 - e2
            Move(Position(3, 5), Position(4, 5))   // 한 차 d6 - e6 (다시 장군)
        )

        // 넉넉히 돌려 봅니다. 규칙이 살아 있으면 이 안에서 끝나야 합니다.
        for (ply in 0 until cycle.size * 6) {
            val next = rules.applyMoveWithRules(cycle[ply % cycle.size], state)
            assertNotNull("합법이어야 할 수가 거부됐습니다 (ply=$ply)", next)
            state = next!!

            if (state.isGameOver()) break
        }

        assertTrue("반복인데도 대국이 끝나지 않았습니다", state.isGameOver())
        assertEquals(GameStatus.FOUL_LOSS, state.status)
        assertEquals("장군을 반복한 한이 져야 합니다", Player.CHO, state.winner)
    }

    @Test
    fun `the same position without a judge just keeps going`() {
        // 위 결과가 엔진에서 나온 것임을 못 박습니다 - 코틀린 규칙만으로는 안 끝납니다.
        val plain = GameRules()
        var state = perpetualCheckOpening()
        val cycle = listOf(
            Move(Position(4, 1), Position(3, 1)),
            Move(Position(4, 5), Position(3, 5)),
            Move(Position(3, 1), Position(4, 1)),
            Move(Position(3, 5), Position(4, 5))
        )

        for (ply in 0 until cycle.size * 6) {
            state = plain.applyMoveWithRules(cycle[ply % cycle.size], state) ?: break
        }

        assertTrue("판정자 없이도 끝났다면 이 테스트의 전제가 틀린 것입니다", !state.isGameOver())
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
