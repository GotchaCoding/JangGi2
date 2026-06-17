package com.example.janggi2.data.repository

import com.example.janggi2.data.local.database.dao.GameDao
import com.example.janggi2.data.mapper.GameMapper
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of GameRepository using Room database.
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val gameMapper: GameMapper
) : GameRepository {

    override suspend fun saveGame(gameState: GameState, name: String): Long {
        val entity = gameMapper.toEntity(gameState, name)
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
                        gameStatus = entity.gameStatus
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
}
