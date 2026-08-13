package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.ui.theme.JangGi2Theme

/**
 * 대국의 수 기록을 순서대로 보여줍니다. 새 수가 두어지면 끝으로 따라갑니다.
 *
 * @param moves 지금까지의 수
 */
@Composable
fun MoveHistoryPanel(
    moves: List<Move>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) {
            listState.animateScrollToItem(moves.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "수 기록",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )

        if (moves.isEmpty()) {
            Text(
                text = "아직 둔 수가 없습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            return@Column
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
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

@Composable
private fun MoveChip(
    number: Int,
    move: Move,
    isLatest: Boolean
) {
    val player = move.movedPiece?.player
    val playerColor = when (player) {
        Player.CHO -> MaterialTheme.colorScheme.primary
        Player.HAN -> MaterialTheme.colorScheme.error
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
            )
        )
    }
}
