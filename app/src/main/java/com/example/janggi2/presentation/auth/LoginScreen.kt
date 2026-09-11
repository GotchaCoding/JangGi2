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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.janggi2.R
import com.example.janggi2.presentation.common.ConfirmDialog
import com.example.janggi2.ui.theme.JangGi2Theme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
                        try {
                            // R.string.default_web_client_id 는 google-services 플러그인이
                            // google-services.json으로부터 빌드 시 자동 생성합니다.
                            // 클라이언트 ID를 코드에 직접 적지 마세요.
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(context.getString(R.string.default_web_client_id))
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = CredentialManager.create(context)
                                .getCredential(context, request)
                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                onGoogleIdTokenReceived(token)
                            } else {
                                onSignInFailed("지원하지 않는 로그인 방식입니다.")
                            }
                        } catch (e: GetCredentialException) {
                            onSignInFailed("로그인이 취소되었거나 실패했습니다.")
                        }
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
