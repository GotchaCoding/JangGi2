package com.example.janggi2.presentation.videoimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.usecase.ImportBoardFromVideoUseCase
import com.example.janggi2.domain.usecase.VideoImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 동영상 불러오기 화면의 상태.
 */
data class VideoImportUiState(
    val selectedVideoUri: Uri? = null,
    val isProcessing: Boolean = false,
    /** 분석 완료 프레임 수 / 전체 정지 프레임 수 */
    val progress: Pair<Int, Int>? = null,
    val movesRecovered: Int? = null,
    val importedGameState: GameState? = null,
    val importedViewpoint: Player = Player.HAN,
    val error: String? = null
)

/**
 * 동영상 불러오기 화면의 뷰모델. 정지 프레임을 찾아 수를 재구성하는 동안 진행 상황을
 * 보여줍니다 - AI 리뷰([com.example.janggi2.presentation.game.GameViewModel.requestReview])
 * 와 같은 모양입니다.
 */
@HiltViewModel
class VideoImportViewModel @Inject constructor(
    private val importBoardFromVideoUseCase: ImportBoardFromVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoImportUiState())
    val uiState: StateFlow<VideoImportUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        _uiState.value = VideoImportUiState(
            selectedVideoUri = uri,
            isProcessing = true
        )

        viewModelScope.launch {
            try {
                importBoardFromVideoUseCase.import(uri).collect { progress ->
                    when (progress) {
                        is VideoImportProgress.Analyzing -> {
                            _uiState.value = _uiState.value.copy(
                                progress = progress.completed to progress.total
                            )
                        }
                        is VideoImportProgress.Finished -> {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                importedGameState = progress.gameState,
                                importedViewpoint = progress.viewpoint,
                                movesRecovered = progress.movesRecovered
                            )
                        }
                        is VideoImportProgress.Failed -> {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                error = progress.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "동영상 불러오기 실패: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        _uiState.value = VideoImportUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
