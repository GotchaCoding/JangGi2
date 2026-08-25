package com.example.janggi2.presentation.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.repository.SavedGameInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Individual saved game item in the list.
 */
@Composable
fun SavedGameItem(
    gameInfo: SavedGameInfo,
    onLoadClick: () -> Unit,
    onReplayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Game name
                Text(
                    text = gameInfo.name,
                    style = MaterialTheme.typography.titleMedium
                )

                // Game info
                Text(
                    text = "${gameInfo.moveCount} 수 • ${gameInfo.currentPlayer}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Players, if recorded
                val playersLine = formatPlayersLine(gameInfo)
                if (playersLine != null) {
                    Text(
                        text = playersLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Saved date
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val dateString = dateFormat.format(Date(gameInfo.savedDate))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            Row {
                TextButton(onClick = onReplayClick) {
                    Text("복기")
                }

                TextButton(onClick = onLoadClick) {
                    Text("불러오기")
                }

                TextButton(onClick = onDeleteClick) {
                    Text(
                        "삭제",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * "초 이름(급수) vs 한 이름(급수)" 형태로 기사 정보를 표시합니다.
 * 아무것도 기록되어 있지 않으면 null을 반환해 줄 자체를 숨깁니다.
 */
private fun formatPlayersLine(gameInfo: SavedGameInfo): String? {
    if (gameInfo.choPlayerName == null && gameInfo.hanPlayerName == null &&
        gameInfo.choRank == null && gameInfo.hanRank == null
    ) {
        return null
    }

    fun side(label: String, name: String?, rank: String?): String {
        val playerName = name ?: label
        return if (rank != null) "$playerName($rank)" else playerName
    }

    return "${side("초", gameInfo.choPlayerName, gameInfo.choRank)} vs ${side("한", gameInfo.hanPlayerName, gameInfo.hanRank)}"
}
