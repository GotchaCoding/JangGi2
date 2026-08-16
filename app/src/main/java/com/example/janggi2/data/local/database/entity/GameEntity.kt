package com.example.janggi2.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved game.
 */
@Entity(tableName = "saved_games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val savedDate: Long,
    val boardStateJson: String, // JSON representation of the board
    val currentPlayer: String, // "CHO" or "HAN"
    val moveCount: Int,
    val gameStatus: String, // "ONGOING", "CHECK", "CHECKMATE", etc.
    val winner: String?, // "CHO", "HAN", or null
    val moveHistoryJson: String = "[]", // JSON representation of move history
    /**
     * 이 대국이 시작된 판(표준 마·상 배치가 아니거나 사진에서 불러왔을 때). null이면
     * 표준 배치입니다. [com.example.janggi2.domain.model.GameState.startBoard] 참고 -
     * 이게 없으면 복기/수 기록 이동이 항상 기본 배치로 되감아 실제와 다른 기물이 나옵니다.
     */
    val startBoardJson: String? = null
)
