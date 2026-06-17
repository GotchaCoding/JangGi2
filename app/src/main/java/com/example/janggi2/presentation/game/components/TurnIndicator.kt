package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.background
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

/**
 * Displays the current player's turn with a visual indicator.
 *
 * @param currentPlayer The player whose turn it is
 * @param moveCount Total number of moves played
 */
@Composable
fun TurnIndicator(
    currentPlayer: Player,
    moveCount: Int,
    modifier: Modifier = Modifier
) {
    val (playerText, playerColor) = when (currentPlayer) {
        Player.CHO -> "초 차례" to Color(0xFF1976D2) // Blue
        Player.HAN -> "한 차례" to Color(0xFFC2185B) // Pink
    }

    Surface(
        modifier = modifier,
        color = playerColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator circle
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = playerColor
            ) {}

            // Turn text
            Text(
                text = playerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = playerColor
            )

            // Move count
            Text(
                text = "($moveCount 수)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TurnIndicatorChoPreview() {
    TurnIndicator(
        currentPlayer = Player.CHO,
        moveCount = 5
    )
}

@Preview(showBackground = true)
@Composable
fun TurnIndicatorHanPreview() {
    TurnIndicator(
        currentPlayer = Player.HAN,
        moveCount = 12
    )
}
