package com.example.janggi2.data.imageprocessing

import com.example.janggi2.data.imageprocessing.BoardRecognitionService.DetectedPieceWithPosition
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [sanitizePieceCounts] 는 진영별 기물 최대 개수(왕1·사2·마2·상2·차2·포2·졸5)를 넘는
 * 검출 결과에서 신뢰도가 낮은 초과분부터 제거합니다. 기물은 잡히기만 하고 늘어나지
 * 않으므로 최대치 초과는 항상 오검출입니다.
 */
class PieceCountSanitizerTest {

    private fun detection(piece: Piece, confidence: Float) =
        DetectedPieceWithPosition(position = piece.position, piece = piece, confidence = confidence)

    @Test
    fun `최대 개수 이하면 그대로 둔다`() {
        val detected = listOf(
            detection(Piece.Soldier(Player.HAN, Position(0, 3)), 0.9f),
            detection(Piece.Soldier(Player.HAN, Position(2, 3)), 0.9f),
            detection(Piece.General(Player.CHO, Position(4, 1)), 0.9f)
        )

        val sanitized = sanitizePieceCounts(detected)

        assertEquals(detected.toSet(), sanitized.toSet())
    }

    @Test
    fun `졸이 6개면 신뢰도가 가장 낮은 1개를 제거해 5개로 만든다`() {
        val soldiers = (0..5).map { col ->
            detection(Piece.Soldier(Player.HAN, Position(col, 3)), confidence = 0.9f - col * 0.01f)
        }
        // soldiers[5] 가 신뢰도 최저(0.85f)

        val sanitized = sanitizePieceCounts(soldiers)

        assertEquals(5, sanitized.size)
        assertEquals(false, sanitized.contains(soldiers[5]))
    }

    @Test
    fun `왕이 2개면 신뢰도가 낮은 쪽을 제거해 1개로 만든다`() {
        val strongGeneral = detection(Piece.General(Player.CHO, Position(4, 1)), 0.95f)
        val weakGeneral = detection(Piece.General(Player.CHO, Position(3, 1)), 0.80f)

        val sanitized = sanitizePieceCounts(listOf(strongGeneral, weakGeneral))

        assertEquals(listOf(strongGeneral), sanitized)
    }

    @Test
    fun `양쪽 진영은 서로 독립적으로 개수를 센다`() {
        val choSoldiers = (0..4).map { col ->
            detection(Piece.Soldier(Player.CHO, Position(col, 6)), 0.9f)
        }
        val hanSoldiers = (0..4).map { col ->
            detection(Piece.Soldier(Player.HAN, Position(col, 3)), 0.9f)
        }

        val sanitized = sanitizePieceCounts(choSoldiers + hanSoldiers)

        assertEquals(10, sanitized.size)
    }
}
