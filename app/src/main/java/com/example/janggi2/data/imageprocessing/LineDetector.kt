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

                return processLineSegments(
                    lineSegments2, mat.cols(), mat.rows(), bitmap, blackLineMask, mat, boardRegion
                )
            }

            return processLineSegments(
                lineSegments, mat.cols(), mat.rows(), bitmap, blackLineMask, mat, boardRegion
            )

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
        mat: Mat,
        woodRegion: Rect?
    ): DetectedLines {
        // 4. 선분에서 세로선/가로선 분류
        val (rawVertical, rawHorizontal) = classifyLineSegments(lineSegments, width, height)
        Log.d(TAG, "Classified: ${rawVertical.size} vertical, ${rawHorizontal.size} horizontal")

        // 5. 클러스터링으로 대표선 추출
        val clusteredVertical = clusterLines(rawVertical, width)
        val clusteredHorizontal = clusterLines(rawHorizontal, height)
        Log.d(TAG, "Clustered: ${clusteredVertical.size} vertical, ${clusteredHorizontal.size} horizontal")

        // 6. 등간격 격자로 확정
        val region = woodRegion ?: Rect(0, 0, width, height)
        val finalVertical = fitLattice(
            clusteredVertical, region.left, region.width(), EXPECTED_VERTICAL_LINES
        )
        val finalHorizontal = fitLattice(
            clusteredHorizontal, region.top, region.height(), EXPECTED_HORIZONTAL_LINES
        )
        Log.d(TAG, "Final: ${finalVertical.size} vertical, ${finalHorizontal.size} horizontal")
        Log.d(TAG, "Lattice spacing: dx=${finalVertical[1] - finalVertical[0]}, " +
                "dy=${finalHorizontal[1] - finalHorizontal[0]}")

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
     * 등간격 격자 맞추기
     *
     * 장기판은 9×10 등간격 격자이므로, 검출된 선을 순서대로 갖다 쓰는 대신 격자
     * 자체를 맞춥니다. 기물이 바깥 선을 가리면 그 선을 놓치는데, 순서대로 쓰면
     * 격자 전체가 밀리거나 압축돼서 맨 윗줄·아랫줄이 판 밖으로 벗어납니다.
     *
     * 기준점은 나무판 영역입니다. 판은 바깥 선보다 정확히 반 칸 넓으므로
     * `간격 = 영역크기 / 선개수`, `첫 선 = 가장자리 + 반 칸`이 닫힌 형태로 나오고,
     * 검출된 선은 이 값을 반 칸 이내에서 다듬는 용도로만 씁니다.
     *
     * @param candidates 클러스터링된 선 좌표 (비어 있어도 됨)
     * @param regionStart 나무판 영역의 시작 좌표
     * @param regionSize 나무판 영역의 크기
     * @param count 이 축에 필요한 선 개수
     */
    private fun fitLattice(
        candidates: List<Float>,
        regionStart: Int,
        regionSize: Int,
        count: Int
    ): List<Float> {
        val cell0 = regionSize.toFloat() / count
        val origin0 = regionStart + cell0 / 2f

        if (candidates.isEmpty() || cell0 <= 0f) {
            Log.d(TAG, "fitLattice: no candidates, using board region estimate")
            return List(count) { origin0 + cell0 * it }
        }

        val sorted = candidates.sorted()
        val tolerance = cell0 * 0.25f
        var bestScore = -1f
        var bestOrigin = origin0
        var bestCell = cell0

        var scale = 0.94f
        while (scale <= 1.0601f) {
            val cell = cell0 * scale
            var origin = origin0 - cell0 * 0.30f
            val originEnd = origin0 + cell0 * 0.30f
            while (origin <= originEnd) {
                var score = 0f
                for (i in 0 until count) {
                    val target = origin + cell * i
                    var nearest = Float.MAX_VALUE
                    for (c in sorted) {
                        val d = abs(c - target)
                        if (d < nearest) nearest = d
                        if (c > target && d > nearest) break
                    }
                    if (nearest < tolerance) score += 1f - nearest / tolerance
                }
                if (score > bestScore) {
                    bestScore = score
                    bestOrigin = origin
                    bestCell = cell
                }
                origin += 1f
            }
            scale += 0.005f
        }

        Log.d(TAG, "fitLattice: count=$count cell=$bestCell origin=$bestOrigin " +
                "match=${"%.1f".format(bestScore)}/$count")
        return List(count) { bestOrigin + bestCell * it }
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
