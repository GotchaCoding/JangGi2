package com.example.janggi2.presentation.puzzle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Player
import com.example.janggi2.presentation.game.BoardWithPieces
import com.example.janggi2.presentation.game.components.MoveQualityLabels
import com.example.janggi2.presentation.game.components.coordinate

/**
 * 악수 직전 국면을 다시 풀어보는 화면. 실제로 수를 두지 않고, 탭한 위치를 채점만 합니다.
 */
@Composable
fun PuzzleScreen(
    viewModel: PuzzleViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isFinished) {
                PuzzleSummary(
                    correctCount = uiState.correctCount,
                    total = uiState.puzzles.size,
                    onNavigateBack = onNavigateBack
                )
                return@Column
            }

            val puzzle = uiState.currentPuzzle
            if (puzzle == null) {
                Text(
                    text = "풀 수 있는 악수 퍼즐이 없습니다",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(24.dp)
                )
                Button(onClick = onNavigateBack, modifier = Modifier.padding(top = 8.dp)) {
                    Text("뒤로")
                }
                return@Column
            }

            Text(
                text = "블런더 퍼즐 ${uiState.currentIndex + 1} / ${uiState.puzzles.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            BoardWithPieces(
                pieces = puzzle.position.board,
                selectedPiece = uiState.selectedPiece,
                validMoves = uiState.validMoves,
                hintMove = if (uiState.status == PuzzleStatus.INCORRECT) uiState.gradeResult?.engineBestMove else null,
                onBoardTap = { position -> viewModel.onEvent(PuzzleUiEvent.BoardTapped(position)) },
                flipped = uiState.viewpoint == Player.CHO,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            PuzzleStatusPanel(
                uiState = uiState,
                onNextClick = { viewModel.onEvent(PuzzleUiEvent.NextPuzzle) }
            )
        }
    }
}

@Composable
private fun PuzzleStatusPanel(
    uiState: PuzzleUiState,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState.status) {
            PuzzleStatus.PENDING -> {
                Text(
                    text = "${uiState.currentPuzzle?.player?.displayName() ?: ""} 차례 - 둘 수를 고르세요",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PuzzleStatus.GRADING -> {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            PuzzleStatus.CORRECT -> {
                val result = uiState.gradeResult
                Text(
                    text = "정답! (${result?.let { MoveQualityLabels.fullLabel(it.quality) } ?: ""})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoveQualityLabels.color(result?.quality ?: MoveQuality.BEST)
                )
                Button(onClick = onNextClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("다음 퍼즐")
                }
            }
            PuzzleStatus.INCORRECT -> {
                val result = uiState.gradeResult
                Text(
                    text = "오답 (${result?.let { MoveQualityLabels.fullLabel(it.quality) } ?: ""})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoveQualityLabels.color(result?.quality ?: MoveQuality.BLUNDER)
                )
                val best = result?.engineBestMove
                if (best != null) {
                    Text(
                        text = "엔진 추천: ${coordinate(best.from)}→${coordinate(best.to)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(onClick = onNextClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("다음 퍼즐")
                }
            }
        }
    }
}

@Composable
private fun PuzzleSummary(
    correctCount: Int,
    total: Int,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (total == 0) "풀 수 있는 악수 퍼즐이 없습니다" else "$correctCount / $total 문제를 맞혔습니다",
            style = MaterialTheme.typography.titleLarge
        )
        Button(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("돌아가기")
        }
    }
}
