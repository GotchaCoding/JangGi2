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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Position
import com.example.janggi2.presentation.common.ConfirmDialog
import com.example.janggi2.presentation.game.components.AiSettingsDialog
import com.example.janggi2.presentation.game.components.GameControls
import com.example.janggi2.domain.rules.GameRules
import com.example.janggi2.domain.rules.MaterialScore
import com.example.janggi2.presentation.game.components.HighlightLayer
import com.example.janggi2.presentation.game.components.HintLayer
import com.example.janggi2.presentation.game.components.JangGiBoard
import com.example.janggi2.presentation.game.components.MoveHistoryPanel
import com.example.janggi2.presentation.game.components.NewGameDialog
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
    onNavigateToImport: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {}
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

            // 점수는 판에서 계산하므로 복기 중에도 그 시점 점수가 그대로 맞습니다.
            val scoreboard = remember(uiState.gameState.board) {
                MaterialScore.of(uiState.gameState)
            }

            // Current player indicator or replay mode indicator
            if (uiState.gameState.isReplayMode) {
                Text(
                    text = "복기 모드",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            TurnIndicator(
                currentPlayer = uiState.gameState.currentPlayer,
                moveCount = uiState.gameState.getMoveCount(),
                scoreboard = scoreboard,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                hintMove = if (uiState.gameState.isReplayMode) null else uiState.hint,
                onBoardTap = { position ->
                    viewModel.onEvent(GameUiEvent.BoardTapped(position))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            )

            // 수 기록
            MoveHistoryPanel(
                moves = uiState.gameState.moveHistory,
                scoreboard = scoreboard,
                modifier = Modifier.padding(bottom = 4.dp)
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
                    isHintLoading = uiState.isHintLoading,
                    onHintClick = {
                        viewModel.onEvent(GameUiEvent.RequestHint)
                    },
                    canPass = uiState.gameState.canPass(),
                    onPassClick = {
                        viewModel.onEvent(GameUiEvent.PassTurn)
                    },
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
                    },
                    onDebugClick = {
                        onNavigateToDebug()
                    },
                    isAiGame = uiState.gameState.gameMode.isAiGame(),
                    onShowAiSettings = {
                        viewModel.onEvent(GameUiEvent.ShowAiSettingsDialog)
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

    // Hint could not be produced (engine not ready, no general on the board, ...)
    uiState.hintError?.let { message ->
        ConfirmDialog(
            title = "AI 힌트",
            message = message,
            confirmText = "확인",
            onConfirm = { viewModel.onEvent(GameUiEvent.DismissHintError) },
            onDismiss = { viewModel.onEvent(GameUiEvent.DismissHintError) }
        )
    }

    // New game dialog
    if (uiState.showNewGameDialog) {
        NewGameDialog(
            onDismiss = { viewModel.onEvent(GameUiEvent.DismissNewGameDialog) },
            onStartGame = { mode, difficulty, player ->
                viewModel.onEvent(
                    GameUiEvent.StartNewGame(
                        gameMode = mode,
                        aiDifficulty = difficulty,
                        aiPlayer = player
                    )
                )
            }
        )
    }

    // AI settings dialog
    if (uiState.showAiSettingsDialog) {
        AiSettingsDialog(
            currentDifficulty = uiState.gameState.aiDifficulty,
            onDismiss = { viewModel.onEvent(GameUiEvent.DismissAiSettingsDialog) },
            onConfirm = { newDifficulty ->
                viewModel.onEvent(GameUiEvent.SetAiDifficulty(newDifficulty))
                viewModel.onEvent(GameUiEvent.DismissAiSettingsDialog)
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
    val winnerName = gameState.winner?.displayName() ?: "?"
    return when (gameState.status) {
        GameStatus.CHECKMATE -> {
            "게임 종료" to "$winnerName 승리!\n외통으로 게임이 끝났습니다."
        }
        GameStatus.POINT_WIN -> {
            val score = MaterialScore.of(gameState)
            val reason = if (gameState.getMoveCount() >= GameRules.MOVE_LIMIT) {
                "${GameRules.MOVE_LIMIT}수가 지나 점수로 가렸습니다."
            } else {
                "둘 수 있는 수가 없어 점수로 가렸습니다."
            }
            "게임 종료" to "$winnerName 승리!\n$reason\n" +
                "초 ${formatScore(score.choScore)} : 한 ${formatScore(score.hanScore)}"
        }
        GameStatus.FOUL_LOSS -> {
            val loser = gameState.winner?.opponent()?.displayName() ?: "?"
            "게임 종료" to "$winnerName 승리!\n${loser}이(가) 같은 수를 반복해 반칙패했습니다."
        }
        // 빅장 규칙은 없앴지만, 그 전에 저장된 대국은 이 상태로 열립니다.
        GameStatus.BIKJANG -> {
            "게임 종료" to "$winnerName 승리!\n빅장(맞장)으로 게임이 끝났습니다."
        }
        GameStatus.STALEMATE -> {
            "게임 종료" to "무승부\n더 이상 움직일 수 있는 수가 없습니다."
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
    checkPosition: Position? = null,
    hintMove: Move? = null
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 판 크기를 정합니다. 기물은 교차점 "위"에 놓이므로 가장자리 기물이 판 밖으로
        // 절반 나가고, 그만큼 사방에 여백이 필요합니다. 기물 지름은 칸에 비례하므로
        // 여백도 판 너비에 비례합니다 - 아래 계수는 그걸 정리한 값입니다.
        //   칸 세로 = 판너비 / (9 * 0.9) 가 칸 가로보다 작으므로 이게 기준
        //   여백 = 칸 * PIECE_TO_CELL / 2
        val overhangFactor = PIECE_TO_CELL / 2f / (BOARD_ASPECT * ROW_GAPS)
        val boardWidth = minOf(
            maxWidth / (1f + 2 * overhangFactor),
            maxHeight / (1f / BOARD_ASPECT + 2 * overhangFactor)
        )
        val boardHeight = boardWidth / BOARD_ASPECT

        // Calculate cell dimensions
        val cellWidth = boardWidth / COL_GAPS
        val cellHeight = boardHeight / ROW_GAPS

        // 칸보다 큰 기물을 그리면 서로 겹칩니다. 좁은 화면에서 특히 그렇습니다.
        val pieceSize = minOf(cellWidth, cellHeight) * PIECE_TO_CELL
        val overhang = pieceSize / 2

        // 터치 영역은 판보다 여백만큼 넓혀야 합니다. 판 크기 그대로 두면 교차점 위에
        // 걸쳐 놓인 가장자리 기물의 바깥쪽 절반이 영역 밖이라 눌리지 않습니다. 손가락
        // 접점이 보이는 지점보다 조금 아래로 잡히는 탓에 맨 아랫줄이 특히 안 눌렸습니다.
        Box(
            modifier = Modifier
                .size(
                    width = boardWidth + overhang * 2f,
                    height = boardHeight + overhang * 2f
                )
                // 판 크기가 바뀌면 아래 계산도 다시 잡혀야 하므로 키로 넘깁니다.
                .pointerInput(cellWidth, cellHeight, overhang) {
                    val overhangPx = overhang.toPx()
                    detectTapGestures { offset ->
                        // Convert tap coordinates to board position
                        val col = ((offset.x - overhangPx) / cellWidth.toPx()).roundToInt()
                        val row = ((offset.y - overhangPx) / cellHeight.toPx()).roundToInt()
                        val position = Position(col, row)

                        if (position.isValid()) {
                            onBoardTap(position)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(width = boardWidth, height = boardHeight)) {
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
                    PieceView(
                        piece = piece,
                        size = pieceSize,
                        fontSize = with(LocalDensity.current) { (pieceSize * 0.58f).toSp() },
                        modifier = Modifier.offset(
                            x = (position.col * cellWidth.value).dp - overhang,
                            y = (position.row * cellHeight.value).dp - overhang
                        )
                    )
                }

                // 힌트는 기물 위에 그려야 보입니다.
                HintLayer(
                    hintMove = hintMove,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight
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

/** 72.0 → "72", 73.5 → "73.5" */
private fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", score)

/** 기물 지름을 칸 크기의 몇 배로 그릴지. 1 을 넘으면 이웃 기물과 겹칩니다. */
private const val PIECE_TO_CELL = 0.92f

/** 장기판 가로:세로 = 9:10 */
private const val BOARD_ASPECT = 0.9f

/** 9열이므로 칸 간격은 8개, 10행이므로 9개 */
private const val COL_GAPS = 8f
private const val ROW_GAPS = 9f
