package com.example.janggi2.fake

import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** [AuthRepository] 의 최소 구현. 테스트에서 필요한 결과만 주입해 씁니다. */
open class FakeAuthRepository(
    private val signInResult: Result<AuthUser> = Result.success(
        AuthUser("uid", "테스트", "test@example.com", null)
    ),
    private val deleteResult: Result<Unit> = Result.success(Unit)
) : AuthRepository {
    override val authState: Flow<AuthUser?> = flowOf(null)
    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = signInResult
    override fun signOut() {}
    override fun getCurrentUser(): AuthUser? = null
    override suspend fun deleteAccount(reAuthIdToken: String): Result<Unit> = deleteResult
}
