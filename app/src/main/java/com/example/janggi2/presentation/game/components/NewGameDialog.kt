package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.GameMode
import com.example.janggi2.domain.model.HorseElephantSetup
import com.example.janggi2.domain.model.Player

/**
 * 대국을 시작하기 전에 정하는 것들 - 상대, 내 진영, 그리고 양측의 마·상 배치.
 */
@Composable
fun NewGameDialog(
    onDismiss: () -> Unit,
    onStartGame: (GameMode, Int, Player, HorseElephantSetup, HorseElephantSetup) -> Unit
) {
    var selectedMode by remember { mutableStateOf(GameMode.PLAYER_VS_PLAYER) }
    var aiDifficulty by remember { mutableIntStateOf(10) }
    var myPlayer by remember { mutableStateOf(Player.HAN) }
    var choSetup by remember { mutableStateOf(HorseElephantSetup.defaultFor(Player.CHO)) }
    var hanSetup by remember { mutableStateOf(HorseElephantSetup.defaultFor(Player.HAN)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 게임") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 배치까지 고르면 낮은 화면에서 넘칩니다.
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                HorizontalDivider()

                // 내 진영. AI 대국이면 AI 는 자동으로 반대편을 잡습니다.
                Text("내 진영", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = myPlayer == Player.CHO,
                        onClick = { myPlayer = Player.CHO },
                        label = { Text("초 (선수)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = myPlayer == Player.HAN,
                        onClick = { myPlayer = Player.HAN },
                        label = { Text("한 (후수)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    if (selectedMode == GameMode.PLAYER_VS_AI) {
                        "고른 진영이 화면 아래쪽에 놓이고, AI 가 반대편을 잡습니다."
                    } else {
                        "고른 진영이 화면 아래쪽에 놓입니다. 초가 먼저 둡니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // 마·상 배치는 대국자가 각자 정하고 시작합니다.
                Text("마·상 배치", style = MaterialTheme.typography.titleSmall)
                SetupPicker(
                    label = "초",
                    selected = choSetup,
                    onSelect = { choSetup = it }
                )
                SetupPicker(
                    label = "한",
                    selected = hanSetup,
                    onSelect = { hanSetup = it }
                )
                Text(
                    "각 진영에서 자기 왼쪽부터 읽은 순서입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedMode == GameMode.PLAYER_VS_AI) {
                    HorizontalDivider()

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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStartGame(selectedMode, aiDifficulty, myPlayer, choSetup, hanSetup) }
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
 * 한 진영의 마·상 배치를 고르는 2×2 격자.
 *
 * 이름이 네 글자라 한 줄에 넷을 늘어놓으면 좁은 화면에서 글자가 잘립니다.
 */
@Composable
private fun SetupPicker(
    label: String,
    selected: HorseElephantSetup,
    onSelect: (HorseElephantSetup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        HorseElephantSetup.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { setup ->
                    FilterChip(
                        selected = selected == setup,
                        onClick = { onSelect(setup) },
                        label = {
                            Text(
                                setup.displayName,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
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

@Preview(showBackground = true)
@Composable
private fun NewGameDialogPreview() {
    NewGameDialog(onDismiss = {}, onStartGame = { _, _, _, _, _ -> })
}
