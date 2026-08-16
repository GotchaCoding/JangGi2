package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.rules.MaterialScoreboard
import com.example.janggi2.ui.theme.JangGi2Theme

/**
 * 대국의 수 기록을 세로로 스크롤되는 목록으로 보여줍니다. 각 줄을 누르면 그 수를 둔
 * 직후 국면으로 판이 바뀝니다.
 *
 * 헤더 줄에는 서로 잡은 기물을 함께 놓습니다.
 *
 * @param moves 지금까지의 수
 * @param scoreboard 잡은 기물 목록을 여기서 가져옵니다
 * @param moveQualities AI 리뷰 결과. 인덱스가 [moves] 와 대응합니다(리뷰 전이면 빈 리스트).
 * @param currentPosition 지금 판에 보이는 위치(둔 수의 개수 - 복기 중이면 그 자리, 아니면
 *   전체 수). 이 위치에 해당하는 줄을 강조합니다.
 * @param onMoveClick 줄을 눌렀을 때. 0부터 시작하는 [moves] 인덱스를 넘깁니다.
 */
@Composable
fun MoveHistoryPanel(
    moves: List<Move>,
    scoreboard: MaterialScoreboard,
    moveQualities: List<MoveQuality?> = emptyList(),
    currentPosition: Int = moves.size,
    onMoveClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPosition) {
        if (moves.isNotEmpty()) {
            listState.animateScrollToItem((currentPosition - 1).coerceIn(0, moves.lastIndex))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "수 기록",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            CapturedStrip(scoreboard.choCaptured, PlayerColors.of(Player.CHO))
            Spacer(Modifier.width(8.dp))
            CapturedStrip(scoreboard.hanCaptured, PlayerColors.of(Player.HAN))
        }

        if (moves.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "아직 둔 수가 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(moves) { index, move ->
                    MoveRow(
                        number = index + 1,
                        move = move,
                        isCurrent = index + 1 == currentPosition,
                        quality = moveQualities.getOrNull(index),
                        onClick = { onMoveClick(index) }
                    )
                }
            }
        }
    }
}

/**
 * 잡은 기물을 글자만 이어 붙여 한 줄로 보여줍니다. 색은 잡은 쪽 색입니다.
 * 한쪽이 최대 15개까지 나올 수 있어 넘치면 말줄임 처리합니다.
 */
@Composable
private fun CapturedStrip(captured: List<Piece>, color: Color) {
    if (captured.isEmpty()) return
    Text(
        text = captured.joinToString("") { it.getDisplayChar() },
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MoveRow(
    number: Int,
    move: Move,
    isCurrent: Boolean,
    quality: MoveQuality?,
    onClick: () -> Unit
) {
    val playerColor = when (move.movedPiece?.player) {
        Player.CHO -> PlayerColors.of(Player.CHO)
        Player.HAN -> PlayerColors.of(Player.HAN)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "$number. ${describeMove(move)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = playerColor
        )
        MoveQualityBadge(quality)
    }
}

/** GOOD·null 은 조용히 지나가고, 그 외 등급만 짧은 라벨로 표시합니다. */
@Composable
private fun MoveQualityBadge(quality: MoveQuality?) {
    val label = quality?.let { MoveQualityLabels.shortLabel(it) } ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MoveQualityLabels.color(quality)
    )
}

/**
 * 예: "졸 1-4→1-5", 잡은 수는 "마 8-10→7-8 x졸".
 *
 * 좌표는 화면에 보이는 대로 세로줄 1-9, 가로줄 1-10 입니다. 두 자리 수가 섞이므로
 * 붙여 쓰지 않고 하이픈으로 나눕니다.
 */
private fun describeMove(move: Move): String {
    if (move.isPass()) return "한 수 쉼"
    val name = move.movedPiece?.getDisplayChar() ?: ""
    val captured = move.capturedPiece?.let { " x${it.getDisplayChar()}" } ?: ""
    return "$name ${coordinate(move.from)}→${coordinate(move.to)}$captured"
}

private fun coordinate(position: Position): String = "${position.col + 1}-${position.row + 1}"

@Preview(showBackground = true)
@Composable
private fun MoveHistoryPanelPreview() {
    val soldier = Piece.Soldier(Player.CHO, Position(0, 3))
    val horse = Piece.Horse(Player.HAN, Position(7, 9))
    JangGi2Theme {
        MoveHistoryPanel(
            moves = listOf(
                Move(Position(0, 3), Position(0, 4), movedPiece = soldier),
                Move(Position(7, 9), Position(6, 7), capturedPiece = soldier, movedPiece = horse)
            ),
            scoreboard = MaterialScoreboard(70.0, 73.5, emptyList(), listOf(soldier))
        )
    }
}
