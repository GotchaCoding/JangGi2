package com.example.janggi2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 사진 불러오기 결과를 [GameState] 로 바꿀 때 초/한 배치가 뒤집힌 사진도
 * 엔진이 기대하는 방향(초가 낮은 row)으로 맞춰지는지 확인합니다.
 */
class ImportedBoardStateTest {

    private fun detected(piece: Piece, confidence: Float = 1f) =
        DetectedPiece(piece = piece, confidence = confidence)

    @Test
    fun `초가 이미 위쪽이면 좌표를 그대로 둔다`() {
        val choGeneral = Piece.General(Player.CHO, Position(4, 1))
        val hanGeneral = Piece.General(Player.HAN, Position(4, 8))
        val imported = ImportedBoardState(
            detectedPieces = mapOf(
                choGeneral.position to detected(choGeneral),
                hanGeneral.position to detected(hanGeneral)
            ),
            overallConfidence = 1f,
            gridDetected = true
        )

        val board = imported.toGameState().board

        assertEquals(choGeneral, board[Position(4, 1)])
        assertEquals(hanGeneral, board[Position(4, 8)])
    }

    @Test
    fun `초가 아래쪽 진영으로 찍혔으면 판을 180도 돌려서 초를 위로 맞춘다`() {
        val choSoldier = Piece.Soldier(Player.CHO, Position(0, 6))
        val hanSoldier = Piece.Soldier(Player.HAN, Position(0, 3))
        val imported = ImportedBoardState(
            detectedPieces = mapOf(
                choSoldier.position to detected(choSoldier),
                hanSoldier.position to detected(hanSoldier)
            ),
            overallConfidence = 1f,
            gridDetected = true
        )

        val board = imported.toGameState().board

        // 180도 회전: (col,row) -> (8-col, 9-row)
        val flippedChoPos = Position(8, 3)
        val flippedHanPos = Position(8, 6)
        assertEquals(Player.CHO, board[flippedChoPos]?.player)
        assertEquals(flippedChoPos, board[flippedChoPos]?.position)
        assertEquals(Player.HAN, board[flippedHanPos]?.player)
        assertEquals(flippedHanPos, board[flippedHanPos]?.position)
    }

    @Test
    fun `초가 아래쪽으로 뒤집혔을 때 졸이 앞으로(상대 쪽으로) 갈 수 있어야 한다`() {
        // 사진 속 초는 아래쪽 진영(row 6)에, 한은 위쪽 진영(row 3)에 있다.
        val choSoldier = Piece.Soldier(Player.CHO, Position(4, 6))
        val hanSoldier = Piece.Soldier(Player.HAN, Position(4, 3))
        val imported = ImportedBoardState(
            detectedPieces = mapOf(
                choSoldier.position to detected(choSoldier),
                hanSoldier.position to detected(hanSoldier)
            ),
            overallConfidence = 1f,
            gridDetected = true
        )

        val gameState = imported.toGameState()
        val normalizedChoSoldier = gameState.board.values.single { it.player == Player.CHO }
        val movement = com.example.janggi2.domain.rules.SoldierMovement()

        val moves = movement.getValidMoves(normalizedChoSoldier, gameState.board)

        // 정규화 후 초는 row 3(위쪽)에 있어야 하고, 엔진 규칙상 전진(row 증가,
        // 한의 진영 쪽)이 유효한 수여야 한다. 이 값이 반대(row 감소)로 나오면
        // 정규화가 실제로는 방향을 못 맞춘 것이다.
        assertEquals(3, normalizedChoSoldier.position.row)
        val forwardMove = Position(normalizedChoSoldier.position.col, normalizedChoSoldier.position.row + 1)
        assertEquals(true, moves.contains(forwardMove))
    }

    @Test
    fun `초가 아래쪽으로 찍혔으면 화면 표시용 viewpoint 도 초가 된다`() {
        val choSoldier = Piece.Soldier(Player.CHO, Position(4, 6))
        val hanSoldier = Piece.Soldier(Player.HAN, Position(4, 3))
        val imported = ImportedBoardState(
            detectedPieces = mapOf(
                choSoldier.position to detected(choSoldier),
                hanSoldier.position to detected(hanSoldier)
            ),
            overallConfidence = 1f,
            gridDetected = true
        )

        assertEquals(Player.CHO, imported.detectedBottomPlayer())
        assertEquals(Player.CHO, imported.toImportedGameResult().viewpoint)
    }

    @Test
    fun `한이 아래쪽으로 찍혔으면 화면 표시용 viewpoint 는 한이 된다`() {
        val choSoldier = Piece.Soldier(Player.CHO, Position(4, 3))
        val hanSoldier = Piece.Soldier(Player.HAN, Position(4, 6))
        val imported = ImportedBoardState(
            detectedPieces = mapOf(
                choSoldier.position to detected(choSoldier),
                hanSoldier.position to detected(hanSoldier)
            ),
            overallConfidence = 1f,
            gridDetected = true
        )

        assertEquals(Player.HAN, imported.detectedBottomPlayer())
        assertEquals(Player.HAN, imported.toImportedGameResult().viewpoint)
    }
}
