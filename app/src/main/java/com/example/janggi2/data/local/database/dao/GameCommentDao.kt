package com.example.janggi2.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.janggi2.data.local.database.entity.GameCommentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for comments left on tested move sequences during AI review replay.
 */
@Dao
interface GameCommentDao {

    @Query("SELECT * FROM game_comments WHERE reviewId = :reviewId ORDER BY createdAt ASC")
    fun getCommentsForReview(reviewId: Long): Flow<List<GameCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: GameCommentEntity): Long

    @Query("DELETE FROM game_comments WHERE id = :commentId")
    suspend fun deleteCommentById(commentId: Long)

    @Query("DELETE FROM game_comments WHERE reviewId = :reviewId")
    suspend fun deleteCommentsForReview(reviewId: Long)
}
