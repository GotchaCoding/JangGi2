package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
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

        val bounds = largestBoardComponent(bitmap) ?: run {
            Log.w(TAG, "❌ Board detection failed: no board-coloured region")
            return null
        }
        val minX = bounds.left
        val minY = bounds.top
        val maxX = bounds.right
        val maxY = bounds.bottom

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

        // 패딩 없이 나무판 경계 그대로 반환합니다.
        // [LineDetector]가 이 경계를 기준으로 격자 간격과 원점을 계산하므로,
        // 여백을 붙이면 격자 전체가 그만큼 어긋납니다.
        val region = Rect(
            minX.coerceAtLeast(0),
            minY.coerceAtLeast(0),
            maxX.coerceAtMost(width),
            maxY.coerceAtMost(height)
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
     * 보드 색 픽셀 중 가장 큰 덩어리의 경계를 구합니다.
     *
     * 색이 맞는 픽셀 전체의 최소/최대를 쓰면, 화면 어딘가의 UI 한 조각만 색이
     * 비슷해도 경계가 그쪽까지 늘어납니다. 장기판은 화면에서 가장 큰 단일 덩어리
     * 이므로 연결 요소 중 최대 면적만 취해서 그런 오염을 걸러냅니다.
     *
     * 회색·흰색에 가까운 색은 보드로 보지 않습니다. 그 범위를 허용하면 밝은
     * 배경이나 하단 정보 영역이 통째로 보드로 잡힙니다.
     */
    private fun largestBoardComponent(bitmap: Bitmap): Rect? {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val hsv = Mat()
        Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
        rgba.release()

        // OpenCV HSV: H 0..179 (=각도/2), S·V 0..255
        val mask = Mat()
        // 전통 장기판 (갈색~노란색): 각도 15~60, 채도 0.15~0.9, 명도 0.3~0.95
        Core.inRange(hsv, Scalar(7.0, 38.0, 76.0), Scalar(30.0, 230.0, 242.0), mask)
        // 디지털 보드 (녹색 계열): 각도 80~160
        val green = Mat()
        Core.inRange(hsv, Scalar(40.0, 25.0, 76.0), Scalar(80.0, 153.0, 230.0), green)
        Core.bitwise_or(mask, green, mask)
        green.release()
        hsv.release()

        // 판의 격자선이 나무 영역을 잘라놓기 때문에, 닫기 연산으로 선 두께만큼
   3      // 메워서 판이 하나의 덩어리가 되게 합니다. 이걸 빼면 판이 세로로 두
        // 덩어리로 쪼개져 큰 쪽만 잡히고, 가로세로비 검사에서 탈락해 화면 중앙을
        // 추정하는 폴백으로 넘어갑니다.
        val bridge = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, bridge)
        bridge.release()

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids, 8)
        mask.release()
        labels.release()
        centroids.release()

        var bestArea = 0
        var best = -1
        for (i in 1 until count) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (area > bestArea) {
                bestArea = area
                best = i
            }
        }
        if (best < 0) {
            stats.release()
            return null
        }

        val x = stats.get(best, Imgproc.CC_STAT_LEFT)[0].toInt()
        val y = stats.get(best, Imgproc.CC_STAT_TOP)[0].toInt()
        val w = stats.get(best, Imgproc.CC_STAT_WIDTH)[0].toInt()
        val h = stats.get(best, Imgproc.CC_STAT_HEIGHT)[0].toInt()
        stats.release()

        Log.d(TAG, "Largest board component: ${w}x$h at ($x,$y), area=$bestArea")
        return Rect(x, y, x + w, y + h)
    }
}
