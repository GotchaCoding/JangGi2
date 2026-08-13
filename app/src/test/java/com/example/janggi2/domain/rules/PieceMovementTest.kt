package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 장기 이동 규칙 검증.
 *
 * 좌표는 (col, row) 이고 row 0 이 초의 뒷줄(화면 위), row 9 가 한의 뒷줄입니다.
 * 초의 궁성은 col 3-5 / row 0-2, 중앙은 (4,1) 입니다.
 */
class PieceMovementTest {

    private fun board(vararg pieces: Piece): Map<Position, Piece> =
        pieces.associateBy { it.position }

    // ---------- 왕 / 사 ----------

    @Test
    fun `general on the palace centre may use all four diagonals`() {
        val general = Piece.General(Player.CHO, Position(4, 1))
        val moves = GeneralMovement().getValidMoves(general, board(general))

        // 상하좌우 4 + 대각선 4
        assertEquals(8, moves.size)
        assertTrue(moves.contains(Position(3, 0)))
        assertTrue(moves.contains(Position(5, 2)))
    }

    @Test
    fun `general on a palace edge point has no diagonals`() {
        // (4,0) 은 궁성 안이지만 대각선이 그려져 있지 않은 변의 가운데 점입니다.
        val general = Piece.General(Player.CHO, Position(4, 0))
        val moves = GeneralMovement().getValidMoves(general, board(general))

        assertFalse(moves.contains(Position(3, 1)))
        assertFalse(moves.contains(Position(5, 1)))
        assertTrue(moves.contains(Position(4, 1)))
    }

    @Test
    fun `general cannot leave its own palace`() {
        val general = Piece.General(Player.CHO, Position(4, 2))
        val moves = GeneralMovement().getValidMoves(general, board(general))

        assertFalse(moves.contains(Position(4, 3)))
    }

    @Test
    fun `guard moves exactly like the general`() {
        val guard = Piece.Guard(Player.CHO, Position(3, 0))
        val moves = GuardMovement().getValidMoves(guard, board(guard))

        assertTrue(moves.contains(Position(4, 1)))  // 대각선으로 중앙
        assertTrue(moves.contains(Position(4, 0)))
        assertTrue(moves.contains(Position(3, 1)))
        assertEquals(3, moves.size)
    }

    // ---------- 마 ----------

    @Test
    fun `horse moves one straight then one diagonal`() {
        val horse = Piece.Horse(Player.CHO, Position(4, 4))
        val moves = HorseMovement().getValidMoves(horse, board(horse))

        assertEquals(8, moves.size)
        assertTrue(moves.contains(Position(3, 2)))
        assertTrue(moves.contains(Position(5, 2)))
        assertTrue(moves.contains(Position(6, 3)))
    }

    @Test
    fun `horse is blocked by a piece on the straight step`() {
        val horse = Piece.Horse(Player.CHO, Position(4, 4))
        val blocker = Piece.Soldier(Player.CHO, Position(4, 3))
        val moves = HorseMovement().getValidMoves(horse, board(horse, blocker))

        assertFalse(moves.contains(Position(3, 2)))
        assertFalse(moves.contains(Position(5, 2)))
        // 다른 방향은 영향 없음
        assertTrue(moves.contains(Position(3, 6)))
    }

    // ---------- 상 ----------

    @Test
    fun `elephant moves one straight then two diagonal`() {
        val elephant = Piece.Elephant(Player.CHO, Position(4, 4))
        val moves = ElephantMovement().getValidMoves(elephant, board(elephant))

        // (4,4) 기준 여덟 방향: 직선 한 칸 + 같은 쪽 대각선 두 칸
        assertTrue(moves.contains(Position(2, 1)))   // 위-왼쪽
        assertTrue(moves.contains(Position(6, 1)))   // 위-오른쪽
        assertTrue(moves.contains(Position(7, 2)))   // 오른쪽-위
        assertTrue(moves.contains(Position(1, 6)))   // 왼쪽-아래
        assertEquals(8, moves.size)
    }

    @Test
    fun `elephant is blocked on either intermediate point`() {
        val elephant = Piece.Elephant(Player.CHO, Position(4, 4))

        val nearBlock = Piece.Soldier(Player.CHO, Position(4, 3))
        assertFalse(
            ElephantMovement().getValidMoves(elephant, board(elephant, nearBlock))
                .contains(Position(2, 1))
        )

        val farBlock = Piece.Soldier(Player.CHO, Position(3, 2))
        assertFalse(
            ElephantMovement().getValidMoves(elephant, board(elephant, farBlock))
                .contains(Position(2, 1))
        )
    }

    // ---------- 차 ----------

    @Test
    fun `chariot slides until blocked and may capture`() {
        val chariot = Piece.Chariot(Player.CHO, Position(0, 4))
        val own = Piece.Soldier(Player.CHO, Position(0, 6))
        val enemy = Piece.Soldier(Player.HAN, Position(3, 4))
        val moves = ChariotMovement().getValidMoves(chariot, board(chariot, own, enemy))

        assertTrue(moves.contains(Position(0, 5)))
        assertFalse(moves.contains(Position(0, 6)))  // 자기 기물
        assertFalse(moves.contains(Position(0, 7)))  // 그 너머
        assertTrue(moves.contains(Position(3, 4)))   // 상대 기물은 잡음
        assertFalse(moves.contains(Position(4, 4)))
    }

    @Test
    fun `chariot slides through the palace centre to the far corner`() {
        val chariot = Piece.Chariot(Player.CHO, Position(3, 0))
        val moves = ChariotMovement().getValidMoves(chariot, board(chariot))

        assertTrue(moves.contains(Position(4, 1)))
        assertTrue(moves.contains(Position(5, 2)))
    }

    @Test
    fun `chariot cannot pass an occupied palace centre`() {
        val chariot = Piece.Chariot(Player.CHO, Position(3, 0))
        val blocker = Piece.Guard(Player.CHO, Position(4, 1))
        val moves = ChariotMovement().getValidMoves(chariot, board(chariot, blocker))

        assertFalse(moves.contains(Position(4, 1)))
        assertFalse(moves.contains(Position(5, 2)))
    }

    // ---------- 포 ----------

    @Test
    fun `cannon needs exactly one screen`() {
        val cannon = Piece.Cannon(Player.CHO, Position(0, 0))
        val screen = Piece.Soldier(Player.CHO, Position(0, 3))
        val moves = CannonMovement().getValidMoves(cannon, board(cannon, screen))

        assertFalse(moves.contains(Position(0, 1)))  // 넘기 전
        assertFalse(moves.contains(Position(0, 3)))  // 넘을 기물 자리
        assertTrue(moves.contains(Position(0, 4)))
        assertTrue(moves.contains(Position(0, 9)))
    }

    @Test
    fun `cannon cannot jump over another cannon`() {
        val cannon = Piece.Cannon(Player.CHO, Position(0, 0))
        val otherCannon = Piece.Cannon(Player.HAN, Position(0, 3))
        val soldier = Piece.Soldier(Player.CHO, Position(0, 5))
        val moves = CannonMovement().getValidMoves(cannon, board(cannon, otherCannon, soldier))

        // 첫 기물이 포라 그 방향은 완전히 막힙니다. 뒤의 졸을 넘을 수도 없습니다.
        assertTrue(moves.none { it.col == 0 })
    }

    @Test
    fun `cannon cannot capture a cannon`() {
        val cannon = Piece.Cannon(Player.CHO, Position(0, 0))
        val screen = Piece.Soldier(Player.CHO, Position(0, 3))
        val target = Piece.Cannon(Player.HAN, Position(0, 5))
        val moves = CannonMovement().getValidMoves(cannon, board(cannon, screen, target))

        assertTrue(moves.contains(Position(0, 4)))
        assertFalse(moves.contains(Position(0, 5)))
        assertFalse(moves.contains(Position(0, 6)))
    }

    @Test
    fun `cannon jumps the palace centre along the diagonal`() {
        val cannon = Piece.Cannon(Player.CHO, Position(3, 0))
        val screen = Piece.Guard(Player.CHO, Position(4, 1))
        val moves = CannonMovement().getValidMoves(cannon, board(cannon, screen))

        assertTrue(moves.contains(Position(5, 2)))
    }

    // ---------- 졸 ----------

    @Test
    fun `soldier moves forward and sideways but never backwards`() {
        val soldier = Piece.Soldier(Player.CHO, Position(4, 4))
        val moves = SoldierMovement().getValidMoves(soldier, board(soldier))

        assertTrue(moves.contains(Position(4, 5)))   // 앞 (초는 아래로)
        assertTrue(moves.contains(Position(3, 4)))   // 좌
        assertTrue(moves.contains(Position(5, 4)))   // 우
        assertFalse(moves.contains(Position(4, 3)))  // 뒤
        assertEquals(3, moves.size)
    }

    @Test
    fun `soldier may move sideways before reaching enemy territory`() {
        // 장기의 졸은 중국 장기와 달리 강을 건너기 전에도 옆으로 갈 수 있습니다.
        val soldier = Piece.Soldier(Player.CHO, Position(4, 3))
        val moves = SoldierMovement().getValidMoves(soldier, board(soldier))

        assertTrue(moves.contains(Position(3, 3)))
        assertTrue(moves.contains(Position(5, 3)))
    }

    @Test
    fun `han soldier moves up the board`() {
        val soldier = Piece.Soldier(Player.HAN, Position(4, 5))
        val moves = SoldierMovement().getValidMoves(soldier, board(soldier))

        assertTrue(moves.contains(Position(4, 4)))
        assertFalse(moves.contains(Position(4, 6)))
    }

    @Test
    fun `soldier uses the palace diagonal only going forward`() {
        // 한의 졸이 초 궁성 모서리에 있으면 대각선으로 중앙까지 갈 수 있습니다.
        val soldier = Piece.Soldier(Player.HAN, Position(3, 2))
        val moves = SoldierMovement().getValidMoves(soldier, board(soldier))

        assertTrue(moves.contains(Position(4, 1)))

        // 반대로 초의 졸은 같은 자리에서 그 대각선을 쓸 수 없습니다(뒤쪽이므로).
        val choSoldier = Piece.Soldier(Player.CHO, Position(3, 2))
        assertFalse(
            SoldierMovement().getValidMoves(choSoldier, board(choSoldier))
                .contains(Position(4, 1))
        )
    }

    @Test
    fun `pieces never capture their own side`() {
        val chariot = Piece.Chariot(Player.CHO, Position(0, 0))
        val own = Piece.Soldier(Player.CHO, Position(0, 1))
        val moves = ChariotMovement().getValidMoves(chariot, board(chariot, own))

        assertFalse(moves.contains(Position(0, 1)))
    }
}
