package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Enforces all Janggi game rules including check, checkmate, and bikjang.
 *
 * @param repetition 장군 반복·수 반복 판정자. 판 하나로는 알 수 없어 엔진에 맡기는
 *   유일한 규칙입니다. null 이면 반복 판정을 건너뜁니다 - 네이티브 라이브러리 없이
 *   도는 단위 테스트와, 엔진 초기화가 끝나기 전을 위한 것입니다.
 */
class GameRules(private val repetition: RepetitionJudge? = null) {
    companion object {
        /** 이 수를 넘기면 기물 점수로 승부를 가립니다. */
        const val MOVE_LIMIT = 200
    }

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
     * 1. Checkmate (win for the current player)
     * 2. No legal moves, or the move limit - decided on material points
     * 3. Check (ongoing with warning)
     * 4. Normal ongoing game
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

        // 빅장 규칙은 쓰지 않습니다. 궁이 마주 봐도 그대로 진행합니다.

        // Check if opponent is in checkmate
        if (checkDetector.isCheckmate(currentPlayer, gameState)) {
            return gameState.withStatus(GameStatus.CHECKMATE, newWinner = opponent)
        }

        // 움직일 수 없는 경우. 장기는 한 수 쉼이 가능해 거의 나오지 않지만,
        // 나온다면 무승부보다 점수로 가리는 쪽이 점수제에 맞습니다.
        if (checkDetector.isStalemate(currentPlayer, gameState)) {
            return gameState.withStatus(
                GameStatus.POINT_WIN,
                newWinner = MaterialScore.leader(gameState)
            )
        }

        // 장군 반복·수 반복. 판 하나로는 알 수 없어 엔진이 수순을 재생해 가립니다.
        // 200수 판정보다 앞에 둡니다 - 반복은 반칙이라 점수와 무관하게 승부가 갈립니다.
        when (repetition?.judge(gameState)) {
            RepetitionOutcome.SIDE_TO_MOVE_LOSES ->
                return gameState.withStatus(GameStatus.FOUL_LOSS, newWinner = opponent)
            RepetitionOutcome.SIDE_TO_MOVE_WINS ->
                return gameState.withStatus(GameStatus.FOUL_LOSS, newWinner = currentPlayer)
            // 이 앱에는 무승부가 없으므로 점수제로 넘깁니다.
            RepetitionOutcome.DRAW ->
                return gameState.withStatus(
                    GameStatus.POINT_WIN,
                    newWinner = MaterialScore.leader(gameState)
                )
            RepetitionOutcome.NONE, null -> Unit
        }

        // 200수가 지나면 기물 점수로 승부를 가립니다.
        if (gameState.moveHistory.size >= MOVE_LIMIT) {
            return gameState.withStatus(
                GameStatus.POINT_WIN,
                newWinner = MaterialScore.leader(gameState)
            )
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
     * 한 수 쉼을 둘 수 있는지.
     *
     * 엔진은 한 수 쉼을 궁 자리에서 같은 자리로 가는 수로 만들고(movegen.cpp
     * "Passing move by king"), 다른 수와 똑같이 합법성 검사를 거칩니다. 판이
     * 그대로이므로 장군을 맞은 상태에서는 그 장군이 풀리지 않아 걸러집니다.
     * 즉 장군일 때는 쉴 수 없습니다.
     */
    fun canPass(gameState: GameState): Boolean {
        if (!gameState.canPass()) return false
        if (gameState.getGeneral(gameState.currentPlayer) == null) return false
        return !checkDetector.isInCheck(gameState.currentPlayer, gameState)
    }

    /**
     * 한 수 쉼을 둡니다. 판은 그대로 두고 차례만 넘깁니다.
     *
     * @return 적용된 상태, 쉴 수 없으면 null
     */
    fun applyPass(gameState: GameState): GameState? {
        if (!canPass(gameState)) return null

        val general = gameState.getGeneral(gameState.currentPlayer) ?: return null
        val pass = Move(
            from = general.position,
            to = general.position,
            movedPiece = general
        )
        return evaluateGameStatus(gameState.applyMove(pass))
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
