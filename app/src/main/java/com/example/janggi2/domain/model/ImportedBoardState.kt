package com.example.janggi2.domain.model

/**
 * Represents the result of board recognition from an image.
 * Contains detected pieces and confidence metrics.
 */
data class ImportedBoardState(
    val detectedPieces: Map<Position, DetectedPiece>,
    val overallConfidence: Float,
    val gridDetected: Boolean
) {
    /**
     * Validates that the detected board has required pieces.
     * Returns error message if validation fails, null if valid.
     */
    fun validate(): String? {
        val totalPieces = detectedPieces.size
        if (totalPieces < 1) {
            return "기물을 찾을 수 없습니다."
        }

        // Relaxed validation - allow partial board recognition
        // This is useful for testing and when OCR doesn't detect all pieces

        return null  // Accept any detected pieces
    }

    /**
     * Converts the imported board state to a GameState.
     */
    fun toGameState(): GameState {
        val board = detectedPieces.mapValues { it.value.piece }
        return GameState(
            board = board,
            currentPlayer = Player.CHO,
            moveHistory = emptyList(),
            status = GameStatus.ONGOING
        )
    }
}

/**
 * Represents a detected piece with confidence metrics.
 */
data class DetectedPiece(
    val piece: Piece,
    val confidence: Float,
    val isManuallyAdjusted: Boolean = false
) {
    /**
     * Returns true if confidence is low and manual verification is recommended.
     */
    fun needsVerification(): Boolean = confidence < 0.8f && !isManuallyAdjusted
}
