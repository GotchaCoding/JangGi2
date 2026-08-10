package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.presentation.common.ConfirmDialog

/**
 * Game control buttons (Undo, Redo, Save, Load, Reset)
 */
@Composable
fun GameControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: (String) -> Unit,
    onLoadClick: () -> Unit,
    onImportClick: () -> Unit,
    onResetClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    isAiGame: Boolean = false,
    onShowAiSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top row: Undo, Redo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = onUndoClick,
                enabled = canUndo
            ) {
                Text("되돌리기")
            }

            OutlinedButton(
                onClick = onRedoClick,
                enabled = canRedo
            ) {
                Text("다시실행")
            }
        }

        // Middle row: Save, Load, Import, AI Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            TextButton(onClick = { showSaveDialog = true }) {
                Text("저장")
            }

            TextButton(onClick = onLoadClick) {
                Text("불러오기")
            }

            TextButton(onClick = onImportClick) {
                Text("사진 불러오기")
            }

            // AI settings button (only show when in AI game mode)
            if (isAiGame) {
                TextButton(onClick = onShowAiSettings) {
                    Text("AI 설정")
                }
            }

            TextButton(onClick = onDebugClick) {
                Text("디버그")
            }
        }

        // Bottom row: Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            TextButton(
                onClick = onResetClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("다시 시작")
            }
        }
    }

    // Save dialog
    if (showSaveDialog) {
        SaveGameDialog(
            onConfirm = { name ->
                onSaveClick(name)
                showSaveDialog = false
            },
            onDismiss = {
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun SaveGameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var gameName by remember { mutableStateOf("") }

    ConfirmDialog(
        title = "게임 저장",
        message = "저장할 게임 이름을 입력하세요:\n(자동으로 날짜가 포함됩니다)",
        confirmText = "저장",
        dismissText = "취소",
        onConfirm = {
            val finalName = if (gameName.isBlank()) {
                "Game ${System.currentTimeMillis()}"
            } else {
                gameName
            }
            onConfirm(finalName)
        },
        onDismiss = onDismiss
    )
}

@Preview(showBackground = true)
@Composable
fun GameControlsPreview() {
    GameControls(
        canUndo = true,
        canRedo = false,
        onUndoClick = {},
        onRedoClick = {},
        onSaveClick = {},
        onLoadClick = {},
        onImportClick = {},
        onResetClick = {},
        onDebugClick = {}
    )
}
