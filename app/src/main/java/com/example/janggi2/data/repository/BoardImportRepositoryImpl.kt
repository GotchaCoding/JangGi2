package com.example.janggi2.data.repository

import android.net.Uri
import com.example.janggi2.data.imageprocessing.BoardRecognitionService
import com.example.janggi2.domain.model.DetectedPiece
import com.example.janggi2.domain.model.ImportedBoardState
import com.example.janggi2.domain.repository.BoardImportRepository
import javax.inject.Inject

/**
 * Implementation of BoardImportRepository using Grid-based recognition.
 */
class BoardImportRepositoryImpl @Inject constructor(
    private val recognitionService: BoardRecognitionService
) : BoardImportRepository {

    override suspend fun recognizeBoardFromImage(imageUri: Uri): Result<ImportedBoardState> {
        return try {
            // Extract pieces from image (grid-based)
            val extractionResult = recognitionService.extractText(imageUri)
            if (extractionResult.isFailure) {
                return Result.failure(
                    extractionResult.exceptionOrNull() ?: Exception("기물 인식 실패")
                )
            }

            val extractedText = extractionResult.getOrNull()
                ?: return Result.failure(Exception("추출 결과를 받을 수 없습니다"))

            // Map to ImportedBoardState (신뢰도가 문턱 미만인 검출은 배경 노이즈 오검출로
            // 보고 버립니다 - ImportedBoardState.MIN_ACCEPTED_CONFIDENCE 참고)
            val detectedPieces = ImportedBoardState.filterByConfidence(
                extractedText.detectedPieces.associate { detected ->
                    detected.position to DetectedPiece(
                        piece = detected.piece,
                        confidence = detected.confidence,
                        isManuallyAdjusted = false
                    )
                }
            )

            if (detectedPieces.isEmpty()) {
                return Result.failure(Exception("기물을 감지하지 못했습니다"))
            }

            val overallConfidence = detectedPieces.values.map { it.confidence }.average().toFloat()

            val importedBoard = ImportedBoardState(
                detectedPieces = detectedPieces,
                overallConfidence = overallConfidence,
                gridDetected = true
            )

            Result.success(importedBoard)
        } catch (e: Exception) {
            Result.failure(Exception("보드 인식 실패: ${e.message}", e))
        }
    }
}
