package com.example.janggi2.data.mapper

import com.example.janggi2.data.local.database.entity.GameEntity
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapper to convert between domain models and database entities.
 */
class GameMapper {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Converts a GameState to a GameEntity for database storage.
     */
    fun toEntity(
        gameState: GameState,
        name: String,
        choPlayerName: String? = null,
        hanPlayerName: String? = null,
        choRank: String? = null,
        hanRank: String? = null
    ): GameEntity {
        val boardJson = serializeBoard(gameState.board)
        val moveHistoryJson = serializeMoveHistory(gameState.moveHistory)
        val startBoardJson = gameState.startBoard?.let { serializeBoard(it) }

        return GameEntity(
            name = name,
            savedDate = System.currentTimeMillis(),
            boardStateJson = boardJson,
            currentPlayer = gameState.currentPlayer.name,
            moveCount = gameState.getMoveCount(),
            gameStatus = gameState.status.name,
            winner = gameState.winner?.name,
            moveHistoryJson = moveHistoryJson,
            startBoardJson = startBoardJson,
            choPlayerName = choPlayerName,
            hanPlayerName = hanPlayerName,
            choRank = choRank,
            hanRank = hanRank
        )
    }

    /**
     * Converts a GameEntity from database to a GameState.
     */
    fun fromEntity(entity: GameEntity): GameState {
        val board = deserializeBoard(entity.boardStateJson)
        val moveHistory = deserializeMoveHistory(entity.moveHistoryJson)
        val startBoard = entity.startBoardJson?.let { deserializeBoard(it) }

        return GameState(
            board = board,
            currentPlayer = Player.valueOf(entity.currentPlayer),
            moveHistory = moveHistory,
            status = GameStatus.valueOf(entity.gameStatus),
            winner = entity.winner?.let { Player.valueOf(it) },
            startBoard = startBoard
        )
    }

    /**
     * Serializes the board to JSON string.
     */
    private fun serializeBoard(board: Map<Position, Piece>): String {
        val serializablePieces = board.map { (position, piece) ->
            SerializablePiece(
                col = position.col,
                row = position.row,
                type = getPieceType(piece),
                player = piece.player.name
            )
        }
        return json.encodeToString(serializablePieces)
    }

    /**
     * Deserializes JSON string to board map.
     */
    private fun deserializeBoard(boardJson: String): Map<Position, Piece> {
        val serializablePieces = json.decodeFromString<List<SerializablePiece>>(boardJson)

        return serializablePieces.associate { sp ->
            val position = Position(sp.col, sp.row)
            val player = Player.valueOf(sp.player)
            val piece = createPiece(sp.type, player, position)
            position to piece
        }
    }

    /**
     * Gets the piece type as a string.
     */
    private fun getPieceType(piece: Piece): String = when (piece) {
        is Piece.General -> "GENERAL"
        is Piece.Guard -> "GUARD"
        is Piece.Horse -> "HORSE"
        is Piece.Elephant -> "ELEPHANT"
        is Piece.Chariot -> "CHARIOT"
        is Piece.Cannon -> "CANNON"
        is Piece.Soldier -> "SOLDIER"
    }

    /**
     * Creates a piece from type string.
     */
    private fun createPiece(type: String, player: Player, position: Position): Piece {
        return when (type) {
            "GENERAL" -> Piece.General(player, position)
            "GUARD" -> Piece.Guard(player, position)
            "HORSE" -> Piece.Horse(player, position)
            "ELEPHANT" -> Piece.Elephant(player, position)
            "CHARIOT" -> Piece.Chariot(player, position)
            "CANNON" -> Piece.Cannon(player, position)
            "SOLDIER" -> Piece.Soldier(player, position)
            else -> throw IllegalArgumentException("Unknown piece type: $type")
        }
    }

    /**
     * Serializes move history to JSON string.
     */
    private fun serializeMoveHistory(moveHistory: List<Move>): String {
        val serializableMoves = moveHistory.map { move ->
            SerializableMove(
                fromCol = move.from.col,
                fromRow = move.from.row,
                toCol = move.to.col,
                toRow = move.to.row,
                capturedPieceType = move.capturedPiece?.let { getPieceType(it) },
                capturedPiecePlayer = move.capturedPiece?.player?.name,
                movedPieceType = move.movedPiece?.let { getPieceType(it) },
                movedPiecePlayer = move.movedPiece?.player?.name
            )
        }
        return json.encodeToString(serializableMoves)
    }

    /**
     * Deserializes JSON string to move history.
     */
    private fun deserializeMoveHistory(moveHistoryJson: String): List<Move> {
        return try {
            val serializableMoves = json.decodeFromString<List<SerializableMove>>(moveHistoryJson)
            serializableMoves.map { sm ->
                val from = Position(sm.fromCol, sm.fromRow)
                val to = Position(sm.toCol, sm.toRow)
                val capturedPiece = if (sm.capturedPieceType != null && sm.capturedPiecePlayer != null) {
                    createPiece(sm.capturedPieceType, Player.valueOf(sm.capturedPiecePlayer), to)
                } else {
                    null
                }
                val movedPiece = if (sm.movedPieceType != null && sm.movedPiecePlayer != null) {
                    createPiece(sm.movedPieceType, Player.valueOf(sm.movedPiecePlayer), to)
                } else {
                    null
                }
                Move(from = from, to = to, capturedPiece = capturedPiece, movedPiece = movedPiece)
            }
        } catch (e: Exception) {
            // Log error and return empty list if JSON is corrupted
            android.util.Log.e("GameMapper", "Failed to deserialize move history: ${e.message}")
            emptyList()
        }
    }
}

/**
 * Serializable representation of a piece for JSON storage.
 */
@Serializable
private data class SerializablePiece(
    val col: Int,
    val row: Int,
    val type: String,
    val player: String
)

/**
 * Serializable representation of a move for JSON storage.
 */
@Serializable
private data class SerializableMove(
    val fromCol: Int,
    val fromRow: Int,
    val toCol: Int,
    val toRow: Int,
    val capturedPieceType: String?,
    val capturedPiecePlayer: String?,
    // 나중에 추가된 항목이라 예전 저장 데이터에는 없습니다. 기본값이 있어야 읽힙니다.
    val movedPieceType: String? = null,
    val movedPiecePlayer: String? = null
)
