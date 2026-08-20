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
 * 진영별 기물 최대 개수(왕1·사2·마2·상2·차2·포2·졸5)를 넘는 검출 결과를 정리합니다.
 * 기물은 잡히기만 하고 늘어나지 않으므로, 같은 진영·종류가 최대치를 넘으면 오검출이
 * 섞여 있다는 뜻입니다 - 신뢰도가 가장 낮은 초과분부터 제거합니다.
 */
internal fun sanitizePieceCounts(
    detected: List<BoardRecognitionService.DetectedPieceWithPosition>
): List<BoardRecognitionService.DetectedPieceWithPosition> {
    val maxCounts = mapOf(
        Piece.General::class to 1,
        Piece.Guard::class to 2,
        Piece.Horse::class to 2,
        Piece.Elephant::class to 2,
        Piece.Chariot::class to 2,
        Piece.Cannon::class to 2,
        Piece.Soldier::class to 5
    )
    return detected
        .groupBy { it.piece.player to it.piece::class }
        .flatMap { (key, group) ->
            val max = maxCounts[key.second] ?: group.size
            if (group.size <= max) group else group.sortedByDescending { it.confidence }.take(max)
        }
}

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

        // 앱에 번들된 0수(초기 배치) 기준 이미지 - 템플릿 매칭용 템플릿을 여기서 자동 추출합니다.
        private const val REFERENCE_TEMPLATE_ASSET = "reference_board_initial.jpg"
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
        val bitmap = loadAndDownsampleImage(imageUri)
            ?: return Result.failure(Exception("이미지를 불러올 수 없습니다"))
        return extractFromBitmap(bitmap)
    }

    /**
     * [extractText] 와 같은 인식을 이미 메모리에 있는 [Bitmap] 에 대해 돌립니다.
     *
     * 동영상 불러오기가 이 진입점을 씁니다 - 동영상 프레임은 `Uri` 로 감쌀 이유가 없어
     * `Bitmap` 을 바로 넘깁니다. `Uri` 를 다루는 부분(파일 읽기·다운샘플)은
     * [loadAndDownsampleImage] 뿐이고 그 아래는 원래도 전부 `Bitmap` 기준이었습니다.
     */
    suspend fun extractFromBitmap(bitmap: Bitmap): Result<ExtractedText> {
        return try {
            Log.d(TAG, "=== Board Recognition Started ===")

            // 0. 기물 종류 템플릿이 없으면 번들된 0수 기준 이미지에서 자동 추출
            //    (디버그 화면에서 수동으로 추출할 필요 없이 실제 앱 흐름에서 바로 동작하도록)
            if (!pieceDetector.isTemplateInitialized()) {
                initializeTemplatesFromBundledAsset()
            }

            Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")

            // 2. Detect board region
            val boardRegion = boardDetector.detectBoard(bitmap)
                ?: boardDetector.estimateBoardRegion(bitmap)
            Log.i(TAG, "Board region: ${boardRegion.width()}x${boardRegion.height()}")

            // 3. 선 검출 기반 인식 시도
            if (USE_LINE_DETECTION) {
                val lineResult = tryLineBasedDetection(bitmap, boardRegion)
                if (lineResult != null) {
                    return Result.success(lineResult.copy(detectedPieces = sanitizePieceCounts(lineResult.detectedPieces)))
                }
                Log.w(TAG, "Line-based detection failed, falling back to grid method")
            }

            // 4. 폴백: 기존 균등 분할 방식
            val fallbackResult = detectWithGridMethod(bitmap, boardRegion)
                .let { it.copy(detectedPieces = sanitizePieceCounts(it.detectedPieces)) }

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
     * 앱에 번들된 0수 기준 이미지에서 기물 템플릿을 자동 추출합니다.
     *
     * 디버그 화면에서 사용자가 수동으로 "템플릿 추출" 버튼을 눌러야만 템플릿이
     * 생겼던 문제를 해결하기 위해, 실제 인식 흐름(extractText) 최초 호출 시
     * 앱에 미리 넣어둔 기준 이미지로 자동 추출을 시도합니다.
     * 실패해도 조용히 넘어가고(템플릿 매칭은 건너뛰고 OCR로만 진행) 전체 흐름은 계속됩니다.
     */
    private suspend fun initializeTemplatesFromBundledAsset() {
        try {
            Log.d(TAG, "=== Auto Template Initialization from bundled asset ===")

            val referenceBitmap = context.assets.open(REFERENCE_TEMPLATE_ASSET).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: run {
                Log.w(TAG, "Failed to decode bundled reference image: $REFERENCE_TEMPLATE_ASSET")
                return
            }

            val boardRegion = boardDetector.detectBoard(referenceBitmap)
                ?: boardDetector.estimateBoardRegion(referenceBitmap)

            val detectedLines = lineDetector.detectLines(referenceBitmap, boardRegion)
            if (detectedLines == null) {
                Log.w(TAG, "Line detection failed on bundled reference image")
                return
            }

            val grid = intersectionCalculator.calculateIntersections(detectedLines)
            val count = pieceDetector.initializeTemplates(referenceBitmap, grid)

            if (pieceDetector.isTemplateInitialized()) {
                Log.i(TAG, "✅ Auto-initialized $count piece templates from bundled reference image")
            } else {
                Log.w(TAG, "⚠️ Template auto-initialization incomplete: $count types (14 needed)")
            }
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "Bundled reference image not found in assets: $REFERENCE_TEMPLATE_ASSET " +
                    "(place a 0-move screenshot at app/src/main/assets/$REFERENCE_TEMPLATE_ASSET)")
        } catch (e: Exception) {
            Log.e(TAG, "Auto template initialization failed", e)
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
