package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 교차점 기반 기물 검출기
 *
 * 각 교차점 주변 영역에서 기물의 존재 여부와 진영(초/한)을 판별합니다.
 * 템플릿 매칭을 사용하여 기물 종류를 인식합니다.
 * 카카오장기의 기물 색상에 최적화되어 있습니다.
 */
@Singleton
class PieceDetector @Inject constructor(
    private val templateExtractor: TemplateExtractor,
    private val templateMatcher: TemplateMatcher,
    private val ocrRecognizer: PieceOcrRecognizer,
    private val pieceClassifier: PieceClassifier
) {

    companion object {
        private const val TAG = "PieceDetector"

        // 기물 검출을 위한 최소 색상 픽셀 비율
        private const val MIN_PIECE_RATIO = 0.05f

        // 보드 배경색과의 RGB 거리 임계값 (이 이상 차이나면 "보드색이 아님" = 기물로 추정)
        private const val BOARD_COLOR_DISTANCE_THRESHOLD = 45.0

        // 교차점 주변에서 보드색이 아닌 픽셀의 비율이 이 값을 넘으면 기물이 있다고 판단
        private const val PIECE_FOREIGN_RATIO_THRESHOLD = 0.35f

        // TEMP DEBUG: 크롭된 이미지를 눈으로 확인하기 위한 덤프 경로
        private const val DEBUG_DUMP_DIR = "/sdcard/Android/data/com.example.janggi2/files/debug_crops"
    }

    /**
     * TEMP DEBUG: 크롭 이미지를 파일로 저장 (실패해도 무시)
     */
    private fun dumpDebugCrop(name: String, mat: Mat) {
        try {
            org.opencv.imgcodecs.Imgcodecs.imwrite("$DEBUG_DUMP_DIR/$name.png", mat)
        } catch (e: Exception) {
            Log.w(TAG, "debug dump failed: $name", e)
        }
    }

    // 템플릿 저장소
    private var pieceTemplates: PieceTemplates? = null

    /**
     * 템플릿 초기화 여부 확인
     */
    fun isTemplateInitialized(): Boolean = pieceTemplates?.isInitialized() == true

    /**
     * 0수 이미지에서 템플릿 초기화
     *
     * @param bitmap 0수 스크린샷
     * @param grid 교차점 그리드 정보
     * @return 추출된 템플릿 개수
     */
    suspend fun initializeTemplates(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
    ): Int {
        Log.i(TAG, "=== Initializing Templates ===")

        // 기존 템플릿 정리
        pieceTemplates?.clear()

        // 새 템플릿 추출 (보드 배경색 기준으로 기물 모양만 타이트하게 크롭)
        val boardColor = estimateBoardBackgroundColor(bitmap, grid)
        val templates = templateExtractor.extractTemplates(bitmap, grid, boardColor)
        pieceTemplates = templates

        val count = templates.size()
        Log.i(TAG, "Templates initialized: $count types")
        Log.i(TAG, templates.getSummary())

        return count
    }

    /**
     * 템플릿 초기화 (외부에서 추출된 템플릿 사용)
     */
    fun setTemplates(templates: PieceTemplates) {
        pieceTemplates?.clear()
        pieceTemplates = templates
        Log.i(TAG, "Templates set externally: ${templates.getSummary()}")
    }

    /**
     * 템플릿 상태 요약
     */
    fun getTemplateStatus(): String {
        return pieceTemplates?.getSummary() ?: "템플릿 미초기화"
    }

    /**
     * 검출된 기물 정보
     */
    data class DetectedPiece(
        val position: Position,
        val player: Player,
        val pieceType: PieceType,
        val confidence: Float,
        val pixelX: Float,
        val pixelY: Float
    ) {
        fun toPiece(): Piece {
            return when (pieceType) {
                PieceType.CHARIOT -> Piece.Chariot(player, position)
                PieceType.CANNON -> Piece.Cannon(player, position)
                PieceType.HORSE -> Piece.Horse(player, position)
                PieceType.ELEPHANT -> Piece.Elephant(player, position)
                PieceType.GUARD -> Piece.Guard(player, position)
                PieceType.GENERAL -> Piece.General(player, position)
                PieceType.SOLDIER -> Piece.Soldier(player, position)
                PieceType.UNKNOWN -> Piece.Chariot(player, position)
            }
        }
    }

    enum class PieceType {
        GENERAL, GUARD, HORSE, ELEPHANT, CHARIOT, CANNON, SOLDIER, UNKNOWN;

        fun toKoreanLabel(): String = when (this) {
            GENERAL -> "왕"
            GUARD -> "사"
            HORSE -> "마"
            ELEPHANT -> "상"
            CHARIOT -> "차"
            CANNON -> "포"
            SOLDIER -> "졸"
            UNKNOWN -> "미확인"
        }
    }

    enum class DominantColor {
        RED, BLUE, GREEN, EMPTY
    }

    /**
     * 교차점 분석 상세 정보
     */
    data class PieceAnalysisDetails(
        val intersectionId: Int,              // 0-89 고유 번호
        val hasPieceBackground: Boolean,      // 기물 유무 (보드 배경색 대비 이질적 픽셀 비율로 판단)
        val backgroundRatio: Float,           // 보드 배경색과 일치하는 픽셀 비율 (0-1)
        val dominantColor: DominantColor,     // 지배적 색상
        val colorConfidence: Float,           // 색상 판단 신뢰도
        val redPixelRatio: Float,             // 빨강 픽셀 비율
        val bluePixelRatio: Float,            // 파랑 픽셀 비율
        val greenPixelRatio: Float,           // 녹색 픽셀 비율
        val totalColoredPixels: Int,          // 분석된 총 색상 픽셀 수
        val ocrText: String,                  // OCR이 읽은 문자
        val ocrConfidence: Float,             // OCR 신뢰도
        val ocrPieceType: PieceType?,         // OCR로 판별된 기물 종류
        val templatePieceType: PieceType?,    // 템플릿 매칭으로 판별된 기물 종류
        val templateMatchScore: Double,       // 템플릿 매칭 최고 점수
        val analysisReason: String            // 분석 이유 문자열
    ) {
        companion object {
            fun create(
                intersectionId: Int,
                hasPieceBackground: Boolean,
                backgroundRatio: Float,
                dominantColor: DominantColor,
                colorConfidence: Float,
                redCount: Int,
                blueCount: Int,
                greenCount: Int,
                totalColored: Int,
                ocrText: String = "",
                ocrConfidence: Float = 0f,
                ocrPieceType: PieceType? = null,
                templatePieceType: PieceType? = null,
                templateMatchScore: Double = 0.0,
                templatesAvailable: Boolean = false
            ): PieceAnalysisDetails {
                val redRatio = if (totalColored > 0) redCount.toFloat() / totalColored else 0f
                val blueRatio = if (totalColored > 0) blueCount.toFloat() / totalColored else 0f
                val greenRatio = if (totalColored > 0) greenCount.toFloat() / totalColored else 0f

                val reason = buildString {
                    append("[#$intersectionId] ")
                    if (!hasPieceBackground) {
                        append("기물 없음: 보드 배경색 일치 ${(backgroundRatio * 100).toInt()}% (65% 이상)")
                    } else {
                        append("기물 감지: 보드 배경색 일치 ${(backgroundRatio * 100).toInt()}% (65% 미만)")
                        if (totalColored >= 5) {
                            append("\n색상: ${dominantColor.name} (${(colorConfidence * 100).toInt()}%)")
                            append("\n- 빨강: ${(redRatio * 100).toInt()}%")
                            append("\n- 파랑: ${(blueRatio * 100).toInt()}%")
                            append("\n- 녹색: ${(greenRatio * 100).toInt()}%")
                            append("\n- 총 픽셀: $totalColored")
                        } else {
                            append("\n색상 픽셀 부족: $totalColored (최소 5 필요)")
                        }
                        append("\n템플릿 매칭: ")
                        append(
                            if (!templatesAvailable) "(템플릿 미추출)"
                            else if (templatePieceType != null) "${templatePieceType.toKoreanLabel()} (점수: ${String.format("%.2f", templateMatchScore)})"
                            else "매칭 실패 (점수: ${String.format("%.2f", templateMatchScore)})"
                        )
                        append("\nOCR 인식: ${if (ocrText.isNotBlank()) "'$ocrText'" else "(문자 없음)"} " +
                                "(${(ocrConfidence * 100).toInt()}%) -> ${ocrPieceType?.toKoreanLabel() ?: "판별 실패"}")
                    }
                }

                return PieceAnalysisDetails(
                    intersectionId = intersectionId,
                    hasPieceBackground = hasPieceBackground,
                    backgroundRatio = backgroundRatio,
                    dominantColor = dominantColor,
                    colorConfidence = colorConfidence,
                    redPixelRatio = redRatio,
                    bluePixelRatio = blueRatio,
                    greenPixelRatio = greenRatio,
                    totalColoredPixels = totalColored,
                    ocrText = ocrText,
                    ocrConfidence = ocrConfidence,
                    ocrPieceType = ocrPieceType,
                    templatePieceType = templatePieceType,
                    templateMatchScore = templateMatchScore,
                    analysisReason = reason
                )
            }
        }
    }

    /**
     * 모든 교차점에서 기물을 검출합니다.
     */
    /**
     * 보드 배경색 추정
     *
     * 서로 인접한 4개 교차점의 정중앙(칸의 중심)은 기물이 놓이는 위치(교차점)와
     * 겹치지 않으므로 항상 순수한 보드 배경입니다. 이 지점들을 샘플링해 실제
     * 이미지의 보드색을 직접 측정합니다. 특정 기물 디자인(흰 배경 등)을 가정하는
     * 대신, "교차점 주변 색이 이 보드색에서 얼마나 벗어나는지"로 기물 유무를
     * 판단하기 위한 기준값입니다.
     */
    fun estimateBoardBackgroundColor(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
    ): IntArray {
        val byPosition = grid.intersections.associateBy { it.position }
        val maxCol = grid.intersections.maxOf { it.position.col }
        val maxRow = grid.intersections.maxOf { it.position.row }

        val reds = mutableListOf<Int>()
        val greens = mutableListOf<Int>()
        val blues = mutableListOf<Int>()

        for (row in 0 until maxRow) {
            for (col in 0 until maxCol) {
                val a = byPosition[Position(col, row)] ?: continue
                val b = byPosition[Position(col + 1, row)] ?: continue
                val c = byPosition[Position(col, row + 1)] ?: continue
                val d = byPosition[Position(col + 1, row + 1)] ?: continue

                val cx = ((a.pixelX + b.pixelX + c.pixelX + d.pixelX) / 4f).toInt()
                val cy = ((a.pixelY + b.pixelY + c.pixelY + d.pixelY) / 4f).toInt()

                if (cx < 0 || cx >= bitmap.width || cy < 0 || cy >= bitmap.height) continue

                val pixel = bitmap.getPixel(cx, cy)
                reds.add(Color.red(pixel))
                greens.add(Color.green(pixel))
                blues.add(Color.blue(pixel))
            }
        }

        val boardColor = intArrayOf(median(reds), median(greens), median(blues))
        Log.d(TAG, "Estimated board background color: RGB(${boardColor[0]}, ${boardColor[1]}, ${boardColor[2]}) " +
                "from ${reds.size} cell-center samples")
        return boardColor
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    suspend fun detectAllPieces(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
    ): List<DetectedPiece> {
        Log.d(TAG, "=== Piece Detection Started ===")

        val boardColor = estimateBoardBackgroundColor(bitmap, grid)
        val detectedPieces = mutableListOf<DetectedPiece>()

        for (intersection in grid.intersections) {
            val piece = detectPieceAt(bitmap, intersection, boardColor)
            if (piece != null) {
                detectedPieces.add(piece)
                Log.d(TAG, "[${intersection.position.row},${intersection.position.col}] " +
                        "${piece.player} detected (confidence: ${String.format("%.2f", piece.confidence)})")
            }
        }

        val choCount = detectedPieces.count { it.player == Player.CHO }
        val hanCount = detectedPieces.count { it.player == Player.HAN }

        Log.i(TAG, "✅ Detected ${detectedPieces.size} pieces (CHO: $choCount, HAN: $hanCount)")

        return detectedPieces
    }

    /**
     * 특정 교차점에서 기물을 검출합니다.
     */
    suspend fun detectPieceAt(
        bitmap: Bitmap,
        intersection: IntersectionCalculator.BoardIntersection,
        boardColor: IntArray
    ): DetectedPiece? {
        try {
            val centerX = intersection.pixelX.toInt()
            val centerY = intersection.pixelY.toInt()
            val radius = intersection.cellRadius.toInt()

            // 경계 체크
            if (centerX - radius < 0 || centerX + radius >= bitmap.width ||
                centerY - radius < 0 || centerY + radius >= bitmap.height) {
                return null
            }

            // 1. 기물 존재 확인 (보드 배경색 대비 이질적인 픽셀 비율로 판단)
            val presenceResult = detectPiecePresence(bitmap, centerX, centerY, radius, boardColor)
            if (!presenceResult.hasPiece) {
                return null
            }

            // 2. 기물 색상 분석 (테두리 색상으로 진영 판별)
            val colorAnalysis = analyzeKakaoPieceColor(bitmap, centerX, centerY, radius)

            if (colorAnalysis.dominantColor == DominantColor.EMPTY) {
                return null
            }

            // 3. 진영 결정
            val player = when (colorAnalysis.dominantColor) {
                DominantColor.BLUE, DominantColor.GREEN -> Player.CHO
                DominantColor.RED -> Player.HAN
                DominantColor.EMPTY -> return null
            }

            // 4. CNN 분류기로 기물 종류 인식 (학습된 진영별 7-클래스 모델, 우선순위 1 —
            //    지금까지 시도한 방법 중 실측 정확도가 가장 높음)
            var pieceType = PieceType.UNKNOWN
            var matchConfidence = 0f

            val classifierResult = run {
                val croppedMat = cropPieceRegion(bitmap, centerX, centerY, radius, boardColor)
                    ?: return@run null
                try {
                    pieceClassifier.classify(croppedMat, player)
                } finally {
                    croppedMat.release()
                }
            }
            if (classifierResult != null) {
                pieceType = classifierResult.pieceType
                matchConfidence = classifierResult.confidence
                Log.v(TAG, "Classifier match at ${intersection.position}: $pieceType " +
                        "(confidence: ${String.format("%.2f", classifierResult.confidence)})")
            }

            // 5. 분류기 실패 시(모델 로드 실패 등) OCR로 폴백
            if (pieceType == PieceType.UNKNOWN) {
                val ocrResult = ocrRecognizer.recognizePiece(bitmap, centerX, centerY, radius)
                if (ocrResult.pieceType != null) {
                    pieceType = ocrResult.pieceType
                    matchConfidence = ocrResult.confidence
                    Log.v(TAG, "OCR match at ${intersection.position}: $pieceType " +
                            "('${ocrResult.recognizedText}', confidence: ${String.format("%.2f", ocrResult.confidence)})")
                }
            }

            // 6. 그래도 실패 시 템플릿 매칭으로 폴백 (0수 템플릿이 추출된 경우에만)
            if (pieceType == PieceType.UNKNOWN) {
                val templates = pieceTemplates
                if (templates != null && templates.isInitialized()) {
                    val croppedMat = cropPieceRegion(bitmap, centerX, centerY, radius, boardColor)
                    if (croppedMat != null) {
                        try {
                            // TEMP DEBUG: 실제 매칭에 쓰인 입력 크롭 확인용
                            dumpDebugCrop("match_${player}_r${intersection.position.row}c${intersection.position.col}", croppedMat)

                            val matchResult = templateMatcher.matchPiece(croppedMat, templates, player)
                            if (matchResult != null) {
                                pieceType = matchResult.pieceType
                                matchConfidence = matchResult.confidence
                                Log.v(TAG, "Template match at ${intersection.position}: " +
                                        "$pieceType (score: ${String.format("%.3f", matchResult.matchScore)})")
                            }
                        } finally {
                            croppedMat.release()
                        }
                    }
                }
            }

            // 최종 신뢰도: 색상 분석과 템플릿 매칭의 조합
            val finalConfidence = if (matchConfidence > 0f) {
                (colorAnalysis.confidence + matchConfidence) / 2f
            } else {
                colorAnalysis.confidence
            }

            return DetectedPiece(
                position = intersection.position,
                player = player,
                pieceType = pieceType,
                confidence = finalConfidence,
                pixelX = intersection.pixelX,
                pixelY = intersection.pixelY
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect piece at ${intersection.position}", e)
            return null
        }
    }

    /**
     * 기물 영역을 크롭하여 Mat으로 반환
     *
     * 교차점 좌표를 그대로 신뢰하지 않고, 보드 배경색과 다른(=기물로 추정되는)
     * 픽셀들의 무게중심/경계에 맞춰 타이트하게 크롭합니다. 템플릿 추출
     * ([TemplateExtractor])과 동일한 기준이라 매칭 시 템플릿-입력 간 구도가
     * 일관됩니다.
     */
    private fun cropPieceRegion(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int,
        boardColor: IntArray
    ): Mat? {
        val nominalRadius = (radius * 0.9f).toInt()
        val searchRadius = (radius * 1.3f).toInt()

        val bounds = refinePieceBounds(bitmap, centerX, centerY, searchRadius, boardColor)
        val refinedX = bounds?.centerX ?: centerX
        val refinedY = bounds?.centerY ?: centerY
        val cropRadius = ((bounds?.radius ?: nominalRadius) * 1.15f).toInt()
            .coerceIn(nominalRadius / 2, searchRadius)

        val left = max(0, refinedX - cropRadius)
        val top = max(0, refinedY - cropRadius)
        val right = min(bitmap.width, refinedX + cropRadius)
        val bottom = min(bitmap.height, refinedY + cropRadius)

        if (right <= left || bottom <= top) {
            return null
        }

        // 크롭된 비트맵 생성
        val width = right - left
        val height = bottom - top
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)

        // Mat으로 변환
        val mat = Mat()
        Utils.bitmapToMat(croppedBitmap, mat)
        croppedBitmap.recycle()

        return mat
    }

    /**
     * 기물 경계 추정 결과 (중심 좌표 + 반경)
     */
    private data class PieceBounds(val centerX: Int, val centerY: Int, val radius: Int)

    /**
     * 탐색 영역 내에서 보드 배경색과 다른 픽셀들의 무게중심/경계를 계산합니다.
     */
    private fun refinePieceBounds(
        bitmap: Bitmap,
        nominalX: Int,
        nominalY: Int,
        searchRadius: Int,
        boardColor: IntArray
    ): PieceBounds? {
        val left = max(0, nominalX - searchRadius)
        val top = max(0, nominalY - searchRadius)
        val right = min(bitmap.width, nominalX + searchRadius)
        val bottom = min(bitmap.height, nominalY + searchRadius)

        val boardR = boardColor[0]
        val boardG = boardColor[1]
        val boardB = boardColor[2]

        var sumX = 0L
        var sumY = 0L
        var count = 0
        var minX = right
        var maxX = left
        var minY = bottom
        var maxY = top

        val step = max(1, searchRadius / 25)

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                val pixel = bitmap.getPixel(x, y)
                val dr = Color.red(pixel) - boardR
                val dg = Color.green(pixel) - boardG
                val db = Color.blue(pixel) - boardB
                val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())

                if (distance > BOARD_COLOR_DISTANCE_THRESHOLD) {
                    sumX += x
                    sumY += y
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (count < 20) return null

        val centerX = (sumX / count).toInt()
        val centerY = (sumY / count).toInt()
        val radius = max(maxX - minX, maxY - minY) / 2
        return PieceBounds(centerX, centerY, radius)
    }

    /**
     * 교차점의 상세 분석 결과 반환
     * @param bitmap 분석할 비트맵 이미지
     * @param intersection 분석할 교차점 정보
     * @param intersectionId 교차점 고유 번호 (0-89)
     * @param boardColor [estimateBoardBackgroundColor]로 미리 계산한 보드 배경색 (RGB)
     * @return 분석 상세 정보
     */
    suspend fun analyzeIntersectionDetails(
        bitmap: Bitmap,
        intersection: IntersectionCalculator.BoardIntersection,
        intersectionId: Int,
        boardColor: IntArray
    ): PieceAnalysisDetails {
        try {
            val centerX = intersection.pixelX.toInt()
            val centerY = intersection.pixelY.toInt()
            val radius = intersection.cellRadius.toInt()

            // 경계 체크
            if (centerX - radius < 0 || centerX + radius >= bitmap.width ||
                centerY - radius < 0 || centerY + radius >= bitmap.height) {
                return PieceAnalysisDetails.create(
                    intersectionId = intersectionId,
                    hasPieceBackground = false,
                    backgroundRatio = 0f,
                    dominantColor = DominantColor.EMPTY,
                    colorConfidence = 0f,
                    redCount = 0,
                    blueCount = 0,
                    greenCount = 0,
                    totalColored = 0
                )
            }

            // 1. 기물 존재 확인 (보드 배경색 대비 이질적인 픽셀 비율로 판단)
            val presenceResult = detectPiecePresence(bitmap, centerX, centerY, radius, boardColor)

            if (!presenceResult.hasPiece) {
                return PieceAnalysisDetails.create(
                    intersectionId = intersectionId,
                    hasPieceBackground = false,
                    backgroundRatio = presenceResult.backgroundRatio,
                    dominantColor = DominantColor.EMPTY,
                    colorConfidence = 0f,
                    redCount = 0,
                    blueCount = 0,
                    greenCount = 0,
                    totalColored = 0
                )
            }

            // 2. 기물 색상 분석 (테두리 색상으로 진영 판별)
            val colorAnalysis = analyzeKakaoPieceColor(bitmap, centerX, centerY, radius)

            val player = when (colorAnalysis.dominantColor) {
                DominantColor.BLUE, DominantColor.GREEN -> Player.CHO
                DominantColor.RED -> Player.HAN
                DominantColor.EMPTY -> null
            }

            // 3. 템플릿 매칭 분석 (디버깅용: 어떤 종류로, 어느 점수로 매칭됐는지 확인)
            var templatePieceType: PieceType? = null
            var templateMatchScore = 0.0
            val templatesAvailable = pieceTemplates?.isInitialized() == true
            if (templatesAvailable && player != null) {
                val croppedMat = cropPieceRegion(bitmap, centerX, centerY, radius, boardColor)
                if (croppedMat != null) {
                    try {
                        val matchResult = templateMatcher.matchPiece(croppedMat, pieceTemplates!!, player)
                        templatePieceType = matchResult?.pieceType
                        templateMatchScore = matchResult?.matchScore ?: 0.0
                    } finally {
                        croppedMat.release()
                    }
                }
            }

            // 4. OCR 분석 (디버깅용: 실제로 어떤 글자를 읽었는지 확인)
            val ocrResult = ocrRecognizer.recognizePiece(bitmap, centerX, centerY, radius)

            return PieceAnalysisDetails.create(
                intersectionId = intersectionId,
                hasPieceBackground = true,
                backgroundRatio = presenceResult.backgroundRatio,
                dominantColor = colorAnalysis.dominantColor,
                colorConfidence = colorAnalysis.confidence,
                redCount = colorAnalysis.redCount,
                blueCount = colorAnalysis.blueCount,
                greenCount = colorAnalysis.greenCount,
                totalColored = colorAnalysis.totalColored,
                ocrText = ocrResult.recognizedText,
                ocrConfidence = ocrResult.confidence,
                ocrPieceType = ocrResult.pieceType,
                templatePieceType = templatePieceType,
                templateMatchScore = templateMatchScore,
                templatesAvailable = templatesAvailable
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to analyze intersection details at ${intersection.position}", e)
            return PieceAnalysisDetails.create(
                intersectionId = intersectionId,
                hasPieceBackground = false,
                backgroundRatio = 0f,
                dominantColor = DominantColor.EMPTY,
                colorConfidence = 0f,
                redCount = 0,
                blueCount = 0,
                greenCount = 0,
                totalColored = 0
            )
        }
    }

    /**
     * 기물 존재 여부 확인 결과
     */
    data class PiecePresenceResult(
        val hasPiece: Boolean,
        val backgroundRatio: Float
    )

    /**
     * 기물 존재 여부 확인
     *
     * 기물 자체의 색을 가정하지 않습니다. 대신 이 교차점 주변 픽셀이 실측한
     * 보드 배경색([estimateBoardBackgroundColor])과 얼마나 다른지를 봅니다.
     * 보드는 항상 넓은 단색 배경이므로, 기물이 없으면 이 영역 대부분이
     * 보드색과 일치하고, 기물이 놓이면 그만큼 보드색 픽셀 비율이 줄어듭니다.
     */
    private fun detectPiecePresence(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int,
        boardColor: IntArray
    ): PiecePresenceResult {
        val innerRadius = (radius * 0.55f).toInt()
        var foreignCount = 0
        var totalCount = 0

        val step = max(1, innerRadius / 6)

        val boardR = boardColor[0]
        val boardG = boardColor[1]
        val boardB = boardColor[2]

        for (dy in -innerRadius..innerRadius step step) {
            for (dx in -innerRadius..innerRadius step step) {
                if (dx * dx + dy * dy > innerRadius * innerRadius) continue

                val px = centerX + dx
                val py = centerY + dy

                if (px < 0 || px >= bitmap.width || py < 0 || py >= bitmap.height) continue

                totalCount++
                val pixel = bitmap.getPixel(px, py)

                val dr = Color.red(pixel) - boardR
                val dg = Color.green(pixel) - boardG
                val db = Color.blue(pixel) - boardB
                val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())

                if (distance > BOARD_COLOR_DISTANCE_THRESHOLD) {
                    foreignCount++
                }
            }
        }

        val foreignRatio = if (totalCount > 0) foreignCount.toFloat() / totalCount else 0f
        return PiecePresenceResult(
            hasPiece = foreignRatio > PIECE_FOREIGN_RATIO_THRESHOLD,
            backgroundRatio = 1f - foreignRatio  // 보드 배경색과 일치하는 픽셀 비율
        )
    }

    /**
     * 색상 분석 결과
     */
    data class ColorAnalysisResult(
        val dominantColor: DominantColor,
        val confidence: Float,
        val redCount: Int,
        val blueCount: Int,
        val greenCount: Int,
        val totalColored: Int
    )

    /**
     * 카카오장기 기물의 색상 분석
     * 기물 테두리의 색상으로 진영을 판별합니다.
     */
    private fun analyzeKakaoPieceColor(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): ColorAnalysisResult {
        // 테두리 영역 분석 (기물 가장자리)
        val outerRadius = (radius * 0.9f).toInt()
        val innerRadius = (radius * 0.5f).toInt()

        var redCount = 0
        var blueCount = 0
        var greenCount = 0
        var totalColored = 0

        val step = max(1, radius / 8)

        for (dy in -outerRadius..outerRadius step step) {
            for (dx in -outerRadius..outerRadius step step) {
                val distSq = dx * dx + dy * dy

                // 테두리 영역만 (내부 원 제외, 외부 원 내부)
                if (distSq < innerRadius * innerRadius || distSq > outerRadius * outerRadius) {
                    continue
                }

                val px = centerX + dx
                val py = centerY + dy

                if (px < 0 || px >= bitmap.width || py < 0 || py >= bitmap.height) continue

                val pixel = bitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // 채도가 충분한 색상만 분석
                if (sat < 0.2f || value < 0.2f) continue

                totalColored++

                // 빨간색 검출 (카카오장기 빨간색)
                // Hue: 0-20 또는 340-360, 채도 높음
                val isRed = (hue in 0f..30f || hue in 330f..360f) && sat > 0.3f && value > 0.3f
                // 추가: RGB 기반 빨간색 검출
                val isRgbRed = r > 150 && r > g * 1.3 && r > b * 1.3

                // 파란색 검출 (카카오장기 파란색/청록색)
                // Hue: 180-250
                val isBlue = hue in 180f..260f && sat > 0.3f && value > 0.3f
                // 추가: RGB 기반 파란색 검출
                val isRgbBlue = b > 120 && b > r * 1.2 && (b > g || abs(b - g) < 50)

                // 녹색 검출 (한 진영이 녹색인 경우)
                val isGreen = hue in 80f..160f && sat > 0.3f && value > 0.3f
                val isRgbGreen = g > 120 && g > r * 1.2 && g > b * 1.1

                if (isRed || isRgbRed) {
                    redCount++
                } else if (isBlue || isRgbBlue) {
                    blueCount++
                } else if (isGreen || isRgbGreen) {
                    greenCount++
                }
            }
        }

        // 내부 영역도 분석 (글자 색상)
        analyzeInnerText(bitmap, centerX, centerY, innerRadius, step).let { (r, b, g) ->
            redCount += r
            blueCount += b
            greenCount += g
            totalColored += r + b + g
        }

        if (totalColored < 5) {
            return ColorAnalysisResult(
                dominantColor = DominantColor.EMPTY,
                confidence = 0f,
                redCount = redCount,
                blueCount = blueCount,
                greenCount = greenCount,
                totalColored = totalColored
            )
        }

        val redRatio = redCount.toFloat() / totalColored
        val blueRatio = blueCount.toFloat() / totalColored
        val greenRatio = greenCount.toFloat() / totalColored

        // 가장 많은 색상 선택
        val maxRatio = maxOf(redRatio, blueRatio, greenRatio)

        if (maxRatio < MIN_PIECE_RATIO) {
            return ColorAnalysisResult(
                dominantColor = DominantColor.EMPTY,
                confidence = 0f,
                redCount = redCount,
                blueCount = blueCount,
                greenCount = greenCount,
                totalColored = totalColored
            )
        }

        val color = when {
            redRatio >= blueRatio && redRatio >= greenRatio -> DominantColor.RED
            blueRatio >= redRatio && blueRatio >= greenRatio -> DominantColor.BLUE
            else -> DominantColor.GREEN
        }

        // 신뢰도 계산
        val confidence = (0.5f + maxRatio * 0.5f).coerceIn(0.5f, 1.0f)

        Log.v(TAG, "Color analysis: red=$redCount, blue=$blueCount, green=$greenCount, " +
                "total=$totalColored -> $color (${String.format("%.1f", confidence * 100)}%)")

        return ColorAnalysisResult(
            dominantColor = color,
            confidence = confidence,
            redCount = redCount,
            blueCount = blueCount,
            greenCount = greenCount,
            totalColored = totalColored
        )
    }

    /**
     * 기물 내부 텍스트 색상 분석
     */
    private fun analyzeInnerText(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        innerRadius: Int,
        step: Int
    ): Triple<Int, Int, Int> {
        var redCount = 0
        var blueCount = 0
        var greenCount = 0

        for (dy in -innerRadius..innerRadius step step) {
            for (dx in -innerRadius..innerRadius step step) {
                if (dx * dx + dy * dy > innerRadius * innerRadius) continue

                val px = centerX + dx
                val py = centerY + dy

                if (px < 0 || px >= bitmap.width || py < 0 || py >= bitmap.height) continue

                val pixel = bitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // 흰색 배경은 제외
                if (sat < 0.15f && value > 0.85f) continue

                // 채도가 높은 색상만 (글자)
                if (sat < 0.25f) continue

                val hue = hsv[0]

                when {
                    (hue in 0f..30f || hue in 330f..360f) && sat > 0.3f -> redCount++
                    hue in 180f..260f && sat > 0.3f -> blueCount++
                    hue in 80f..160f && sat > 0.3f -> greenCount++
                }
            }
        }

        return Triple(redCount, blueCount, greenCount)
    }

    /**
     * 디버그용: 검출 결과 시각화 이미지 생성
     */
    fun createDebugImage(
        bitmap: Bitmap,
        detectedPieces: List<DetectedPiece>,
        grid: IntersectionCalculator.IntersectionGrid
    ): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // 모든 교차점 그리기 (회색 점)
        for (intersection in grid.intersections) {
            Imgproc.circle(
                mat,
                Point(intersection.pixelX.toDouble(), intersection.pixelY.toDouble()),
                3,
                Scalar(128.0, 128.0, 128.0),
                -1
            )
        }

        // 검출된 기물 그리기
        for (piece in detectedPieces) {
            val color = if (piece.player == Player.CHO) {
                Scalar(255.0, 0.0, 0.0)  // Red
            } else {
                Scalar(0.0, 0.0, 255.0)  // Blue
            }

            val radius = grid.averageCellWidth * 0.35
            Imgproc.circle(
                mat,
                Point(piece.pixelX.toDouble(), piece.pixelY.toDouble()),
                radius.toInt(),
                color,
                3
            )

            Imgproc.circle(
                mat,
                Point(piece.pixelX.toDouble(), piece.pixelY.toDouble()),
                5,
                color,
                -1
            )

            val confidenceText = String.format("%.0f%%", piece.confidence * 100)
            Imgproc.putText(
                mat,
                confidenceText,
                Point(piece.pixelX.toDouble() - 15, piece.pixelY.toDouble() - radius - 5),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.4,
                color,
                1
            )
        }

        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, result)
        mat.release()

        return result
    }
}
