package com.example.janggi2.presentation.importboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.usecase.ImportBoardFromImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the import screen.
 */
data class ImportUiState(
    val selectedImageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val importedGameState: GameState? = null,
    val error: String? = null,
    val processingMessage: String = "사진을 선택하세요"
)

/**
 * ViewModel for the import screen.
 * Manages the image import and OCR processing flow.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importBoardUseCase: ImportBoardFromImageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * Called when user selects an image from the photo picker.
     * Automatically starts the import process.
     */
    fun onImageSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            isProcessing = true,
            error = null,
            processingMessage = "이미지 분석 중...",
            importedGameState = null
        )

        viewModelScope.launch {
            processImage(uri)
        }
    }

    /**
     * Processes the selected image and imports the board state.
     */
    private suspend fun processImage(uri: Uri) {
        try {
            // Update processing message
            _uiState.value = _uiState.value.copy(
                processingMessage = "기물 인식 중..."
            )

            // Call the use case to import the board
            val result = importBoardUseCase(uri)

            if (result.isSuccess) {
                val gameState = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    importedGameState = gameState,
                    error = null,
                    processingMessage = "인식 완료!"
                )
            } else {
                val errorMessage = result.exceptionOrNull()?.message
                    ?: "알 수 없는 오류가 발생했습니다"
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = errorMessage,
                    processingMessage = "오류 발생"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "기보 불러오기 실패: ${e.message}",
                processingMessage = "오류 발생"
            )
        }
    }

    /**
     * Resets the import state to allow trying again.
     */
    fun retry() {
        _uiState.value = ImportUiState()
    }

    /**
     * Clears the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Gets the imported game state for navigation.
     * Returns null if no game state is available.
     */
    fun getImportedGameState(): GameState? {
        return _uiState.value.importedGameState
    }
}
