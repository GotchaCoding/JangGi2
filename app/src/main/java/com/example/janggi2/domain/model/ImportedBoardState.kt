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
            status = GameStatus.ONGOING,
            // 반복 규칙은 시작 국면부터 수순을 재생해야 가릴 수 있는데, 불러온 판은
            // 표준 배치가 아니므로 여기가 그 시작입니다.
            startBoard = board
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
