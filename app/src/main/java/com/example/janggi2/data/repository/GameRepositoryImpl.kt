package com.example.janggi2.data.repository

import android.util.Log
import com.example.janggi2.data.local.database.dao.GameCommentDao
import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.local.database.dao.GameReviewDao
import com.example.janggi2.data.local.database.entity.GameEntity
import com.example.janggi2.data.mapper.GameCloudMapper
import com.example.janggi2.data.mapper.GameMapper
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import com.example.janggi2.domain.repository.SavedReview
import com.example.janggi2.domain.repository.SavedReviewInfo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "GameRepositoryImpl"
private const val GAMES_COLLECTION = "games"
private const val USERS_COLLECTION = "users"

/**
 * Implementation of GameRepository using Room database, with best-effort push/pull sync of
 * saved_games to Firestore when signed in. Cloud calls never throw - a failed push/pull/delete
 * is logged and swallowed so local persistence always succeeds regardless of network/auth state.
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val gameReviewDao: GameReviewDao,
    private val gameCommentDao: GameCommentDao,
    private val gameMapper: GameMapper,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
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
        val id = gameDao.insertGame(entity)
        pushToCloud(entity)
        return id
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
        val entity = gameDao.getGameById(gameId)
        gameDao.deleteGameById(gameId)
        entity?.let { deleteFromCloud(it.remoteId) }
    }

    override suspend fun deleteAllGames() {
        gameDao.deleteAllGames()
    }

    override suspend fun pullFromCloud(): Int {
        val uid = authRepository.getCurrentUser()?.uid ?: return 0
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION).document(uid)
                .collection(GAMES_COLLECTION).get().await()
            var pulled = 0
            for (doc in snapshot.documents) {
                if (gameDao.getGameByRemoteId(doc.id) != null) continue
                val entity = doc.data?.let { GameCloudMapper.fromMap(it, doc.id) } ?: continue
                gameDao.insertGame(entity)
                pulled++
            }
            pulled
        } catch (e: Exception) {
            Log.w(TAG, "Cloud pull failed", e)
            0
        }
    }

    override suspend fun purgeAllUserData() {
        // 클라우드를 먼저 지웁니다. 여기서 실패하면 예외가 나가고 계정은 살아 있어,
        // 사용자가 다시 시도할 수 있습니다(순서를 뒤집으면 지울 방법이 사라집니다).
        val uid = authRepository.getCurrentUser()?.uid
        if (uid != null) {
            val snapshot = firestore.collection(USERS_COLLECTION).document(uid)
                .collection(GAMES_COLLECTION).get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
        }
        // 기기에 남은 기보·리뷰·댓글도 지웁니다. 다음에 이 기기로 로그인한 사람에게
        // 보이면 안 됩니다.
        gameDao.deleteAllGames()
        gameReviewDao.deleteAllReviews()
        gameCommentDao.deleteAllComments()
    }

    private suspend fun pushToCloud(entity: GameEntity) {
        if (entity.name == "auto_save") return
        val uid = authRepository.getCurrentUser()?.uid ?: return
        try {
            firestore.collection(USERS_COLLECTION).document(uid)
                .collection(GAMES_COLLECTION).document(entity.remoteId)
                .set(GameCloudMapper.toMap(entity)).await()
        } catch (e: Exception) {
            Log.w(TAG, "Cloud push failed, staying local-only until next write", e)
        }
    }

    private suspend fun deleteFromCloud(remoteId: String) {
        if (remoteId.isEmpty()) return
        val uid = authRepository.getCurrentUser()?.uid ?: return
        try {
            firestore.collection(USERS_COLLECTION).document(uid)
                .collection(GAMES_COLLECTION).document(remoteId)
                .delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Cloud delete failed", e)
        }
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
