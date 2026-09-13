package com.example.janggi2.presentation.auth

import com.example.janggi2.MainDispatcherRule
import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.usecase.SyncGamesUseCase
import com.example.janggi2.fake.FakeAuthRepository
import com.example.janggi2.fake.FakeGameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModelWith(signInResult: Result<AuthUser>) = LoginViewModel(
        FakeAuthRepository(signInResult = signInResult),
        SyncGamesUseCase(FakeGameRepository())
    )

    @Test
    fun `구글 로그인 성공 시 signedInUser 가 채워진다`() = runTest {
        val user = AuthUser("u1", "테스트", "test@example.com", null)
        val viewModel = viewModelWith(Result.success(user))

        viewModel.onGoogleIdTokenReceived("token")
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.signedInUser)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `구글 로그인 실패 시 errorMessage 가 설정된다`() = runTest {
        val viewModel = viewModelWith(Result.failure(RuntimeException("network")))

        viewModel.onGoogleIdTokenReceived("token")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.signedInUser)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `Credential Manager 단계 실패는 onSignInFailed 로 errorMessage 를 설정한다`() = runTest {
        val viewModel = viewModelWith(Result.success(AuthUser("u1", null, null, null)))

        viewModel.onSignInFailed("로그인이 취소되었거나 실패했습니다.")

        assertEquals("로그인이 취소되었거나 실패했습니다.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.signedInUser)
    }
}
