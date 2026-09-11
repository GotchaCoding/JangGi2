package com.example.janggi2.data.mapper

import com.example.janggi2.data.local.database.entity.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameCloudMapperTest {

    private val entity = GameEntity(
        id = 1,
        name = "테스트 대국",
        savedDate = 1_700_000_000_000L,
        boardStateJson = "[]",
        currentPlayer = "CHO",
        moveCount = 3,
        gameStatus = "ONGOING",
        winner = null,
        moveHistoryJson = "[]",
        startBoardJson = null,
        choPlayerName = "홍길동",
        hanPlayerName = null,
        choRank = "3급",
        hanRank = null,
        remoteId = "remote-1"
    )

    @Test
    fun `toMap 후 fromMap 하면 remoteId 를 제외한 필드가 그대로 복원된다`() {
        val map = GameCloudMapper.toMap(entity)
        val restored = GameCloudMapper.fromMap(map, remoteId = entity.remoteId)

        assertEquals(entity.copy(id = 0), restored)
    }

    @Test
    fun `필수 필드가 빠진 문서는 fromMap 이 null 을 반환한다`() {
        val malformed = mapOf("name" to "이름만 있음")

        assertNull(GameCloudMapper.fromMap(malformed, remoteId = "x"))
    }
}
