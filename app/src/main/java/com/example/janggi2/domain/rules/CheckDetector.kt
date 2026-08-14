package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Detects check and checkmate conditions in Janggi.
 */
class CheckDetector {
    private val moveValidator = MoveValidator()

    /**
     * Checks if the specified player's General is in check.
     * A General is in check if any opponent piece can capture it on the next move.
     *
     * @param player The player whose General to check
     * @param gameState Current game state
     * @return True if the player's General is in check
     */
    fun isInCheck(player: Player, gameState: GameState): Boolean {
        val general = gameState.getGeneral(player) ?: return false
        val generalPosition = general.position
        val opponent = player.opponent()

        // Check if any opponent piece can capture the General
        val opponentPieces = gameState.getPiecesForPlayer(opponent)
        return opponentPieces.any { piece ->
            val validMoves = moveValidator.getValidMoves(piece, gameState)
            validMoves.contains(generalPosition)
        }
    }

    /**
     * Checks if the specified player is in checkmate.
     * Checkmate occurs when:
     * 1. The player is in check
     * 2. The player has no legal moves that escape check
     *
     * @param player The player to check for checkmate
     * @param gameState Current game state
     * @return True if the player is in checkmate
     */
    fun isCheckmate(player: Player, gameState: GameState): Boolean {
        // Must be in check for checkmate
        if (!isInCheck(player, gameState)) {
            return false
        }

        // Check if there are any legal moves that escape check
        return !hasLegalMoves(player, gameState)
    }

    /**
     * Checks if the player is in stalemate.
     * Stalemate occurs when the player is NOT in check but has no legal moves.
     *
     * @param player The player to check
     * @param gameState Current game state
     * @return True if the player is in stalemate
     */
    fun isStalemate(player: Player, gameState: GameState): Boolean {
        // Must NOT be in check for stalemate
        if (isInCheck(player, gameState)) {
            return false
        }

        // Must have no legal moves
        return !hasLegalMoves(player, gameState)
    }

    /**
     * Checks if the player has any legal moves available.
     * A legal move is one that:
     * 1. Is valid according to piece movement rules
     * 2. Does not leave the player's General in check
     *
     * @param player The player to check
     * @param gameState Current game state
     * @return True if the player has at least one legal move
     */
    fun hasLegalMoves(player: Player, gameState: GameState): Boolean {
        val playerPieces = gameState.getPiecesForPlayer(player)

        for (piece in playerPieces) {
            val possibleMoves = moveValidator.getValidMoves(piece, gameState)

            for (destination in possibleMoves) {
                // Simulate the move
                val simulatedState = simulateMove(gameState, piece.position, destination)

                // Check if this move leaves the player in check
                if (!isInCheck(player, simulatedState)) {
                    return true // Found at least one legal move
                }
            }
        }

        return false // No legal moves found
    }

    /**
     * Simulates a move without modifying the game state.
     * Used to test if a move would leave the player in check.
     *
     * @param gameState Current game state
     * @param from Starting position
     * @param to Destination position
     * @return New game state with the move applied (without switching turns or updating status)
     */
    private fun simulateMove(gameState: GameState, from: Position, to: Position): GameState {
        val piece = gameState.getPieceAt(from) ?: return gameState

        val newBoard = gameState.board.toMutableMap()
        newBoard.remove(from)
        newBoard[to] = piece.moveTo(to)

        return gameState.copy(board = newBoard)
    }

    /**
     * Gets all legal moves for a piece (moves that don't leave own General in check).
     *
     * @param piece The piece to get legal moves for
     * @param gameState Current game state
     * @return List of legal destination positions
     */
    fun getLegalMoves(piece: Piece, gameState: GameState): List<Position> {
        val possibleMoves = moveValidator.getValidMoves(piece, gameState)
        val legalMoves = mutableListOf<Position>()

        for (destination in possibleMoves) {
            // Simulate the move
            val simulatedState = simulateMove(gameState, piece.position, destination)

            // Check if this move leaves the player in check
            if (!isInCheck(piece.player, simulatedState)) {
                legalMoves.add(destination)
            }
        }

        return legalMoves
    }
}
