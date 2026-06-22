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
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 교차점 기반 기물 검출기
 *
 * 각 교차점 주변 영역에서 기물의 존재 여부와 진영(초/한)을 판별합니다.
 * 카카오장기의 기물 색상에 최적화되어 있습니다.
 */
@Singleton
class PieceDetector @Inject constructor() {

    companion object {
        private const val TAG = "PieceDetector"

        // 기물 검출을 위한 최소 색상 픽셀 비율
        private const val MIN_PIECE_RATIO = 0.05f

        // 기물 배경색(흰색/크림색) 검출 임계값
        private const val PIECE_BG_MIN_VALUE = 0.85f  // 밝기
        private const val PIECE_BG_MAX_SATURATION = 0.15f  // 낮은 채도
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
        GENERAL, GUARD, HORSE, ELEPHANT, CHARIOT, CANNON, SOLDIER, UNKNOWN
    }

    private enum class DominantColor {
        RED, BLUE, GREEN, EMPTY
    }

    /**
     * 모든 교차점에서 기물을 검출합니다.
     */
    fun detectAllPieces(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
    ): List<DetectedPiece> {
        Log.d(TAG, "=== Piece Detection Started ===")

        val detectedPieces = mutableListOf<DetectedPiece>()

        for (intersection in grid.intersections) {
            val piece = detectPieceAt(bitmap, intersection)
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
    fun detectPieceAt(
        bitmap: Bitmap,
        intersection: IntersectionCalculator.BoardIntersection
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

            // 1. 기물 존재 확인 (흰색/크림색 배경 검출)
            val hasPiece = detectPiecePresence(bitmap, centerX, centerY, radius)
            if (!hasPiece) {
                return null
            }

            // 2. 기물 색상 분석 (테두리 색상으로 진영 판별)
            val colorAnalysis = analyzeKakaoPieceColor(bitmap, centerX, centerY, radius)

            if (colorAnalysis.first == DominantColor.EMPTY) {
                return null
            }

            // 3. 진영 결정
            val player = when (colorAnalysis.first) {
                DominantColor.RED -> Player.CHO
                DominantColor.BLUE, DominantColor.GREEN -> Player.HAN
                DominantColor.EMPTY -> return null
            }

            return DetectedPiece(
                position = intersection.position,
                player = player,
                pieceType = PieceType.UNKNOWN,
                confidence = colorAnalysis.second,
                pixelX = intersection.pixelX,
                pixelY = intersection.pixelY
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect piece at ${intersection.position}", e)
            return null
        }
    }

    /**
     * 기물 존재 여부 확인 (흰색/크림색 배경 검출)
     */
    private fun detectPiecePresence(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Boolean {
        val innerRadius = (radius * 0.5f).toInt()
        var whitishCount = 0
        var totalCount = 0

        val step = max(1, innerRadius / 5)

        for (dy in -innerRadius..innerRadius step step) {
            for (dx in -innerRadius..innerRadius step step) {
                if (dx * dx + dy * dy > innerRadius * innerRadius) continue

                val px = centerX + dx
                val py = centerY + dy

                if (px < 0 || px >= bitmap.width || py < 0 || py >= bitmap.height) continue

                totalCount++
                val pixel = bitmap.getPixel(px, py)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                // 흰색/크림색 (높은 밝기, 낮은 채도)
                if (hsv[2] > PIECE_BG_MIN_VALUE && hsv[1] < PIECE_BG_MAX_SATURATION) {
                    whitishCount++
                }
            }
        }

        val ratio = if (totalCount > 0) whitishCount.toFloat() / totalCount else 0f
        return ratio > 0.3f  // 30% 이상이 흰색이면 기물 있음
    }

    /**
     * 카카오장기 기물의 색상 분석
     * 기물 테두리의 색상으로 진영을 판별합니다.
     */
    private fun analyzeKakaoPieceColor(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Pair<DominantColor, Float> {
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
            return Pair(DominantColor.EMPTY, 0f)
        }

        val redRatio = redCount.toFloat() / totalColored
        val blueRatio = blueCount.toFloat() / totalColored
        val greenRatio = greenCount.toFloat() / totalColored

        // 가장 많은 색상 선택
        val maxRatio = maxOf(redRatio, blueRatio, greenRatio)

        if (maxRatio < MIN_PIECE_RATIO) {
            return Pair(DominantColor.EMPTY, 0f)
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

        return Pair(color, confidence)
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
