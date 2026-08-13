package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Enforces all Janggi game rules including check, checkmate, and bikjang.
 */
class GameRules {
    private val checkDetector = CheckDetector()
    private val moveValidator = MoveValidator()

    /**
     * Checks if a move is legal.
     * A legal move must:
     * 1. Be valid according to piece movement rules
     * 2. Not leave the moving player's General in check
     * 3. Not create bikjang (if it does, it's an instant loss)
     *
     * @param from Starting position
     * @param to Destination position
     * @param gameState Current game state
     * @return True if the move is legal
     */
    fun isMoveLegal(from: Position, to: Position, gameState: GameState): Boolean {
        val piece = gameState.getPieceAt(from) ?: return false

        // Check if it's the correct player's turn
        if (piece.player != gameState.currentPlayer) {
            return false
        }

        // Check if the move is valid according to piece rules
        if (!moveValidator.isValidMove(piece, to, gameState)) {
            return false
        }

        // Simulate the move and check if it leaves own General in check
        val simulatedState = simulateMove(gameState, from, to)
        if (checkDetector.isInCheck(piece.player, simulatedState)) {
            return false // Cannot make a move that puts/leaves own General in check
        }

        return true
    }

    /**
     * Evaluates the game status after a move.
     * Checks for:
     * 1. Bikjang (instant loss for the player who created it)
     * 2. Checkmate (win for the current player)
     * 3. Stalemate (draw)
     * 4. Check (ongoing with warning)
     * 5. Normal ongoing game
     *
     * @param gameState The game state after a move
     * @return Updated game state with correct status and winner
     */
    fun evaluateGameStatus(gameState: GameState): GameState {
        val currentPlayer = gameState.currentPlayer
        val opponent = currentPlayer.opponent()

        // 궁이 잡히면 그대로 끝납니다. 합법 수 필터가 자기 궁을 잡히게 두지 않으므로
        // 정상 대국에서는 나오지 않지만, 사진에서 불러온 판처럼 궁이 빠진 상태로
        // 시작하는 경우를 위해 먼저 확인합니다.
        if (gameState.getGeneral(currentPlayer) == null) {
            return gameState.withStatus(GameStatus.CHECKMATE, newWinner = opponent)
        }
        if (gameState.getGeneral(opponent) == null) {
            return gameState.withStatus(GameStatus.CHECKMATE, newWinner = currentPlayer)
        }

        // Check for bikjang (facing generals)
        if (checkDetector.isBikjang(gameState)) {
            // The player who just moved created bikjang and loses
            return gameState.withStatus(GameStatus.BIKJANG, newWinner = currentPlayer)
        }

        // Check if opponent is in checkmate
        if (checkDetector.isCheckmate(currentPlayer, gameState)) {
            return gameState.withStatus(GameStatus.CHECKMATE, newWinner = opponent)
        }

        // Check if opponent is in stalemate
        if (checkDetector.isStalemate(currentPlayer, gameState)) {
            return gameState.withStatus(GameStatus.STALEMATE, newWinner = null)
        }

        // Check if opponent is in check
        if (checkDetector.isInCheck(currentPlayer, gameState)) {
            return gameState.withStatus(GameStatus.CHECK, newWinner = null)
        }

        // Game continues normally
        return gameState.withStatus(GameStatus.ONGOING, newWinner = null)
    }

    /**
     * Gets all legal moves for a piece (considering check rules).
     *
     * @param piece The piece to get legal moves for
     * @param gameState Current game state
     * @return List of legal destination positions
     */
    fun getLegalMoves(piece: Position, gameState: GameState): List<Position> {
        val actualPiece = gameState.getPieceAt(piece) ?: return emptyList()
        return checkDetector.getLegalMoves(actualPiece, gameState)
    }

    /**
     * Applies a move and updates the game status.
     * This is the main function to execute a move with all rule checking.
     *
     * @param move The move to apply
     * @param gameState Current game state
     * @return New game state with move applied and status updated, or null if move is illegal
     */
    fun applyMoveWithRules(move: Move, gameState: GameState): GameState? {
        // Check if move is legal
        if (!isMoveLegal(move.from, move.to, gameState)) {
            return null
        }

        // Apply the move
        var newState = gameState.applyMove(move)

        // Evaluate game status
        newState = evaluateGameStatus(newState)

        return newState
    }

    /**
     * Simulates a move without modifying the game state.
     */
    private fun simulateMove(gameState: GameState, from: Position, to: Position): GameState {
        val piece = gameState.getPieceAt(from) ?: return gameState

        val newBoard = gameState.board.toMutableMap()
        newBoard.remove(from)
        newBoard[to] = piece.moveTo(to)

        return gameState.copy(board = newBoard)
    }
}
