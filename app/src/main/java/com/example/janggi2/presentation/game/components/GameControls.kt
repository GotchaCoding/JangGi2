package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

private val COMPACT_PADDING = PaddingValues(horizontal = 6.dp, vertical = 8.dp)

/**
 * Game control buttons (Undo, Redo, Save, Load, Reset)
 */
@Composable
fun GameControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: (name: String, choPlayerName: String?, hanPlayerName: String?, choRank: String?, hanRank: String?) -> Unit,
    onLoadClick: () -> Unit,
    onImportClick: () -> Unit,
    onVideoImportClick: () -> Unit = {},
    onResetClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    onHintClick: () -> Unit = {},
    isHintLoading: Boolean = false,
    onPassClick: () -> Unit = {},
    canPass: Boolean = false,
    isAiGame: Boolean = false,
    onShowAiSettings: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    isReviewLoading: Boolean = false,
    canReview: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 대국 중 쓰는 버튼 네 개. 좁은 화면에서 마지막 버튼이 찌그러지지 않도록
        // 각자 같은 너비를 갖고 안쪽 여백을 줄였습니다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onUndoClick,
                enabled = canUndo,
                modifier = Modifier.weight(1f),
                contentPadding = COMPACT_PADDING
            ) {
                Text("되돌리기", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }

            OutlinedButton(
                onClick = onRedoClick,
                enabled = canRedo,
                modifier = Modifier.weight(1f),
                contentPadding = COMPACT_PADDING
            ) {
                Text("다시실행", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }

            OutlinedButton(
                onClick = onPassClick,
                enabled = canPass,
                modifier = Modifier.weight(1f),
                contentPadding = COMPACT_PADDING
            ) {
                Text("한 수 쉼", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }

            Button(
                onClick = onHintClick,
                enabled = !isHintLoading,
                modifier = Modifier.weight(1f),
                contentPadding = COMPACT_PADDING
            ) {
                if (isHintLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("AI 힌트", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }

        // Middle row: Save, Load, Import, AI Settings
        // 버튼이 최대 7개까지 늘어날 수 있어(저장/불러오기/사진 불러오기/동영상
        // 불러오기/AI 리뷰/AI 설정/디버그) 화면 폭에 다 안 들어가면 Row는 그냥 잘리고
        // 넘친 버튼이 안 보입니다. FlowRow 는 다 안 들어가면 자동으로 다음 줄로
        // 내려서 항상 모든 버튼이 보이게 합니다.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

            TextButton(onClick = onVideoImportClick) {
                Text("동영상 불러오기")
            }

            TextButton(
                onClick = onReviewClick,
                enabled = canReview && !isReviewLoading
            ) {
                Text(if (isReviewLoading) "AI 리뷰 중..." else "AI 리뷰")
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
            onConfirm = { name, choPlayerName, hanPlayerName, choRank, hanRank ->
                onSaveClick(name, choPlayerName, hanPlayerName, choRank, hanRank)
                showSaveDialog = false
            },
            onDismiss = {
                showSaveDialog = false
            }
        )
    }
}

/**
 * 기보 저장 대화상자. 제목은 필수, 기사 이름·급수는 선택 입력입니다.
 */
@Composable
private fun SaveGameDialog(
    onConfirm: (name: String, choPlayerName: String?, hanPlayerName: String?, choRank: String?, hanRank: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var choPlayerName by remember { mutableStateOf("") }
    var hanPlayerName by remember { mutableStateOf("") }
    var choRank by remember { mutableStateOf("") }
    var hanRank by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기보 저장") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = choPlayerName,
                        onValueChange = { choPlayerName = it },
                        label = { Text("초 기사") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = choRank,
                        onValueChange = { choRank = it },
                        label = { Text("초 급수") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hanPlayerName,
                        onValueChange = { hanPlayerName = it },
                        label = { Text("한 기사") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hanRank,
                        onValueChange = { hanRank = it },
                        label = { Text("한 급수") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalTitle = title.ifBlank { "Game ${System.currentTimeMillis()}" }
                    onConfirm(
                        finalTitle,
                        choPlayerName.ifBlank { null },
                        hanPlayerName.ifBlank { null },
                        choRank.ifBlank { null },
                        hanRank.ifBlank { null }
                    )
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
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
        onSaveClick = { _, _, _, _, _ -> },
        onLoadClick = {},
        onImportClick = {},
        onResetClick = {},
        onDebugClick = {}
    )
}
