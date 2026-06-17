package com.example.janggi2.presentation.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Generic confirmation/notification dialog.
 *
 * @param title Dialog title
 * @param message Dialog message
 * @param confirmText Text for confirm button
 * @param dismissText Text for dismiss button (null to hide)
 * @param onConfirm Callback when confirm is clicked
 * @param onDismiss Callback when dismiss is clicked or dialog is dismissed
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "확인",
    dismissText: String? = null,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it)
                }
            }
        }
    )
}

@Preview
@Composable
fun ConfirmDialogPreview() {
    ConfirmDialog(
        title = "게임 종료",
        message = "초가 승리했습니다!",
        confirmText = "새 게임",
        dismissText = "취소",
        onConfirm = {},
        onDismiss = {}
    )
}
