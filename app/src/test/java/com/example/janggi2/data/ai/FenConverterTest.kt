package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Test

class FenConverterTest {

    private val converter = FenConverter()

    @Test
    fun `initial position maps to the app's own layout, not the engine's startFen`() {
        // 엔진의 janggi startFen 은 rnba1abnr (마-상) 이지만 이 앱은 상-마로 놓습니다.
        // 마·상 배치는 대국자가 고르는 것이라 둘 다 정당하며, 바로 이 차이 때문에
        // startpos 를 쓸 수 없고 FEN 을 보내야 합니다. 이 단언이 그 회귀 방지선입니다.
        val expected = "rbna1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RBNA1ABNR w - - 0 1"

        assertEquals(expected, converter.toFen(initialGameState()))
    }

    @Test
    fun `side to move is b when HAN is to play`() {
        val state = initialGameState().copy(currentPlayer = Player.HAN)

        assertEquals("b", converter.toFen(state).split(" ")[1])
    }

    @Test
    fun `empty board is nine empty ranks`() {
        val state = GameState(board = emptyMap())

        assertEquals("9/9/9/9/9/9/9/9/9/9 w - - 0 1", converter.toFen(state))
    }

    @Test
    fun `uppercase is CHO and lowercase is HAN`() {
        val state = GameState(
            board = mapOf(
                Position(0, 0) to Piece.Chariot(Player.CHO, Position(0, 0)),
                Position(8, 9) to Piece.Chariot(Player.HAN, Position(8, 9))
            )
        )

        // FEN 첫 줄이 앱의 row 9, 마지막 줄이 row 0
        assertEquals("8r/9/9/9/9/9/9/9/9/R8 w - - 0 1", converter.toFen(state))
    }

    @Test
    fun `every piece type has its own letter`() {
        val state = GameState(
            board = mapOf(
                Position(0, 0) to Piece.General(Player.CHO, Position(0, 0)),
                Position(1, 0) to Piece.Guard(Player.CHO, Position(1, 0)),
                Position(2, 0) to Piece.Horse(Player.CHO, Position(2, 0)),
                Position(3, 0) to Piece.Elephant(Player.CHO, Position(3, 0)),
                Position(4, 0) to Piece.Chariot(Player.CHO, Position(4, 0)),
                Position(5, 0) to Piece.Cannon(Player.CHO, Position(5, 0)),
                Position(6, 0) to Piece.Soldier(Player.CHO, Position(6, 0))
            )
        )

        assertEquals("KANBRCP2", converter.toFen(state).split(" ")[0].split("/").last())
    }

    @Test
    fun `full move number advances every two plies`() {
        val twoPlies = initialGameState().let { state ->
            val move = com.example.janggi2.domain.model.Move(Position(0, 3), Position(0, 4))
            state.copy(moveHistory = listOf(move, move))
        }

        assertEquals("2", converter.toFen(twoPlies).split(" ").last())
    }
}
