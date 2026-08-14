package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.rules.MaterialScoreboard
import java.util.Locale

/**
 * 차례와 양측 기물 점수를 한 줄에 보여줍니다.
 *
 * 차례는 세 가지로 드러납니다 — 배경 틴트, 색 점, 그리고 차례인 쪽 점수만 굵게.
 * "차례"라는 글자는 초/한이 이미 점수 옆에 나오므로 빼서 폭을 아꼈습니다.
 *
 * @param currentPlayer 둘 차례인 쪽
 * @param moveCount 지금까지 둔 수
 * @param scoreboard 양측 기물 점수
 */
@Composable
fun TurnIndicator(
    currentPlayer: Player,
    moveCount: Int,
    scoreboard: MaterialScoreboard,
    modifier: Modifier = Modifier
) {
    val playerColor = PlayerColors.of(currentPlayer)

    Surface(
        modifier = modifier,
        color = playerColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 차례를 나타내는 색 점
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = playerColor
            ) {}

            ScoreText("초", scoreboard.choScore, PlayerColors.of(Player.CHO), currentPlayer == Player.CHO)
            ScoreText("한", scoreboard.hanScore, PlayerColors.of(Player.HAN), currentPlayer == Player.HAN)

            Text(
                text = "$moveCount 수",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreText(name: String, score: Double, color: Color, isTurn: Boolean) {
    Text(
        text = "$name ${formatScore(score)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
        color = if (isTurn) color else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 72.0 → "72", 73.5 → "73.5". 소수점은 덤 때문에 생기는 .5 하나뿐입니다.
 *
 * Locale 을 고정하지 않으면 한국어·유럽 로케일에서 "73,5" 가 됩니다.
 */
private fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString()
    else String.format(Locale.US, "%.1f", score)

private val openingScore = MaterialScoreboard(72.0, 73.5, emptyList(), emptyList())

@Preview(showBackground = true)
@Composable
private fun TurnIndicatorChoPreview() {
    TurnIndicator(currentPlayer = Player.CHO, moveCount = 5, scoreboard = openingScore)
}

@Preview(showBackground = true)
@Composable
private fun TurnIndicatorHanPreview() {
    TurnIndicator(currentPlayer = Player.HAN, moveCount = 12, scoreboard = openingScore)
}

@Preview(showBackground = true)
@Composable
private fun TurnIndicatorLopsidedPreview() {
    // 폭이 가장 넓어지는 경우 확인용
    TurnIndicator(
        currentPlayer = Player.HAN,
        moveCount = 148,
        scoreboard = MaterialScoreboard(13.0, 60.5, emptyList(), emptyList())
    )
}
