package com.example.janggi2.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 게임 화면 상단의 계정 메뉴(로그아웃)를 위한 최소 ViewModel. 계정 상태는 게임 상태와
 * 무관하므로 GameUiState에 끼워넣지 않고 분리했습니다.
 */
@HiltViewModel
class AccountMenuViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.getCurrentUser())

    fun signOut() {
        authRepository.signOut()
    }
}
