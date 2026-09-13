package com.example.janggi2.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.presentation.common.ConfirmDialog
import com.example.janggi2.ui.theme.JangGi2Theme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onGoogleIdTokenReceived: (String) -> Unit,
    onSignInFailed: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("장기", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(32.dp))
            Button(
                enabled = !uiState.isLoading,
                onClick = {
                    scope.launch {
                        requestGoogleIdToken(context)
                            .onSuccess(onGoogleIdTokenReceived)
                            .onFailure { onSignInFailed("로그인이 취소되었거나 실패했습니다.") }
                    }
                }
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Google로 로그인")
                }
            }
        }
    }

    uiState.errorMessage?.let { message ->
        ConfirmDialog(
            title = "로그인",
            message = message,
            confirmText = "확인",
            onConfirm = onDismissError,
            onDismiss = onDismissError
        )
    }
}

@Preview
@Composable
fun LoginScreenPreview() {
    JangGi2Theme {
        LoginScreen(
            uiState = LoginUiState(),
            onGoogleIdTokenReceived = {},
            onSignInFailed = {},
            onDismissError = {}
        )
    }
}
