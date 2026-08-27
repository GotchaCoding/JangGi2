package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.MoveReview
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractBlunderPuzzlesUseCaseTest {

    private val choSoldierMove = Move(Position(0, 3), Position(0, 4))
    private val hanSoldierMove = Move(Position(0, 6), Position(0, 5))

    private val gameState = initialGameState()
        .applyMove(choSoldierMove)
        .applyMove(hanSoldierMove)

    private val useCase = ExtractBlunderPuzzlesUseCase()

    @Test
    fun `BLUNDER 등급의 수만 퍼즐로 뽑는다`() {
        val review = GameReview(
            listOf(
                MoveReview(0, choSoldierMove, Player.CHO, MoveQuality.BEST, 0, null),
                MoveReview(1, hanSoldierMove, Player.HAN, MoveQuality.BLUNDER, 400, null)
            )
        )

        val puzzles = useCase(gameState, review)

        assertEquals(1, puzzles.size)
        assertEquals(1, puzzles[0].moveIndex)
        assertEquals(Player.HAN, puzzles[0].player)
        assertEquals(hanSoldierMove, puzzles[0].blunderMove)
    }

    @Test
    fun `퍼즐 국면은 악수를 두기 직전 상태와 같다`() {
        val review = GameReview(
            listOf(
                MoveReview(0, choSoldierMove, Player.CHO, MoveQuality.BEST, 0, null),
                MoveReview(1, hanSoldierMove, Player.HAN, MoveQuality.BLUNDER, 400, null)
            )
        )

        val puzzle = useCase(gameState, review).single()
        val expected = gameState.reconstructStateAtPosition(1)

        assertEquals(expected.board, puzzle.position.board)
        assertEquals(Player.HAN, puzzle.position.currentPlayer)
        assertTrue(puzzle.position.moveHistory.isEmpty())
        assertTrue(puzzle.position.undoStack.isEmpty())
        assertTrue(puzzle.position.redoStack.isEmpty())
        assertEquals(false, puzzle.position.isReplayMode)
    }

    @Test
    fun `악수가 없으면 빈 목록을 낸다`() {
        val review = GameReview(
            listOf(
                MoveReview(0, choSoldierMove, Player.CHO, MoveQuality.BEST, 0, null),
                MoveReview(1, hanSoldierMove, Player.HAN, MoveQuality.GOOD, 20, null)
            )
        )

        assertTrue(useCase(gameState, review).isEmpty())
    }
}
