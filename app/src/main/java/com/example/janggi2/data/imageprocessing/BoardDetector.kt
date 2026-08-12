package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 장기판 검출기
 *
 * 색상 기반으로 보드 영역을 검출하고 9×10 그리드로 분할합니다.
 */
@Singleton
class BoardDetector @Inject constructor() {

    companion object {
        private const val TAG = "BoardDetector"
        private const val MIN_BOARD_SIZE = 150
        private const val BOARD_COLS = 9
        private const val BOARD_ROWS = 10
    }

    /**
     * 이미지에서 장기판 영역 검출
     *
     * @param bitmap 입력 이미지
     * @return 검출된 보드 영역, 실패 시 null
     */
    fun detectBoard(bitmap: Bitmap): Rect? {
        Log.d(TAG, "Detecting board in ${bitmap.width}x${bitmap.height} image...")

        val width = bitmap.width
        val height = bitmap.height

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var boardColorPixels = 0

        val sampleStep = 4 // 성능을 위해 샘플링

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                if (isBoardColor(pixel)) {
                    boardColorPixels++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        Log.d(TAG, "Found $boardColorPixels board color pixels")

        // 유효한 영역인지 확인
        val detectedWidth = maxX - minX
        val detectedHeight = maxY - minY

        Log.d(TAG, "Detected region: ${detectedWidth}x${detectedHeight} at ($minX,$minY)")

        if (detectedWidth < MIN_BOARD_SIZE || detectedHeight < MIN_BOARD_SIZE) {
            Log.w(TAG, "❌ Board detection failed: size too small (${detectedWidth}x${detectedHeight} < ${MIN_BOARD_SIZE}x${MIN_BOARD_SIZE})")
            return null
        }

        // 비율 확인 (장기판은 9:10 비율)
        val aspectRatio = detectedWidth.toFloat() / detectedHeight
        Log.d(TAG, "Aspect ratio: $aspectRatio")

        if (aspectRatio < 0.7f || aspectRatio > 1.1f) {
            Log.w(TAG, "❌ Board detection failed: aspect ratio out of range ($aspectRatio)")
            return null
        }

        // 약간의 패딩 추가
        val padding = 10
        val region = Rect(
            (minX - padding).coerceAtLeast(0),
            (minY - padding).coerceAtLeast(0),
            (maxX + padding).coerceAtMost(width),
            (maxY + padding).coerceAtMost(height)
        )

        Log.i(TAG, "✅ Board detected: ${region.width()}x${region.height()} at (${region.left},${region.top})")

        return region
    }

    /**
     * 이미지 중앙 영역을 보드로 추정 (폴백용)
     *
     * @param bitmap 입력 이미지
     * @return 추정된 보드 영역
     */
    fun estimateBoardRegion(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height

        // 화면 중앙의 정사각형 영역을 보드로 추정
        val boardSize = minOf(width, height) * 0.85f
        val centerX = width / 2
        val centerY = height / 2

        val halfSize = (boardSize / 2).toInt()

        val region = Rect(
            (centerX - halfSize).coerceAtLeast(0),
            (centerY - halfSize).coerceAtLeast(0),
            (centerX + halfSize).coerceAtMost(width),
            (centerY + halfSize).coerceAtMost(height)
        )

        Log.d(TAG, "Estimated board region (fallback): ${region.width()}x${region.height()} at (${region.left},${region.top})")

        return region
    }

    /**
     * 장기판 영역을 9×10 셀로 분할
     *
     * @param bitmap 입력 이미지
     * @param region 보드 영역 (null이면 전체 이미지 사용)
     * @return 10행 × 9열의 2차원 배열 (각 셀은 Bitmap)
     */
    fun splitIntoCells(
        bitmap: Bitmap,
        region: Rect? = null
    ): Array<Array<Bitmap>> {
        val sourceBitmap = if (region != null) {
            Bitmap.createBitmap(
                bitmap,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
        } else {
            bitmap
        }

        val cellWidth = sourceBitmap.width / BOARD_COLS
        val cellHeight = sourceBitmap.height / BOARD_ROWS

        Log.d(TAG, "Splitting board into ${BOARD_ROWS}x${BOARD_COLS} cells, each ${cellWidth}x${cellHeight}")

        return Array(BOARD_ROWS) { row ->
            Array(BOARD_COLS) { col ->
                Bitmap.createBitmap(
                    sourceBitmap,
                    col * cellWidth,
                    row * cellHeight,
                    cellWidth,
                    cellHeight
                )
            }
        }
    }

    /**
     * 장기판 색상인지 확인
     * 다양한 디지털 장기판 색상 지원 (갈색, 노란색, 녹색, 회색 등)
     */
    private fun isBoardColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // HSV로 변환하여 검사
        val hsv = FloatArray(3)
        Color.colorToHSV(pixel, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

 2        // 전통 장기판 색상 (갈색~노란색)
        val isTraditionalBoard = hue in 15f..60f &&
                saturation in 0.15f..0.9f &&
                value in 0.3f..0.95f

        // 카카오 장기 등 디지털 보드 (녹색 계열)
        val isGreenBoard = hue in 80f..160f &&
                saturation in 0.1f..0.6f &&
                value in 0.3f..0.9f

    // 회색/베이지 계열 보스톡드
        val isNeutralBoard = saturation < 0.25f &&
                value in 0.35f..0.85f

        // 밝은 크림색/아이보리 보드
        val isLightBoard = hue in 30f..60f &&
                saturation in 0.05f..0.3f &&
                value in 0.7f..0.98f

        return isTraditionalBoard || isGreenBoard || isNeutralBoard || isLightBoard
    }
}
