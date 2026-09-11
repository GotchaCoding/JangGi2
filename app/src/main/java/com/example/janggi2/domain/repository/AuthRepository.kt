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
     * 현재 로그인된 사용자를 동기적으로 확인합니다 (Firebase가 세션을 로컬에 유지하므로
     * 네트워크 호출 없이 즉시 반환됩니다). 앱 시작 시 로그인 화면/게임 화면 중 어디로
     * 시작할지 결정할 때 사용합니다.
     */
    fun getCurrentUser(): AuthUser?
}
