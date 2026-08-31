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
 *
 * @param stepLabel 지금 진행 중인 세부 단계 설명(예: "영상 훑는 중", "기물 인식 중") -
 *   null이면 아직 아무 단계도 시작 안 한 상태입니다.
 * @param etaSeconds 지금 단계가 끝나기까지 예상 남은 시간(초). 이 단계에서 아직 속도를
 *   가늠할 만큼 진행이 안 됐으면 null입니다.
 */
data class VideoImportUiState(
    val selectedVideoUri: Uri? = null,
    val isProcessing: Boolean = false,
    val stepLabel: String? = null,
    val stepCompleted: Int = 0,
    val stepTotal: Int = 0,
    val etaSeconds: Long? = null,
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

    // 예상 완료 시간(ETA) 계산용 - 단계가 바뀔 때마다 그 단계가 막 시작된 시각으로
    // 다시 잡습니다. 단계마다 한 표본당 걸리는 시간이 서로 달라서(예: 체크포인트
    // 검증은 표본 하나당 OCR을 최대 11번 돌림), 이전 단계에서 잰 속도를 이어 쓰면
    // ETA가 완전히 틀어집니다.
    private var currentStepLabel: String? = null
    private var stepStartMs: Long = 0L

    fun onVideoSelected(uri: Uri) {
        _uiState.value = VideoImportUiState(
            selectedVideoUri = uri,
            isProcessing = true
        )
        currentStepLabel = null

        viewModelScope.launch {
            try {
                importBoardFromVideoUseCase.import(uri).collect { progress ->
                    when (progress) {
                        is VideoImportProgress.Step -> {
                            if (progress.label != currentStepLabel) {
                                currentStepLabel = progress.label
                                stepStartMs = System.currentTimeMillis()
                            }
                            val elapsedMs = System.currentTimeMillis() - stepStartMs
                            val etaSeconds = if (progress.completed in 1 until progress.total) {
                                (elapsedMs.toDouble() / progress.completed * (progress.total - progress.completed) / 1000)
                                    .toLong()
                                    .coerceAtLeast(0)
                            } else {
                                null
                            }
                            _uiState.value = _uiState.value.copy(
                                stepLabel = progress.label,
                                stepCompleted = progress.completed,
                                stepTotal = progress.total,
                                etaSeconds = etaSeconds
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
        currentStepLabel = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
