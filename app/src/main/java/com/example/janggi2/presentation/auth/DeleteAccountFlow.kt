package com.example.janggi2.presentation.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.janggi2.presentation.common.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * 계정 삭제 절차의 다이얼로그들. 되돌릴 수 없는 동작이라 먼저 확인을 받고, 구글
 * 재인증을 거친 뒤 지웁니다.
 *
 * @param showConfirm 확인 다이얼로그를 띄울지 여부(메뉴에서 "계정 삭제"를 누른 상태)
 */
@Composable
fun DeleteAccountFlow(
    showConfirm: Boolean,
    uiState: AccountMenuUiState,
    onConfirmDismiss: () -> Unit,
    onReAuthTokenReceived: (String) -> Unit,
    onReAuthFailed: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showConfirm && !uiState.isDeleting) {
        ConfirmDialog(
            title = "계정 삭제",
            message = "계정과 저장된 기보를 모두 삭제합니다. 클라우드와 이 기기에서 " +
                "지워지며 되돌릴 수 없습니다.\n\n계속하려면 Google 계정으로 다시 " +
                "인증해야 합니다.",
            confirmText = "삭제",
            dismissText = "취소",
            onConfirm = {
                onConfirmDismiss()
                scope.launch {
                    requestGoogleIdToken(context)
                        .onSuccess(onReAuthTokenReceived)
                        .onFailure { onReAuthFailed("인증이 취소되어 계정을 삭제하지 않았습니다.") }
                }
            },
            onDismiss = onConfirmDismiss
        )
    }

    if (uiState.isDeleting) {
        // 삭제 중에는 닫을 수 없게 둡니다 - 중간에 화면을 벗어나면 클라우드 문서는
        // 지워졌는데 계정은 남는 어중간한 상태가 보이기 쉽습니다.
        AlertDialog(
            onDismissRequest = {},
            title = { Text("계정 삭제") },
            text = { Text("계정과 기보를 삭제하고 있습니다...") },
            confirmButton = { CircularProgressIndicator() }
        )
    }

    uiState.deleteErrorMessage?.let { message ->
        ConfirmDialog(
            title = "계정 삭제",
            message = message,
            confirmText = "확인",
            onConfirm = onDismissError,
            onDismiss = onDismissError
        )
    }
}
