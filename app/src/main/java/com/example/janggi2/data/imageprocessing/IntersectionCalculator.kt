package com.example.janggi2.data.imageprocessing

import android.util.Log
import com.example.janggi2.domain.model.Position
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

/**
 * 장기판 교차점 계산기
 *
 * 검출된 선들로부터 90개의 교차점 좌표를 계산하고,
 * 각 교차점에서 기물 검출에 사용할 셀 반지름을 동적으로 계산합니다.
 */
@Singleton
class IntersectionCalculator @Inject constructor() {

    companion object {
        private const val TAG = "IntersectionCalculator"
        private const val BOARD_COLS = 9
        private const val BOARD_ROWS = 10
    }

    /**
     * 보드 교차점 정보
     *
     * @property position 보드 좌표 (col, row)
     * @property pixelX 이미지 내 X 픽셀 좌표
     * @property pixelY 이미지 내 Y 픽셀 좌표
     * @property cellRadius 해당 셀의 반지름 (기물 검출 범위)
     */
    data class BoardIntersection(
        val position: Position,
        val pixelX: Float,
        val pixelY: Float,
        val cellRadius: Float
    )

    /**
     * 교차점 계산 결과
     *
     * @property intersections 90개의 교차점 목록
     * @property averageCellWidth 평균 셀 너비
     * @property averageCellHeight 평균 셀 높이
     */
    data class IntersectionGrid(
        val intersections: List<BoardIntersection>,
        val averageCellWidth: Float,
        val averageCellHeight: Float
    )

    /**
     * 검출된 선들로부터 모든 교차점을 계산합니다.
     *
     * @param detectedLines 선 검출 결과
     * @return 교차점 그리드 정보
     */
    fun calculateIntersections(detectedLines: LineDetector.DetectedLines): IntersectionGrid {
        Log.d(TAG, "=== Calculating Intersections ===")

        val verticalLines = detectedLines.verticalLines
        val horizontalLines = detectedLines.horizontalLines

        // 유효성 검사
        if (verticalLines.size < BOARD_COLS) {
            Log.w(TAG, "Not enough vertical lines: ${verticalLines.size}/$BOARD_COLS")
        }
        if (horizontalLines.size < BOARD_ROWS) {
            Log.w(TAG, "Not enough horizontal lines: ${horizontalLines.size}/$BOARD_ROWS")
        }

        // 평균 셀 크기 계산
        val avgCellWidth = calculateAverageSpacing(verticalLines)
        val avgCellHeight = calculateAverageSpacing(horizontalLines)

        Log.d(TAG, "Average cell size: ${avgCellWidth}x${avgCellHeight}")

        val intersections = mutableListOf<BoardIntersection>()

        // 정확히 9x10 = 90개의 교차점 생성
        for (row in 0 until BOARD_ROWS) {
            for (col in 0 until BOARD_COLS) {
                // 선 인덱스 범위 체크
                val x = if (col < verticalLines.size) {
                    verticalLines[col]
                } else {
                    // 마지막 선에서 평균 간격만큼 확장
                    verticalLines.lastOrNull()?.plus(avgCellWidth * (col - verticalLines.size + 1))
                        ?: (col * avgCellWidth)
                }

                val y = if (row < horizontalLines.size) {
                    horizontalLines[row]
                } else {
                    horizontalLines.lastOrNull()?.plus(avgCellHeight * (row - horizontalLines.size + 1))
                        ?: (row * avgCellHeight)
                }

                // 셀 반지름 계산 (인접 선 간격의 최소값의 절반)
                val cellRadius = calculateCellRadius(
                    verticalLines, horizontalLines,
                    col, row,
                    avgCellWidth, avgCellHeight
                )

                intersections.add(
                    BoardIntersection(
                        position = Position(col, row),
                        pixelX = x,
                        pixelY = y,
                        cellRadius = cellRadius
                    )
                )
            }
        }

        Log.i(TAG, "✅ Calculated ${intersections.size} intersections")

        return IntersectionGrid(
            intersections = intersections,
            averageCellWidth = avgCellWidth,
            averageCellHeight = avgCellHeight
        )
    }

    /**
     * 평균 선 간격 계산
     */
    private fun calculateAverageSpacing(lines: List<Float>): Float {
        if (lines.size < 2) return 50f  // 기본값

        val sorted = lines.sorted()
        var totalSpacing = 0f
        var count = 0

        for (i in 1 until sorted.size) {
            totalSpacing += sorted[i] - sorted[i - 1]
            count++
        }

        return if (count > 0) totalSpacing / count else 50f
    }

    /**
     * 특정 교차점의 셀 반지름 계산
     *
     * 인접 선들과의 거리 중 최소값의 절반을 사용하여,
     * 기물 검출 시 인접 셀과 겹치지 않도록 합니다.
     */
    private fun calculateCellRadius(
        verticalLines: List<Float>,
        horizontalLines: List<Float>,
        col: Int,
        row: Int,
        avgCellWidth: Float,
        avgCellHeight: Float
    ): Float {
        // 좌우 간격 계산
        val leftSpacing = if (col > 0 && col < verticalLines.size && col - 1 < verticalLines.size) {
            abs(verticalLines[col] - verticalLines[col - 1])
        } else {
            avgCellWidth
        }

        val rightSpacing = if (col < verticalLines.size - 1 && col + 1 < verticalLines.size) {
            abs(verticalLines[col + 1] - verticalLines[col])
        } else {
            avgCellWidth
        }

        // 상하 간격 계산
        val topSpacing = if (row > 0 && row < horizontalLines.size && row - 1 < horizontalLines.size) {
            abs(horizontalLines[row] - horizontalLines[row - 1])
        } else {
            avgCellHeight
        }

        val bottomSpacing = if (row < horizontalLines.size - 1 && row + 1 < horizontalLines.size) {
            abs(horizontalLines[row + 1] - horizontalLines[row])
        } else {
            avgCellHeight
        }

        // 인접 셀과 겹치지 않도록 최소 간격 기준으로 반지름을 잡습니다.
        // 비율은 [TemplateBinarizer]와 공유합니다 - [PieceDetector]가 이 값을
        // 거꾸로 나눠서 셀 크기를 복원해 크롭 크기를 정하므로, 두 값이 어긋나면
        // 크롭 배율이 통째로 틀어집니다.
        val minHorizontalSpacing = min(leftSpacing, rightSpacing)
        val minVerticalSpacing = min(topSpacing, bottomSpacing)
        val minSpacing = min(minHorizontalSpacing, minVerticalSpacing)

        return minSpacing * TemplateBinarizer.CELL_RADIUS_RATIO
    }

    /**
     * 특정 위치에 해당하는 교차점 찾기
     */
    fun findIntersection(
        grid: IntersectionGrid,
        position: Position
    ): BoardIntersection? {
        return grid.intersections.find { it.position == position }
    }

    /**
     * 픽셀 좌표로 가장 가까운 교차점 찾기
     */
    fun findNearestIntersection(
        grid: IntersectionGrid,
        pixelX: Float,
        pixelY: Float
    ): BoardIntersection? {
        return grid.intersections.minByOrNull { intersection ->
            val dx = intersection.pixelX - pixelX
            val dy = intersection.pixelY - pixelY
            dx * dx + dy * dy  // 거리의 제곱 (sqrt 불필요)
        }
    }

    /**
     * 교차점 그리드의 경계 영역 계산
     */
    fun getBoundingRect(grid: IntersectionGrid): android.graphics.Rect {
        val minX = grid.intersections.minOfOrNull { it.pixelX }?.toInt() ?: 0
        val maxX = grid.intersections.maxOfOrNull { it.pixelX }?.toInt() ?: 0
        val minY = grid.intersections.minOfOrNull { it.pixelY }?.toInt() ?: 0
        val maxY = grid.intersections.maxOfOrNull { it.pixelY }?.toInt() ?: 0

        return android.graphics.Rect(minX, minY, maxX, maxY)
    }
}
