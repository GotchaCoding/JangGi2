package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationConverterTest {

    private val converter = NotationConverter()

    @Test
    fun `ranks are one-based so row 0 is rank 1`() {
        // 엔진의 UCI::square 는 CurrentProtocol 이 UCI_GENERAL 일 때 랭크를 1부터 셉니다.
        // 이 빌드는 UCI::loop 를 돌리지 않아 그 값이 그대로 유지됩니다.
        assertEquals("a1", converter.positionToUci(Position(0, 0)))
        assertEquals("e2", converter.positionToUci(Position(4, 1)))
        assertEquals("e9", converter.positionToUci(Position(4, 8)))
    }

    @Test
    fun `rank 10 is three characters`() {
        assertEquals("a10", converter.positionToUci(Position(0, 9)))
        assertEquals("i10", converter.positionToUci(Position(8, 9)))
    }

    @Test
    fun `every square round-trips`() {
        for (col in 0..8) {
            for (row in 0..9) {
                val position = Position(col, row)
                val uci = converter.positionToUci(position)
                assertEquals(position, converter.uciToPosition(uci))
            }
        }
    }

    @Test
    fun `moves split correctly whichever end uses rank 10`() {
        assertEquals("a1" to "b2", converter.splitUciMove("a1b2"))
        assertEquals("a1" to "b10", converter.splitUciMove("a1b10"))
        assertEquals("a10" to "b2", converter.splitUciMove("a10b2"))
        assertEquals("a10" to "b10", converter.splitUciMove("a10b10"))
    }

    @Test
    fun `a pass is encoded as a move to the same square`() {
        val move = converter.uciToMove("e2e2", initialGameState())

        assertEquals(move.from, move.to)
    }

    @Test
    fun `move notation round-trips through a Move`() {
        val state = initialGameState()
        val move = converter.uciToMove("a10b10", state)

        assertEquals(Position(0, 9), move.from)
        assertEquals(Position(1, 9), move.to)
        assertEquals("a10b10", converter.moveToUci(move))
    }

    @Test
    fun `malformed notation is rejected`() {
        assertFalse(converter.isValidUci("a0"))   // rank 0 does not exist
        assertFalse(converter.isValidUci("j1"))   // file beyond i
        assertFalse(converter.isValidUci("a11"))
        assertFalse(converter.isValidUciMove("a1b"))
        assertFalse(converter.isValidUciMove("a1b2c3"))
        assertTrue(converter.isValidUciMove("a10b10"))
    }
}
