package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
     * @return 추출된 템플릿 저장소
     */
    suspend fun extractTemplates(
        bitmap: Bitmap,
        grid: IntersectionCalculator.IntersectionGrid
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
                val (pieceType, player) = pieceInfo

                // 이미 해당 타입의 템플릿이 있으면 건너뛰기
                // (같은 종류의 기물은 동일한 모양이므로 하나만 필요)
                if (templates.getTemplate(pieceType, player) != null) {
                    Log.v(TAG, "Skipping duplicate: $player $pieceType at $position")
                    skippedCount++
                    continue
                }

                // 교차점 찾기
                val intersection = grid.intersections.find { it.position == position }
                if (intersection == null) {
                    Log.w(TAG, "Intersection not found for position: $position")
                    continue
                }

                // 기물 영역 크롭
                val croppedMat = cropPieceRegion(srcMat, intersection, grid)
                if (croppedMat == null) {
                    Log.w(TAG, "Failed to crop piece at $position")
                    continue
                }

                // 색상 기반 진영 검증
                val detectedPlayer = detectPlayerFromColor(bitmap, intersection)
                if (detectedPlayer != null && detectedPlayer != player) {
                    Log.w(TAG, "Player mismatch at $position: expected=$player, detected=$detectedPlayer")
                    // 검출된 진영으로 보정
                }

                // 전처리 및 템플릿 저장
                val templateMat = preprocessTemplate(croppedMat)
                val size = Size(croppedMat.cols().toDouble(), croppedMat.rows().toDouble())

                templates.addTemplate(pieceType, player, templateMat, size)
                extractedCount++

                // 메모리 해제
                croppedMat.release()
                templateMat.release()

                Log.d(TAG, "Extracted: $player $pieceType at $position")
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
     */
    private fun cropPieceRegion(
        srcMat: Mat,
        intersection: IntersectionCalculator.BoardIntersection,
        grid: IntersectionCalculator.IntersectionGrid
    ): Mat? {
        val centerX = intersection.pixelX.toInt()
        val centerY = intersection.pixelY.toInt()

        // 셀 크기 기반 크롭 영역 계산
        val cellWidth = grid.averageCellWidth
        val cellHeight = grid.averageCellHeight
        val cropRadius = (minOf(cellWidth, cellHeight) * 0.45f).toInt()

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
     * 템플릿 전처리
     *
     * 템플릿 매칭을 위해 그레이스케일로 변환하고 정규화합니다.
     */
    private fun preprocessTemplate(mat: Mat): Mat {
        val result = Mat()

        // 그레이스케일 변환
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, result, Imgproc.COLOR_RGBA2GRAY)
        } else {
            mat.copyTo(result)
        }

        // 히스토그램 균등화로 대비 향상
        Imgproc.equalizeHist(result, result)

        return result
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
            redRatio > blueGreenRatio && redRatio > 0.1f -> Player.CHO
            blueGreenRatio > redRatio && blueGreenRatio > 0.1f -> Player.HAN
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
