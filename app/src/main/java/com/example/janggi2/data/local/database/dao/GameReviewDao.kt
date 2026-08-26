package com.example.janggi2.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.janggi2.data.local.database.entity.GameReviewEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved AI reviews.
 */
@Dao
interface GameReviewDao {

    @Query("SELECT * FROM game_reviews ORDER BY savedDate DESC")
    fun getAllReviews(): Flow<List<GameReviewEntity>>

    @Query("SELECT * FROM game_reviews WHERE id = :reviewId")
    suspend fun getReviewById(reviewId: Long): GameReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: GameReviewEntity): Long

    @Query("DELETE FROM game_reviews WHERE id = :reviewId")
    suspend fun deleteReviewById(reviewId: Long)

    @Query("DELETE FROM game_reviews")
    suspend fun deleteAllReviews()
}
