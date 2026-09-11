package com.example.janggi2.data.mapper

import com.example.janggi2.data.local.database.entity.GameEntity

/**
 * Converts between [GameEntity] and the plain map that Firestore reads/writes, kept separate
 * from [GameMapper] so the field mapping is unit-testable without mocking the Firestore SDK.
 */
object GameCloudMapper {

    fun toMap(entity: GameEntity): Map<String, Any?> = mapOf(
        "name" to entity.name,
        "savedDate" to entity.savedDate,
        "boardStateJson" to entity.boardStateJson,
        "currentPlayer" to entity.currentPlayer,
        "moveCount" to entity.moveCount,
        "gameStatus" to entity.gameStatus,
        "winner" to entity.winner,
        "moveHistoryJson" to entity.moveHistoryJson,
        "startBoardJson" to entity.startBoardJson,
        "choPlayerName" to entity.choPlayerName,
        "hanPlayerName" to entity.hanPlayerName,
        "choRank" to entity.choRank,
        "hanRank" to entity.hanRank
    )

    /**
     * @param remoteId the Firestore document ID (not stored as a field inside the document
     * itself, so it's passed in separately here).
     * @return null if the document is missing a required field (e.g. malformed/foreign data) -
     * the caller should skip it rather than crash the pull.
     */
    fun fromMap(map: Map<String, Any?>, remoteId: String): GameEntity? = try {
        GameEntity(
            id = 0, // local PK is device-specific; let Room autogenerate it
            name = map["name"] as String,
            savedDate = (map["savedDate"] as Number).toLong(),
            boardStateJson = map["boardStateJson"] as String,
            currentPlayer = map["currentPlayer"] as String,
            moveCount = (map["moveCount"] as Number).toInt(),
            gameStatus = map["gameStatus"] as String,
            winner = map["winner"] as String?,
            moveHistoryJson = map["moveHistoryJson"] as String,
            startBoardJson = map["startBoardJson"] as String?,
            choPlayerName = map["choPlayerName"] as String?,
            hanPlayerName = map["hanPlayerName"] as String?,
            choRank = map["choRank"] as String?,
            hanRank = map["hanRank"] as String?,
            remoteId = remoteId
        )
    } catch (e: Exception) {
        null
    }
}
