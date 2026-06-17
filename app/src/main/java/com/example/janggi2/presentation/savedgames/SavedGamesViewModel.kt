package com.example.janggi2.presentation.savedgames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.repository.GameRepository
import com.example.janggi2.domain.repository.SavedGameInfo
import com.example.janggi2.domain.usecase.DeleteGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the saved games screen.
 */
@HiltViewModel
class SavedGamesViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val deleteGameUseCase: DeleteGameUseCase
) : ViewModel() {

    val savedGames: StateFlow<List<SavedGameInfo>> = gameRepository.getAllGames()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            deleteGameUseCase(gameId)
        }
    }
}
