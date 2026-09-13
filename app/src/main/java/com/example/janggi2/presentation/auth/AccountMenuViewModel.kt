package com.example.janggi2.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.domain.usecase.DeleteAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountMenuUiState(
    val isDeleting: Boolean = false,
    val deleteErrorMessage: String? = null,
    val isDeleted: Boolean = false
)

/**
 * 게임 화면 상단의 계정 메뉴(로그아웃·계정 삭제)를 위한 ViewModel. 계정 상태는 게임
 * 상태와 무관하므로 GameUiState 에 끼워넣지 않고 분리했습니다.
 */
@HiltViewModel
class AccountMenuViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.getCurrentUser())

    private val _uiState = MutableStateFlow(AccountMenuUiState())
    val uiState: StateFlow<AccountMenuUiState> = _uiState.asStateFlow()

    fun signOut() {
        authRepository.signOut()
    }

    /** 재인증용 구글 ID 토큰을 받은 뒤 계정과 데이터를 삭제합니다. */
    fun deleteAccount(reAuthIdToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, deleteErrorMessage = null)
            deleteAccountUseCase(reAuthIdToken)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleting = false, isDeleted = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteErrorMessage = "계정 삭제에 실패했습니다: ${e.message}"
                    )
                }
        }
    }

    /** 재인증 단계에서 취소·실패한 경우. */
    fun onDeleteFailed(message: String) {
        _uiState.value = _uiState.value.copy(isDeleting = false, deleteErrorMessage = message)
    }

    fun dismissDeleteError() {
        _uiState.value = _uiState.value.copy(deleteErrorMessage = null)
    }
}
