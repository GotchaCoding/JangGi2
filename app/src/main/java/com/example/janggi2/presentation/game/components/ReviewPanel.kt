package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.MoveQuality
import com.example.janggi2.domain.model.Player
import com.example.janggi2.ui.theme.JangGi2Theme

/** [MoveQuality] 등급별 표시 라벨·색. 수 기록 배지와 리뷰 요약 줄이 함께 씁니다. */
object MoveQualityLabels {
    /** 조용히 지나가야 할 등급(GOOD)은 null. */
    fun shortLabel(quality: MoveQuality): String? = when (quality) {
        MoveQuality.BEST -> "최선"
        MoveQuality.GOOD -> null
        MoveQuality.INACCURACY -> "부정확"
        MoveQuality.MISTAKE -> "실수"
        MoveQuality.BLUNDER -> "악수"
    }

    fun fullLabel(quality: MoveQuality): String = when (quality) {
        MoveQuality.BEST -> "최선"
        MoveQuality.GOOD -> "좋음"
        MoveQuality.INACCURACY -> "부정확"
        MoveQuality.MISTAKE -> "실수"
        MoveQuality.BLUNDER -> "악수"
    }

    fun color(quality: MoveQuality): Color = when (quality) {
        MoveQuality.BEST -> Color(0xFF2E7D32)
        MoveQuality.GOOD -> Color(0xFF757575)
        MoveQuality.INACCURACY -> Color(0xFFF9A825)
        MoveQuality.MISTAKE -> Color(0xFFEF6C00)
        MoveQuality.BLUNDER -> Color(0xFFC62828)
    }
}

/**
 * AI 리뷰가 국면을 하나씩 분석하는 동안 뜨는 진행률 다이얼로그.
 *
 * 국면 하나당 엔진 탐색이 한 번씩 필요해 수십 초가 걸릴 수 있어, 진행 상황과 취소
 * 버튼을 보여줍니다.
 */
@Composable
fun ReviewProgressDialog(
    completed: Int,
    total: Int,
    onCancel: () -> Unit
) {
    val progress = if (total > 0) completed.toFloat() / total else 0f

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("AI 리뷰") },
        text = {
            Column {
                Text("$completed / $total 국면 분석 중...")
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("취소")
            }
        }
    )
}

/**
 * 초/한 각각 등급별 개수를 한 줄씩 보여주는 리뷰 요약.
 */
@Composable
fun ReviewSummaryRow(
    review: GameReview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        PlayerReviewSummary(Player.CHO, review)
        PlayerReviewSummary(Player.HAN, review)
    }
}

@Composable
private fun PlayerReviewSummary(player: Player, review: GameReview) {
    val counts = MoveQuality.entries.associateWith { quality ->
        review.moveReviews.count { it.player == player && it.quality == quality }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = player.displayName(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PlayerColors.of(player)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = MoveQuality.entries.joinToString("   ") { quality ->
                "${MoveQualityLabels.fullLabel(quality)} ${counts[quality] ?: 0}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewProgressDialogPreview() {
    JangGi2Theme {
        ReviewProgressDialog(completed = 12, total = 45, onCancel = {})
    }
}
