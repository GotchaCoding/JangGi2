package com.example.janggi2.fake

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import com.example.janggi2.domain.repository.SavedReview
import com.example.janggi2.domain.repository.SavedReviewInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [GameRepository] 의 최소 구현. 테스트에서 관심 있는 메서드만 골라 열어 씁니다.
 * (이 프로젝트는 Mockito/MockK 를 쓰지 않고 손으로 Fake 를 만드는 관례입니다.)
 */
open class FakeGameRepository : GameRepository {
    override suspend fun saveGame(
        gameState: GameState,
        name: String,
        choPlayerName: String?,
        hanPlayerName: String?,
        choRank: String?,
        hanRank: String?
    ): Long = 0

    override suspend fun autoSave(gameState: GameState) {}
    override suspend fun loadGame(gameId: Long): GameState? = null
    override suspend fun loadAutoSave(): GameState? = null
    override fun getAllGames(): Flow<List<SavedGameInfo>> = flowOf(emptyList())
    override suspend fun deleteGame(gameId: Long) {}
    override suspend fun deleteAllGames() {}
    override suspend fun pullFromCloud(): Int = 0
    override suspend fun purgeAllUserData() {}
    override suspend fun saveReview(gameState: GameState, review: GameReview, name: String): Long = 0
    override suspend fun loadReview(reviewId: Long): SavedReview? = null
    override fun getAllReviews(): Flow<List<SavedReviewInfo>> = flowOf(emptyList())
    override suspend fun deleteReview(reviewId: Long) {}
    override suspend fun saveComment(
        reviewId: Long,
        message: String,
        branchStartIndex: Int,
        moves: List<Move>
    ): Long = 0

    override fun getCommentsForReview(reviewId: Long): Flow<List<ReviewComment>> = flowOf(emptyList())
}
