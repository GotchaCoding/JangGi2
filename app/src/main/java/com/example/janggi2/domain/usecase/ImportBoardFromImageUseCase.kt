package com.example.janggi2.domain.usecase

import android.net.Uri
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.repository.BoardImportRepository
import javax.inject.Inject

/**
 * Use case for importing a board position from an image.
 * Orchestrates the image recognition and validation process.
 */
class ImportBoardFromImageUseCase @Inject constructor(
    private val repository: BoardImportRepository
) {
    /**
     * Imports a board position from an image.
     *
     * @param imageUri URI of the image to process
     * @return Result containing GameState on success, or error on failure
     */
    suspend operator fun invoke(imageUri: Uri): Result<GameState> {
        return try {
            // Step 1: Recognize board from image
            val importResult = repository.recognizeBoardFromImage(imageUri)

            if (importResult.isFailure) {
                return Result.failure(
                    importResult.exceptionOrNull()
                        ?: Exception("이미지 인식 실패")
                )
            }

            val importedBoard = importResult.getOrNull()
                ?: return Result.failure(Exception("이미지 인식 결과를 받을 수 없습니다"))

            // Step 2: Validate the detected board
            val validationError = importedBoard.validate()
            if (validationError != null) {
                return Result.failure(Exception(validationError))
            }

            // Step 3: Convert to GameState
            val gameState = importedBoard.toGameState()

            Result.success(gameState)
        } catch (e: Exception) {
            Result.failure(Exception("기보 불러오기 실패: ${e.message}", e))
        }
    }
}
