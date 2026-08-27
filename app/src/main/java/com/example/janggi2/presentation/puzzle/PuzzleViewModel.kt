package com.example.janggi2.presentation.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.Puzzle
import com.example.janggi2.domain.rules.CheckDetector
import com.example.janggi2.domain.usecase.ExtractBlunderPuzzlesUseCase
import com.example.janggi2.domain.usecase.GradePuzzleAttemptUseCase
import com.example.janggi2.domain.usecase.PuzzleAttemptResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PuzzleStatus { PENDING, GRADING, CORRECT, INCORRECT }

data class PuzzleUiState(
    val puzzles: List<Puzzle> = emptyList(),
    val currentIndex: Int = 0,
    val selectedPiece: Piece? = null,
    val validMoves: List<Position> = emptyList(),
    val status: PuzzleStatus = PuzzleStatus.PENDING,
    val gradeResult: PuzzleAttemptResult? = null,
    val attemptedMove: Move? = null,
    val viewpoint: Player = Player.HAN,
    val error: String? = null,
    val correctCount: Int = 0,
    val isFinished: Boolean = false
) {
    val currentPuzzle: Puzzle? get() = puzzles.getOrNull(currentIndex)
}

/**
 * 악수 직전 국면을 다시 풀어보는 퍼즐 화면의 ViewModel. 정답 판정은 저장된 값이 아니라
 * 매번 [GradePuzzleAttemptUseCase]로 그 자리에서 엔진에 새로 물어봅니다.
 */
@HiltViewModel
class PuzzleViewModel @Inject constructor(
    private val extractBlunderPuzzlesUseCase: ExtractBlunderPuzzlesUseCase,
    private val gradePuzzleAttemptUseCase: GradePuzzleAttemptUseCase
) : ViewModel() {
    private val checkDetector = CheckDetector()

    private val _uiState = MutableStateFlow(PuzzleUiState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    fun loadPuzzles(gameState: GameState, review: GameReview, viewpoint: Player) {
        val puzzles = extractBlunderPuzzlesUseCase(gameState, review)
        _uiState.value = PuzzleUiState(
            puzzles = puzzles,
            viewpoint = viewpoint,
            isFinished = puzzles.isEmpty()
        )
    }

    fun onEvent(event: PuzzleUiEvent) {
        when (event) {
            is PuzzleUiEvent.BoardTapped -> handleBoardTap(event.position)
            is PuzzleUiEvent.NextPuzzle -> nextPuzzle()
            is PuzzleUiEvent.FlipBoard -> flipBoard()
            is PuzzleUiEvent.DismissError -> dismissError()
        }
    }

    private fun handleBoardTap(position: Position) {
        val currentState = _uiState.value
        if (currentState.status != PuzzleStatus.PENDING) return
        val puzzle = currentState.currentPuzzle ?: return

        val tappedPiece = puzzle.position.getPieceAt(position)

        when {
            currentState.selectedPiece == null -> {
                if (tappedPiece != null && tappedPiece.player == puzzle.position.currentPlayer) {
                    selectPiece(tappedPiece, puzzle)
                }
            }
            currentState.selectedPiece.position == position -> {
                deselectPiece()
            }
            else -> {
                if (currentState.validMoves.contains(position)) {
                    attemptMove(puzzle, currentState.selectedPiece, position)
                } else if (tappedPiece != null && tappedPiece.player == puzzle.position.currentPlayer) {
                    selectPiece(tappedPiece, puzzle)
                } else {
                    deselectPiece()
                }
            }
        }
    }

    private fun selectPiece(piece: Piece, puzzle: Puzzle) {
        val legalMoves = checkDetector.getLegalMoves(piece, puzzle.position)
        _uiState.value = _uiState.value.copy(selectedPiece = piece, validMoves = legalMoves)
    }

    private fun deselectPiece() {
        _uiState.value = _uiState.value.copy(selectedPiece = null, validMoves = emptyList())
    }

    private fun attemptMove(puzzle: Puzzle, piece: Piece, destination: Position) {
        val move = Move(
            from = piece.position,
            to = destination,
            capturedPiece = puzzle.position.getPieceAt(destination),
            movedPiece = piece
        )

        _uiState.value = _uiState.value.copy(
            status = PuzzleStatus.GRADING,
            selectedPiece = null,
            validMoves = emptyList(),
            attemptedMove = move
        )

        viewModelScope.launch {
            val result = gradePuzzleAttemptUseCase(puzzle.position, move)
            val latest = _uiState.value
            // 채점하는 동안 다음 퍼즐로 넘어갔으면 결과를 버립니다.
            if (latest.currentPuzzle != puzzle) return@launch

            if (result == null) {
                _uiState.value = latest.copy(
                    status = PuzzleStatus.PENDING,
                    attemptedMove = null,
                    error = "채점 중 오류가 발생했습니다."
                )
            } else {
                _uiState.value = latest.copy(
                    status = if (result.correct) PuzzleStatus.CORRECT else PuzzleStatus.INCORRECT,
                    gradeResult = result,
                    correctCount = latest.correctCount + if (result.correct) 1 else 0
                )
            }
        }
    }

    private fun nextPuzzle() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentIndex + 1
        if (nextIndex >= currentState.puzzles.size) {
            _uiState.value = currentState.copy(isFinished = true)
        } else {
            _uiState.value = currentState.copy(
                currentIndex = nextIndex,
                selectedPiece = null,
                validMoves = emptyList(),
                status = PuzzleStatus.PENDING,
                gradeResult = null,
                attemptedMove = null
            )
        }
    }

    private fun flipBoard() {
        _uiState.value = _uiState.value.copy(viewpoint = _uiState.value.viewpoint.opponent())
    }

    private fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
