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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val stats: DetectionStats? = null
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

        // 8. 상태 업데이트
        _uiState.value = DebugUiState(
            isLoading = false,
            originalBitmap = bitmap,
            debugBitmap = debugBitmap,
            detectedLines = detectedLines,
            intersectionGrid = grid,
            detectedPieces = pieces,
            stats = stats
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
}
