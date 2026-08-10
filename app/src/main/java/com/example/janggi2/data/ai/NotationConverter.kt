package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Position
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts between Kotlin Position objects and UCI notation used by Fairy-Stockfish.
 *
 * Janggi board: 9 columns (0-8) x 10 rows (0-9)
 * UCI notation: file (a-i) + rank (0-9)
 *
 * Examples:
 * - Position(0, 0) = "a0" (CHO left chariot)
 * - Position(4, 1) = "e1" (CHO general)
 * - Position(4, 8) = "e8" (HAN general)
 * - Position(8, 9) = "i9" (HAN right chariot)
 */
@Singleton
class NotationConverter @Inject constructor() {

    /**
     * Converts a Position to UCI notation.
     * @param position Position on the Janggi board
     * @return UCI notation string (e.g., "a0", "e8")
     */
    fun positionToUci(position: Position): String {
        require(position.col in 0..8) { "Invalid column: ${position.col}" }
        require(position.row in 0..9) { "Invalid row: ${position.row}" }

        val file = ('a' + position.col)
        val rank = position.row.toString()
        return "$file$rank"
    }

    /**
     * Converts UCI notation to a Position.
     * @param uci UCI notation string (e.g., "a0", "e8")
     * @return Position object
     * @throws IllegalArgumentException if UCI notation is invalid
     */
    fun uciToPosition(uci: String): Position {
        require(uci.length >= 2) { "UCI notation must be at least 2 characters: $uci" }

        val file = uci[0]
        require(file in 'a'..'i') { "Invalid file: $file" }

        val col = file - 'a'
        val row = uci.substring(1).toIntOrNull()
            ?: throw IllegalArgumentException("Invalid rank in UCI: $uci")

        require(row in 0..9) { "Invalid row: $row" }

        return Position(col, row)
    }

    /**
     * Converts a Move to UCI notation.
     * @param move Move object
     * @return UCI move string (e.g., "a0b0", "e1e2")
     */
    fun moveToUci(move: Move): String {
        val from = positionToUci(move.from)
        val to = positionToUci(move.to)
        return "$from$to"
    }

    /**
     * Converts UCI move notation to a Move object.
     * @param uciMove UCI move string (e.g., "a0b0", "e1e2")
     * @param gameState Current game state (needed to determine captured piece)
     * @return Move object
     * @throws IllegalArgumentException if UCI move is invalid
     */
    fun uciToMove(uciMove: String, gameState: GameState): Move {
        require(uciMove.length >= 4) {
            "UCI move must be at least 4 characters: $uciMove"
        }

        val from = uciToPosition(uciMove.substring(0, 2))
        val to = uciToPosition(uciMove.substring(2, 4))
        val capturedPiece = gameState.getPieceAt(to)

        return Move(from, to, capturedPiece)
    }

    /**
     * Validates if a UCI notation string is valid.
     * @param uci UCI notation to validate
     * @return true if valid, false otherwise
     */
    fun isValidUci(uci: String): Boolean {
        return try {
            uciToPosition(uci)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Validates if a UCI move string is valid (format only, not game legality).
     * @param uciMove UCI move to validate
     * @return true if format is valid, false otherwise
     */
    fun isValidUciMove(uciMove: String): Boolean {
        if (uciMove.length < 4) return false

        return try {
            uciToPosition(uciMove.substring(0, 2))
            uciToPosition(uciMove.substring(2, 4))
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
