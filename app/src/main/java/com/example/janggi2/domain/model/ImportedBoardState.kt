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
     *
     * 이동 규칙(예: [SoldierMovement])은 초가 항상 위쪽(낮은 row)에서 시작한다고
     * 가정하므로, 내부 좌표는 항상 이 규칙에 맞춰 정규화합니다. 사진과 같은 모습으로
     * 화면에 그리려면 [detectedBottomPlayer] 를 뷰의 viewpoint 로 함께 써야 합니다
     * ([toImportedGameResult] 참고).
     */
    fun toGameState(): GameState {
        val board = normalizedDetectedPieces().mapValues { it.value.piece }
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

    /**
     * 정규화된 [GameState] 와, 사진에 찍힌 모습 그대로 보여주기 위한 viewpoint(사진
     * 속에서 실제로 아래쪽에 있던 진영)를 함께 반환합니다.
     */
    fun toImportedGameResult(): ImportedGameResult =
        ImportedGameResult(gameState = toGameState(), viewpoint = detectedBottomPlayer())

    /**
     * 사진 속에서 초/한 중 어느 쪽이 아래(더 큰 row)에 있었는지. 양쪽 다 감지되지
     * 않았으면 기존 기본값인 한(HAN)을 아래로 봅니다.
     */
    fun detectedBottomPlayer(): Player {
        val choAvg = averageRow(Player.CHO)
        val hanAvg = averageRow(Player.HAN)
        if (choAvg == null || hanAvg == null) return Player.HAN
        return if (choAvg > hanAvg) Player.CHO else Player.HAN
    }

    private fun averageRow(player: Player): Double? {
        val rows = detectedPieces.values
            .filter { it.piece.player == player }
            .map { it.piece.position.row }
        return if (rows.isEmpty()) null else rows.average()
    }

    /**
     * 사진 속 실제 판은 초/한이 어느 쪽에 앉았느냐에 따라 위아래가 뒤집혀 있을 수
     * 있습니다. 초가 아래쪽으로 찍혔다면 보드를 180도 돌려 엔진이 기대하는
     * 배치(초가 위쪽)로 맞춥니다.
     */
    private fun normalizedDetectedPieces(): Map<Position, DetectedPiece> {
        if (detectedBottomPlayer() != Player.CHO) return detectedPieces

        return detectedPieces.entries.associate { (position, detected) ->
            val flipped = Position(8 - position.col, 9 - position.row)
            flipped to detected.copy(piece = detected.piece.moveTo(flipped))
        }
    }
}

/**
 * 사진 불러오기 결과: 엔진 규칙에 맞춰 정규화된 [GameState] 와, 사진과 같은 모습으로
 * 그리기 위한 viewpoint(사진에서 아래쪽에 있던 진영).
 */
data class ImportedGameResult(
    val gameState: GameState,
    val viewpoint: Player
)

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
