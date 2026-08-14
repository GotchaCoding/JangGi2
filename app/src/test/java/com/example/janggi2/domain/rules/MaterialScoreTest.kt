package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기물 점수(점수제) 검증.
 *
 * 값의 출처는 엔진입니다 — `fairystockfish/src/position.h` 의
 * `Position::material_counting_result()`, `JANGGI_MATERIAL` 분기.
 */
class MaterialScoreTest {

    private fun stateOf(vararg pieces: Piece) =
        GameState(board = pieces.associateBy { it.position })

    @Test
    fun `opening reads 72 for cho and 73_5 for han`() {
        // 차13×2 + 포7×2 + 마5×2 + 상3×2 + 사3×2 + 졸2×5 = 72, 궁은 0점.
        // 한은 초가 선수인 대가로 덤 1.5를 받습니다.
        val board = MaterialScore.of(initialGameState())

        assertEquals(72.0, board.choScore, 0.0)
        assertEquals(73.5, board.hanScore, 0.0)
    }

    @Test
    fun `each piece is worth what the engine counts`() {
        // 엔진에는 이것 말고 탐색용 평가값(RookValueMg 1276 등)이 따로 있습니다.
        // 점수제에는 그쪽을 쓰면 안 되므로 여기서 못 박습니다.
        val pos = Position(0, 0)
        assertEquals(13, MaterialScore.valueOf(Piece.Chariot(Player.CHO, pos)))
        assertEquals(7, MaterialScore.valueOf(Piece.Cannon(Player.CHO, pos)))
        assertEquals(5, MaterialScore.valueOf(Piece.Horse(Player.CHO, pos)))
        assertEquals(3, MaterialScore.valueOf(Piece.Elephant(Player.CHO, pos)))
        assertEquals(3, MaterialScore.valueOf(Piece.Guard(Player.CHO, pos)))
        assertEquals(2, MaterialScore.valueOf(Piece.Soldier(Player.CHO, pos)))
    }

    @Test
    fun `the general is worth nothing`() {
        val state = stateOf(
            Piece.General(Player.CHO, Position(4, 1)),
            Piece.General(Player.HAN, Position(4, 8))
        )
        val board = MaterialScore.of(state)

        assertEquals(0.0, board.choScore, 0.0)
        assertEquals(1.5, board.hanScore, 0.0)  // 덤만 남음
    }

    @Test
    fun `the handicap goes only to han`() {
        val board = MaterialScore.of(GameState(board = emptyMap()))

        assertEquals(0.0, board.choScore, 0.0)
        assertEquals(1.5, board.hanScore, 0.0)
        assertEquals(Player.HAN, board.winner)
    }

    @Test
    fun `losing a chariot costs thirteen`() {
        val state = initialGameState().let { s ->
            s.copy(board = s.board - Position(0, 9))  // 한의 왼쪽 차
        }
        val board = MaterialScore.of(state)

        assertEquals(72.0, board.choScore, 0.0)
        assertEquals(73.5 - 13, board.hanScore, 0.0)
        assertEquals(1, board.choCaptured.size)
        assertTrue(board.choCaptured.single() is Piece.Chariot)
    }

    @Test
    fun `captured pieces come from the board, not the move history`() {
        // 사진에서 불러온 판은 수 기록이 비어 있습니다. 수 기록으로 셌다면
        // 잡은 기물이 하나도 없다고 나왔을 겁니다.
        val state = initialGameState().let { s ->
            s.copy(
                board = s.board - Position(0, 9) - Position(8, 9),
                moveHistory = emptyList()
            )
        }
        val board = MaterialScore.of(state)

        assertEquals(2, board.choCaptured.size)
        assertTrue(board.choCaptured.all { it is Piece.Chariot })
        assertEquals(73.5 - 26, board.hanScore, 0.0)
    }

    @Test
    fun `a board with more pieces than the opening never reports negative captures`() {
        // 사진 인식은 부분·과다 인식을 허용하므로 차가 셋인 판도 들어올 수 있습니다.
        val state = stateOf(
            Piece.Chariot(Player.HAN, Position(0, 9)),
            Piece.Chariot(Player.HAN, Position(8, 9)),
            Piece.Chariot(Player.HAN, Position(4, 9))
        )
        val board = MaterialScore.of(state)

        assertTrue(board.choCaptured.none { it is Piece.Chariot })
        assertEquals(39 + 1.5, board.hanScore, 0.0)  // 점수 자체는 언제나 정확
    }

    @Test
    fun `the 1_5 handicap agrees with the engine's minus one on every margin`() {
        // 엔진은 (초 - 한) - 1 > 0 일 때 초 승리로 봅니다. 기물 값이 모두 정수라
        // 덤 1.5 표기와 완전히 같은 판정이어야 합니다 — 초는 2점 이상 앞서야 이깁니다.
        for (margin in -2..3) {
            val choSoldiers = 10 + margin   // 졸 하나가 2점이므로 2점 단위로 벌어짐
            val hanSoldiers = 10
            val pieces = buildList {
                repeat(choSoldiers) { add(Piece.Soldier(Player.CHO, Position(it % 9, it / 9))) }
                repeat(hanSoldiers) { add(Piece.Soldier(Player.HAN, Position(it % 9, 5 + it / 9))) }
            }
            val state = GameState(board = pieces.associateBy { it.position })
            val d = 2 * margin

            val expected = if (d >= 2) Player.CHO else Player.HAN
            assertEquals("margin $d", expected, MaterialScore.leader(state))
        }
    }
}
