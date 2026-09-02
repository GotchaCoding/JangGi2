package com.example.janggi2.data.mapper

import com.example.janggi2.data.local.database.entity.GameCommentEntity
import com.example.janggi2.data.local.database.entity.GameEntity
import com.example.janggi2.data.local.database.entity.GameReviewEntity
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.MoveReview
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.SavedReview
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
     * Converts a GameState and its [GameReview] to a GameReviewEntity for database storage.
     */
    fun toReviewEntity(gameState: GameState, review: GameReview, name: String): GameReviewEntity {
        val boardJson = serializeBoard(gameState.board)
        val moveHistoryJson = serializeMoveHistory(gameState.moveHistory)
        val startBoardJson = gameState.startBoard?.let { serializeBoard(it) }
        val reviewJson = serializeReview(review)

        return GameReviewEntity(
            name = name,
            savedDate = System.currentTimeMillis(),
            boardStateJson = boardJson,
            currentPlayer = gameState.currentPlayer.name,
            moveCount = gameState.getMoveCount(),
            gameStatus = gameState.status.name,
            winner = gameState.winner?.name,
            moveHistoryJson = moveHistoryJson,
            startBoardJson = startBoardJson,
            reviewJson = reviewJson
        )
    }

    /**
     * Converts a GameReviewEntity from database back to a [SavedReview].
     */
    fun reviewFromEntity(entity: GameReviewEntity): SavedReview {
        val board = deserializeBoard(entity.boardStateJson)
        val moveHistory = deserializeMoveHistory(entity.moveHistoryJson)
        val startBoard = entity.startBoardJson?.let { deserializeBoard(it) }

        val gameState = GameState(
            board = board,
            currentPlayer = Player.valueOf(entity.currentPlayer),
            moveHistory = moveHistory,
            status = GameStatus.valueOf(entity.gameStatus),
            winner = entity.winner?.let { Player.valueOf(it) },
            startBoard = startBoard
        )
        val review = deserializeReview(entity.reviewJson, moveHistory)

        return SavedReview(gameState = gameState, review = review)
    }

    /**
     * Serializes a [GameReview] to JSON string. [MoveReview.move]/[MoveReview.bestMove] are
     * dropped - move는 moveHistoryJson에 이미 있고, bestMove는 화면에 쓰이지 않습니다.
     */
    private fun serializeReview(review: GameReview): String {
        val serializableReviews = review.moveReviews.map { mr ->
            SerializableMoveReview(
                moveIndex = mr.moveIndex,
                player = mr.player.name,
                quality = mr.quality.name,
                lossCp = mr.lossCp
            )
        }
        return json.encodeToString(serializableReviews)
    }

    /**
     * Deserializes JSON string to a [GameReview], reusing the already-deserialized
     * [moveHistory] to rebuild each [MoveReview.move].
     */
    private fun deserializeReview(reviewJson: String, moveHistory: List<Move>): GameReview {
        return try {
            val serializableReviews = json.decodeFromString<List<SerializableMoveReview>>(reviewJson)
            val moveReviews = serializableReviews.mapNotNull { sr ->
                val move = moveHistory.getOrNull(sr.moveIndex) ?: return@mapNotNull null
                MoveReview(
                    moveIndex = sr.moveIndex,
                    move = move,
                    player = Player.valueOf(sr.player),
                    quality = MoveQuality.valueOf(sr.quality),
                    lossCp = sr.lossCp,
                    bestMove = null
                )
            }
            GameReview(moveReviews)
        } catch (e: Exception) {
            android.util.Log.e("GameMapper", "Failed to deserialize game review: ${e.message}")
            GameReview(emptyList())
        }
    }

    /**
     * Converts a [ReviewComment] to a [GameCommentEntity] for database storage.
     */
    fun toCommentEntity(comment: ReviewComment): GameCommentEntity {
        return GameCommentEntity(
            id = comment.id,
            reviewId = comment.reviewId,
            message = comment.message,
            branchStartIndex = comment.branchStartIndex,
            movesJson = serializeMoveHistory(comment.moves),
            createdAt = comment.createdAt
        )
    }

    /**
     * Converts a [GameCommentEntity] from database back to a [ReviewComment].
     */
    fun commentFromEntity(entity: GameCommentEntity): ReviewComment {
        return ReviewComment(
            id = entity.id,
            reviewId = entity.reviewId,
            message = entity.message,
            branchStartIndex = entity.branchStartIndex,
            moves = deserializeMoveHistory(entity.movesJson),
            createdAt = entity.createdAt
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

/**
 * Serializable representation of a [MoveReview] for JSON storage.
 */
@Serializable
private data class SerializableMoveReview(
    val moveIndex: Int,
    val player: String,
    val quality: String,
    val lossCp: Int
)
