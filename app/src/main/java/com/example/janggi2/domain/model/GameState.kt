package com.example.janggi2.domain.model

/**
 * Represents the status of the game.
 */
enum class GameStatus {
    ONGOING,    // Game in progress
    CHECK,      // Current player's opponent is in check
    CHECKMATE,  // Game over - checkmate
    STALEMATE,  // Game over - stalemate (no legal moves)
    BIKJANG     // Game over - bikjang (facing generals)
}

/**
 * Represents the complete state of a Janggi game.
 * Immutable - all modifications create new instances.
 */
data class GameState(
    val board: Map<Position, Piece>,
    val currentPlayer: Player = Player.CHO,
    val moveHistory: List<Move> = emptyList(),
    val status: GameStatus = GameStatus.ONGOING,
    val winner: Player? = null,
    val undoStack: List<GameState> = emptyList(),
    val redoStack: List<GameState> = emptyList(),
    val isReplayMode: Boolean = false,
    val replayPosition: Int = 0  // 0 = initial state, N = after N moves
) {
    /**
     * Returns the piece at the given position, or null if empty.
     */
    fun getPieceAt(position: Position): Piece? = board[position]

    /**
     * Returns all pieces belonging to the given player.
     */
    fun getPiecesForPlayer(player: Player): List<Piece> =
        board.values.filter { it.player == player }

    /**
     * Returns the General piece for the given player.
     */
    fun getGeneral(player: Player): Piece.General? =
        board.values.filterIsInstance<Piece.General>().firstOrNull { it.player == player }

    /**
     * Applies a move to the game state, returning a new state.
     * This function:
     * 1. Removes the piece from its starting position
     * 2. Places it at the destination position
     * 3. Removes any captured piece
     * 4. Switches the current player
     * 5. Adds the move to history
     *
     * @param move The move to apply
     * @return New GameState with the move applied
     */
    fun applyMove(move: Move): GameState {
        val piece = board[move.from] ?: return this // No piece to move

        // Create new board with move applied
        val newBoard = board.toMutableMap()

        // Remove piece from starting position
        newBoard.remove(move.from)

        // Move piece to new position (updating its position property)
        val movedPiece = piece.moveTo(move.to)
        newBoard[move.to] = movedPiece

        // Create move record with captured piece if any
        val capturedPiece = board[move.to]
        val moveRecord = move.copy(capturedPiece = capturedPiece)

        return GameState(
            board = newBoard,
            currentPlayer = currentPlayer.opponent(),
            moveHistory = moveHistory + moveRecord,
            status = status, // Status will be updated by GameRules
            winner = winner
        )
    }

    /**
     * Returns the last move played, or null if no moves have been made.
     */
    fun getLastMove(): Move? = moveHistory.lastOrNull()

    /**
     * Returns the total number of moves played.
     */
    fun getMoveCount(): Int = moveHistory.size

    /**
     * Returns true if the game is over (checkmate, stalemate, or bikjang).
     */
    fun isGameOver(): Boolean = status in listOf(
        GameStatus.CHECKMATE,
        GameStatus.STALEMATE,
        GameStatus.BIKJANG
    )

    /**
     * Updates the game status and winner.
     */
    fun withStatus(newStatus: GameStatus, newWinner: Player? = null): GameState {
        return copy(status = newStatus, winner = newWinner)
    }

    /**
     * Creates a new game state with the current state pushed to the undo stack.
     * Used before making a move to enable undo functionality.
     */
    fun pushToUndoStack(): GameState {
        // Save current state to undo stack (without undo/redo stacks to avoid infinite recursion)
        val stateToSave = this.copy(undoStack = emptyList(), redoStack = emptyList())
        return this.copy(
            undoStack = undoStack + stateToSave,
            redoStack = emptyList() // Clear redo stack when making a new move
        )
    }

    /**
     * Undoes the last move.
     * @return The previous game state, or null if no moves to undo
     */
    fun undo(): GameState? {
        if (undoStack.isEmpty()) return null

        val previousState = undoStack.last()
        val newUndoStack = undoStack.dropLast(1)

        // Save current state to redo stack
        val stateToSave = this.copy(undoStack = emptyList(), redoStack = emptyList())

        return previousState.copy(
            undoStack = newUndoStack,
            redoStack = redoStack + stateToSave
        )
    }

    /**
     * Redoes a previously undone move.
     * @return The next game state, or null if no moves to redo
     */
    fun redo(): GameState? {
        if (redoStack.isEmpty()) return null

        val nextState = redoStack.last()
        val newRedoStack = redoStack.dropLast(1)

        // Save current state to undo stack
        val stateToSave = this.copy(undoStack = emptyList(), redoStack = emptyList())

        return nextState.copy(
            undoStack = undoStack + stateToSave,
            redoStack = newRedoStack
        )
    }

    /**
     * Returns true if undo is available.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Returns true if redo is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Enters replay mode at position 0 (initial state).
     */
    fun enterReplayMode(): GameState {
        return copy(
            isReplayMode = true,
            replayPosition = 0
        ).reconstructStateAtPosition(0)
    }

    /**
     * Exits replay mode, returning to the final position.
     */
    fun exitReplayMode(): GameState {
        return reconstructStateAtPosition(moveHistory.size).copy(
            isReplayMode = false,
            replayPosition = 0
        )
    }

    /**
     * Navigates to the first position (initial state).
     */
    fun replayFirst(): GameState {
        if (!isReplayMode) return this
        return reconstructStateAtPosition(0).copy(replayPosition = 0)
    }

    /**
     * Navigates to the previous position.
     */
    fun replayPrevious(): GameState {
        if (!isReplayMode || replayPosition <= 0) return this
        val newPosition = replayPosition - 1
        return reconstructStateAtPosition(newPosition).copy(replayPosition = newPosition)
    }

    /**
     * Navigates to the next position.
     */
    fun replayNext(): GameState {
        if (!isReplayMode || replayPosition >= moveHistory.size) return this
        val newPosition = replayPosition + 1
        return reconstructStateAtPosition(newPosition).copy(replayPosition = newPosition)
    }

    /**
     * Navigates to the last position (final state).
     */
    fun replayLast(): GameState {
        if (!isReplayMode) return this
        val finalPosition = moveHistory.size
        return reconstructStateAtPosition(finalPosition).copy(replayPosition = finalPosition)
    }

    /**
     * Continues playing from the current replay position.
     * Truncates move history to current position and exits replay mode.
     */
    fun continueFromReplayPosition(): GameState {
        if (!isReplayMode) return this

        val truncatedHistory = moveHistory.take(replayPosition)
        return reconstructStateAtPosition(replayPosition).copy(
            moveHistory = truncatedHistory,
            isReplayMode = false,
            replayPosition = 0,
            undoStack = emptyList(),  // Clear undo/redo when branching
            redoStack = emptyList()
        )
    }

    /**
     * Returns true if can navigate to previous position.
     */
    fun canReplayPrevious(): Boolean = isReplayMode && replayPosition > 0

    /**
     * Returns true if can navigate to next position.
     */
    fun canReplayNext(): Boolean = isReplayMode && replayPosition < moveHistory.size

    /**
     * Reconstructs the game state at a specific position in move history.
     * Position 0 = initial state, N = after N moves applied.
     */
    private fun reconstructStateAtPosition(position: Int): GameState {
        var state = initialGameState()

        // Apply moves up to the target position
        for (i in 0 until position.coerceAtMost(moveHistory.size)) {
            state = state.applyMove(moveHistory[i])
        }

        // Preserve original move history and replay metadata
        return state.copy(
            moveHistory = this.moveHistory,
            isReplayMode = this.isReplayMode,
            replayPosition = position
        )
    }
}

/**
 * Creates the initial game state with all pieces in starting positions.
 * Traditional Janggi starting position with 28 pieces (14 per player).
 */
fun initialGameState(): GameState {
    val pieces = mutableMapOf<Position, Piece>()

    // CHO (top player) pieces
    // Back row (row 0): Chariot, Elephant, Horse, Guard, (empty), Guard, Elephant, Horse, Chariot
    pieces[Position(0, 0)] = Piece.Chariot(Player.CHO, Position(0, 0))
    pieces[Position(1, 0)] = Piece.Elephant(Player.CHO, Position(1, 0))
    pieces[Position(2, 0)] = Piece.Horse(Player.CHO, Position(2, 0))
    pieces[Position(3, 0)] = Piece.Guard(Player.CHO, Position(3, 0))
    // Position(4, 0) empty
    pieces[Position(5, 0)] = Piece.Guard(Player.CHO, Position(5, 0))
    pieces[Position(6, 0)] = Piece.Elephant(Player.CHO, Position(6, 0))
    pieces[Position(7, 0)] = Piece.Horse(Player.CHO, Position(7, 0))
    pieces[Position(8, 0)] = Piece.Chariot(Player.CHO, Position(8, 0))

    // General (row 1, center)
    pieces[Position(4, 1)] = Piece.General(Player.CHO, Position(4, 1))

    // Cannons (row 2, columns 1 and 7)
    pieces[Position(1, 2)] = Piece.Cannon(Player.CHO, Position(1, 2))
    pieces[Position(7, 2)] = Piece.Cannon(Player.CHO, Position(7, 2))

    // Soldiers (row 3, columns 0, 2, 4, 6, 8)
    pieces[Position(0, 3)] = Piece.Soldier(Player.CHO, Position(0, 3))
    pieces[Position(2, 3)] = Piece.Soldier(Player.CHO, Position(2, 3))
    pieces[Position(4, 3)] = Piece.Soldier(Player.CHO, Position(4, 3))
    pieces[Position(6, 3)] = Piece.Soldier(Player.CHO, Position(6, 3))
    pieces[Position(8, 3)] = Piece.Soldier(Player.CHO, Position(8, 3))

    // HAN (bottom player) pieces
    // Soldiers (row 6, columns 0, 2, 4, 6, 8)
    pieces[Position(0, 6)] = Piece.Soldier(Player.HAN, Position(0, 6))
    pieces[Position(2, 6)] = Piece.Soldier(Player.HAN, Position(2, 6))
    pieces[Position(4, 6)] = Piece.Soldier(Player.HAN, Position(4, 6))
    pieces[Position(6, 6)] = Piece.Soldier(Player.HAN, Position(6, 6))
    pieces[Position(8, 6)] = Piece.Soldier(Player.HAN, Position(8, 6))

    // Cannons (row 7, columns 1 and 7)
    pieces[Position(1, 7)] = Piece.Cannon(Player.HAN, Position(1, 7))
    pieces[Position(7, 7)] = Piece.Cannon(Player.HAN, Position(7, 7))

    // General (row 8, center)
    pieces[Position(4, 8)] = Piece.General(Player.HAN, Position(4, 8))

    // Back row (row 9): Chariot, Elephant, Horse, Guard, (empty), Guard, Elephant, Horse, Chariot
    pieces[Position(0, 9)] = Piece.Chariot(Player.HAN, Position(0, 9))
    pieces[Position(1, 9)] = Piece.Elephant(Player.HAN, Position(1, 9))
    pieces[Position(2, 9)] = Piece.Horse(Player.HAN, Position(2, 9))
    pieces[Position(3, 9)] = Piece.Guard(Player.HAN, Position(3, 9))
    // Position(4, 9) empty
    pieces[Position(5, 9)] = Piece.Guard(Player.HAN, Position(5, 9))
    pieces[Position(6, 9)] = Piece.Elephant(Player.HAN, Position(6, 9))
    pieces[Position(7, 9)] = Piece.Horse(Player.HAN, Position(7, 9))
    pieces[Position(8, 9)] = Piece.Chariot(Player.HAN, Position(8, 9))

    return GameState(board = pieces, currentPlayer = Player.CHO)
}
