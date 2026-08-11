package com.example.janggi2.data.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 장기판 인식 서비스
 *
 * 선 검출 기반 정확한 기물 인식:
 * 1. Hough Line Transform으로 9개 세로선, 10개 가로선 검출
 * 2. 90개 교차점 좌표 정밀 계산
 * 3. 각 교차점에서 원형 ROI 기반 기물 검출
 *
 * 선 검출 실패 시 기존 균등 분할 방식으로 폴백
 */
@Singleton
class BoardRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boardDetector: BoardDetector,
    private val lineDetector: LineDetector,
    private val intersectionCalculator: IntersectionCalculator,
    private val pieceDetector: PieceDetector
) {
    companion object {
        private const val TAG = "BoardRecognition"
        private const val USE_LINE_DETECTION = true  // 선 검출 사용 여부
    }

    /**
     * 이미지에서 장기 기물을 검출합니다.
     *
     * 1차: 선 검출 기반 정밀 인식 시도
     * 2차 (폴백): 균등 분할 + 색상 감지
     *
     * @param imageUri URI of the image to process
     * @return Result containing detected pieces
     */
    suspend fun extractText(imageUri: Uri): Result<ExtractedText> {
        return try {
            Log.d(TAG, "=== Board Recognition Started ===")

            // 1. Load image
            val bitmap = loadAndDownsampleImage(imageUri)
                ?: return Result.failure(Exception("이미지를 불러올 수 없습니다"))

            Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")

            // 2. Detect board region
            val boardRegion = boardDetector.detectBoard(bitmap)
                ?: boardDetector.estimateBoardRegion(bitmap)
            Log.i(TAG, "Board region: ${boardRegion.width()}x${boardRegion.height()}")

            // 3. 선 검출 기반 인식 시도
            if (USE_LINE_DETECTION) {
                val lineResult = tryLineBasedDetection(bitmap, boardRegion)
                if (lineResult != null) {
                    return Result.success(lineResult)
                }
                Log.w(TAG, "Line-based detection failed, falling back to grid method")
            }

            // 4. 폴백: 기존 균등 분할 방식
            val fallbackResult = detectWithGridMethod(bitmap, boardRegion)

            if (fallbackResult.detectedPieces.isEmpty()) {
                return Result.failure(Exception("기물을 감지하지 못했습니다"))
            }

            Result.success(fallbackResult)
        } catch (e: Exception) {
            Log.e(TAG, "Board recognition failed", e)
            Result.failure(Exception("기물 인식 실패: ${e.message}", e))
        }
    }

    /**
     * 선 검출 기반 정밀 인식
     */
    private suspend fun tryLineBasedDetection(
        bitmap: Bitmap,
        boardRegion: android.graphics.Rect
    ): ExtractedText? {
        try {
            Log.d(TAG, "=== Line-based Detection ===")

            // 1. 선 검출
            val detectedLines = lineDetector.detectLines(bitmap, boardRegion)
            if (detectedLines == null) {
                Log.w(TAG, "Line detection failed")
                return null
            }

            Log.d(TAG, "Detected lines: V=${detectedLines.verticalLines.size}, " +
                    "H=${detectedLines.horizontalLines.size}, " +
                    "confidence=${detectedLines.confidence}")

            // 신뢰도가 너무 낮으면 폴백
            if (detectedLines.confidence < 0.5f) {
                Log.w(TAG, "Line detection confidence too low: ${detectedLines.confidence}")
                return null
            }

            // 2. 교차점 계산
            val grid = intersectionCalculator.calculateIntersections(detectedLines)
            Log.d(TAG, "Calculated ${grid.intersections.size} intersections")

            // 3. 각 교차점에서 기물 검출
            val detectedPieces = pieceDetector.detectAllPieces(bitmap, grid)

            if (detectedPieces.isEmpty()) {
                Log.w(TAG, "No pieces detected with line-based method")
                return null
            }

            // 4. 결과 변환
            val piecesWithPosition = detectedPieces.map { detected ->
                DetectedPieceWithPosition(
                    position = detected.position,
                    piece = detected.toPiece(),
                    confidence = detected.confidence
                )
            }

            val choCount = piecesWithPosition.count { it.piece.player == Player.CHO }
            val hanCount = piecesWithPosition.count { it.piece.player == Player.HAN }

            Log.i(TAG, "✅ Line-based detection complete: ${piecesWithPosition.size} pieces " +
                    "(CHO=$choCount, HAN=$hanCount)")

            return ExtractedText(
                detectedPieces = piecesWithPosition,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                detectedLines = detectedLines,
                intersectionGrid = grid
            )
        } catch (e: Exception) {
            Log.e(TAG, "Line-based detection error", e)
            return null
        }
    }

    /**
     * 균등 분할 방식 (폴백)
     */
    private fun detectWithGridMethod(
        bitmap: Bitmap,
        boardRegion: android.graphics.Rect
    ): ExtractedText {
        Log.d(TAG, "=== Grid-based Detection (Fallback) ===")

        // Split into 9×10 grid
        val cells = boardDetector.splitIntoCells(bitmap, boardRegion)
        Log.d(TAG, "Split into 10 rows × 9 cols = 90 cells")

        val detectedPieces = mutableListOf<DetectedPieceWithPosition>()
        var checkedCells = 0

        for (row in 0 until 10) {
            for (col in 0 until 9) {
                checkedCells++
                val cellBitmap = cells[row][col]
                val position = Position(col, row)

                val dominantColor = detectDominantColor(cellBitmap)

                if (dominantColor != DominantColor.EMPTY) {
                    val player = when (dominantColor) {
                        DominantColor.RED -> Player.CHO
                        DominantColor.BLUE -> Player.HAN
                        DominantColor.EMPTY -> continue
                    }

                    val piece = Piece.Chariot(player, position)

                    detectedPieces.add(
                        DetectedPieceWithPosition(
                            position = position,
                            piece = piece,
                            confidence = 0.7f
                        )
                    )

                    Log.d(TAG, "[$row,$col] ✅ Detected: 차 $player (color-based)")
                }
            }
        }

        Log.d(TAG, "=== Grid Detection Complete ===")
        Log.d(TAG, "Pieces detected: ${detectedPieces.size}/$checkedCells cells")

        val choCount = detectedPieces.count { it.piece.player == Player.CHO }
        val hanCount = detectedPieces.count { it.piece.player == Player.HAN }
        Log.d(TAG, "Distribution: CHO=$choCount, HAN=$hanCount")

        return ExtractedText(
            detectedPieces = detectedPieces,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            detectedLines = null,
            intersectionGrid = null
        )
    }

    /**
     * 지배적인 색상 감지
     */
    private fun detectDominantColor(bitmap: Bitmap): DominantColor {
        val width = bitmap.width
        val height = bitmap.height

        if (width == 0 || height == 0) return DominantColor.EMPTY

        var redCount = 0
        var blueCount = 0
        var totalColoredPixels = 0

        // 전체 픽셀 샘플링 (성능을 위해 step 사용)
        val step = maxOf(1, width / 16)

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]

                // 채도와 명도가 충분한 픽셀만 카운트
                if (saturation > 0.2f && value > 0.3f) {
                    when {
                        // 빨간색: 0-30도 또는 330-360도
                        (hue in 0f..30f || hue in 330f..360f) -> {
                            redCount++
                            totalColoredPixels++
                        }
                        // 파란색: 200-260도
                        hue in 200f..260f -> {
                            blueCount++
                            totalColoredPixels++
                        }
                    }
                }
            }
        }

        // 최소 10개 이상의 색상 픽셀이 있어야 함
        if (totalColoredPixels < 10) return DominantColor.EMPTY

        val redRatio = redCount.toFloat() / totalColoredPixels
        val blueRatio = blueCount.toFloat() / totalColoredPixels

        return when {
            redRatio > 0.15f && redRatio > blueRatio -> DominantColor.RED
            blueRatio > 0.15f && blueRatio > redRatio -> DominantColor.BLUE
            else -> DominantColor.EMPTY
        }
    }

    /**
     * Loads an image from URI and downsamples it to 2048px width.
     */
    private fun loadAndDownsampleImage(imageUri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return null

            // First, get image dimensions without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            Log.d(TAG, "Original image size: ${options.outWidth}x${options.outHeight}")

            // Calculate sample size to downsample to ~2048px width
            val targetWidth = 2048
            val sampleSize = if (options.outWidth > targetWidth) {
                options.outWidth / targetWidth
            } else {
                1
            }

            // Load the downsampled bitmap
            val inputStream2 = context.contentResolver.openInputStream(imageUri)
                ?: return null

            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val bitmap = BitmapFactory.decodeStream(inputStream2, null, loadOptions)
            inputStream2.close()

            Log.d(TAG, "Loaded bitmap size: ${bitmap?.width}x${bitmap?.height}")

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image", e)
            null
        }
    }

    /**
     * Data class containing extracted pieces and image dimensions.
     */
    data class ExtractedText(
        val detectedPieces: List<DetectedPieceWithPosition>,
        val imageWidth: Int,
        val imageHeight: Int,
        val detectedLines: LineDetector.DetectedLines? = null,
        val intersectionGrid: IntersectionCalculator.IntersectionGrid? = null
    ) {
        /**
         * 선 검출 기반 인식이 사용되었는지 여부
         */
        val usedLineDetection: Boolean
            get() = detectedLines != null && intersectionGrid != null
    }

    /**
     * 검출된 기물과 위치 정보
     */
    data class DetectedPieceWithPosition(
        val position: Position,
        val piece: Piece,
        val confidence: Float
    )

    /**
     * 색상 정보
     */
    enum class DominantColor {
        RED,    // 빨강 (초)
        BLUE,   // 파랑 (한)
        EMPTY   // 빈 칸
    }
}
