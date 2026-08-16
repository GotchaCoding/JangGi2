package com.example.janggi2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 사진 불러오기로 시작한 대국(표준 배치가 아닌 [GameState.startBoard])이 복기·되돌리기·
 * AI 리뷰가 쓰는 [GameState.reconstructStateAtPosition] 에서도 제대로 재생되는지 확인합니다.
 * 예전에는 여기서 항상 [initialGameState] 로 되돌아가는 버그가 있었습니다.
 */
class GameStateReplayTest {

    private val importedBoard = mapOf(
        Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
        Position(4, 8) to Piece.General(Player.HAN, Position(4, 8)),
        Position(0, 3) to Piece.Soldier(Player.CHO, Position(0, 3))
    )

    private fun importedGame(): GameState =
        GameState(board = importedBoard, currentPlayer = Player.CHO, startBoard = importedBoard)

    @Test
    fun `사진으로 불러온 판을 국면 0으로 되감으면 표준 배치가 아니라 그 판이다`() {
        val afterMove = importedGame().applyMove(Move(Position(0, 3), Position(0, 4)))

        val reconstructed = afterMove.reconstructStateAtPosition(0)

        assertEquals(importedBoard, reconstructed.board)
    }

    @Test
    fun `사진으로 불러온 판은 수를 재생해도 표준 배치의 기물이 섞이지 않는다`() {
        val afterMove = importedGame().applyMove(Move(Position(0, 3), Position(0, 4)))

        val reconstructed = afterMove.reconstructStateAtPosition(1)

        // 표준 배치라면 이 자리(0,0)에 車 가 있어야 하는데, 불러온 판에는 없다.
        assertEquals(null, reconstructed.getPieceAt(Position(0, 0)))
    }

    @Test
    fun `복기로 들어가도 startBoard 표시를 잃지 않는다`() {
        val replaying = importedGame().enterReplayMode()

        assertEquals(importedBoard, replaying.startBoard)
    }

    @Test
    fun `수 기록을 눌러 임의 위치로 건너뛸 수 있다`() {
        val choMove = Move(Position(0, 3), Position(0, 4))
        val hanMove = Move(Position(0, 6), Position(0, 5))
        val played = initialGameState().applyMove(choMove).applyMove(hanMove)
        val replaying = played.enterReplayMode()

        val atMoveOne = replaying.replayTo(1)

        assertEquals(1, atMoveOne.replayPosition)
        // 첫 수만 반영됐어야 하므로 초 졸은 옮겨졌고 한 졸은 아직 원래 자리.
        assertEquals(null, atMoveOne.getPieceAt(Position(0, 3)))
        assertEquals(Player.CHO, atMoveOne.getPieceAt(Position(0, 4))?.player)
        assertEquals(Player.HAN, atMoveOne.getPieceAt(Position(0, 6))?.player)
    }

    @Test
    fun `범위를 벗어난 위치는 0과 수 개수 사이로 눌러 담는다`() {
        val played = initialGameState()
            .applyMove(Move(Position(0, 3), Position(0, 4)))
            .applyMove(Move(Position(0, 6), Position(0, 5)))
        val replaying = played.enterReplayMode()

        assertEquals(0, replaying.replayTo(-5).replayPosition)
        assertEquals(2, replaying.replayTo(99).replayPosition)
    }

    @Test
    fun `복기 모드가 아니면 replayTo 는 아무것도 하지 않는다`() {
        val played = initialGameState().applyMove(Move(Position(0, 3), Position(0, 4)))

        val result = played.replayTo(0)

        assertEquals(played, result)
    }
}
