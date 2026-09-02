package com.example.janggi2.data.repository

import com.example.janggi2.data.local.database.dao.GameCommentDao
import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.local.database.dao.GameReviewDao
import com.example.janggi2.data.mapper.GameMapper
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import com.example.janggi2.domain.repository.SavedReview
import com.example.janggi2.domain.repository.SavedReviewInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of GameRepository using Room database.
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val gameReviewDao: GameReviewDao,
    private val gameCommentDao: GameCommentDao,
    private val gameMapper: GameMapper
) : GameRepository {

    override suspend fun saveGame(
        gameState: GameState,
        name: String,
        choPlayerName: String?,
        hanPlayerName: String?,
        choRank: String?,
        hanRank: String?
    ): Long {
        val entity = gameMapper.toEntity(gameState, name, choPlayerName, hanPlayerName, choRank, hanRank)
        return gameDao.insertGame(entity)
    }

    override suspend fun autoSave(gameState: GameState) {
        val entity = gameMapper.toEntity(gameState, "auto_save")
        gameDao.insertGame(entity)
    }

    override suspend fun loadGame(gameId: Long): GameState? {
        val entity = gameDao.getGameById(gameId) ?: return null
        return gameMapper.fromEntity(entity)
    }

    override suspend fun loadAutoSave(): GameState? {
        val entity = gameDao.getAutoSave() ?: return null
        return gameMapper.fromEntity(entity)
    }

    override fun getAllGames(): Flow<List<SavedGameInfo>> {
        return gameDao.getAllGames().map { entities ->
            entities
                .filter { it.name != "auto_save" } // Exclude auto-save from list
                .map { entity ->
                    SavedGameInfo(
                        id = entity.id,
                        name = entity.name,
                        savedDate = entity.savedDate,
                        moveCount = entity.moveCount,
                        currentPlayer = entity.currentPlayer,
                        gameStatus = entity.gameStatus,
                        choPlayerName = entity.choPlayerName,
                        hanPlayerName = entity.hanPlayerName,
                        choRank = entity.choRank,
                        hanRank = entity.hanRank
                    )
                }
        }
    }

    override suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    override suspend fun deleteAllGames() {
        gameDao.deleteAllGames()
    }

    override suspend fun saveReview(gameState: GameState, review: GameReview, name: String): Long {
        val entity = gameMapper.toReviewEntity(gameState, review, name)
        return gameReviewDao.insertReview(entity)
    }

    override suspend fun loadReview(reviewId: Long): SavedReview? {
        val entity = gameReviewDao.getReviewById(reviewId) ?: return null
        return gameMapper.reviewFromEntity(entity)
    }

    override fun getAllReviews(): Flow<List<SavedReviewInfo>> {
        return gameReviewDao.getAllReviews().map { entities ->
            entities.map { entity ->
                SavedReviewInfo(
                    id = entity.id,
                    name = entity.name,
                    savedDate = entity.savedDate,
                    moveCount = entity.moveCount
                )
            }
        }
    }

    override suspend fun deleteReview(reviewId: Long) {
        gameReviewDao.deleteReviewById(reviewId)
        gameCommentDao.deleteCommentsForReview(reviewId)
    }

    override suspend fun saveComment(
        reviewId: Long,
        message: String,
        branchStartIndex: Int,
        moves: List<Move>
    ): Long {
        val entity = gameMapper.toCommentEntity(
            ReviewComment(
                reviewId = reviewId,
                message = message,
                branchStartIndex = branchStartIndex,
                moves = moves,
                createdAt = System.currentTimeMillis()
            )
        )
        return gameCommentDao.insertComment(entity)
    }

    override fun getCommentsForReview(reviewId: Long): Flow<List<ReviewComment>> {
        return gameCommentDao.getCommentsForReview(reviewId).map { entities ->
            entities.map { entity -> gameMapper.commentFromEntity(entity) }
        }
    }
}
