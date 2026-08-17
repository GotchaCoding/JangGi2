package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferMoveFromBoardDiffUseCaseTest {

    @Test
    fun `잡지 않는 수를 되짚는다`() {
        val soldier = Piece.Soldier(Player.CHO, Position(0, 3))
        val before = mapOf(Position(0, 3) to soldier)
        val after = mapOf(Position(0, 4) to soldier.moveTo(Position(0, 4)))

        val move = inferMoveFromBoardDiff(before, after)

        assertEquals(Position(0, 3), move?.from)
        assertEquals(Position(0, 4), move?.to)
        assertNull(move?.capturedPiece)
        assertEquals(soldier, move?.movedPiece)
    }

    @Test
    fun `잡는 수는 잡힌 기물을 같이 되짚는다`() {
        val chariot = Piece.Chariot(Player.CHO, Position(0, 0))
        val soldier = Piece.Soldier(Player.HAN, Position(0, 3))
        val before = mapOf(
            Position(0, 0) to chariot,
            Position(0, 3) to soldier
        )
        val after = mapOf(
            Position(0, 3) to chariot.moveTo(Position(0, 3))
        )

        val move = inferMoveFromBoardDiff(before, after)

        assertEquals(Position(0, 0), move?.from)
        assertEquals(Position(0, 3), move?.to)
        assertEquals(soldier, move?.capturedPiece)
    }

    @Test
    fun `바뀐 칸이 없으면(한 수 쉼 등) 되짚지 못한다`() {
        val soldier = Piece.Soldier(Player.CHO, Position(0, 3))
        val board = mapOf(Position(0, 3) to soldier)

        val move = inferMoveFromBoardDiff(board, board)

        assertNull(move)
    }

    @Test
    fun `바뀐 칸이 두 개가 아니면(수를 놓쳤거나 여러 수가 겹친 경우) 건너뛴다`() {
        val before = mapOf(
            Position(0, 3) to Piece.Soldier(Player.CHO, Position(0, 3)),
            Position(2, 3) to Piece.Soldier(Player.CHO, Position(2, 3))
        )
        // 두 수가 한 프레임에 겹쳐 네 칸이 한꺼번에 바뀐 상황을 흉내냅니다.
        val after = mapOf(
            Position(0, 4) to Piece.Soldier(Player.CHO, Position(0, 4)),
            Position(2, 4) to Piece.Soldier(Player.CHO, Position(2, 4))
        )

        val move = inferMoveFromBoardDiff(before, after)

        assertNull(move)
    }

    @Test
    fun `도착 칸의 기물 종류가 출발 기물과 다르면(오인식) 되짚지 않는다`() {
        val before = mapOf(
            Position(0, 3) to Piece.Soldier(Player.CHO, Position(0, 3))
        )
        // 졸이 사라진 자리에 엉뚱하게 마가 나타난 것처럼 오인식된 상황.
        val after = mapOf(
            Position(0, 4) to Piece.Horse(Player.CHO, Position(0, 4))
        )

        val move = inferMoveFromBoardDiff(before, after)

        assertNull(move)
    }

    @Test
    fun `expectedMover 를 주면 상대 진영이 같은 프레임에서 움직이는 중이라도 무시하고 그 진영 수만 찾는다`() {
        val choSoldier = Piece.Soldier(Player.CHO, Position(0, 3))
        val hanSoldier = Piece.Soldier(Player.HAN, Position(0, 6))
        val before = mapOf(
            Position(0, 3) to choSoldier,
            Position(0, 6) to hanSoldier
        )
        // 초의 졸은 완전히 이동을 마쳤지만, 같은 프레임에서 한의 졸은 아직 움직이는
        // 중이라(원래 자리에서 사라졌지만 아직 어디에도 다시 나타나지 않은) 흐릿하게
        // 잡힌 상황을 흉내냅니다.
        val after = mapOf(
            Position(0, 4) to choSoldier.moveTo(Position(0, 4))
            // Position(0, 6) 은 비어 보이고, 한의 졸이 도착한 칸은 아직 안 보임
        )

        val move = inferMoveFromBoardDiff(before, after, expectedMover = Player.CHO)

        assertEquals(Position(0, 3), move?.from)
        assertEquals(Position(0, 4), move?.to)
        assertEquals(choSoldier, move?.movedPiece)
    }

    @Test
    fun `expectedMover 가 있으면 도착 칸 기물 종류가 달라 보여도(마상 오인식 등) 출발 기물 정체를 그대로 믿는다`() {
        val horse = Piece.Horse(Player.HAN, Position(2, 9))
        val before = mapOf(Position(2, 9) to horse)
        // 도착 칸의 기물이 인식기에는 상(象)으로 잘못 보였다고 흉내냅니다.
        val after = mapOf(Position(3, 7) to Piece.Elephant(Player.HAN, Position(3, 7)))

        val move = inferMoveFromBoardDiff(before, after, expectedMover = Player.HAN)

        assertEquals(Position(2, 9), move?.from)
        assertEquals(Position(3, 7), move?.to)
        // 도착 칸 인식(상)이 아니라 출발 칸에서 알던 정체(마)를 그대로 씁니다.
        assertEquals(horse, move?.movedPiece)
    }

    @Test
    fun `expectedMover 가 있어도 도착 칸의 진영(색) 자체가 다르면 되짚지 않는다`() {
        val horse = Piece.Horse(Player.HAN, Position(2, 9))
        val before = mapOf(Position(2, 9) to horse)
        // 도착 칸에 엉뚱하게 초의 기물이 인식된 상황 - 진짜 다른 사건이므로 걸러야 합니다.
        val after = mapOf(Position(3, 7) to Piece.Elephant(Player.CHO, Position(3, 7)))

        val move = inferMoveFromBoardDiff(before, after, expectedMover = Player.HAN)

        assertNull(move)
    }

    @Test
    fun `expectedMover 진영 소유 칸이 두 개가 아니면(아직 안 멈췄거나 오인식) 건너뛴다`() {
        val choSoldier = Piece.Soldier(Player.CHO, Position(0, 3))
        val before = mapOf(Position(0, 3) to choSoldier)
        // 출발 칸만 비었을 뿐 도착 칸이 아직 인식되지 않은(피스가 공중에 있는) 상황.
        val after = emptyMap<Position, Piece>()

        val move = inferMoveFromBoardDiff(before, after, expectedMover = Player.CHO)

        assertNull(move)
    }

    @Test
    fun `expectedMover 와 다른 진영만 바뀌었으면(아직 그 진영 차례가 안 됨) 건너뛴다`() {
        val hanSoldier = Piece.Soldier(Player.HAN, Position(0, 6))
        val before = mapOf(Position(0, 6) to hanSoldier)
        val after = mapOf(Position(0, 5) to hanSoldier.moveTo(Position(0, 5)))

        val move = inferMoveFromBoardDiff(before, after, expectedMover = Player.CHO)

        assertNull(move)
    }
}
