package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Position

/**
 * Validates and calculates valid moves for pieces.
 * Coordinates the piece-specific movement rules.
 */
class MoveValidator {
    private val generalMovement = GeneralMovement()
    private val guardMovement = GuardMovement()
    private val horseMovement = HorseMovement()
    private val elephantMovement = ElephantMovement()
    private val chariotMovement = ChariotMovement()
    private val cannonMovement = CannonMovement()
    private val soldierMovement = SoldierMovement()

    /**
     * Gets all valid destination positions for a piece.
     * Does not consider check/checkmate - only basic movement rules.
     *
     * @param piece The piece to get moves for
     * @param gameState Current game state
     * @return List of valid destination positions
     */
    fun getValidMoves(piece: Piece, gameState: GameState): List<Position> {
        val movement = when (piece) {
            is Piece.General -> generalMovement
            is Piece.Guard -> guardMovement
            is Piece.Horse -> horseMovement
            is Piece.Elephant -> elephantMovement
            is Piece.Chariot -> chariotMovement
            is Piece.Cannon -> cannonMovement
            is Piece.Soldier -> soldierMovement
        }

        return movement.getValidMoves(piece, gameState.board)
    }

    /**
     * Checks if a specific move is valid for a piece.
     *
     * @param piece The piece to move
     * @param destination The destination position
     * @param gameState Current game state
     * @return True if the move is valid
     */
    fun isValidMove(piece: Piece, destination: Position, gameState: GameState): Boolean {
        return getValidMoves(piece, gameState).contains(destination)
    }

    /**
     * Checks if a piece can move to capture another piece.
     * Ensures you cannot capture your own pieces.
     *
     * @param piece The piece attempting to capture
     * @param destination The position of the piece to capture
     * @param gameState Current game state
     * @return True if the capture is valid
     */
    fun canCapture(piece: Piece, destination: Position, gameState: GameState): Boolean {
        val targetPiece = gameState.getPieceAt(destination)

        // Cannot capture empty space or own pieces
        if (targetPiece == null || targetPiece.player == piece.player) {
            return false
        }

        return isValidMove(piece, destination, gameState)
    }
}
