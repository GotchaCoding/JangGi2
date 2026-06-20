package com.example.janggi2.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.rules.CheckDetector
import com.example.janggi2.domain.rules.GameRules
import com.example.janggi2.domain.usecase.LoadGameUseCase
import com.example.janggi2.domain.usecase.LoadGameForReplayUseCase
import com.example.janggi2.domain.usecase.SaveGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the game screen.
 */
data class GameUiState(
    val gameState: GameState = initialGameState(),
    val selectedPiece: Piece? = null,
    val validMoves: List<Position> = emptyList(),
    val showGameOverDialog: Boolean = false,
    val isLoading: Boolean = false
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
    private val loadGameForReplayUseCase: LoadGameForReplayUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val gameRules = GameRules()
    private val checkDetector = CheckDetector()

    init {
        // Try to load auto-saved game, otherwise start new game
        loadAutoSaveOrStartNew()
    }

    /**
     * Handles UI events from the game screen.
     */
    fun onEvent(event: GameUiEvent) {
        when (event) {
            is GameUiEvent.BoardTapped -> handleBoardTap(event.position)
            is GameUiEvent.ResetGame -> resetGame()
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
    private fun executeMove(piece: Piece, destination: Position) {
        val currentState = _uiState.value

        // Push current state to undo stack before making move
        var gameStateWithUndo = currentState.gameState.pushToUndoStack()

        // Create the move
        val move = Move(
            from = piece.position,
            to = destination,
            capturedPiece = gameStateWithUndo.getPieceAt(destination)
        )

        // Apply the move with rule checking
        val newGameState = gameRules.applyMoveWithRules(move, gameStateWithUndo)

        if (newGameState != null) {
            // Update UI state with new game state and clear selection
            _uiState.value = currentState.copy(
                gameState = newGameState,
                selectedPiece = null,
                validMoves = emptyList(),
                showGameOverDialog = newGameState.isGameOver()
            )

            // Auto-save after each move
            autoSave()
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
                validMoves = emptyList()
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
                validMoves = emptyList()
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
            isLoading = false
        )
        autoSave()
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
     */
    fun loadImportedGame(gameState: GameState) {
        _uiState.value = GameUiState(
            gameState = gameState,
            selectedPiece = null,
            validMoves = emptyList(),
            showGameOverDialog = false,
            isLoading = false
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
            validMoves = emptyList()
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
            validMoves = emptyList()
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
            validMoves = emptyList()
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
            validMoves = emptyList()
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
            validMoves = emptyList()
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
            validMoves = emptyList()
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
            validMoves = emptyList()
        )
        // Auto-save after continuing from replay
        autoSave()
    }
}
