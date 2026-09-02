package com.example.janggi2.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a comment left on a tested move sequence while
 * replaying a saved AI review ([GameReviewEntity]). [reviewId]는 game_reviews
 * 테이블의 id를 가리킵니다(실제 FK 제약은 이 프로젝트의 다른 테이블들과 같은 이유로 안 겁니다).
 */
@Entity(tableName = "game_comments")
data class GameCommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reviewId: Long,
    val message: String,
    /** 원래 기보의 moveHistory 에서 이 수순이 갈라져 나간 지점(0부터 시작하는 인덱스). */
    val branchStartIndex: Int,
    /** JSON representation of the tested [com.example.janggi2.domain.model.Move] sequence. */
    val movesJson: String,
    val createdAt: Long
)
