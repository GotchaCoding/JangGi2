package com.example.janggi2.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.domain.usecase.SyncGamesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val signedInUser: AuthUser? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncGamesUseCase: SyncGamesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Credential Manager에서 받은 구글 ID 토큰으로 Firebase 로그인을 시도합니다. */
    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    syncGamesUseCase() // best-effort 풀 동기화, 실패는 내부에서 삼켜짐
                    _uiState.value = _uiState.value.copy(isLoading = false, signedInUser = user)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "로그인에 실패했습니다: ${e.message}"
                    )
                }
        }
    }

    /** Credential Manager 단계에서 로그인 자체가 실패/취소된 경우. */
    fun onSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
