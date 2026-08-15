package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.rules.MaterialScoreboard
import com.example.janggi2.ui.theme.JangGi2Theme

/** 수가 있든 없든 같은 높이를 씁니다. */
private val ROW_HEIGHT = 44.dp

/**
 * 대국의 수 기록을 순서대로 보여줍니다. 새 수가 두어지면 끝으로 따라갑니다.
 *
 * 헤더 줄에는 서로 잡은 기물을 함께 놓습니다. 그 줄은 원래 라벨 하나만 쓰고 있어서
 * 세로 공간이 늘지 않고, 보드가 남는 높이를 다 쓰는 화면이라 그게 중요합니다.
 *
 * @param moves 지금까지의 수
 * @param scoreboard 잡은 기물 목록을 여기서 가져옵니다
 */
@Composable
fun MoveHistoryPanel(
    moves: List<Move>,
    scoreboard: MaterialScoreboard,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) {
            listState.animateScrollToItem(moves.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
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

        // 높이를 고정합니다. 첫 수를 두는 순간 이 영역이 커지면 보드가 weight 로
        // 남은 높이를 쓰기 때문에 그만큼 줄어들어 판이 작아져 보입니다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT),
            contentAlignment = Alignment.CenterStart
        ) {
            if (moves.isEmpty()) {
                Text(
                    text = "아직 둔 수가 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(moves) { index, move ->
                        MoveChip(
                            number = index + 1,
                            move = move,
                            isLatest = index == moves.lastIndex
                        )
                    }
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
private fun MoveChip(
    number: Int,
    move: Move,
    isLatest: Boolean
) {
    val playerColor = when (move.movedPiece?.player) {
        Player.CHO -> PlayerColors.of(Player.CHO)
        Player.HAN -> PlayerColors.of(Player.HAN)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = "$number. ${describeMove(move)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
        color = playerColor,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isLatest) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
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
