package com.example.janggi2.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved AI review of a game. 저장된 기보(GameEntity)와는
 * 독립적으로 저장/조회/삭제됩니다 - AI 리뷰를 돌릴 때마다 자동 저장되고, 그 기보를
 * 따로 저장했는지와는 무관합니다.
 */
@Entity(tableName = "game_reviews")
data class GameReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val savedDate: Long,
    val boardStateJson: String,
    val currentPlayer: String,
    val moveCount: Int,
    val gameStatus: String,
    val winner: String?,
    val moveHistoryJson: String,
    val startBoardJson: String?,
    /** JSON representation of [com.example.janggi2.domain.model.GameReview.moveReviews]. */
    val reviewJson: String
)
