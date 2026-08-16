package com.example.janggi2.data.mapper

import com.example.janggi2.domain.model.HorseElephantSetup
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장했다가 불러온 대국이 실제로 골랐던 마·상 배치를 유지하는지 확인합니다.
 *
 * 예전에는 [com.example.janggi2.domain.model.GameState.startBoard] 를 저장/복원하지
 * 않아서, 기본이 아닌 배치를 고른 대국을 저장했다가 불러와 복기하면(또는 수 기록을
 * 눌러 이동하면) 항상 기본 배치로 되감겨 실제와 다른 기물이 나오는 버그가 있었습니다.
 */
class GameMapperTest {

    private val mapper = GameMapper()

    @Test
    fun `기본이 아닌 마상 배치도 저장했다 불러오면 그대로 유지된다`() {
        // 초 기본은 마상마상(HORSE_FIRST_OUTER) - 일부러 다른 배치를 고릅니다.
        val original = initialGameState(
            choSetup = HorseElephantSetup.HORSES_OUTSIDE,
            hanSetup = HorseElephantSetup.defaultFor(Player.HAN)
        )

        val entity = mapper.toEntity(original, "테스트 대국")
        val restored = mapper.fromEntity(entity)

        assertEquals(original.board, restored.startBoard)
    }

    @Test
    fun `불러온 대국을 복기하면 저장 당시 골랐던 배치의 기물이 나온다`() {
        val original = initialGameState(
            choSetup = HorseElephantSetup.HORSES_OUTSIDE,
            hanSetup = HorseElephantSetup.defaultFor(Player.HAN)
        )
            // 초의 뒷줄은 건드리지 않는 수 하나만 두고 저장합니다.
            .applyMove(Move(Position(0, 6), Position(0, 5)))

        val restored = mapper.fromEntity(mapper.toEntity(original, "테스트 대국"))
        val replaying = restored.enterReplayMode()

        // HORSES_OUTSIDE 는 초의 1열에 마를 놓습니다 - 기본(마상마상)이었다면 상이 옵니다.
        assertTrue(replaying.getPieceAt(Position(1, 0)) is Piece.Horse)
    }

    @Test
    fun `사진으로 불러온 판(startBoard 있음)도 저장 복원 후 그대로 유지된다`() {
        val importedBoard = mapOf(
            Position(4, 1) to Piece.General(Player.CHO, Position(4, 1)),
            Position(4, 8) to Piece.General(Player.HAN, Position(4, 8))
        )
        val imported = com.example.janggi2.domain.model.GameState(
            board = importedBoard,
            currentPlayer = Player.CHO,
            startBoard = importedBoard
        )

        val restored = mapper.fromEntity(mapper.toEntity(imported, "사진 대국"))

        assertEquals(importedBoard, restored.startBoard)
    }
}
