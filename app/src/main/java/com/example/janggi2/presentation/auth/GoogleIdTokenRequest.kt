package com.example.janggi2.presentation.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.janggi2.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Credential Manager 로 구글 계정을 고르게 하고 ID 토큰을 받습니다. 로그인과 계정
 * 삭제(재인증) 둘 다 같은 절차가 필요해 한 곳에 모았습니다.
 *
 * @return 성공 시 ID 토큰. 사용자가 취소했거나 실패하면 [Result.failure].
 */
suspend fun requestGoogleIdToken(context: Context): Result<String> = try {
    // R.string.default_web_client_id 는 google-services 플러그인이
    // google-services.json 으로부터 빌드 시 자동 생성합니다.
    // 클라이언트 ID 를 코드에 직접 적지 마세요.
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } else {
        Result.failure(IllegalStateException("지원하지 않는 로그인 방식입니다."))
    }
} catch (e: GetCredentialException) {
    Result.failure(e)
}
