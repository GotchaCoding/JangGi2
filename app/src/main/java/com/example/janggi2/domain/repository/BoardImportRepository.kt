package com.example.janggi2.domain.repository

import android.net.Uri
import com.example.janggi2.domain.model.ImportedBoardState

/**
 * Repository interface for importing board positions from images.
 */
interface BoardImportRepository {
    /**
     * Recognizes Janggi board position from an image.
     *
     * @param imageUri URI of the image to process
     * @return Result containing ImportedBoardState on success, or error on failure
     */
    suspend fun recognizeBoardFromImage(imageUri: Uri): Result<ImportedBoardState>
}
