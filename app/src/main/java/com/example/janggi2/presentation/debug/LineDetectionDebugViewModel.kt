package com.example.janggi2.presentation.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janggi2.data.imageprocessing.BoardDetector
import com.example.janggi2.data.imageprocessing.IntersectionCalculator
import com.example.janggi2.data.imageprocessing.LineDetector
import com.example.janggi2.data.imageprocessing.PieceDetector
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 선 검출 디버그 화면 ViewModel
 */
@HiltViewModel
class LineDetectionDebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boardDetector: BoardDetector,
    private val lineDetector: LineDetector,
    private val intersectionCalculator: IntersectionCalculator,
    private val pieceDetector: PieceDetector
) : ViewModel() {

    companion object {
        private const val TAG = "LineDetectionDebug"
    }

    /**
     * 교차점 검증 데이터
     */
    data class IntersectionVerification(
        val position: Position,
        val pixelX: Float,
        val pixelY: Float,
        val detectedPiece: PieceDetector.DetectedPiece?,  // AI가 검출한 기물
        val verifiedPieceType: PieceDetector.PieceType? = null,         // 사용자가 확인한 기물 타입
        val verifiedPlayer: Player? = null,               // 사용자가 확인한 진영
        val isVerified: Boolean = false,                  // 검증 완료 여부
        val isCorrect: Boolean? = null                    // AI 검출이 맞았는지
    )

    /**
     * UI 상태
     */
    data class DebugUiState(
        val isLoading: Boolean = false,
        val originalBitmap: Bitmap? = null,
        val debugBitmap: Bitmap? = null,
        val detectedLines: LineDetector.DetectedLines? = null,
        val intersectionGrid: IntersectionCalculator.IntersectionGrid? = null,
        val detectedPieces: List<PieceDetector.DetectedPiece> = emptyList(),
        val error: String? = null,
        val stats: DetectionStats? = null,
        // 교차점 검증 관련
        val intersectionVerifications: Map<Position, IntersectionVerification> = emptyMap(),
        val selectedPosition: Position? = null,
        val showVerificationDialog: Boolean = false
    )

    /**
     * 검출 통계
     */
    data class DetectionStats(
        val verticalLineCount: Int,
        val horizontalLineCount: Int,
        val intersectionCount: Int,
        val pieceCount: Int,
        val choCount: Int,
        val hanCount: Int,
        val lineConfidence: Float,
        val avgPieceConfidence: Float,
        val usedLineDetection: Boolean
    )

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    /**
     * 이미지 분석 시작
     */
    fun analyzeImage(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                withContext(Dispatchers.IO) {
                    performAnalysis(imageUri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "분석 실패: ${e.message}"
                )
            }
        }
    }

    private suspend fun performAnalysis(imageUri: Uri) {
        // 1. 이미지 로드
        val bitmap = loadBitmap(imageUri) ?: run {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "이미지를 불러올 수 없습니다"
            )
            return
        }

        Log.d(TAG, "Image loaded: ${bitmap.width}x${bitmap.height}")

        // 2. 보드 영역 검출
        val boardRegion = boardDetector.detectBoard(bitmap)
            ?: boardDetector.estimateBoardRegion(bitmap)

        Log.d(TAG, "Board region: ${boardRegion.width()}x${boardRegion.height()}")

        // 3. 선 검출
        val detectedLines = lineDetector.detectLines(bitmap, boardRegion)

        if (detectedLines == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                originalBitmap = bitmap,
                error = "선 검출 실패"
            )
            return
        }

        Log.d(TAG, "Lines detected: V=${detectedLines.verticalLines.size}, " +
                "H=${detectedLines.horizontalLines.size}")

        // 4. 교차점 계산
        val grid = intersectionCalculator.calculateIntersections(detectedLines)

        Log.d(TAG, "Intersections: ${grid.intersections.size}")

        // 5. 기물 검출
        val pieces = pieceDetector.detectAllPieces(bitmap, grid)

        Log.d(TAG, "Pieces detected: ${pieces.size}")

        // 6. 디버그 이미지 생성
        val debugBitmap = createDebugBitmap(bitmap, detectedLines, grid, pieces)

        // 7. 통계 계산
        val stats = DetectionStats(
            verticalLineCount = detectedLines.verticalLines.size,
            horizontalLineCount = detectedLines.horizontalLines.size,
            intersectionCount = grid.intersections.size,
            pieceCount = pieces.size,
            choCount = pieces.count { it.player == com.example.janggi2.domain.model.Player.CHO },
            hanCount = pieces.count { it.player == com.example.janggi2.domain.model.Player.HAN },
            lineConfidence = detectedLines.confidence,
            avgPieceConfidence = if (pieces.isNotEmpty()) {
                pieces.map { it.confidence }.average().toFloat()
            } else 0f,
            usedLineDetection = true
        )

        // 8. 교차점 검증 데이터 초기화
        val intersectionVerifications = grid.intersections.associate { intersection ->
            val detectedPiece = pieces.find { it.position == intersection.position }
            intersection.position to IntersectionVerification(
                position = intersection.position,
                pixelX = intersection.pixelX,
                pixelY = intersection.pixelY,
                detectedPiece = detectedPiece
            )
        }

        // 9. 상태 업데이트
        _uiState.value = DebugUiState(
            isLoading = false,
            originalBitmap = bitmap,
            debugBitmap = debugBitmap,
            detectedLines = detectedLines,
            intersectionGrid = grid,
            detectedPieces = pieces,
            stats = stats,
            intersectionVerifications = intersectionVerifications
        )
    }

    private fun loadBitmap(imageUri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Downsample to 2048px width
            val targetWidth = 2048
            val sampleSize = if (options.outWidth > targetWidth) {
                options.outWidth / targetWidth
            } else {
                1
            }

            val inputStream2 = context.contentResolver.openInputStream(imageUri)
                ?: return null

            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val bitmap = BitmapFactory.decodeStream(inputStream2, null, loadOptions)
            inputStream2.close()

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun createDebugBitmap(
        bitmap: Bitmap,
        lines: LineDetector.DetectedLines,
        grid: IntersectionCalculator.IntersectionGrid,
        pieces: List<PieceDetector.DetectedPiece>
    ): Bitmap {
        // 선 디버그 이미지 생성
        var debugBitmap = lineDetector.createDebugImage(bitmap, lines)

        // 기물 디버그 이미지 생성 (기존 이미지 위에 오버레이)
        debugBitmap = pieceDetector.createDebugImage(debugBitmap, pieces, grid)

        return debugBitmap
    }

    /**
     * 에러 상태 초기화
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 교차점 클릭 처리
     */
    fun onIntersectionClick(position: Position) {
        Log.d(TAG, "Intersection clicked: $position")
        _uiState.update { state ->
            state.copy(
                selectedPosition = position,
                showVerificationDialog = true
            )
        }
    }

    /**
     * 검증 다이얼로그 닫기
     */
    fun dismissVerificationDialog() {
        _uiState.update { state ->
            state.copy(
                showVerificationDialog = false,
                selectedPosition = null
            )
        }
    }

    /**
     * 기물 검증 결과 저장
     * @param position 교차점 위치
     * @param pieceType 실제 기물 타입 (null이면 기물 없음)
     * @param player 실제 진영 (null이면 기물 없음)
     */
    fun verifyIntersection(position: Position, pieceType: PieceDetector.PieceType?, player: Player?) {
        Log.d(TAG, "Verifying intersection $position: pieceType=$pieceType, player=$player")

        val currentState = _uiState.value
        val grid = currentState.intersectionGrid ?: return
        val intersection = grid.intersections.find { it.position == position } ?: return

        // AI가 검출한 기물 찾기
        val detectedPiece = currentState.detectedPieces.find { it.position == position }

        // AI 검출이 맞았는지 확인
        val isCorrect = when {
            pieceType == null && detectedPiece == null -> true  // 둘 다 없음
            pieceType == null && detectedPiece != null -> false // AI만 검출
            pieceType != null && detectedPiece == null -> false // AI 미검출
            else -> detectedPiece?.pieceType == pieceType && detectedPiece?.player == player
        }

        val verification = IntersectionVerification(
            position = position,
            pixelX = intersection.pixelX,
            pixelY = intersection.pixelY,
            detectedPiece = detectedPiece,
            verifiedPieceType = pieceType,
            verifiedPlayer = player,
            isVerified = true,
            isCorrect = isCorrect
        )

        _uiState.update { state ->
            state.copy(
                intersectionVerifications = state.intersectionVerifications + (position to verification),
                showVerificationDialog = false,
                selectedPosition = null
            )
        }

        Log.d(TAG, "Verification saved: position=$position, isCorrect=$isCorrect")
    }

    /**
     * 선택된 교차점의 현재 검증 정보 가져오기
     */
    fun getSelectedVerification(): IntersectionVerification? {
        val position = _uiState.value.selectedPosition ?: return null
        val grid = _uiState.value.intersectionGrid ?: return null
        val intersection = grid.intersections.find { it.position == position } ?: return null

        return _uiState.value.intersectionVerifications[position]
            ?: IntersectionVerification(
                position = position,
                pixelX = intersection.pixelX,
                pixelY = intersection.pixelY,
                detectedPiece = _uiState.value.detectedPieces.find { it.position == position }
            )
    }

    /**
     * 검증 통계 계산
     */
    fun getVerificationStats(): VerificationStats {
        val verifications = _uiState.value.intersectionVerifications.values
        val verified = verifications.filter { it.isVerified }
        val correct = verified.filter { it.isCorrect == true }
        val withPieces = verified.filter { it.verifiedPieceType != null }

        return VerificationStats(
            totalIntersections = 90,
            verifiedCount = verified.size,
            correctCount = correct.size,
            incorrectCount = verified.size - correct.size,
            piecesFound = withPieces.size,
            accuracy = if (verified.isNotEmpty()) correct.size.toFloat() / verified.size else 0f
        )
    }

    /**
     * 검증 데이터 내보내기 (JSON 형태)
     */
    fun exportVerificationData(): String {
        val verifications = _uiState.value.intersectionVerifications.values
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"verifications\": [")

        verifications.forEachIndexed { index, v ->
            sb.appendLine("    {")
            sb.appendLine("      \"position\": { \"col\": ${v.position.col}, \"row\": ${v.position.row} },")
            sb.appendLine("      \"pixelX\": ${v.pixelX},")
            sb.appendLine("      \"pixelY\": ${v.pixelY},")
            sb.appendLine("      \"detectedPiece\": ${v.detectedPiece?.let { "\"${it.pieceType}\"" } ?: "null"},")
            sb.appendLine("      \"detectedPlayer\": ${v.detectedPiece?.let { "\"${it.player}\"" } ?: "null"},")
            sb.appendLine("      \"verifiedPiece\": ${v.verifiedPieceType?.let { "\"$it\"" } ?: "null"},")
            sb.appendLine("      \"verifiedPlayer\": ${v.verifiedPlayer?.let { "\"$it\"" } ?: "null"},")
            sb.appendLine("      \"isCorrect\": ${v.isCorrect}")
            sb.append("    }")
            if (index < verifications.size - 1) sb.appendLine(",") else sb.appendLine()
        }

        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * 검증 통계 데이터
     */
    data class VerificationStats(
        val totalIntersections: Int,
        val verifiedCount: Int,
        val correctCount: Int,
        val incorrectCount: Int,
        val piecesFound: Int,
        val accuracy: Float
    )
}
