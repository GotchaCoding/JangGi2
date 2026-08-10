package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.GameMode
import com.example.janggi2.domain.model.Player

/**
 * Dialog for selecting game mode and AI settings when starting a new game.
 */
@Composable
fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (GameMode, Int, Player) -> Unit
) {
    var selectedMode by remember { mutableStateOf(GameMode.PLAYER_VS_PLAYER) }
    var aiDifficulty by remember { mutableStateOf(10) }
    var aiPlayer by remember { mutableStateOf(Player.HAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 게임") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Game mode selection
                Text("게임 모드", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == GameMode.PLAYER_VS_PLAYER,
                        onClick = { selectedMode = GameMode.PLAYER_VS_PLAYER },
                        label = { Text("2인 대국") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == GameMode.PLAYER_VS_AI,
                        onClick = { selectedMode = GameMode.PLAYER_VS_AI },
                        label = { Text("AI 대국") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // AI settings (only show when PvAI mode is selected)
                if (selectedMode == GameMode.PLAYER_VS_AI) {
                    HorizontalDivider()

                    // AI difficulty
                    Text(
                        "AI 난이도: ${getDifficultyName(aiDifficulty)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = aiDifficulty.toFloat(),
                        onValueChange = { aiDifficulty = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("초급", style = MaterialTheme.typography.bodySmall)
                        Text("고급", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // AI player selection
                    Text("AI가 플레이할 진영", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = aiPlayer == Player.CHO,
                            onClick = { aiPlayer = Player.CHO },
                            label = { Text("초 (상단)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = aiPlayer == Player.HAN,
                            onClick = { aiPlayer = Player.HAN },
                            label = { Text("한 (하단)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStartGame(selectedMode, aiDifficulty, aiPlayer)
                }
            ) {
                Text("시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * Converts numeric difficulty level to human-readable Korean name.
 */
private fun getDifficultyName(level: Int): String = when {
    level <= 3 -> "초급"
    level <= 7 -> "초보"
    level <= 13 -> "중급"
    level <= 17 -> "고급"
    else -> "전문가"
}
