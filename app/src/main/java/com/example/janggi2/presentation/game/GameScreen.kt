package com.example.janggi2.presentation.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Position
import com.example.janggi2.presentation.common.ConfirmDialog
import com.example.janggi2.presentation.game.components.GameControls
import com.example.janggi2.presentation.game.components.HighlightLayer
import com.example.janggi2.presentation.game.components.JangGiBoard
import com.example.janggi2.presentation.game.components.PieceView
import com.example.janggi2.presentation.game.components.ReplayControls
import com.example.janggi2.presentation.game.components.TurnIndicator
import com.example.janggi2.ui.theme.JangGi2Theme
import kotlin.math.roundToInt

/**
 * Main game screen that displays the Janggi board and pieces.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    onNavigateToSavedGames: () -> Unit = {},
    onNavigateToImport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "장기",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            // Current player indicator or replay mode indicator
            if (uiState.gameState.isReplayMode) {
                Text(
                    text = "복기 모드",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                TurnIndicator(
                    currentPlayer = uiState.gameState.currentPlayer,
                    moveCount = uiState.gameState.getMoveCount(),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Check status message
            if (uiState.gameState.status == GameStatus.CHECK) {
                Text(
                    text = "⚠️ 장군!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Board with pieces
            BoardWithPieces(
                pieces = uiState.gameState.board,
                selectedPiece = if (uiState.gameState.isReplayMode) null else uiState.selectedPiece,
                validMoves = if (uiState.gameState.isReplayMode) emptyList() else uiState.validMoves,
                checkPosition = getCheckPosition(uiState.gameState),
                onBoardTap = { position ->
                    viewModel.onEvent(GameUiEvent.BoardTapped(position))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            )

            // Game controls or replay controls
            if (uiState.gameState.isReplayMode) {
                ReplayControls(
                    currentPosition = uiState.gameState.replayPosition,
                    totalMoves = uiState.gameState.moveHistory.size,
                    canPrevious = uiState.gameState.canReplayPrevious(),
                    canNext = uiState.gameState.canReplayNext(),
                    onFirstClick = {
                        viewModel.onEvent(GameUiEvent.ReplayFirst)
                    },
                    onPreviousClick = {
                        viewModel.onEvent(GameUiEvent.ReplayPrevious)
                    },
                    onNextClick = {
                        viewModel.onEvent(GameUiEvent.ReplayNext)
                    },
                    onLastClick = {
                        viewModel.onEvent(GameUiEvent.ReplayLast)
                    },
                    onContinueClick = {
                        viewModel.onEvent(GameUiEvent.ContinueFromReplay)
                    },
                    onExitReplayClick = {
                        viewModel.onEvent(GameUiEvent.ExitReplayMode)
                    }
                )
            } else {
                GameControls(
                    canUndo = uiState.gameState.canUndo(),
                    canRedo = uiState.gameState.canRedo(),
                    onUndoClick = {
                        viewModel.onEvent(GameUiEvent.Undo)
                    },
                    onRedoClick = {
                        viewModel.onEvent(GameUiEvent.Redo)
                    },
                    onSaveClick = { name ->
                        viewModel.onEvent(GameUiEvent.SaveGame(name))
                    },
                    onLoadClick = {
                        onNavigateToSavedGames()
                    },
                    onImportClick = {
                        onNavigateToImport()
                    },
                    onResetClick = {
                        viewModel.onEvent(GameUiEvent.ResetGame)
                    }
                )
            }
        }
    }

    // Game over dialog
    if (uiState.showGameOverDialog) {
        val (title, message) = getGameOverMessage(uiState.gameState)
        ConfirmDialog(
            title = title,
            message = message,
            confirmText = "새 게임",
            dismissText = "닫기",
            onConfirm = {
                viewModel.onEvent(GameUiEvent.ResetGame)
            },
            onDismiss = {
                viewModel.onEvent(GameUiEvent.DismissGameOverDialog)
            }
        )
    }
}

/**
 * Gets the position of a General that's in check, or null.
 */
private fun getCheckPosition(gameState: com.example.janggi2.domain.model.GameState): Position? {
    if (gameState.status == GameStatus.CHECK || gameState.status == GameStatus.CHECKMATE) {
        return gameState.getGeneral(gameState.currentPlayer)?.position
    }
    return null
}

/**
 * Gets the game over dialog title and message.
 */
private fun getGameOverMessage(gameState: com.example.janggi2.domain.model.GameState): Pair<String, String> {
    return when (gameState.status) {
        GameStatus.CHECKMATE -> {
            val winnerName = gameState.winner?.displayName() ?: "?"
            "게임 종료" to "$winnerName 승리!\n체크메이트로 게임이 끝났습니다."
        }
        GameStatus.STALEMATE -> {
            "게임 종료" to "무승부\n더 이상 움직일 수 있는 수가 없습니다."
        }
        GameStatus.BIKJANG -> {
            val winnerName = gameState.winner?.displayName() ?: "?"
            "게임 종료" to "$winnerName 승리!\n빅장(맞장)으로 게임이 끝났습니다."
        }
        else -> "게임 종료" to "게임이 끝났습니다."
    }
}

/**
 * Displays the board with all pieces positioned on it.
 */
@Composable
private fun BoardWithPieces(
    pieces: Map<Position, Piece>,
    selectedPiece: Piece?,
    validMoves: List<Position>,
    onBoardTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
    checkPosition: Position? = null
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardWidth = maxWidth
        val boardHeight = maxWidth / 0.9f // Maintain 9:10 aspect ratio

        // Calculate cell dimensions
        val cellWidth = boardWidth / 8f // 9 columns = 8 spaces
        val cellHeight = boardHeight / 9f // 10 rows = 9 spaces

        Box(
            modifier = Modifier
                .size(width = boardWidth, height = boardHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Convert tap coordinates to board position
                        val col = (offset.x / cellWidth.toPx()).roundToInt()
                        val row = (offset.y / cellHeight.toPx()).roundToInt()
                        val position = Position(col, row)

                        if (position.isValid()) {
                            onBoardTap(position)
                        }
                    }
                }
        ) {
            // Draw the board
            JangGiBoard()

            // Draw highlight layer (selection + valid moves + check indicator)
            HighlightLayer(
                selectedPosition = selectedPiece?.position,
                validMoves = validMoves,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                checkPosition = checkPosition
            )

            // Draw all pieces
            pieces.forEach { (position, piece) ->
                val offsetX = (position.col * cellWidth.value).dp - 20.dp // Center piece (piece size / 2)
                val offsetY = (position.row * cellHeight.value).dp - 20.dp

                PieceView(
                    piece = piece,
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    JangGi2Theme {
        GameScreen()
    }
}
