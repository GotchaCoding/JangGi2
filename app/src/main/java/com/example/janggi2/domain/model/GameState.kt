package com.example.janggi2.domain.model

/**
 * Represents the status of the game.
 */
enum class GameStatus {
    ONGOING,    // Game in progress
    CHECK,      // Current player's opponent is in check
    CHECKMATE,  // Game over - checkmate
    STALEMATE,  // Game over - stalemate (no legal moves)

    // 빅장 규칙은 제거됐지만 상수는 남깁니다. 저장된 대국이 이름으로 직렬화돼 있어
    // (GameMapper 가 status.name / GameStatus.valueOf 를 씁니다) 지우면 옛 기록이 안 열립니다.
    BIKJANG,

    /** 외통 없이 끝나 기물 점수로 승부가 갈린 경우 */
    POINT_WIN,

    /**
     * 장군 반복·수 반복으로 반칙패. 판정은 엔진이 합니다 - [GameStatus.POINT_WIN] 때와
     * 같이, 이 상수를 모르는 옛 빌드로 내려가면 GameMapper 의 valueOf 가 터집니다.
     */
    FOUL_LOSS
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
    val replayPosition: Int = 0,  // 0 = initial state, N = after N moves
    val gameMode: GameMode = GameMode.PLAYER_VS_PLAYER,
    val aiDifficulty: Int = 10,  // 1-20, default to medium difficulty
    val aiPlayer: Player = Player.HAN,  // Which player is AI (default: HAN)

    /**
     * 이 대국이 시작된 판. null 이면 표준 시작 배치입니다.
     *
     * 반복 규칙 판정에 필요합니다 - 엔진이 국면 이력을 거슬러 올라가려면 시작 국면부터
     * [moveHistory] 를 재생해야 하는데, 사진에서 불러온 판은 표준 배치가 아닙니다.
     * 시작 차례는 이 앱에서 언제나 초이므로 판만 있으면 충분합니다.
     */
    val startBoard: Map<Position, Piece>? = null
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

        // 한 수 쉼은 출발과 도착이 같습니다. 그때 board[to] 는 자기 자신이라
        // 그대로 두면 자기 기물을 잡은 것으로 기록됩니다.
        val capturedPiece = if (move.from == move.to) null else board[move.to]
        val moveRecord = move.copy(capturedPiece = capturedPiece)

        // copy() 여야 합니다. 생성자를 부르면 undoStack·redoStack·gameMode·
        // aiDifficulty·aiPlayer·복기 상태가 매 수마다 기본값으로 돌아가서,
        // 되돌리기가 늘 비활성이 되고 AI 대국이 첫 수 뒤에 멈춥니다.
        return copy(
            board = newBoard,
            currentPlayer = currentPlayer.opponent(),
            moveHistory = moveHistory + moveRecord
            // status/winner 는 GameRules 가 이어서 정합니다
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
     * Returns true if the game is over.
     */
    /**
     * 한 수 쉼을 둘 수 있는지.
     *
     * status 가 CHECK 면 "둘 차례인 쪽이 장군을 맞은 상태"입니다
     * (evaluateGameStatus 가 수를 둔 뒤 상대 기준으로 판정하므로).
     * 엔진도 장군일 때는 한 수 쉼을 합법성 검사에서 걸러냅니다.
     */
    fun canPass(): Boolean =
        !isGameOver() && !isReplayMode && status != GameStatus.CHECK

    fun isGameOver(): Boolean = status in listOf(
        GameStatus.CHECKMATE,
        GameStatus.STALEMATE,
        GameStatus.BIKJANG,
        GameStatus.POINT_WIN,
        GameStatus.FOUL_LOSS
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
     * Returns true if it's currently the AI's turn to move.
     */
    fun isAiTurn(): Boolean = gameMode.isAiGame() && currentPlayer == aiPlayer

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
     * 임의의 위치로 곧장 이동합니다. 수 기록을 눌러 그 자리로 건너뛸 때 씁니다.
     * 범위를 벗어난 값은 0..moveHistory.size 로 눌러 담습니다.
     */
    fun replayTo(position: Int): GameState {
        if (!isReplayMode) return this
        val clamped = position.coerceIn(0, moveHistory.size)
        return reconstructStateAtPosition(clamped).copy(replayPosition = clamped)
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
     *
     * 사진 불러오기로 시작한 대국은 [startBoard] 가 표준 배치가 아니므로 거기서부터
     * 다시 재생해야 합니다 - 그냥 [initialGameState] 에서 시작하면 복기·되돌리기·
     * AI 리뷰가 모두 엉뚱한 판을 보게 됩니다.
     */
    internal fun reconstructStateAtPosition(position: Int): GameState {
        var state = GameState(board = startBoard ?: initialGameState().board, currentPlayer = Player.CHO)

        // Apply moves up to the target position
        for (i in 0 until position.coerceAtMost(moveHistory.size)) {
            state = state.applyMove(moveHistory[i])
        }

        // Preserve original move history and replay metadata
        return state.copy(
            moveHistory = this.moveHistory,
            isReplayMode = this.isReplayMode,
            replayPosition = position,
            startBoard = this.startBoard
        )
    }
}

/**
 * Creates the initial game state with all pieces in starting positions.
 * Traditional Janggi starting position with 32 pieces (16 per player).
 *
 * @param gameMode Game mode (PvP or PvAI), defaults to PvP
 * @param aiDifficulty AI difficulty level (1-20), defaults to 10 (medium)
 * @param aiPlayer Which player the AI controls (CHO or HAN), defaults to HAN
 * @param choSetup 초의 마·상 배치
 * @param hanSetup 한의 마·상 배치
 */
fun initialGameState(
    gameMode: GameMode = GameMode.PLAYER_VS_PLAYER,
    aiDifficulty: Int = 10,
    aiPlayer: Player = Player.HAN,
    choSetup: HorseElephantSetup = HorseElephantSetup.defaultFor(Player.CHO),
    hanSetup: HorseElephantSetup = HorseElephantSetup.defaultFor(Player.HAN)
): GameState {
    val pieces = mutableMapOf<Position, Piece>()

    // CHO (top player) pieces
    // Back row (row 0): Chariot, [마·상 4자리], Guard, (empty), Guard, ..., Chariot
    pieces[Position(0, 0)] = Piece.Chariot(Player.CHO, Position(0, 0))
    pieces[Position(3, 0)] = Piece.Guard(Player.CHO, Position(3, 0))
    // Position(4, 0) empty
    pieces[Position(5, 0)] = Piece.Guard(Player.CHO, Position(5, 0))
    pieces[Position(8, 0)] = Piece.Chariot(Player.CHO, Position(8, 0))
    pieces.putAll(backRankHorsesAndElephants(Player.CHO, row = 0, setup = choSetup))

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

    // Back row (row 9): Chariot, [마·상 4자리], Guard, (empty), Guard, ..., Chariot
    pieces[Position(0, 9)] = Piece.Chariot(Player.HAN, Position(0, 9))
    pieces[Position(3, 9)] = Piece.Guard(Player.HAN, Position(3, 9))
    // Position(4, 9) empty
    pieces[Position(5, 9)] = Piece.Guard(Player.HAN, Position(5, 9))
    pieces[Position(8, 9)] = Piece.Chariot(Player.HAN, Position(8, 9))
    pieces.putAll(backRankHorsesAndElephants(Player.HAN, row = 9, setup = hanSetup))

    return GameState(
        board = pieces,
        currentPlayer = Player.CHO,
        gameMode = gameMode,
        aiDifficulty = aiDifficulty,
        aiPlayer = aiPlayer,
        // 마·상 배치가 기본값이 아닐 수 있어(대국자가 고름), 복기·수 기록 이동이
        // reconstructStateAtPosition 으로 되감을 때 이 판을 그대로 시작점으로 씁니다.
        // 없으면 항상 기본 배치로 되감아 실제와 다른 기물이 나옵니다.
        startBoard = pieces
    )
}

/** 고른 배치대로 뒷줄 네 자리에 마와 상을 놓습니다. */
private fun backRankHorsesAndElephants(
    player: Player,
    row: Int,
    setup: HorseElephantSetup
): Map<Position, Piece> =
    setup.backRank(player).entries.associate { (col, isHorse) ->
        val position = Position(col, row)
        position to if (isHorse) Piece.Horse(player, position) else Piece.Elephant(player, position)
    }
