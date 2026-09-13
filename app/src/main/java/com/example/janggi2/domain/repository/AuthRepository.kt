package com.example.janggi2.domain.repository

import com.example.janggi2.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * 구글 로그인(Firebase Authentication) 상태를 다루는 저장소.
 */
interface AuthRepository {

    /** 로그인 상태가 바뀔 때마다 현재 사용자(또는 로그아웃 상태면 null)를 냅니다. */
    val authState: Flow<AuthUser?>

    /**
     * 구글 ID 토큰으로 Firebase에 로그인합니다.
     * @param idToken Credential Manager에서 받은 Google ID 토큰
     */
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    /** 로그아웃합니다. */
    fun signOut()

    /**
     * Firebase 계정 자체를 삭제합니다. 계정에 딸린 기보 데이터는 이 함수가 아니라
     * [com.example.janggi2.domain.usecase.DeleteAccountUseCase] 가 먼저 지웁니다 -
     * 계정이 사라지면 uid 를 잃어 클라우드 문서를 찾을 수 없게 되므로 순서가 중요합니다.
     *
     * @param reAuthIdToken Firebase 는 마지막 로그인이 오래되면 계정 삭제를 거부하므로
     * ([com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException]),
     * 다시 받은 구글 ID 토큰으로 재인증한 뒤 지웁니다.
     */
    suspend fun deleteAccount(reAuthIdToken: String): Result<Unit>

    /**
     * 현재 로그인된 사용자를 동기적으로 확인합니다 (Firebase가 세션을 로컬에 유지하므로
     * 네트워크 호출 없이 즉시 반환됩니다). 앱 시작 시 로그인 화면/게임 화면 중 어디로
     * 시작할지 결정할 때 사용합니다.
     */
    fun getCurrentUser(): AuthUser?
}
