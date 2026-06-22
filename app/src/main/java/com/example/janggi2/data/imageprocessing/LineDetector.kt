package com.example.janggi2.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
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
 * 나무색 보드 위의 검은색 선을 직접 검출하여
 * 9x10 그리드의 교차점을 계산합니다.
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
     */
    fun detectLines(bitmap: Bitmap, boardRegion: Rect?): DetectedLines? {
        try {
            Log.d(TAG, "=== Line Detection Started (Black Line Detection) ===")
            Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")

            // 1. OpenCV Mat으로 변환
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // 2. 검은색 선 검출 (그레이스케일 + 이진화)
            val blackLineMask = detectBlackLines(mat)

            // 3. HoughLinesP로 선분 검출
            val lineSegments = detectLinesWithHoughP(blackLineMask)
            Log.d(TAG, "HoughLinesP detected ${lineSegments.size} line segments")

            if (lineSegments.isEmpty()) {
                Log.w(TAG, "No lines detected, trying with lower threshold")
                // 더 낮은 임계값으로 재시도
                val lineSegments2 = detectLinesWithHoughPLowThreshold(blackLineMask)
                Log.d(TAG, "Retry detected ${lineSegments2.size} line segments")

                if (lineSegments2.isEmpty()) {
                    blackLineMask.release()
                    mat.release()
                    return fallbackGridDetection(bitmap)
                }

                return processLineSegments(lineSegments2, mat.cols(), mat.rows(), bitmap, blackLineMask, mat)
            }

            return processLineSegments(lineSegments, mat.cols(), mat.rows(), bitmap, blackLineMask, mat)

        } catch (e: Exception) {
            Log.e(TAG, "Line detection failed", e)
            return null
        }
    }

    /**
     * 검출된 선분을 처리하여 최종 그리드 생성
     */
    private fun processLineSegments(
        lineSegments: List<DoubleArray>,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        blackLineMask: Mat,
        mat: Mat
    ): DetectedLines {
        // 4. 선분에서 세로선/가로선 분류
        val (rawVertical, rawHorizontal) = classifyLineSegments(lineSegments, width, height)
        Log.d(TAG, "Classified: ${rawVertical.size} vertical, ${rawHorizontal.size} horizontal")

        // 5. 클러스터링으로 대표선 추출
        val clusteredVertical = clusterLines(rawVertical, width)
        val clusteredHorizontal = clusterLines(rawHorizontal, height)
        Log.d(TAG, "Clustered: ${clusteredVertical.size} vertical, ${clusteredHorizontal.size} horizontal")

        // 6. 9개, 10개 선 선택/보간
        val finalVertical = selectOrInterpolateLines(clusteredVertical, EXPECTED_VERTICAL_LINES)
        val finalHorizontal = selectOrInterpolateLines(clusteredHorizontal, EXPECTED_HORIZONTAL_LINES)
        Log.d(TAG, "Final: ${finalVertical.size} vertical, ${finalHorizontal.size} horizontal")

        // 7. 보드 영역 계산
        val detectedBoardRegion = if (finalVertical.isNotEmpty() && finalHorizontal.isNotEmpty()) {
            Rect(
                finalVertical.first().toInt(),
                finalHorizontal.first().toInt(),
                finalVertical.last().toInt(),
                finalHorizontal.last().toInt()
            )
        } else {
            Rect(0, 0, bitmap.width, bitmap.height)
        }

        // 8. 신뢰도 계산
        val confidence = calculateConfidence(clusteredVertical.size, clusteredHorizontal.size)

        blackLineMask.release()
        mat.release()

        Log.i(TAG, "Line detection complete: confidence=$confidence")
        Log.d(TAG, "Board region: ${detectedBoardRegion.left},${detectedBoardRegion.top} - ${detectedBoardRegion.right},${detectedBoardRegion.bottom}")

        return DetectedLines(
            verticalLines = finalVertical,
            horizontalLines = finalHorizontal,
            confidence = confidence,
            boardRegion = detectedBoardRegion
        )
    }

    /**
     * 검은색 선 검출
     * 나무색 배경에서 어두운(검은색) 선을 추출합니다.
     */
    private fun detectBlackLines(mat: Mat): Mat {
        // 1. 그레이스케일 변환
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

        // 2. 가우시안 블러 (노이즈 제거)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        // 3. 검은색 검출을 위한 이진화
        // 낮은 값(어두운 픽셀) = 흰색으로 변환
        val binary = Mat()

        // 방법 1: 단순 임계값 (검은색 선은 밝기가 낮음)
        // 임계값 80 이하 = 검은색으로 판단
        Imgproc.threshold(blurred, binary, 80.0, 255.0, Imgproc.THRESH_BINARY_INV)

        // 4. 모폴로지 연산으로 선 강화
        // 얇은 선을 약간 두껍게
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        Imgproc.dilate(binary, binary, kernel)

        gray.release()
        blurred.release()

        Log.d(TAG, "Black line mask created: ${binary.cols()}x${binary.rows()}")
        return binary
    }

    /**
     * HoughLinesP로 선분 검출
     */
    private fun detectLinesWithHoughP(binary: Mat): List<DoubleArray> {
        val lines = Mat()

        // Canny 엣지
        val edges = Mat()
        Imgproc.Canny(binary, edges, 50.0, 150.0)

        // 선분 길이 기준 (이미지 크기의 20%)
        val minLineLength = (min(binary.rows(), binary.cols()) * 0.2).toDouble()

        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0,                    // rho
            Math.PI / 180,          // theta
            80,                     // threshold
            minLineLength,          // minLineLength
            30.0                    // maxLineGap
        )

        val result = extractLineSegments(lines)
        Log.d(TAG, "HoughLinesP (threshold=80, minLen=$minLineLength): ${result.size} segments")

        edges.release()
        lines.release()

        return result
    }

    /**
     * 낮은 임계값으로 재시도
     */
    private fun detectLinesWithHoughPLowThreshold(binary: Mat): List<DoubleArray> {
        val lines = Mat()

        val edges = Mat()
        Imgproc.Canny(binary, edges, 30.0, 100.0)

        val minLineLength = (min(binary.rows(), binary.cols()) * 0.15).toDouble()

        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0,
            Math.PI / 180,
            40,                     // 낮은 threshold
            minLineLength,
            50.0                    // 더 큰 maxLineGap
        )

        val result = extractLineSegments(lines)
        Log.d(TAG, "HoughLinesP LOW (threshold=40, minLen=$minLineLength): ${result.size} segments")

        edges.release()
        lines.release()

        return result
    }

    /**
     * Mat에서 선분 데이터 추출
     */
    private fun extractLineSegments(lines: Mat): List<DoubleArray> {
        val result = mutableListOf<DoubleArray>()
        for (i in 0 until lines.rows()) {
            val data = lines.get(i, 0)
            if (data != null && data.size >= 4) {
                result.add(data)
            }
        }
        return result
    }

    /**
     * 선분을 세로선/가로선으로 분류
     */
    private fun classifyLineSegments(
        lineSegments: List<DoubleArray>,
        width: Int,
        height: Int
    ): Pair<MutableList<Float>, MutableList<Float>> {
        val verticalXs = mutableListOf<Float>()
        val horizontalYs = mutableListOf<Float>()

        for (segment in lineSegments) {
            val x1 = segment[0]
            val y1 = segment[1]
            val x2 = segment[2]
            val y2 = segment[3]

            val dx = abs(x2 - x1)
            val dy = abs(y2 - y1)
            val length = Math.sqrt(dx * dx + dy * dy)

            // 최소 길이 필터 (이미지 크기의 10%)
            val minLength = min(width, height) * 0.1
            if (length < minLength) continue

            // 세로선: 거의 수직 (각도 75~105도)
            if (dy > dx * 2) {
                val avgX = ((x1 + x2) / 2).toFloat()
                if (avgX in 0f..width.toFloat()) {
                    verticalXs.add(avgX)
                    Log.v(TAG, "Vertical line at x=$avgX (dy=$dy, dx=$dx)")
                }
            }
            // 가로선: 거의 수평 (각도 -15~15도)
            else if (dx > dy * 2) {
                val avgY = ((y1 + y2) / 2).toFloat()
                if (avgY in 0f..height.toFloat()) {
                    horizontalYs.add(avgY)
                    Log.v(TAG, "Horizontal line at y=$avgY (dx=$dx, dy=$dy)")
                }
            }
        }

        return Pair(verticalXs, horizontalYs)
    }

    /**
     * 가까운 선들을 클러스터링하여 대표선 추출
     */
    private fun clusterLines(lines: List<Float>, maxValue: Int): List<Float> {
        if (lines.isEmpty()) return emptyList()

        val sorted = lines.sorted()

        // 클러스터 간격 임계값 (이미지 크기의 3%)
        val threshold = maxValue * 0.03f

        val clusters = mutableListOf<MutableList<Float>>()
        var currentCluster = mutableListOf(sorted[0])

        for (i in 1 until sorted.size) {
            if (sorted[i] - currentCluster.last() < threshold) {
                currentCluster.add(sorted[i])
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(sorted[i])
            }
        }
        clusters.add(currentCluster)

        // 각 클러스터의 평균값
        val clusterCenters = clusters.map { cluster ->
            cluster.average().toFloat()
        }

        Log.d(TAG, "Clustered ${lines.size} lines -> ${clusterCenters.size} clusters")
        return clusterCenters
    }

    /**
     * 선 개수에 맞게 선택 또는 보간
     */
    private fun selectOrInterpolateLines(lines: List<Float>, expectedCount: Int): List<Float> {
        if (lines.isEmpty()) {
            Log.w(TAG, "No lines to process, cannot interpolate")
            return emptyList()
        }

        val sorted = lines.sorted()

        // 검출된 선이 충분하면 균등 간격으로 선택
        if (sorted.size >= expectedCount) {
            return selectEvenlySpacedLines(sorted, expectedCount)
        }

        // 부족하면 보간
        return interpolateToCount(sorted, expectedCount)
    }

    /**
     * 균등 간격으로 선 선택
     */
    private fun selectEvenlySpacedLines(lines: List<Float>, count: Int): List<Float> {
        if (lines.size <= count) return lines

        val minVal = lines.first()
        val maxVal = lines.last()
        val idealSpacing = (maxVal - minVal) / (count - 1)

        val result = mutableListOf<Float>()
        val used = BooleanArray(lines.size)

        for (i in 0 until count) {
            val idealPos = minVal + idealSpacing * i

            // 가장 가까운 미사용 선 찾기
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE

            for (j in lines.indices) {
                if (!used[j]) {
                    val dist = abs(lines[j] - idealPos)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = j
                    }
                }
            }

            if (bestIdx >= 0) {
                result.add(lines[bestIdx])
                used[bestIdx] = true
            }
        }

        return result.sorted()
    }

    /**
     * 검출된 선 사이를 보간하여 채우기
     */
    private fun interpolateToCount(lines: List<Float>, expectedCount: Int): List<Float> {
        if (lines.size < 2) {
            Log.w(TAG, "Need at least 2 lines to interpolate")
            return lines
        }

        val minVal = lines.first()
        val maxVal = lines.last()
        val idealSpacing = (maxVal - minVal) / (expectedCount - 1)

        val result = mutableListOf<Float>()
        for (i in 0 until expectedCount) {
            val idealPos = minVal + idealSpacing * i

            // 검출된 선 중 가까운 것이 있으면 사용
            val closest = lines.minByOrNull { abs(it - idealPos) }

            if (closest != null && abs(closest - idealPos) < idealSpacing * 0.4f) {
                // 기존 선과 충분히 가까우면 그 값 사용
                if (closest !in result) {
                    result.add(closest)
                } else {
                    result.add(idealPos)
                }
            } else {
                // 아니면 보간된 위치 사용
                result.add(idealPos)
            }
        }

        return result.sorted().take(expectedCount)
    }

    /**
     * 폴백: 전체 이미지 균등 분할
     */
    private fun fallbackGridDetection(bitmap: Bitmap): DetectedLines {
        Log.w(TAG, "Using fallback grid detection (no lines detected)")

        // 이미지의 중앙 80% 영역 사용
        val marginX = (bitmap.width * 0.1f).toInt()
        val marginY = (bitmap.height * 0.1f).toInt()

        val boardRegion = Rect(
            marginX,
            marginY,
            bitmap.width - marginX,
            bitmap.height - marginY
        )

        val verticalLines = (0 until EXPECTED_VERTICAL_LINES).map { i ->
            boardRegion.left + boardRegion.width().toFloat() * i / (EXPECTED_VERTICAL_LINES - 1)
        }

        val horizontalLines = (0 until EXPECTED_HORIZONTAL_LINES).map { i ->
            boardRegion.top + boardRegion.height().toFloat() * i / (EXPECTED_HORIZONTAL_LINES - 1)
        }

        return DetectedLines(
            verticalLines = verticalLines,
            horizontalLines = horizontalLines,
            confidence = 0.3f,
            boardRegion = boardRegion
        )
    }

    /**
     * 신뢰도 계산
     */
    private fun calculateConfidence(detectedVertical: Int, detectedHorizontal: Int): Float {
        val verticalScore = min(detectedVertical.toFloat() / EXPECTED_VERTICAL_LINES, 1f)
        val horizontalScore = min(detectedHorizontal.toFloat() / EXPECTED_HORIZONTAL_LINES, 1f)
        return ((verticalScore + horizontalScore) / 2).coerceIn(0f, 1f)
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

        val boardRect = detectedLines.boardRegion

        // 보드 영역 표시 (노란색 사각형)
        Imgproc.rectangle(
            mat,
            Point(boardRect.left.toDouble(), boardRect.top.toDouble()),
            Point(boardRect.right.toDouble(), boardRect.bottom.toDouble()),
            Scalar(255.0, 255.0, 0.0),
            3
        )

        // 세로선 그리기 (초록색)
        for (x in detectedLines.verticalLines) {
            Imgproc.line(
                mat,
                Point(x.toDouble(), 0.0),
                Point(x.toDouble(), mat.rows().toDouble()),
                Scalar(0.0, 255.0, 0.0),
                2
            )
        }

        // 가로선 그리기 (초록색)
        for (y in detectedLines.horizontalLines) {
            Imgproc.line(
                mat,
                Point(0.0, y.toDouble()),
                Point(mat.cols().toDouble(), y.toDouble()),
                Scalar(0.0, 255.0, 0.0),
                2
            )
        }

        // 교차점 그리기 (빨간색 원)
        for (x in detectedLines.verticalLines) {
            for (y in detectedLines.horizontalLines) {
                Imgproc.circle(
                    mat,
                    Point(x.toDouble(), y.toDouble()),
                    6,
                    Scalar(255.0, 0.0, 0.0),
                    -1
                )
            }
        }

        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, result)
        mat.release()

        return result
    }

    /**
     * 디버그용: 검은색 선 마스크 시각화
     */
    fun createBlackLineMaskDebug(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val mask = detectBlackLines(mat)

        // 마스크를 컬러로 변환
        val colorMask = Mat()
        Imgproc.cvtColor(mask, colorMask, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(colorMask.cols(), colorMask.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(colorMask, result)

        mat.release()
        mask.release()
        colorMask.release()

        return result
    }
}
