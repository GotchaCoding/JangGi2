package com.example.janggi2.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.GameStatus
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.presentation.common.ConfirmDialog
import com.example.janggi2.presentation.game.components.AiSettingsDialog
import com.example.janggi2.presentation.game.components.GameControls
import com.example.janggi2.domain.rules.GameRules
import com.example.janggi2.domain.rules.MaterialScore
import com.example.janggi2.presentation.game.components.HighlightLayer
import com.example.janggi2.presentation.game.components.HintLayer
import com.example.janggi2.presentation.game.components.BoardArtwork
import com.example.janggi2.presentation.game.components.JangGiBoard
import com.example.janggi2.presentation.game.components.MoveHistoryPanel
import com.example.janggi2.presentation.game.components.NewGameDialog
import com.example.janggi2.presentation.game.components.PieceView
import com.example.janggi2.presentation.game.components.ReplayControls
import com.example.janggi2.presentation.game.components.ReviewCommentsPanel
import com.example.janggi2.presentation.game.components.ReviewProgressDialog
import com.example.janggi2.presentation.game.components.ReviewSummaryRow
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
    onNavigateToVideoImport: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToPuzzle: (GameState, GameReview, Player) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val isReplay = uiState.gameState.isReplayMode

    // 복기 중에는 시스템 뒤로가기(제스처 포함)가 앱을 나가는 대신 "검토"(원래 이름
    // "여기서 계속")와 같은 동작을 하도록 가로챕니다. "복기 종료"(ExitReplayMode)는 늘 마지막 수순
    // 으로 돌아가버려서, 보고 있던 수순이 아니라 엉뚱한(마지막) 국면이 열리는
    // 것처럼 보였습니다 - ContinueFromReplay 는 지금 보고 있던 그 수순을 그대로
    // 살아있는 대국 상태로 만듭니다.
    BackHandler(enabled = isReplay) {
        viewModel.onEvent(GameUiEvent.ContinueFromReplay)
    }

    // AI 리뷰를 "검토"로 갈라져 나가 수를 두는 중(isReplayMode 는 꺼졌지만 그 리뷰를
    // 벗어난 건 아님)에 뒤로가기를 하면, 검토를 눌렀던 그 지점의 AI 리뷰 화면으로
    // 돌아갑니다 - 갈라져 나가 둬 본 수는 버려집니다.
    val isReviewBranch = !isReplay &&
        uiState.currentReviewId != null &&
        uiState.reviewBranchStartIndex != null
    BackHandler(enabled = isReviewBranch) {
        viewModel.onEvent(GameUiEvent.BackToReviewFromBranch)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        // 화면 전체를 항상 스크롤 가능하게 둡니다. 복기 모드는 한때 수 기록에
        // weight(1f)를 줘서 화면을 스크롤 없이 한 번에 보여줬지만, AI 리뷰 요약·
        // 블런더 퍼즐 버튼이 그 자리를 나눠 가지면서 수 기록이 거의 0dp로
        // 눌리는 경우가 생겼습니다(weight는 부모가 스크롤되지 않을 때만 쓸 수
        // 있어서, 그때는 남는 공간이 없으면 정말 안 보이지도 스크롤도 안 됩니다).
        // 수 기록은 항상 고정 높이 박스 안에서 자체적으로 스크롤되고, 그 박스
        // 자체가 화면 밖으로 넘치면 이 바깥 스크롤로 끌어올려 봅니다.
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 기보 이름. 저장된 적 없는 대국(새 대국·자동저장 복원)은 이름이 없습니다.
            // 복기 중이거나 AI 리뷰를 "검토"로 갈라져 나가 보는 중에는 판을 화면 맨
            // 위로 올리려고 이 제목 자체를 안 보여줍니다 - "검토"를 누르면
            // isReplayMode 는 꺼지지만 currentReviewId 는 그대로라 이 화면인 동안
            // 계속 안 보여야 합니다.
            val hideTitle = isReplay || uiState.currentReviewId != null
            if (!hideTitle) {
                Text(
                    text = uiState.currentGameName ?: "이름 없는 대국",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        top = 16.dp,
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                )
            }

            // 점수는 판에서 계산하므로 복기 중에도 그 시점 점수가 그대로 맞습니다.
            val scoreboard = remember(uiState.gameState.board) {
                MaterialScore.of(uiState.gameState)
            }

            // 장군 상태에 들어갈 때마다 1초만 판 위에 띄우고 사라지는 알림.
            // 판 바깥에 두면 그만큼 판이 줄어들어, 판 위에 겹쳐 그립니다.
            var showCheckBanner by remember { mutableStateOf(false) }
            LaunchedEffect(uiState.gameState) {
                if (uiState.gameState.status == GameStatus.CHECK) {
                    showCheckBanner = true
                    delay(1000)
                    showCheckBanner = false
                } else {
                    showCheckBanner = false
                }
            }

            // 판. 좌우 여백 없이 화면 폭에 꽉 채웁니다 - weight 를 다른 형제와
            // 나눠 갖지 않고 스스로 가로폭만으로 크기를 정하므로, 아래 목록이
            // 아무리 길어져도 판 크기는 흔들리지 않습니다.
            BoardWithPieces(
                pieces = uiState.gameState.board,
                selectedPiece = if (uiState.gameState.isReplayMode) null else uiState.selectedPiece,
                validMoves = if (uiState.gameState.isReplayMode) emptyList() else uiState.validMoves,
                checkPosition = getCheckPosition(uiState.gameState),
                hintMove = if (uiState.gameState.isReplayMode) null else uiState.hint,
                onBoardTap = { position ->
                    viewModel.onEvent(GameUiEvent.BoardTapped(position))
                },
                // 고른 진영이 아래로 오게 돌립니다. 한은 원래 아래라 그대로입니다.
                flipped = uiState.viewpoint == Player.CHO,
                repetitionNotice = uiState.repetitionNotice,
                checkNotice = showCheckBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isReplay) 2.dp else 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = if (isReplay) 2.dp else 8.dp)
            ) {
                TurnIndicator(
                    currentPlayer = uiState.gameState.currentPlayer,
                    moveCount = uiState.gameState.getMoveCount(),
                    scoreboard = scoreboard
                )
                // 불러오기·복기는 앉은 쪽과 무관하게 늘 한이 아래로 뜨므로, 실제와
                // 다르면 직접 뒤집어 볼 수 있게 둡니다.
                IconButton(
                    onClick = { viewModel.onEvent(GameUiEvent.FlipBoard) },
                    modifier = if (isReplay) Modifier.size(32.dp) else Modifier
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "판 뒤집기",
                        modifier = if (isReplay) Modifier.size(18.dp) else Modifier
                    )
                }
            }

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
                    onSaveClick = { name, choPlayerName, hanPlayerName, choRank, hanRank ->
                        viewModel.onEvent(
                            GameUiEvent.SaveGame(name, choPlayerName, hanPlayerName, choRank, hanRank)
                        )
                    },
                    onLoadClick = {
                        onNavigateToSavedGames()
                    },
                    onImportClick = {
                        onNavigateToImport()
                    },
                    onVideoImportClick = {
                        onNavigateToVideoImport()
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
                    },
                    onReviewClick = {
                        viewModel.onEvent(GameUiEvent.RequestReview)
                    },
                    isReviewLoading = uiState.isReviewLoading,
                    canReview = uiState.gameState.moveHistory.isNotEmpty()
                )
            }

            // AI 리뷰를 보는 중에만 댓글 영역을 보여줍니다("검토"로 갈라져 나간 뒤에도
            // isReplayMode 는 꺼지지만 currentReviewId 는 남아 있어 계속 보입니다).
            if (uiState.currentReviewId != null) {
                ReviewCommentsPanel(
                    comments = uiState.comments,
                    commentInput = uiState.commentInput,
                    canWrite = uiState.reviewBranchStartIndex != null,
                    onCommentInputChange = { text ->
                        viewModel.onEvent(GameUiEvent.CommentTextChanged(text))
                    },
                    onSaveComment = {
                        viewModel.onEvent(GameUiEvent.SaveComment)
                    },
                    onCommentClick = { comment ->
                        viewModel.onEvent(GameUiEvent.OpenComment(comment))
                    }
                )
            }

            uiState.gameReview?.let { review ->
                ReviewSummaryRow(review = review)
                if (review.moveReviews.any { it.quality == MoveQuality.BLUNDER }) {
                    TextButton(
                        onClick = {
                            onNavigateToPuzzle(uiState.gameState, review, uiState.viewpoint)
                        }
                    ) {
                        Text("블런더 퍼즐로 풀기")
                    }
                }
            }

            // 수 기록. 판 크기와 무관하게 늘 일정한 높이의 리스트(LazyColumn)로
            // 보이고, 그 안에서 드래그로 스크롤됩니다. 줄을 누르면 그 수를 둔
            // 직후 국면으로 판이 바뀝니다.
            val moveQualities = remember(uiState.gameReview) {
                val review = uiState.gameReview
                if (review == null) {
                    emptyList()
                } else {
                    val byIndex = review.moveReviews.associateBy { it.moveIndex }
                    List(review.moveReviews.size) { i -> byIndex[i]?.quality }
                }
            }
            MoveHistoryPanel(
                moves = uiState.gameState.moveHistory,
                scoreboard = scoreboard,
                moveQualities = moveQualities,
                currentPosition = if (uiState.gameState.isReplayMode) {
                    uiState.gameState.replayPosition
                } else {
                    uiState.gameState.moveHistory.size
                },
                onMoveClick = { index ->
                    viewModel.onEvent(GameUiEvent.JumpToMove(index))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isReplay) 360.dp else 280.dp)
            )
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

    // AI 리뷰 진행 중
    if (uiState.isReviewLoading) {
        val (completed, total) = uiState.reviewProgress ?: (0 to 1)
        ReviewProgressDialog(
            completed = completed,
            total = total,
            onCancel = { viewModel.onEvent(GameUiEvent.CancelReview) }
        )
    }

    // AI 리뷰 오류
    uiState.reviewError?.let { message ->
        ConfirmDialog(
            title = "AI 리뷰",
            message = message,
            confirmText = "확인",
            onConfirm = { viewModel.onEvent(GameUiEvent.DismissReviewError) },
            onDismiss = { viewModel.onEvent(GameUiEvent.DismissReviewError) }
        )
    }

    // New game dialog
    if (uiState.showNewGameDialog) {
        NewGameDialog(
            onDismiss = { viewModel.onEvent(GameUiEvent.DismissNewGameDialog) },
            onStartGame = { mode, difficulty, myPlayer, choSetup, hanSetup ->
                viewModel.onEvent(
                    GameUiEvent.StartNewGame(
                        gameMode = mode,
                        aiDifficulty = difficulty,
                        myPlayer = myPlayer,
                        choSetup = choSetup,
                        hanSetup = hanSetup
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
 * 판을 돌려 볼 때 쓰는 좌표 변환.
 *
 * 180도 회전이라 자기 자신이 역변환입니다 - 그릴 때도 탭을 되돌릴 때도 같은 함수를 씁니다.
 */
private fun Position.oriented(flipped: Boolean): Position =
    if (flipped) Position(BOARD_COLS - 1 - col, BOARD_ROWS - 1 - row) else this

/**
 * Displays the board with all pieces positioned on it.
 *
 * 판을 돌려 보는 기능 때문에 탭 해석이 틀어지기 쉬워, 계측 테스트가 직접 부릅니다.
 */
@Composable
internal fun BoardWithPieces(
    pieces: Map<Position, Piece>,
    selectedPiece: Piece?,
    validMoves: List<Position>,
    onBoardTap: (Position) -> Unit,
    modifier: Modifier = Modifier,
    checkPosition: Position? = null,
    hintMove: Move? = null,
    flipped: Boolean = false,
    repetitionNotice: Boolean = false,
    checkNotice: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 판 그림을 통째로 넣고 그 안의 격자에 기물을 얹습니다. 그림에 이미 격자 바깥
        // 여백이 넉넉히 들어 있어(칸의 0.63배) 가장자리 기물이 그림 밖으로 나가지
        // 않습니다. 예전처럼 판 밖으로 여백을 더 잡을 필요가 없습니다.
        val boardWidth = minOf(maxWidth, maxHeight * BoardArtwork.ASPECT)
        val boardHeight = boardWidth / BoardArtwork.ASPECT

        // 칸은 정사각형이고 그림 비율을 지켜 그리므로 가로·세로가 같습니다.
        val cell = boardWidth * BoardArtwork.CELL
        val gridOrigin = DpOffset(
            x = boardWidth * BoardArtwork.GRID_LEFT,
            y = boardHeight * BoardArtwork.GRID_TOP
        )

        Box(
            modifier = Modifier
                .size(width = boardWidth, height = boardHeight)
                // 아래 블록은 키가 바뀔 때만 다시 만들어지고, 그 전까지는 처음 잡은 값을
                // 계속 씁니다. 그래서 계산에 쓰는 값을 **빠짐없이** 키로 넘겨야 합니다.
                // flipped 를 빠뜨렸다가, 판은 돌아갔는데 탭만 안 돌아가서 한 기물을
                // 누르면 초 기물이 잡히는 버그가 났습니다.
                .pointerInput(cell, gridOrigin, flipped) {
                    val cellPx = cell.toPx()
                    val originX = gridOrigin.x.toPx()
                    val originY = gridOrigin.y.toPx()
                    detectTapGestures { offset ->
                        // 격자는 그림 안쪽에서 시작하므로 그만큼 빼고 나눕니다.
                        val col = ((offset.x - originX) / cellPx).roundToInt()
                        val row = ((offset.y - originY) / cellPx).roundToInt()
                        val position = Position(col, row)

                        if (position.isValid()) {
                            // 그릴 때 돌렸으므로 되돌려서 실제 칸을 냅니다.
                            onBoardTap(position.oriented(flipped))
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
                    selectedPosition = selectedPiece?.position?.oriented(flipped),
                    validMoves = validMoves.map { it.oriented(flipped) },
                    cellWidth = cell,
                    cellHeight = cell,
                    checkPosition = checkPosition?.oriented(flipped),
                    origin = gridOrigin
                )

                // 알마다 크기가 달라서, 칸 크기의 상자를 교차점에 맞춰 놓고 그 안에서
                // 가운데 정렬합니다. 어떤 알이 얼마나 큰지는 PieceView 가 압니다.
                pieces.forEach { (position, piece) ->
                    val shown = position.oriented(flipped)
                    Box(
                        modifier = Modifier
                            .offset(
                                x = gridOrigin.x + cell * shown.col.toFloat() - cell / 2f,
                                y = gridOrigin.y + cell * shown.row.toFloat() - cell / 2f
                            )
                            .size(cell),
                        contentAlignment = Alignment.Center
                    ) {
                        PieceView(piece = piece, cellSize = cell)
                    }
                }

                // 힌트는 기물 위에 그려야 보입니다.
                HintLayer(
                    hintMove = hintMove?.let {
                        it.copy(from = it.from.oriented(flipped), to = it.to.oriented(flipped))
                    },
                    cellWidth = cell,
                    cellHeight = cell,
                    origin = gridOrigin
                )
            }

            // 반복이라 막힌 자리를 눌렀을 때 잠깐 뜨는 알림. 판 위에 겹쳐 놓아
            // 다른 요소를 밀어내지 않습니다 - 밀어냈다면 판이 줄었을 겁니다.
            if (repetitionNotice) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "반복수 자리입니다",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            // 장군 알림도 같은 이유로 판 위에 겹쳐 그립니다 - 판 바깥에 자리를
            // 잡으면 그 높이만큼 판이 줄어듭니다.
            if (checkNotice) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "⚠️ 장군!",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
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

/** 교차점 개수. 판을 돌릴 때 좌표를 뒤집는 데 씁니다. */
private const val BOARD_COLS = 9
private const val BOARD_ROWS = 10
