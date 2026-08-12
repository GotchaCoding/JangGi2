package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 0수 이미지에서 기물 템플릿을 추출하는 클래스
 *
 * 장기 초기 배치에서 모든 기물의 위치가 고정되어 있음을 활용하여,
 * 각 위치에서 기물 이미지를 크롭하고 템플릿으로 저장합니다.
 */
@Singleton
class TemplateExtractor @Inject constructor() {

    companion object {
        private const val TAG = "TemplateExtractor"

        // 보드 배경색과의 RGB 거리 임계값 (PieceDetector와 동일한 기준)
        private const val BOARD_COLOR_DISTANCE_THRESHOLD = 45.0

        // TEMP DEBUG: 크롭된 이미지를 눈으로 확인하기 위한 덤프 경로
        private const val DEBUG_DUMP_DIR = "/sdcard/Android/data/com.example.janggi2/files/debug_crops"
    }

    /**
     * TEMP DEBUG: 크롭/템플릿 이미지를 파일로 저장 (실패해도 무시)
     */
    private fun dumpDebugCrop(name: String, mat: Mat) {
        try {
            org.opencv.imgcodecs.Imgcodecs.imwrite("$DEBUG_DUMP_DIR/$name.png", mat)
        } catch (e: Exception) {
            Log.w(TAG, "debug dump failed: $name", e)
        }
    }

    /**
     * 0수 초기 배치의 기물 위치
     *
     * 漢 (HAN, 파랑/녹색) - Row 0~3 (위쪽)
     * 楚 (CHO, 빨강) - Row 6~9 (아래쪽)
     *
     * Note: 馬/象 위치는 마상차림에 따라 다를 수 있음
     * 기본값은 외상마차림: 車-象-馬-士-王-士-馬-象-車
     */
    private val initialPositions: Map<Position, Pair<PieceDetector.PieceType, Player>> = buildMap {
        // === 漢 (HAN) - 파랑/녹색, 위쪽 ===
        // Row 0: 車 象 馬 士 · 士 馬 象 車
        put(Position(0, 0), PieceDetector.PieceType.CHARIOT to Player.HAN)
        put(Position(1, 0), PieceDetector.PieceType.ELEPHANT to Player.HAN)
        put(Position(2, 0), PieceDetector.PieceType.HORSE to Player.HAN)
        put(Position(3, 0), PieceDetector.PieceType.GUARD to Player.HAN)
        put(Position(5, 0), PieceDetector.PieceType.GUARD to Player.HAN)
        put(Position(6, 0), PieceDetector.PieceType.HORSE to Player.HAN)
        put(Position(7, 0), PieceDetector.PieceType.ELEPHANT to Player.HAN)
        put(Position(8, 0), PieceDetector.PieceType.CHARIOT to Player.HAN)

        // Row 1: · · · · 漢 · · · ·
        put(Position(4, 1), PieceDetector.PieceType.GENERAL to Player.HAN)

        // Row 2: · 包 · · · · · 包 ·
        put(Position(1, 2), PieceDetector.PieceType.CANNON to Player.HAN)
        put(Position(7, 2), PieceDetector.PieceType.CANNON to Player.HAN)

        // Row 3: 兵 · 兵 · 兵 · 兵 · 兵
        put(Position(0, 3), PieceDetector.PieceType.SOLDIER to Player.HAN)
        put(Position(2, 3), PieceDetector.PieceType.SOLDIER to Player.HAN)
        put(Position(4, 3), PieceDetector.PieceType.SOLDIER to Player.HAN)
        put(Position(6, 3), PieceDetector.PieceType.SOLDIER to Player.HAN)
        put(Position(8, 3), PieceDetector.PieceType.SOLDIER to Player.HAN)

        // === 楚 (CHO) - 빨강, 아래쪽 ===
        // Row 6: 卒 · 卒 · 卒 · 卒 · 卒
        put(Position(0, 6), PieceDetector.PieceType.SOLDIER to Player.CHO)
        put(Position(2, 6), PieceDetector.PieceType.SOLDIER to Player.CHO)
        put(Position(4, 6), PieceDetector.PieceType.SOLDIER to Player.CHO)
        put(Position(6, 6), PieceDetector.PieceType.SOLDIER to Player.CHO)
        put(Position(8, 6), PieceDetector.PieceType.SOLDIER to Player.CHO)

        // Row 7: · 包 · · · · · 包 ·
        put(Position(1, 7), PieceDetector.PieceType.CANNON to Player.CHO)
        put(Position(7, 7), PieceDetector.PieceType.CANNON to Player.CHO)

        // Row 8: · · · · 楚 · · · ·
        put(Position(4, 8), PieceDetector.PieceType.GENERAL to Player.CHO)

        // Row 9: 車 象 馬 士 · 士 馬 象 車
        put(Position(0, 9), PieceDetector.PieceType.CHARIOT to Player.CHO)
        put(Position(1, 9), PieceDetector.PieceType.ELEPHANT to Player.CHO)
        put(Position(2, 9), PieceDetector.PieceType.HORSE to Player.CHO)
        put(Position(3, 9), PieceDetector.PieceType.GUARD to Player.CHO)
        put(Position(5, 9), PieceDetector.PieceType.GUARD to Player.CHO)
        put(Position(6, 9), PieceDetector.PieceType.HORSE to Player.CHO)
        put(Position(7, 9), PieceDetector.PieceType.ELEPHANT to Player.CHO)
        put(Position(8, 9), PieceDetector.PieceType.CHARIOT to Player.CHO)
    }

    /**
     * 0수 이미지에서 기물 템플릿 추출
     *
     * @param bitmap 0수 스크린샷
     * @param grid 교차점 그리드 정보
     * @param boardColor [PieceDetector.estimateBoardBackgroundColor]로 측정한 보드 배경색 (RGB).
     *   기물 영역을 그리드 좌표가 아니라 실제 기물 모양(보드색과 다른 픽셀)에 맞춰
     *   타이트하게 크롭하는 데 사용합니다.
     * @return 추출된 템플릿 저장소
     */
    suspend fun extractTemplates(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid,
        boardColor: IntArray
    ): PieceTemplates = withContext(Dispatchers.Default) {
        Log.d(TAG, "=== Template Extraction Started ===")
        Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")
        Log.d(TAG, "Grid: ${grid.intersections.size} intersections")

        val templates = PieceTemplates()
        var extractedCount = 0
        var skippedCount = 0

        // Mat으로 변환
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        try {
            // 초기 배치의 각 위치에서 템플릿 추출
            for ((position, pieceInfo) in initialPositions) {
                val (pieceType, assumedPlayer) = pieceInfo

                // 교차점 찾기
                val intersection = grid.intersections.find { it.position == position }
                if (intersection == null) {
                    Log.w(TAG, "Intersection not found for position: $position")
                    continue
                }

                // 색상 기반 실제 진영 판별 (위치 기반 가정보다 우선)
                // initialPositions의 위치 기반 가정(위=HAN/아래=CHO)이 실제 앱의 색상
                // 배색(예: 위쪽이 빨강인 앱)과 다를 수 있으므로, 실시간 매칭이 항상
                // 신뢰하는 "색상으로 판별한 진영"을 최종 라벨로 사용합니다. 그래야
                // 매칭 시점(색상 기준)과 템플릿 라벨(위치 기준 가정)이 항상 일치합니다.
                val detectedPlayer = detectPlayerFromColor(bitmap, intersection)
                val actualPlayer = detectedPlayer ?: assumedPlayer
                if (detectedPlayer != null && detectedPlayer != assumedPlayer) {
                    Log.w(TAG, "Player mismatch at $position: assumed=$assumedPlayer, detected(used)=$detectedPlayer")
                }

                // 이미 해당 타입의 템플릿이 있으면 건너뛰기 (실제 진영 기준으로 판단)
                // (같은 종류의 기물은 동일한 모양이므로 하나만 필요)
                if (templates.getTemplate(pieceType, actualPlayer) != null) {
                    Log.v(TAG, "Skipping duplicate: $actualPlayer $pieceType at $position")
                    skippedCount++
                    continue
                }

                // 기물 영역 크롭 (그리드 좌표가 아니라 실제 기물 모양에 맞춰 타이트하게)
                val croppedMat = cropPieceRegion(srcMat, bitmap, intersection, grid, boardColor)
                if (croppedMat == null) {
                    Log.w(TAG, "Failed to crop piece at $position")
                    continue
                }

                // 전처리 및 템플릿 저장
                val templateMat = preprocessTemplate(croppedMat)
                val size = Size(croppedMat.cols().toDouble(), croppedMat.rows().toDouble())

                templates.addTemplate(pieceType, actualPlayer, templateMat, size)
                extractedCount++

                // TEMP DEBUG: 실제로 어떤 이미지가 템플릿으로 저장됐는지 확인용
                dumpDebugCrop("template_${actualPlayer}_${pieceType}", croppedMat)
                dumpDebugCrop("template_${actualPlayer}_${pieceType}_gray", templateMat)

                // 메모리 해제
                croppedMat.release()
                templateMat.release()

                Log.d(TAG, "Extracted: $actualPlayer $pieceType at $position")
            }
        } finally {
            srcMat.release()
        }

        Log.i(TAG, "=== Template Extraction Complete ===")
        Log.i(TAG, "Extracted: $extractedCount, Skipped (duplicates): $skippedCount")
        Log.i(TAG, templates.getSummary())

        templates
    }

    /**
     * 교차점에서 기물 영역 크롭
     *
     * 그리드 좌표를 그대로 신뢰하지 않고, 그 주변 탐색 영역에서 보드 배경색과
     * 다른(=기물로 추정되는) 픽셀들의 무게중심과 경계를 직접 찾아 그 모양에
     * 맞춰 타이트하게 크롭합니다. 그리드 좌표가 한두 픽셀 어긋나도 실제 기물
     * 중심으로 자동 보정되고, 템플릿에 보드 배경이 섞여 들어가지 않습니다.
     */
    private fun cropPieceRegion(
        srcMat: Mat,
        bitmap: Bitmap,
        intersection: IntersectionCalculator.BoardIntersection,
        grid: IntersectionCalculator.IntersectionGrid,
        boardColor: IntArray
    ): Mat? {
        val nominalX = intersection.pixelX.toInt()
        val nominalY = intersection.pixelY.toInt()

        val cellWidth = grid.averageCellWidth
        val cellHeight = grid.averageCellHeight
        val nominalRadius = (minOf(cellWidth, cellHeight) * 0.45f).toInt()
        val searchRadius = (minOf(cellWidth, cellHeight) * 0.65f).toInt()

        val bounds = refinePieceBounds(bitmap, nominalX, nominalY, searchRadius, boardColor)
        val centerX = bounds?.centerX ?: nominalX
        val centerY = bounds?.centerY ?: nominalY
        val cropRadius = ((bounds?.radius ?: nominalRadius) * 1.15f).toInt()
            .coerceIn(nominalRadius / 2, searchRadius)

        // 경계 체크
        val left = max(0, centerX - cropRadius)
        val top = max(0, centerY - cropRadius)
        val right = minOf(srcMat.cols(), centerX + cropRadius)
        val bottom = minOf(srcMat.rows(), centerY + cropRadius)

        if (right <= left || bottom <= top) {
            return null
        }

        val rect = Rect(left, top, right - left, bottom - top)
        return Mat(srcMat, rect)
    }

    /**
     * 기물 경계 추정 결과 (중심 좌표 + 반경)
     */
    private data class PieceBounds(val centerX: Int, val centerY: Int, val radius: Int)

    /**
     * 탐색 영역 내에서 보드 배경색과 다른 픽셀들의 무게중심/경계를 계산합니다.
     * 일치하는 픽셀이 너무 적으면(기물이 없거나 탐지 실패) null을 반환합니다.
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
     * 템플릿 전처리
     *
     * 템플릿 매칭을 위해 그레이스케일로 변환하고 정규화합니다.
     */
    private fun preprocessTemplate(mat: Mat): Mat {
        return TemplateBinarizer.binarize(mat, TemplateBinarizer.TEMPLATE_SIZE)
    }

    /**
     * 색상 기반 진영 검출
     */
    private fun detectPlayerFromColor(
        bitmap: Bitmap,
        intersection: IntersectionCalculator.BoardIntersection
    ): Player? {
        val centerX = intersection.pixelX.toInt()
        val centerY = intersection.pixelY.toInt()
        val radius = (intersection.cellRadius * 0.8f).toInt()

        var redCount = 0
        var blueGreenCount = 0
        var totalCount = 0

        val step = max(1, radius / 5)

        for (dy in -radius..radius step step) {
            for (dx in -radius..radius step step) {
                if (dx * dx + dy * dy > radius * radius) continue

                val px = centerX + dx
                val py = centerY + dy

                if (px < 0 || px >= bitmap.width || py < 0 || py >= bitmap.height) continue

                val pixel = bitmap.getPixel(px, py)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // 채도가 낮으면 흰색/회색 (기물 배경)으로 건너뛰기
                if (sat < 0.2f || value < 0.2f) continue

                totalCount++

                // 빨간색: Hue 0-30 또는 330-360
                if ((hue in 0f..30f || hue in 330f..360f) && sat > 0.3f) {
                    redCount++
                }
                // 파란색/녹색: Hue 80-260
                else if (hue in 80f..260f && sat > 0.3f) {
                    blueGreenCount++
                }
            }
        }

        if (totalCount < 10) return null

        val redRatio = redCount.toFloat() / totalCount
        val blueGreenRatio = blueGreenCount.toFloat() / totalCount

        return when {
            blueGreenRatio > redRatio && blueGreenRatio > 0.1f -> Player.CHO
            redRatio > blueGreenRatio && redRatio > 0.1f -> Player.HAN
            else -> null
        }
    }

    /**
     * 마상차림 감지 (선택사항)
     *
     * 실제 馬/象 위치를 감지하여 초기 배치 정보를 보정합니다.
     * TODO: 필요시 구현
     */
    private fun detectMasangCharim(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
    ): MasangCharim {
        // 기본값: 외상마차림 (象-馬)
        return MasangCharim.STANDARD
    }

    /**
     * 마상차림 유형
     */
    enum class MasangCharim {
        STANDARD,    // 외상마 (象-馬)
        INNER_HORSE, // 내마상 (馬-象)
        DOUBLE_HORSE,// 양마 (馬-馬)
        DOUBLE_ELEPHANT // 양상 (象-象)
    }
}
