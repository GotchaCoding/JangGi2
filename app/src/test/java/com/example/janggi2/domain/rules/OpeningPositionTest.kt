package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 초기 배치에서 규칙 전체가 함께 맞물리는지 확인합니다.
 * 개별 기물 규칙은 [PieceMovementTest] 가 봅니다.
 */
class OpeningPositionTest {

    private val rules = GameRules()
    private val detector = CheckDetector()

    @Test
    fun `cho moves first`() {
        assertEquals(Player.CHO, initialGameState().currentPlayer)
    }

    @Test
    fun `cho has exactly 31 legal moves at the start`() {
        val state = initialGameState()
        val total = state.getPiecesForPlayer(Player.CHO)
            .sumOf { detector.getLegalMoves(it, state).size }

        assertEquals(31, total)
    }

    @Test
    fun `cannons cannot move at the start because nothing can be jumped`() {
        // 장기의 포는 반드시 기물 하나를 넘어야 하고 포는 넘지 못합니다.
        // 초기 배치에서는 넘을 대상이 없어 첫 수로 포를 움직일 수 없습니다.
        val state = initialGameState()
        val cannons = state.getPiecesForPlayer(Player.CHO).filterIsInstance<Piece.Cannon>()

        assertEquals(2, cannons.size)
        cannons.forEach { assertTrue(detector.getLegalMoves(it, state).isEmpty()) }
    }

    @Test
    fun `general in the palace centre has six moves, its guards blocking two diagonals`() {
        val state = initialGameState()
        val general = state.getGeneral(Player.CHO)!!

        assertEquals(6, detector.getLegalMoves(general, state).size)
    }

    @Test
    fun `a soldier may open by stepping forward or sideways`() {
        val state = initialGameState()
        val soldier = state.getPieceAt(Position(2, 3))!!
        val moves = detector.getLegalMoves(soldier, state)

        assertTrue(moves.contains(Position(2, 4)))
        assertTrue(moves.contains(Position(1, 3)))
        assertTrue(moves.contains(Position(3, 3)))
    }

    @Test
    fun `playing a move hands the turn to the opponent`() {
        val state = initialGameState()
        val soldier = state.getPieceAt(Position(2, 3))!!
        val after = rules.applyMoveWithRules(
            Move(Position(2, 3), Position(2, 4), movedPiece = soldier), state
        )

        assertNotNull(after)
        assertEquals(Player.HAN, after!!.currentPlayer)
        assertEquals(1, after.moveHistory.size)
        assertEquals(GameStatus.ONGOING, after.status)
    }

    @Test
    fun `a move by the player who is not to move is rejected`() {
        val state = initialGameState()
        val hanSoldier = state.getPieceAt(Position(2, 6))!!

        assertNull(
            rules.applyMoveWithRules(
                Move(Position(2, 6), Position(2, 5), movedPiece = hanSoldier), state
            )
        )
    }

    @Test
    fun `losing the general ends the game`() {
        // 합법 수 필터가 자기 궁을 잡히게 두지 않으므로 정상 대국에서는 나오지 않지만,
        // 사진에서 불러온 판처럼 궁이 없는 상태를 방어합니다.
        val state = initialGameState().let { s ->
            s.copy(board = s.board.filterValues { it !is Piece.General || it.player != Player.HAN })
        }

        val evaluated = rules.evaluateGameStatus(state)

        assertEquals(GameStatus.CHECKMATE, evaluated.status)
        assertEquals(Player.CHO, evaluated.winner)
    }
}
