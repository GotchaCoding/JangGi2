package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog for adjusting AI difficulty during a game.
 */
@Composable
fun AiSettingsDialog(
    currentDifficulty: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var difficulty by remember { mutableStateOf(currentDifficulty) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 설정") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "AI 난이도: ${getDifficultyName(difficulty)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { difficulty = it.toInt() },
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(difficulty) }) {
                Text("적용")
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
