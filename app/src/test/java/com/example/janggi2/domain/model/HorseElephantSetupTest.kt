package com.example.janggi2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대국 전에 고르는 마·상 배치.
 */
class HorseElephantSetupTest {

    /** [row] 의 마·상 네 자리를 열 번호 순으로 읽습니다. 'N' 이 마, 'B' 가 상입니다. */
    private fun backRankOf(state: GameState, row: Int): String =
        HorseElephantSetup.COLUMNS.joinToString("") { col ->
            when (state.getPieceAt(Position(col, row))) {
                is Piece.Horse -> "N"
                is Piece.Elephant -> "B"
                else -> "?"
            }
        }

    @Test
    fun `every setup places two horses and two elephants`() {
        // 배치가 늘거나 오타가 나면 여기서 걸립니다.
        for (setup in HorseElephantSetup.entries) {
            for (player in Player.entries) {
                val slots = setup.backRank(player)
                assertEquals("$setup/$player", 4, slots.size)
                assertEquals("$setup/$player 마 둘", 2, slots.values.count { it })
                assertEquals("$setup/$player 상 둘", 2, slots.values.count { !it })
                assertEquals("$setup/$player 자리", HorseElephantSetup.COLUMNS.toSet(), slots.keys)
            }
        }
    }

    @Test
    fun `the default keeps the board the app has always started from`() {
        // 이 판으로 실기기 검증과 기존 테스트를 다 했으므로 바뀌면 안 됩니다.
        val state = initialGameState()

        assertEquals("BNBN", backRankOf(state, 0))  // 초
        assertEquals("BNBN", backRankOf(state, 9))  // 한
    }

    @Test
    fun `a setup is read from each player's own left`() {
        // 마주 보고 앉으므로 위쪽인 초의 왼쪽은 화면의 오른쪽입니다. 같은 이름을 골라도
        // 두 진영의 열 배치는 서로 뒤집혀 나와야 합니다.
        val state = initialGameState(
            choSetup = HorseElephantSetup.HORSE_FIRST_OUTER,   // 마상마상
            hanSetup = HorseElephantSetup.HORSE_FIRST_OUTER
        )

        assertEquals("BNBN", backRankOf(state, 0))  // 초: 7열부터 읽어야 마상마상
        assertEquals("NBNB", backRankOf(state, 9))  // 한: 1열부터 읽어 마상마상
    }

    @Test
    fun `the palindromic setups look the same from both sides`() {
        // 마상상마·상마마상은 뒤집어도 같은 순서라 두 진영의 열 배치가 일치합니다.
        for (setup in listOf(HorseElephantSetup.HORSES_OUTSIDE, HorseElephantSetup.HORSES_INSIDE)) {
            val state = initialGameState(choSetup = setup, hanSetup = setup)
            assertEquals(setup.displayName, backRankOf(state, 0), backRankOf(state, 9))
        }
    }

    @Test
    fun `horses sit on the outside for the yanggwima setup`() {
        // 마상상마 - 자기 왼쪽부터 마, 상, 상, 마.
        val state = initialGameState(hanSetup = HorseElephantSetup.HORSES_OUTSIDE)

        assertEquals("NBBN", backRankOf(state, 9))
    }

    @Test
    fun `horses sit on the inside for the wonangma setup`() {
        // 상마마상 - 자기 왼쪽부터 상, 마, 마, 상.
        val state = initialGameState(hanSetup = HorseElephantSetup.HORSES_INSIDE)

        assertEquals("BNNB", backRankOf(state, 9))
    }

    @Test
    fun `changing the setup moves nothing but the horses and elephants`() {
        val classic = initialGameState()
        val changed = initialGameState(
            choSetup = HorseElephantSetup.HORSES_OUTSIDE,
            hanSetup = HorseElephantSetup.HORSES_INSIDE
        )

        assertEquals("기물 수", classic.board.size, changed.board.size)

        val movable = HorseElephantSetup.COLUMNS.flatMap { listOf(Position(it, 0), Position(it, 9)) }
        val untouched = classic.board.keys - movable.toSet()
        for (position in untouched) {
            assertEquals(
                "$position",
                classic.getPieceAt(position)!!::class,
                changed.getPieceAt(position)!!::class
            )
        }
        // 그리고 네 자리는 실제로 달라져야 합니다.
        assertTrue(backRankOf(classic, 9) != backRankOf(changed, 9))
    }
}
