package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 외통 없이 끝나는 경우의 승부 판정.
 */
class PointAdjudicationTest {

    private val rules = GameRules()

    /** 아무 수나 [count] 개 채운 기록. 200수 판정만 볼 때 씁니다. */
    private fun filler(count: Int): List<Move> =
        List(count) { Move(Position(0, 3), Position(0, 4)) }

    @Test
    fun `the game runs on until the move limit`() {
        val state = initialGameState().copy(moveHistory = filler(GameRules.MOVE_LIMIT - 1))

        val evaluated = rules.evaluateGameStatus(state)

        assertFalse(evaluated.isGameOver())
        assertEquals(GameStatus.ONGOING, evaluated.status)
    }

    @Test
    fun `reaching the move limit decides the game on points`() {
        val state = initialGameState().copy(moveHistory = filler(GameRules.MOVE_LIMIT))

        val evaluated = rules.evaluateGameStatus(state)

        assertEquals(GameStatus.POINT_WIN, evaluated.status)
        assertTrue(evaluated.isGameOver())
        // 초기 배치 그대로면 초 72 : 한 73.5 이므로 덤을 받은 한이 이깁니다.
        assertEquals(Player.HAN, evaluated.winner)
    }

    @Test
    fun `the side ahead on material wins at the move limit`() {
        val state = initialGameState()
            .let { it.copy(board = it.board - Position(0, 9)) }  // 한의 차 하나가 빠짐
            .copy(moveHistory = filler(GameRules.MOVE_LIMIT))

        val evaluated = rules.evaluateGameStatus(state)

        assertEquals(GameStatus.POINT_WIN, evaluated.status)
        assertEquals(Player.CHO, evaluated.winner)
    }

    @Test
    fun `facing generals no longer end the game`() {
        // 빅장 규칙을 없앴습니다. 예전에는 이 국면이 그 자리에서 끝났습니다.
        val state = GameState(
            board = mapOf(
                Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
                Position(4, 8) to Piece.General(Player.HAN, Position(4, 8)),
                // 양쪽에 둘 수를 남겨 무수 판정으로 새지 않게 합니다
                Position(0, 3) to Piece.Soldier(Player.CHO, Position(0, 3)),
                Position(8, 6) to Piece.Soldier(Player.HAN, Position(8, 6))
            ),
            currentPlayer = Player.HAN
        )

        val evaluated = rules.evaluateGameStatus(state)

        assertFalse(evaluated.isGameOver())
    }

    @Test
    fun `checkmate takes precedence over the move limit`() {
        // 200수에 도달했더라도 외통이면 외통이 이깁니다.
        val state = GameState(
            board = mapOf(
                Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
                Position(4, 8) to Piece.General(Player.HAN, Position(4, 8)),
                Position(3, 0) to Piece.Chariot(Player.HAN, Position(3, 0)),
                Position(5, 0) to Piece.Chariot(Player.HAN, Position(5, 0)),
                Position(3, 2) to Piece.Chariot(Player.HAN, Position(3, 2)),
                Position(5, 2) to Piece.Chariot(Player.HAN, Position(5, 2)),
                Position(4, 2) to Piece.Chariot(Player.HAN, Position(4, 2)),
                Position(4, 0) to Piece.Chariot(Player.HAN, Position(4, 0))
            ),
            currentPlayer = Player.CHO,
            moveHistory = filler(GameRules.MOVE_LIMIT)
        )

        val evaluated = rules.evaluateGameStatus(state)

        assertEquals(GameStatus.CHECKMATE, evaluated.status)
        assertEquals(Player.HAN, evaluated.winner)
    }

    // ---------- 한 수 쉼 ----------

    @Test
    fun `a pass hands the turn over without touching the board`() {
        val state = initialGameState()

        val after = rules.applyPass(state)!!

        assertEquals(state.board, after.board)
        assertEquals(Player.HAN, after.currentPlayer)
        assertEquals(1, after.moveHistory.size)
        assertTrue(after.moveHistory.single().isPass())
        // 자기 궁을 잡은 것으로 기록되면 안 됩니다
        assertEquals(null, after.moveHistory.single().capturedPiece)
    }

    @Test
    fun `a pass does not change the score`() {
        val state = initialGameState()
        val before = MaterialScore.of(state)
        val after = MaterialScore.of(rules.applyPass(state)!!)

        assertEquals(before.choScore, after.choScore, 0.0)
        assertEquals(before.hanScore, after.hanScore, 0.0)
    }

    @Test
    fun `you cannot pass out of check`() {
        // 엔진도 한 수 쉼을 다른 수와 같이 합법성 검사에 걸어서, 판이 그대로면
        // 장군이 풀리지 않으므로 걸러냅니다.
        val state = GameState(
            board = mapOf(
                Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
                Position(4, 8) to Piece.General(Player.HAN, Position(4, 8)),
                Position(4, 4) to Piece.Chariot(Player.HAN, Position(4, 4))
            ),
            currentPlayer = Player.CHO
        )

        assertFalse(rules.canPass(state))
        assertEquals(null, rules.applyPass(state))
    }

    @Test
    fun `passing counts toward the move limit`() {
        val state = initialGameState().copy(moveHistory = filler(GameRules.MOVE_LIMIT - 1))

        val after = rules.applyPass(state)!!

        assertEquals(GameStatus.POINT_WIN, after.status)
    }

    @Test
    fun `a missing general still ends the game immediately`() {
        val state = initialGameState().let { s ->
            s.copy(board = s.board.filterValues { it !is Piece.General || it.player != Player.HAN })
        }

        val evaluated = rules.evaluateGameStatus(state)

        assertEquals(GameStatus.CHECKMATE, evaluated.status)
        assertEquals(Player.CHO, evaluated.winner)
    }
}
