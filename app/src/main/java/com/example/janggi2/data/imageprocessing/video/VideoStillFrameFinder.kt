package com.example.janggi2.data.imageprocessing.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.janggi2.data.imageprocessing.BoardDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 동영상에서 수 하나가 착수 지점에 다 도착해 멈춘(정지) 순간의 프레임만 골라냅니다.
 *
 * 처음엔 "화면 전체가 안 움직이는 구간"을 찾으려 했는데, 실제 영상(수마다 기물이 도착
 * 칸에서 서서히 또렷해지는 애니메이션)에서는 한 수의 애니메이션이 끝나자마자 곧바로 다음
 * 수가 시작돼 화면이 거의 쉬지 않고 계속 움직입니다 - "가만히 있는 구간"은 없다시피
 * 합니다.
 *
 * 대신 **어느 칸이 움직이고 있는지**를 봅니다: 정지해 있던 기물 하나가 움직이기
 * 시작하면 그 칸이 계속 바뀌다가, 다른 칸이 바뀌기 시작하면 그건 방금 그 기물이
 * 멈추고 다음 기물이 움직이기 시작했다는 뜻입니다 - 그 경계 직전 프레임이 방금 그
 * 수의 정지 프레임입니다. 바뀐 픽셀의 위치를 판의 9x10 칸 좌표로 변환해 "지금 움직이는
 * 칸"을 추적하고, 그 칸이 바뀌는 순간을 경계로 씁니다.
 *
 * 두 단계로 나눠 처리합니다:
 * 1. **훑기(싸다)**: 판 영역만 작은 썸네일로 촘촘히 훑으며, 앞 프레임과 달라진 픽셀들이
 *    어느 칸에 몰려 있는지만 봅니다. 판 밖 다른 UI(타이머·수 카운터 등)의 움직임은
 *    무시하기 위해 처음에 한 번만 판 경계를 찾아 그 안쪽만 봅니다.
 * 2. **뽑기(비싸다)**: 움직이는 칸이 넘어가는 경계마다 그 직전 프레임의 시점만 원본
 *    해상도로 다시 뽑습니다.
 *
 * 프레임을 전부 원본 해상도로 들고 있으면 메모리가 감당이 안 됩니다(1080x2340 한 장이
 * ARGB_8888 기준 ~10MB) - 그래서 훑는 동안은 작은 썸네일만 씁니다.
 */
@Singleton
class VideoStillFrameFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boardDetector: BoardDetector
) {
    companion object {
        private const val TAG = "VideoStillFrameFinder"

        /**
         * 훑는 단계의 표본 간격. 수 하나의 애니메이션이 아주 짧을 수 있어(실측 샘플 영상
         * 기준 수십 ms) 성기게 훑으면 경계를 그냥 지나칩니다.
         */
        private const val SCAN_INTERVAL_MS = 10L

        /** 훑는 프레임 수 상한 - 긴 영상에서도 처리 시간이 무한정 늘지 않도록 간격을 늘립니다. */
        private const val MAX_SCAN_FRAMES = 800

        /**
         * 훑는 단계에서 프레임 전체를 줄이는 크기. 1080:2340 비율을 그대로 유지합니다
         * (가로세로 비율이 달라지면 판 경계 좌표를 그대로 못 씁니다).
         */
        private const val SCAN_WIDTH = 270
        private const val SCAN_HEIGHT = 585

        /** 이 값보다 밝기 차이가 크면(0~255) 그 픽셀은 "바뀌었다"로 봅니다. */
        private const val PIXEL_DIFF_THRESHOLD = 20

        /** 바뀐 픽셀이 이보다 적으면 노이즈로 보고 "이 프레임 사이엔 안 바뀌었다"로 칩니다. */
        private const val MIN_CHANGED_PIXELS = 4

        /** 장기판의 칸 수(교차점 기준 9x10). */
        private const val BOARD_COLS = 9
        private const val BOARD_ROWS = 10

        /**
         * 지금 움직이는 칸에서 이만큼(칸 단위) 안쪽이면 "같은 기물이 계속 움직이는
         * 중"으로 봅니다. 기물이 커지며 나타나는 애니메이션이라 바뀐 픽셀 뭉치의 무게중심이
         * 살짝 옆 칸으로 번질 수 있어 여유를 둡니다.
         */
        private const val CELL_TOLERANCE = 1

        /**
         * 움직이는 칸이 넘어가는 경계마다, 그 경계 직전 프레임(=그 칸이 마지막으로 바뀐
         * 채로 남아있던 프레임)의 인덱스를 고릅니다. 처음과 마지막 프레임은 항상
         * 포함합니다(각각 시작 국면·영상이 끝난 시점의 국면).
         *
         * 안드로이드 프레임워크 없이도 테스트할 수 있게 companion object 에 둡니다.
         *
         * @param diffCells `diffCells[k]` 는 프레임 k와 프레임 k+1 사이 바뀐 픽셀들의
         *   무게중심이 속한 칸(바뀐 픽셀이 없으면 null). 그래서 크기는 "프레임 수 - 1" 입니다.
         */
        internal fun pickSettledFrameIndices(
            diffCells: List<CellCoordinate?>,
            cellTolerance: Int = CELL_TOLERANCE
        ): List<Int> {
            if (diffCells.isEmpty()) return listOf(0)

            val settled = linkedSetOf(0)
            var currentCell: CellCoordinate? = null
            var lastFrameInEvent: Int? = null

            for (k in diffCells.indices) {
                val cell = diffCells[k] ?: continue

                val continuesEvent = currentCell?.isNear(cell, cellTolerance) ?: true
                if (!continuesEvent) {
                    lastFrameInEvent?.let { settled.add(it) }
                }

                // 자리를 최신 칸으로 갱신합니다 - 애니메이션이 진행되며 무게중심이
                // 서서히 옮겨가도(예: 대각선으로 걸친 경우) 계속 같은 기물로 따라갑니다.
                currentCell = cell
                lastFrameInEvent = k + 1
            }

            settled.add(diffCells.size) // 마지막 프레임 인덱스 (diffCells.size == frames.lastIndex)
            return settled.sorted()
        }
    }

    /**
     * @return 정지 구간마다 하나씩, 시간순으로 정렬된 원본 해상도 프레임. 실패하면 빈 목록.
     */
    suspend fun findStillFrames(videoUri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (durationMs == null || durationMs <= 0) {
                Log.w(TAG, "Could not read video duration")
                return@withContext emptyList()
            }

            val sampleTimesMs = buildSampleTimes(durationMs)
            val boardRect = detectBoardRect(retriever, sampleTimesMs.first())
            val diffCells = scanDiffCells(retriever, sampleTimesMs, boardRect)
            val settledIndices = pickSettledFrameIndices(diffCells)
            Log.d(
                TAG,
                "Sampled ${sampleTimesMs.size} frames, picked ${settledIndices.size} settled frames " +
                    "(board rect: $boardRect)"
            )
            Log.d(
                TAG,
                "Settled frame times(ms): " + settledIndices.joinToString(", ") { sampleTimesMs[it].toString() }
            )
            logCellTransitions(diffCells)

            settledIndices.mapNotNull { index ->
                retriever.getFrameAtTime(
                    sampleTimesMs[index] * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read video", e)
            emptyList()
        } finally {
            retriever.release()
        }
    }

    private fun buildSampleTimes(durationMs: Long): List<Long> {
        val naiveCount = durationMs / SCAN_INTERVAL_MS + 1
        val step = if (naiveCount > MAX_SCAN_FRAMES) {
            maxOf(SCAN_INTERVAL_MS, durationMs / MAX_SCAN_FRAMES)
        } else {
            SCAN_INTERVAL_MS
        }
        return (0..durationMs step step).toList()
    }

    /**
     * 판 경계를 첫 프레임에서 한 번만 찾습니다 - 같은 영상 안에서 판이 움직일 리 없으니,
     * 매 프레임 다시 찾을 필요가 없습니다. 실패하면 화면 전체를 판으로 취급합니다.
     */
    private fun detectBoardRect(retriever: MediaMetadataRetriever, firstTimeMs: Long): IntRect {
        val first = retriever.getScaledFrameAtTime(
            firstTimeMs * 1000,
            MediaMetadataRetriever.OPTION_CLOSEST,
            SCAN_WIDTH,
            SCAN_HEIGHT
        )
        if (first == null) return IntRect(0, 0, SCAN_WIDTH, SCAN_HEIGHT)

        val rect = boardDetector.detectBoard(first) ?: boardDetector.estimateBoardRegion(first)
        first.recycle()
        return IntRect(rect.left, rect.top, rect.right, rect.bottom)
            .clampTo(SCAN_WIDTH, SCAN_HEIGHT)
    }

    private fun scanDiffCells(
        retriever: MediaMetadataRetriever,
        sampleTimesMs: List<Long>,
        boardRect: IntRect
    ): List<CellCoordinate?> {
        val diffCells = mutableListOf<CellCoordinate?>()
        var previous: FloatArray? = null

        for (timeMs in sampleTimesMs) {
            val thumb = retriever.getScaledFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST,
                SCAN_WIDTH,
                SCAN_HEIGHT
            )
            val gray = thumb?.let { toGrayFloatArray(it, boardRect) }
            thumb?.recycle()

            if (previous != null && gray != null) {
                diffCells.add(diffCentroidCell(previous, gray, boardRect))
            }
            if (gray != null) previous = gray
        }

        return diffCells
    }

    /**
     * 진단용 - 바뀐 칸이 어느 프레임에서 어디로 넘어갔는지 죽 나열합니다. 특정 수가 안
     * 잡히는 문제를 볼 때, 그 수의 도착 칸이 아예 여기 없다면 정지 프레임을 못 찾은
     * 것이고(원인 A), 있는데 인식이 틀렸다면 인식 자체 문제입니다(원인 B).
     */
    private fun logCellTransitions(diffCells: List<CellCoordinate?>) {
        val entries = diffCells.mapIndexedNotNull { k, cell -> cell?.let { "$k:$it" } }
        Log.d(TAG, "Diff cells (frame:col,row): ${entries.joinToString(" ")}")
    }

    /** [rect] 안쪽만 잘라 그레이스케일 밝기값을 뽑습니다 - 판 밖 UI 움직임은 걸러냅니다. */
    private fun toGrayFloatArray(bitmap: Bitmap, rect: IntRect): FloatArray {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0 || h <= 0) return FloatArray(0)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, rect.left, rect.top, w, h)
        return FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (r + g + b) / 3f
        }
    }

    /**
     * 밝기 차이가 [PIXEL_DIFF_THRESHOLD] 를 넘는 픽셀들의 무게중심을 판의 칸 좌표로
     * 바꿉니다. 노이즈 수준이면(바뀐 픽셀이 거의 없으면) null.
     */
    private fun diffCentroidCell(a: FloatArray, b: FloatArray, boardRect: IntRect): CellCoordinate? {
        val width = boardRect.width()
        if (a.size != b.size || width <= 0) return null

        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        for (i in a.indices) {
            if (abs(a[i] - b[i]) > PIXEL_DIFF_THRESHOLD) {
                sumX += i % width
                sumY += i / width
                count++
            }
        }

        if (count < MIN_CHANGED_PIXELS) return null

        val centroidX = sumX / count
        val centroidY = sumY / count
        val col = ((centroidX / boardRect.width()) * BOARD_COLS).toInt().coerceIn(0, BOARD_COLS - 1)
        val row = ((centroidY / boardRect.height()) * BOARD_ROWS).toInt().coerceIn(0, BOARD_ROWS - 1)
        return CellCoordinate(col, row)
    }
}

/** 판 위 한 칸의 좌표(열/행, 0-based). */
internal data class CellCoordinate(val col: Int, val row: Int) {
    /** [other] 가 이 칸에서 [tolerance] 칸 이내(체비셰프 거리)이면 true. */
    fun isNear(other: CellCoordinate, tolerance: Int): Boolean =
        abs(col - other.col) <= tolerance && abs(row - other.row) <= tolerance
}

/**
 * 이 파일만의 단순 사각형. `android.graphics.Rect` 는 안드로이드 프레임워크가 있어야
 * 동작해서(순수 JVM 단위 테스트에서 못 씀) 대신 씁니다.
 */
internal data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left
    fun height() = bottom - top

    fun clampTo(maxWidth: Int, maxHeight: Int) = IntRect(
        left.coerceIn(0, maxWidth),
        top.coerceIn(0, maxHeight),
        right.coerceIn(0, maxWidth),
        bottom.coerceIn(0, maxHeight)
    )
}
