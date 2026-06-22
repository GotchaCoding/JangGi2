package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 장기판 선 검출기
 *
 * 카카오장기 보드의 정확한 경계를 찾고 수학적으로 그리드를 계산합니다.
 * Hough Transform 대신 색상 기반 경계 검출을 사용합니다.
 */
@Singleton
class LineDetector @Inject constructor() {

    companion object {
        private const val TAG = "LineDetector"
        private const val EXPECTED_VERTICAL_LINES = 9
        private const val EXPECTED_HORIZONTAL_LINES = 10
    }

    /**
     * 검출된 선 정보
     */
    data class DetectedLines(
        val verticalLines: List<Float>,      // 9개 세로선의 X 좌표
        val horizontalLines: List<Float>,    // 10개 가로선의 Y 좌표
        val confidence: Float,               // 검출 신뢰도
        val boardRegion: Rect                // 보드 영역
    )

    /**
     * 비트맵에서 장기판 선을 검출합니다.
     *
     * @param bitmap 입력 이미지
     * @param boardRegion 보드 영역 힌트 (정밀 검출에 사용)
     * @return 검출된 선 정보, 실패 시 null
     */
    fun detectLines(bitmap: Bitmap, boardRegion: Rect?): DetectedLines? {
        try {
            Log.d(TAG, "=== Line Detection Started (Color-based) ===")

            // 1. 정확한 보드 경계 찾기 (색상 기반)
            val preciseBoardRegion = findPreciseBoardRegion(bitmap, boardRegion)

            if (preciseBoardRegion == null) {
                Log.w(TAG, "Failed to find precise board region")
                return null
            }

            Log.d(TAG, "Precise board region: ${preciseBoardRegion.width()}x${preciseBoardRegion.height()} " +
                    "at (${preciseBoardRegion.left},${preciseBoardRegion.top})")

            // 2. 수학적으로 그리드 계산 (9x10 분할)
            val verticalLines = calculateVerticalLines(preciseBoardRegion)
            val horizontalLines = calculateHorizontalLines(preciseBoardRegion)

            Log.d(TAG, "Calculated ${verticalLines.size} vertical, ${horizontalLines.size} horizontal lines")

            // 3. 신뢰도 계산 (보드 영역이 유효한 비율인지)
            val aspectRatio = preciseBoardRegion.width().toFloat() / preciseBoardRegion.height()
            val expectedRatio = 9f / 10f  // 장기판 비율
            val confidence = 1f - abs(aspectRatio - expectedRatio).coerceAtMost(0.5f)

            Log.i(TAG, "✅ Line detection complete: confidence=$confidence, aspectRatio=$aspectRatio")

            return DetectedLines(
                verticalLines = verticalLines,
                horizontalLines = horizontalLines,
                confidence = confidence,
                boardRegion = preciseBoardRegion
            )
        } catch (e: Exception) {
            Log.e(TAG, "Line detection failed", e)
            return null
        }
    }

    /**
     * 색상 기반으로 정확한 보드 경계를 찾습니다.
     * 힌트 영역 내에서 보드 색상이 연속된 가장 큰 사각형을 찾습니다.
     */
    private fun findPreciseBoardRegion(bitmap: Bitmap, hint: Rect?): Rect? {
        val width = bitmap.width
        val height = bitmap.height

        // 힌트 영역 사용 (없으면 중앙 추정)
        val searchRegion = hint ?: Rect(
            (width * 0.05).toInt(),
            (height * 0.15).toInt(),
            (width * 0.95).toInt(),
            (height * 0.85).toInt()
        )

        Log.d(TAG, "Search region: ${searchRegion.width()}x${searchRegion.height()} at (${searchRegion.left},${searchRegion.top})")

        // 중앙에서 시작하여 각 방향으로 경계 찾기
        val centerX = searchRegion.centerX()
        val centerY = searchRegion.centerY()

        // 중앙 픽셀이 보드 색상인지 확인
        if (!isKakaoBoardColor(bitmap.getPixel(centerX, centerY))) {
            Log.w(TAG, "Center pixel is not board color, searching for board area...")
            // 중앙이 보드가 아니면 보드 영역을 찾아야 함
            return findBoardByScanning(bitmap, searchRegion)
        }

        // 중앙에서 각 방향으로 스캔하여 경계 찾기
        val left = findEdge(bitmap, centerX, centerY, -1, 0, searchRegion.left)
        val right = findEdge(bitmap, centerX, centerY, 1, 0, searchRegion.right)
        val top = findEdge(bitmap, centerX, centerY, 0, -1, searchRegion.top)
        val bottom = findEdge(bitmap, centerX, centerY, 0, 1, searchRegion.bottom)

        Log.d(TAG, "Found edges: left=$left, right=$right, top=$top, bottom=$bottom")

        // 유효성 검사
        if (right <= left || bottom <= top) {
            Log.w(TAG, "Invalid board edges detected")
            return findBoardByScanning(bitmap, searchRegion)
        }

        val region = Rect(left, top, right, bottom)

        if (region.width() < 100 || region.height() < 100) {
            Log.w(TAG, "Board region too small: ${region.width()}x${region.height()}")
            return null
        }

        Log.d(TAG, "Precise board region: ${region.width()}x${region.height()} at (${region.left},${region.top})")

        return region
    }

    /**
     * 중앙에서 한 방향으로 스캔하여 보드 경계를 찾습니다.
     */
    private fun findEdge(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        dx: Int,
        dy: Int,
        limit: Int
    ): Int {
        var x = startX
        var y = startY
        var lastBoardX = startX
        var lastBoardY = startY
        var nonBoardCount = 0

        while (true) {
            x += dx
            y += dy

            // 경계 체크
            if (dx != 0 && (x < 0 || x >= bitmap.width || (dx > 0 && x > limit) || (dx < 0 && x < limit))) break
            if (dy != 0 && (y < 0 || y >= bitmap.height || (dy > 0 && y > limit) || (dy < 0 && y < limit))) break

            if (isKakaoBoardColor(bitmap.getPixel(x, y))) {
                lastBoardX = x
                lastBoardY = y
                nonBoardCount = 0
            } else {
                nonBoardCount++
                // 연속 5픽셀 이상 보드 색상이 아니면 경계로 판단
                if (nonBoardCount > 5) {
                    break
                }
            }
        }

        return if (dx != 0) lastBoardX else lastBoardY
    }

    /**
     * 스캔 방식으로 보드 영역 찾기 (폴백)
     */
    private fun findBoardByScanning(bitmap: Bitmap, searchRegion: Rect): Rect? {
        var minX = searchRegion.right
        var maxX = searchRegion.left
        var minY = searchRegion.bottom
        var maxY = searchRegion.top

        val step = 4
        var boardPixelCount = 0

        for (y in searchRegion.top until searchRegion.bottom step step) {
            for (x in searchRegion.left until searchRegion.right step step) {
                if (isKakaoBoardColor(bitmap.getPixel(x, y))) {
                    boardPixelCount++
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }

        Log.d(TAG, "Scanning found $boardPixelCount board pixels")

        if (maxX <= minX || maxY <= minY) {
            Log.w(TAG, "No valid board region found by scanning")
            return null
        }

        // 약간의 마진 추가
        val margin = 3
        return Rect(
            (minX + margin).coerceAtLeast(0),
            (minY + margin).coerceAtLeast(0),
            (maxX - margin).coerceAtMost(bitmap.width),
            (maxY - margin).coerceAtMost(bitmap.height)
        )
    }

    /**
     * 카카오장기 보드 색상인지 확인 (연한 갈색/베이지)
     */
    private fun isKakaoBoardColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val hsv = FloatArray(3)
        Color.colorToHSV(pixel, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        // 카카오장기 보드 색상 (연한 갈색/베이지)
        // Hue: 25-50 (오렌지~노랑 계열)
        // Saturation: 0.15-0.5 (약간의 채도)
        // Value: 0.6-0.95 (밝은 편)
        val isKakaoBoard = hue in 20f..55f &&
                saturation in 0.10f..0.55f &&
                value in 0.55f..0.95f

        // 추가: RGB 기반 검증 (갈색/베이지 계열)
        // R > G > B 패턴이고, 전체적으로 밝은 색
        val isBeigeTone = r > g && g > b &&
                r in 160..250 &&
                g in 140..220 &&
                b in 100..190 &&
                (r - b) in 30..100  // R과 B의 차이가 적당함

        return isKakaoBoard || isBeigeTone
    }

    /**
     * 보드 영역을 9등분하여 세로선 X 좌표 계산
     */
    private fun calculateVerticalLines(boardRegion: Rect): List<Float> {
        val lines = mutableListOf<Float>()
        val boardWidth = boardRegion.width().toFloat()

        // 9개 세로선 (0부터 8까지, 총 9개 열의 경계)
        for (i in 0 until EXPECTED_VERTICAL_LINES) {
            val x = boardRegion.left + (boardWidth * i / (EXPECTED_VERTICAL_LINES - 1))
            lines.add(x)
        }

        return lines
    }

    /**
     * 보드 영역을 10등분하여 가로선 Y 좌표 계산
     */
    private fun calculateHorizontalLines(boardRegion: Rect): List<Float> {
        val lines = mutableListOf<Float>()
        val boardHeight = boardRegion.height().toFloat()

        // 10개 가로선 (0부터 9까지, 총 10개 행의 경계)
        for (i in 0 until EXPECTED_HORIZONTAL_LINES) {
            val y = boardRegion.top + (boardHeight * i / (EXPECTED_HORIZONTAL_LINES - 1))
            lines.add(y)
        }

        return lines
    }

    /**
     * 디버그용: 선 시각화 이미지 생성
     */
    fun createDebugImage(
        bitmap: Bitmap,
        detectedLines: DetectedLines
    ): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // 보드 영역 표시 (노란색 사각형)
        val boardRect = detectedLines.boardRegion
        Imgproc.rectangle(
            mat,
            Point(boardRect.left.toDouble(), boardRect.top.toDouble()),
            Point(boardRect.right.toDouble(), boardRect.bottom.toDouble()),
            Scalar(255.0, 255.0, 0.0),  // Yellow
            3
        )

        // 세로선 그리기 (초록색)
        for (x in detectedLines.verticalLines) {
            Imgproc.line(
                mat,
                Point(x.toDouble(), boardRect.top.toDouble()),
                Point(x.toDouble(), boardRect.bottom.toDouble()),
                Scalar(0.0, 255.0, 0.0),  // Green
                2
            )
        }

        // 가로선 그리기 (초록색)
        for (y in detectedLines.horizontalLines) {
            Imgproc.line(
                mat,
                Point(boardRect.left.toDouble(), y.toDouble()),
                Point(boardRect.right.toDouble(), y.toDouble()),
                Scalar(0.0, 255.0, 0.0),  // Green
                2
            )
        }

        // 교차점 그리기 (파란색)
        for (x in detectedLines.verticalLines) {
            for (y in detectedLines.horizontalLines) {
                Imgproc.circle(
                    mat,
                    Point(x.toDouble(), y.toDouble()),
                    5,
                    Scalar(0.0, 0.0, 255.0),  // Blue
                    -1
                )
            }
        }

        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, result)
        mat.release()

        return result
    }
}
