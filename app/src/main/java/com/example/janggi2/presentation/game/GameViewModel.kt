package com.example.janggi2.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.GameMode
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.HorseElephantSetup
import com.example.janggi2.domain.model.initialGameState
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.rules.CheckDetector
import com.example.janggi2.domain.rules.GameRules
import com.example.janggi2.domain.rules.RepetitionJudge
import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.usecase.GetAiMoveUseCase
import com.example.janggi2.domain.usecase.GetHintUseCase
import com.example.janggi2.domain.usecase.InitializeAiUseCase
import com.example.janggi2.domain.usecase.LoadGameUseCase
import com.example.janggi2.domain.usecase.LoadGameForReplayUseCase
import com.example.janggi2.domain.usecase.SaveGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

/**
 * UI state for the game screen.
 */
data class GameUiState(
    val gameState: GameState = initialGameState(),
    val selectedPiece: Piece? = null,
    val validMoves: List<Position> = emptyList(),
    val showGameOverDialog: Boolean = false,
    val isLoading: Boolean = false,
    val showNewGameDialog: Boolean = false,
    val showAiSettingsDialog: Boolean = false,
    /** 엔진이 추천한 수. 보드에 화살표로만 그리고, 두지는 않습니다. */
    val hint: Move? = null,
    val isHintLoading: Boolean = false,
    val hintError: String? = null,
    /** 반복이라 막힌 수를 두려 했을 때 잠깐 띄우는 알림 */
    val repetitionNotice: Boolean = false,
    /**
     * 아래쪽에 놓고 보는 진영. 고른 쪽이 자기 앞에 오도록 판을 돌려 그립니다.
     * 예전부터 한이 아래였으므로 그것이 기본입니다.
     */
    val viewpoint: Player = Player.HAN
)

/**
 * ViewModel for the main game screen.
 * Manages game state using StateFlow with Hilt injection.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val saveGameUseCase: SaveGameUseCase,
    private val loadGameUseCase: LoadGameUseCase,
    private val loadGameForReplayUseCase: LoadGameForReplayUseCase,
    private val aiEngine: AiEngine,
    private val initializeAiUseCase: InitializeAiUseCase,
    private val getAiMoveUseCase: GetAiMoveUseCase,
    private val getHintUseCase: GetHintUseCase,
    repetitionJudge: RepetitionJudge
) : ViewModel() {
    companion object {
        private const val TAG = "GameViewModel"

        /** 반복수 알림이 떠 있는 시간 */
        private const val REPETITION_NOTICE_MS = 1000L
    }

    /** 반복수 알림을 지우는 타이머. 연달아 눌렀을 때 갈아끼웁니다. */
    private var repetitionNoticeJob: Job? = null
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val gameRules = GameRules(repetitionJudge)
    private val checkDetector = CheckDetector()

    init {
        // Try to load auto-saved game, otherwise start new game
        loadAutoSaveOrStartNew()

        // Initialize AI engine in background
        viewModelScope.launch {
            try {
                initializeAiUseCase()
                Log.d(TAG, "AI engine initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AI engine", e)
            }
        }
    }

    /**
     * Handles UI events from the game screen.
     */
    fun onEvent(event: GameUiEvent) {
        when (event) {
            is GameUiEvent.BoardTapped -> handleBoardTap(event.position)
            is GameUiEvent.ResetGame -> showNewGameDialog()
            is GameUiEvent.DismissGameOverDialog -> dismissGameOverDialog()
            is GameUiEvent.Undo -> undo()
            is GameUiEvent.Redo -> redo()
            is GameUiEvent.SaveGame -> saveGame(event.name)
            is GameUiEvent.EnterReplayMode -> enterReplayMode()
            is GameUiEvent.ExitReplayMode -> exitReplayMode()
            is GameUiEvent.ReplayFirst -> replayFirst()
            is GameUiEvent.ReplayPrevious -> replayPrevious()
            is GameUiEvent.ReplayNext -> replayNext()
            is GameUiEvent.ReplayLast -> replayLast()
            is GameUiEvent.ContinueFromReplay -> continueFromReplay()
            is GameUiEvent.RequestAiMove -> requestAiMove()
            is GameUiEvent.SetAiDifficulty -> setAiDifficulty(event.difficulty)
            is GameUiEvent.ShowNewGameDialog -> showNewGameDialog()
            is GameUiEvent.DismissNewGameDialog -> dismissNewGameDialog()
            is GameUiEvent.StartNewGame -> startNewGame(
                event.gameMode,
                event.aiDifficulty,
                event.myPlayer,
                event.choSetup,
                event.hanSetup
            )
            is GameUiEvent.ShowAiSettingsDialog -> showAiSettingsDialog()
            is GameUiEvent.DismissAiSettingsDialog -> dismissAiSettingsDialog()
            is GameUiEvent.RequestHint -> requestHint()
            is GameUiEvent.DismissHintError -> dismissHintError()
            is GameUiEvent.PassTurn -> passTurn()
        }
    }

    /**
     * Handles tapping on a board position.
     * - If no piece selected: selects the piece at the tapped position (if it belongs to current player)
     * - If piece selected and tapping same piece: deselects
     * - If piece selected and tapping valid move position: executes the move
     * - If piece selected and tapping another own piece: switches selection
     */
    private fun handleBoardTap(position: Position) {
        val currentState = _uiState.value

        // Don't allow moves if game is over or in replay mode
        if (currentState.gameState.isGameOver() || currentState.gameState.isReplayMode) {
            return
        }

        val tappedPiece = currentState.gameState.getPieceAt(position)

        when {
            // Case 1: No piece currently selected
            currentState.selectedPiece == null -> {
                if (tappedPiece != null && tappedPiece.player == currentState.gameState.currentPlayer) {
                    // Select this piece and calculate valid moves
                    selectPiece(tappedPiece)
                }
            }

            // Case 2: Tapping the same piece again - deselect
            currentState.selectedPiece.position == position -> {
                deselectPiece()
            }

            // Case 3: Tapping a different position with a piece selected
            else -> {
                // Check if this is a valid move position
                if (currentState.validMoves.contains(position)) {
                    // Execute the move
                    executeMove(currentState.selectedPiece, position)
                }
                // If tapping another piece of the same player, switch selection
                else if (tappedPiece != null && tappedPiece.player == currentState.gameState.currentPlayer) {
                    selectPiece(tappedPiece)
                }
                // Otherwise, deselect
                else {
                    deselectPiece()
                }
            }
        }
    }

    /**
     * Executes a move, updating the game state and switching turns.
     * Applies all game rules including check detection.
     * Automatically saves after each move.
     *
     * @param piece The piece to move
     * @param destination The destination position
     */
    /**
     * @param blockRepetition 반복이 되는 자리면 두지 않고 알림만 띄울지. AI 수에는
     *   끄는데, 막아 봐야 알릴 상대가 없고 대신 둘 수를 다시 고르는 길도 없어서 AI 가
     *   그대로 멈춰 버리기 때문입니다. 엔진은 탐색에서 이미 반복을 비김·패로 치므로
     *   굳이 고른 수라면 다른 선택지가 없었다는 뜻입니다.
     */
    private fun executeMove(
        piece: Piece,
        destination: Position,
        blockRepetition: Boolean = true
    ) {
        val currentState = _uiState.value

        // Push current state to undo stack before making move
        var gameStateWithUndo = currentState.gameState.pushToUndoStack()

        // Create the move
        val move = Move(
            from = piece.position,
            to = destination,
            capturedPiece = gameStateWithUndo.getPieceAt(destination),
            movedPiece = piece
        )

        // 반복이 되는 수는 그 자리에 둘 수 없습니다. 알림만 띄우고 선택은 살려 둡니다.
        if (blockRepetition && gameRules.wouldRepeat(move, gameStateWithUndo)) {
            showRepetitionNotice()
            return
        }

        // Apply the move with rule checking
        val newGameState = gameRules.applyMoveWithRules(move, gameStateWithUndo)

        if (newGameState != null) {
            // Update UI state with new game state and clear selection
            _uiState.value = currentState.copy(
                gameState = newGameState,
                selectedPiece = null,
                validMoves = emptyList(),
                hint = null,
                showGameOverDialog = newGameState.isGameOver()
            )

            // Auto-save after each move
            autoSave()

            // Check if it's AI's turn and request AI move
            if (newGameState.isAiTurn() && !newGameState.isGameOver()) {
                requestAiMove()
            }
        }
    }

    /**
     * Selects a piece and calculates its legal moves (considering check rules).
     */
    private fun selectPiece(piece: Piece) {
        // Use legal moves instead of just valid moves (filters out moves that leave king in check)
        val legalMoves = checkDetector.getLegalMoves(piece, _uiState.value.gameState)
        _uiState.value = _uiState.value.copy(
            selectedPiece = piece,
            validMoves = legalMoves
        )
    }

    /**
     * Deselects the currently selected piece.
     */
    private fun deselectPiece() {
        _uiState.value = _uiState.value.copy(
            selectedPiece = null,
            validMoves = emptyList()
        )
    }

    /**
     * Dismisses the game over dialog.
     */
    private fun dismissGameOverDialog() {
        _uiState.value = _uiState.value.copy(showGameOverDialog = false)
    }

    /**
     * Undoes the last move.
     */
    private fun undo() {
        val currentState = _uiState.value
        val previousState = currentState.gameState.undo()

        if (previousState != null) {
            _uiState.value = currentState.copy(
                gameState = previousState,
                selectedPiece = null,
                validMoves = emptyList(),
                hint = null
            )
            autoSave()
        }
    }

    /**
     * Redoes a previously undone move.
     */
    private fun redo() {
        val currentState = _uiState.value
        val nextState = currentState.gameState.redo()

        if (nextState != null) {
            _uiState.value = currentState.copy(
                gameState = nextState,
                selectedPiece = null,
                validMoves = emptyList(),
                hint = null
            )
            autoSave()
        }
    }

    /**
     * Saves the current game with a custom name.
     */
    private fun saveGame(name: String) {
        viewModelScope.launch {
            try {
                saveGameUseCase(uiState.value.gameState, name)
            } catch (e: Exception) {
                // Handle save error
            }
        }
    }

    /**
     * Auto-saves the current game state.
     * Skips auto-save if in replay mode.
     */
    private fun autoSave() {
        // Don't auto-save in replay mode
        if (_uiState.value.gameState.isReplayMode) {
            return
        }

        viewModelScope.launch {
            try {
                gameRepository.autoSave(uiState.value.gameState)
            } catch (e: Exception) {
                // Silently fail auto-save
            }
        }
    }

    /**
     * Loads auto-saved game or starts a new game.
     */
    private fun loadAutoSaveOrStartNew() {
        viewModelScope.launch {
            try {
                val autoSavedState = gameRepository.loadAutoSave()
                if (autoSavedState != null) {
                    _uiState.value = GameUiState(
                        gameState = autoSavedState,
                        selectedPiece = null,
                        validMoves = emptyList(),
                        showGameOverDialog = false,
                        isLoading = false
                    )
                } else {
                    resetGame()
                }
            } catch (e: Exception) {
                resetGame()
            }
        }
    }

    /**
     * Loads a saved game by ID.
     */
    fun loadGame(gameId: Long) {
        viewModelScope.launch {
            try {
                val loadedState = loadGameUseCase(gameId)
                if (loadedState != null) {
                    _uiState.value = GameUiState(
                        gameState = loadedState,
                        selectedPiece = null,
                        validMoves = emptyList(),
                        showGameOverDialog = false,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                // Handle load error
            }
        }
    }

    /**
     * Resets the game to the initial state.
     */
    fun resetGame() {
        _uiState.value = GameUiState(
            gameState = initialGameState(),
            selectedPiece = null,
            validMoves = emptyList(),
            showGameOverDialog = false,
            isLoading = false,
            showNewGameDialog = false,
            showAiSettingsDialog = false
        )
        autoSave()
    }

    /**
     * Shows the new game dialog.
     */
    private fun showNewGameDialog() {
        _uiState.value = _uiState.value.copy(showNewGameDialog = true)
    }

    /**
     * Dismisses the new game dialog.
     */
    private fun dismissNewGameDialog() {
        _uiState.value = _uiState.value.copy(showNewGameDialog = false)
    }

    /**
     * Starts a new game with specified settings.
     */
    private fun startNewGame(
        gameMode: GameMode,
        aiDifficulty: Int,
        myPlayer: Player,
        choSetup: HorseElephantSetup,
        hanSetup: HorseElephantSetup
    ) {
        // Create new game state with specified settings
        val newGameState = initialGameState(
            gameMode = gameMode,
            aiDifficulty = aiDifficulty,
            // 내가 잡은 진영의 반대편을 AI 가 맡습니다.
            aiPlayer = myPlayer.opponent(),
            choSetup = choSetup,
            hanSetup = hanSetup
        )

        _uiState.value = GameUiState(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            showGameOverDialog = false,
            isLoading = false,
            showNewGameDialog = false,
            showAiSettingsDialog = false,
            // 고른 진영이 자기 앞에 오도록 판을 돌립니다.
            viewpoint = myPlayer
        )

        // Auto-save
        autoSave()

        // If AI plays first (CHO), request AI move
        if (newGameState.isAiTurn()) {
            Log.d(TAG, "AI plays first, requesting AI move")
            requestAiMove()
        }
    }

    /**
     * Shows the AI settings dialog.
     */
    private fun showAiSettingsDialog() {
        _uiState.value = _uiState.value.copy(showAiSettingsDialog = true)
    }

    /**
     * Dismisses the AI settings dialog.
     */
    private fun dismissAiSettingsDialog() {
        _uiState.value = _uiState.value.copy(showAiSettingsDialog = false)
    }

    /**
     * Loads a saved game for replay mode.
     */
    fun loadGameForReplay(gameId: Long) {
        viewModelScope.launch {
            try {
                val loadedState = loadGameForReplayUseCase(gameId)
                if (loadedState != null) {
                    _uiState.value = GameUiState(
                        gameState = loadedState,
                        selectedPiece = null,
                        validMoves = emptyList(),
                        showGameOverDialog = false,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                // Handle load error
            }
        }
    }

    /**
     * Loads an imported game state from image recognition.
     *
     * @param viewpoint 사진 속에서 실제로 아래쪽에 있던 진영. 그 진영이 화면 아래에
     *   오도록 판을 그립니다(사진과 같은 모습).
     */
    fun loadImportedGame(gameState: GameState, viewpoint: Player = Player.HAN) {
        _uiState.value = GameUiState(
            gameState = gameState,
            selectedPiece = null,
            validMoves = emptyList(),
            showGameOverDialog = false,
            isLoading = false,
            viewpoint = viewpoint
        )
        autoSave()
    }

    /**
     * Enters replay mode.
     */
    private fun enterReplayMode() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.enterReplayMode()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Exits replay mode.
     */
    private fun exitReplayMode() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.exitReplayMode()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Navigates to the first position in replay mode.
     */
    private fun replayFirst() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.replayFirst()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Navigates to the previous position in replay mode.
     */
    private fun replayPrevious() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.replayPrevious()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Navigates to the next position in replay mode.
     */
    private fun replayNext() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.replayNext()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Navigates to the last position in replay mode.
     */
    private fun replayLast() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.replayLast()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
    }

    /**
     * Continues playing from the current replay position.
     */
    private fun continueFromReplay() {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.continueFromReplayPosition()
        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null
        )
        // Auto-save after continuing from replay
        autoSave()
    }

    /**
     * Requests AI to calculate and make a move.
     */
    private fun requestAiMove() {
        val currentState = _uiState.value

        // Don't request AI move if game is over or in replay mode
        if (currentState.gameState.isGameOver() || currentState.gameState.isReplayMode) {
            return
        }

        // Don't request AI move if not AI's turn
        if (!currentState.gameState.isAiTurn()) {
            return
        }

        viewModelScope.launch {
            try {
                // Show loading state
                _uiState.value = currentState.copy(isLoading = true)

                Log.d(TAG, "Requesting AI move (difficulty: ${currentState.gameState.aiDifficulty})")

                // Calculate AI move
                val aiMove = getAiMoveUseCase(
                    gameState = currentState.gameState,
                    thinkTimeMs = calculateThinkTime(currentState.gameState.aiDifficulty)
                )

                if (aiMove != null) {
                    Log.d(TAG, "AI move: ${aiMove.from} -> ${aiMove.to}")

                    // Get the piece at the from position
                    val piece = currentState.gameState.getPieceAt(aiMove.from)

                    if (piece != null) {
                        // Execute the AI move
                        executeMove(piece, aiMove.to, blockRepetition = false)
                    } else {
                        Log.e(TAG, "No piece found at AI move source position: ${aiMove.from}")
                    }
                } else {
                    Log.w(TAG, "AI returned no move")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error requesting AI move", e)
            } finally {
                // Clear loading state
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Asks the engine for the best move for the side to move and shows it as an arrow.
     * 수를 두지는 않습니다.
     */
    private fun requestHint() {
        val currentState = _uiState.value
        val gameState = currentState.gameState

        if (currentState.isHintLoading) return
        if (gameState.isGameOver() || gameState.isReplayMode) return
        if (!aiEngine.isReady()) {
            _uiState.value = currentState.copy(hintError = "AI 엔진을 준비 중입니다. 잠시 후 다시 시도해주세요.")
            return
        }
        // 사진 인식은 일부만 인식된 판도 허용하므로 궁이 빠져 있을 수 있습니다.
        // 그 상태로 엔진에 넘기면 합법수 생성이 정의되지 않으므로 미리 막습니다.
        if (gameState.getGeneral(Player.CHO) == null || gameState.getGeneral(Player.HAN) == null) {
            _uiState.value = currentState.copy(hintError = "궁이 없어 힌트를 계산할 수 없습니다.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHintLoading = true, hintError = null)
            try {
                val hint = getHintUseCase(gameState)

                // 계산하는 동안 판이 바뀌었으면 결과를 버립니다.
                if (_uiState.value.gameState !== gameState) {
                    Log.d(TAG, "Discarding stale hint")
                    return@launch
                }

                if (hint == null) {
                    _uiState.value = _uiState.value.copy(hintError = "추천할 수를 찾지 못했습니다.")
                } else {
                    Log.d(TAG, "Hint: ${hint.from} -> ${hint.to}")
                    _uiState.value = _uiState.value.copy(hint = hint)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting hint", e)
                _uiState.value = _uiState.value.copy(hintError = "힌트 계산 중 오류가 발생했습니다.")
            } finally {
                _uiState.value = _uiState.value.copy(isHintLoading = false)
            }
        }
    }

    /**
     * 한 수 쉼. 판은 그대로 두고 차례만 넘깁니다.
     * 장군을 맞은 상태에서는 [GameRules.canPass] 가 막습니다.
     */
    private fun passTurn() {
        val currentState = _uiState.value
        val withUndo = currentState.gameState.pushToUndoStack()

        // 판이 그대로여도 차례가 넘어가 국면이 되풀이될 수 있습니다.
        if (gameRules.passWouldRepeat(withUndo)) {
            showRepetitionNotice()
            return
        }

        val newGameState = gameRules.applyPass(withUndo) ?: return

        _uiState.value = currentState.copy(
            gameState = newGameState,
            selectedPiece = null,
            validMoves = emptyList(),
            hint = null,
            showGameOverDialog = newGameState.isGameOver()
        )
        autoSave()

        if (newGameState.isAiTurn() && !newGameState.isGameOver()) {
            requestAiMove()
        }
    }

    private fun dismissHintError() {
        _uiState.value = _uiState.value.copy(hintError = null)
    }

    /**
     * "반복수 자리입니다" 알림을 1초 띄웠다 지웁니다.
     *
     * 이전 알림이 떠 있는 동안 또 누르면 그 타이머를 취소하고 다시 셉니다. 그러지
     * 않으면 먼저 걸린 타이머가 새 알림을 일찍 지워 버립니다.
     */
    private fun showRepetitionNotice() {
        repetitionNoticeJob?.cancel()
        _uiState.value = _uiState.value.copy(repetitionNotice = true)
        repetitionNoticeJob = viewModelScope.launch {
            delay(REPETITION_NOTICE_MS)
            _uiState.value = _uiState.value.copy(repetitionNotice = false)
        }
    }

    /**
     * Sets AI difficulty level.
     */
    private fun setAiDifficulty(difficulty: Int) {
        val currentState = _uiState.value
        val newGameState = currentState.gameState.copy(aiDifficulty = difficulty)
        _uiState.value = currentState.copy(gameState = newGameState)
        Log.d(TAG, "AI difficulty set to $difficulty")
    }

    /**
     * Calculates think time based on difficulty level.
     * Lower difficulty = less time, higher difficulty = more time.
     */
    private fun calculateThinkTime(difficulty: Int): Int {
        return when {
            difficulty <= 5 -> 1000   // Beginner: 1 second
            difficulty <= 10 -> 2000  // Medium: 2 seconds
            difficulty <= 15 -> 3000  // Hard: 3 seconds
            else -> 5000              // Expert: 5 seconds
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 엔진은 @Singleton 이고 네이티브 탐색은 viewModelScope 취소로 멈출 수 없습니다.
        // 여기서 destroy() 하면 탐색 중인 객체가 삭제될 수 있어 프로세스 수명 동안 살려둡니다.
    }
}
