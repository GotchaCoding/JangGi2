package com.example.janggi2.presentation.auth

import com.example.janggi2.MainDispatcherRule
import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import com.example.janggi2.domain.repository.SavedReview
import com.example.janggi2.domain.repository.SavedReviewInfo
import com.example.janggi2.domain.usecase.SyncGamesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    private class FakeAuthRepository(private val signInResult: Result<AuthUser>) : AuthRepository {
        override val authState: Flow<AuthUser?> = flowOf(null)
        override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = signInResult
        override fun signOut() {}
        override fun getCurrentUser(): AuthUser? = null
    }

    /** [GameRepository]의 이번 테스트와 무관한 메서드는 전부 최소 스텁입니다. */
    private class FakeGameRepository : GameRepository {
        override suspend fun saveGame(
            gameState: GameState,
            name: String,
            choPlayerName: String?,
            hanPlayerName: String?,
            choRank: String?,
            hanRank: String?
        ): Long = 0

        override suspend fun autoSave(gameState: GameState) {}
        override suspend fun loadGame(gameId: Long): GameState? = null
        override suspend fun loadAutoSave(): GameState? = null
        override fun getAllGames(): Flow<List<SavedGameInfo>> = flowOf(emptyList())
        override suspend fun deleteGame(gameId: Long) {}
        override suspend fun deleteAllGames() {}
        override suspend fun pullFromCloud(): Int = 0
        override suspend fun saveReview(gameState: GameState, review: GameReview, name: String): Long = 0
        override suspend fun loadReview(reviewId: Long): SavedReview? = null
        override fun getAllReviews(): Flow<List<SavedReviewInfo>> = flowOf(emptyList())
        override suspend fun deleteReview(reviewId: Long) {}
        override suspend fun saveComment(
            reviewId: Long,
            message: String,
            branchStartIndex: Int,
            moves: List<Move>
        ): Long = 0

        override fun getCommentsForReview(reviewId: Long): Flow<List<ReviewComment>> = flowOf(emptyList())
    }

    @Test
    fun `구글 로그인 성공 시 signedInUser 가 채워진다`() = runTest {
        val user = AuthUser("u1", "테스트", "test@example.com", null)
        val viewModel = LoginViewModel(
            FakeAuthRepository(Result.success(user)),
            SyncGamesUseCase(FakeGameRepository())
        )

        viewModel.onGoogleIdTokenReceived("token")
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.signedInUser)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `구글 로그인 실패 시 errorMessage 가 설정된다`() = runTest {
        val viewModel = LoginViewModel(
            FakeAuthRepository(Result.failure(RuntimeException("network"))),
            SyncGamesUseCase(FakeGameRepository())
        )

        viewModel.onGoogleIdTokenReceived("token")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.signedInUser)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `Credential Manager 단계 실패는 onSignInFailed 로 errorMessage 를 설정한다`() = runTest {
        val viewModel = LoginViewModel(
            FakeAuthRepository(Result.success(AuthUser("u1", null, null, null))),
            SyncGamesUseCase(FakeGameRepository())
        )

        viewModel.onSignInFailed("로그인이 취소되었거나 실패했습니다.")

        assertEquals("로그인이 취소되었거나 실패했습니다.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.signedInUser)
    }
}
