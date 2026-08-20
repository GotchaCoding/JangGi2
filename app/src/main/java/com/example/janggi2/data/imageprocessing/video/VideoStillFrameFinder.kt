package com.example.janggi2.data.imageprocessing.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 동영상에서 기보 재구성에 쓸 정지 프레임을 고릅니다.
 *
 * 기물이 다 멈췄는지를 픽셀로 추측하는 대신, 화면에 찍힌 "N/전체" 수 번호를
 * OCR([ReplayPositionReader])로 직접 읽습니다. 어떤 수 번호가 화면에 떠 있는 동안은
 * 표본 여러 개가 같은 값을 읽어내는데(그 구간을 "고원(plateau)"이라 부릅니다), 그 값이
 * **처음 나타난 시점**(=수 번호가 막 바뀐 타이밍)의 표본을 그 수의 대표 프레임으로
 * 씁니다.
 *
 * 착수 완료 여부를 직접 판정하지 않는 게 핵심입니다: 어차피 뒤([ImportBoardFromVideoUseCase])
 * 에서 이 프레임의 **상대 진영**(방금 안 움직인 쪽) 위치만 신뢰해서 쓰므로, "이 수를 둔
 * 기물이 완전히 멈췄는가"는 몰라도 됩니다.
 */
@Singleton
class VideoStillFrameFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val replayPositionReader: ReplayPositionReader
) {
    companion object {
        private const val TAG = "VideoStillFrameFinder"

        /** 훑는 단계의 표본 간격. */
        private const val SCAN_INTERVAL_MS = 10L

        /** 훑는 프레임 수 상한 - 긴 영상에서도 처리 시간이 무한정 늘지 않도록 간격을 늘립니다. */
        private const val MAX_SCAN_FRAMES = 800

        /**
         * 수 번호 표시 영역을 화면 비율로 자릅니다(기기·해상도가 달라도 비율은 비슷할
         * 것으로 가정). 실측 샘플 영상(1080x2340)에서 "14 / 49" 표시가 대략 이 안에
         * 들어오는 걸 확인하고, 여유를 두어 넓혔습니다.
         */
        private const val COUNTER_LEFT_RATIO = 0.35
        private const val COUNTER_TOP_RATIO = 0.54
        private const val COUNTER_RIGHT_RATIO = 0.65
        private const val COUNTER_BOTTOM_RATIO = 0.65

        /**
         * OCR로 읽은 "지금 수" 값 목록에서, 같은 값이 이어지는 구간(고원)을 순서대로
         * 묶습니다. 읽기 실패(null)는 그 사이에 끼어도 구간을 끊지 않습니다 - 순간적인
         * 오독으로 보고 건너뜁니다.
         */
        internal fun groupPlateaus(currents: List<Int?>): List<Plateau> {
            val result = mutableListOf<Plateau>()
            var value: Int? = null
            var indices = mutableListOf<Int>()

            for (i in currents.indices) {
                val v = currents[i] ?: continue
                if (v != value) {
                    if (value != null) result.add(Plateau(value, indices))
                    value = v
                    indices = mutableListOf()
                }
                indices.add(i)
            }
            if (value != null) result.add(Plateau(value, indices))
            return result
        }

        /**
         * 이 리플레이 화면의 특성: 대기 화면("0/전체")에서 "다음"을 처음 누르면(실제로는
         * 1수인데) 화면에 "1" 대신 **전체 수와 같은 값**이 찍힙니다(예: 전체 45수짜리
         * 영상에서 1수째가 "45/45"로 표시됨).
         *
         * "전체 수"를 OCR로 따로 읽어서 대조하는 방식은 못 씁니다 - 실측 결과 "전체
         * 수" 쪽 숫자가 자주 다른 자릿수로 오독되는 게(예: "45"를 "145"로) 확인돼서,
         * 그 값 자체를 신뢰할 수 없었습니다. 대신 **수 번호는 항상 증가한다**는 사실만
         * 이용합니다: 대기 화면(0) 바로 다음 고원의 값이 그 다음 고원보다 오히려 **더
         * 크면**(정상이라면 항상 작아야 함 - 1수는 2수보다 작아야 하니까) 그건 "1"이
         * 아니라 전체 수로 잘못 찍힌 것으로 보고 1로 바로잡습니다.
         */
        internal fun correctFirstMoveLabel(plateaus: List<Plateau>): List<Plateau> {
            val idleIndex = plateaus.indexOfFirst { it.value == 0 }
            if (idleIndex == -1 || idleIndex + 2 >= plateaus.size) return plateaus
            val mislabeled = plateaus[idleIndex + 1]
            val afterNext = plateaus[idleIndex + 2]
            if (mislabeled.value <= afterNext.value) return plateaus

            return plateaus.mapIndexed { i, p -> if (i == idleIndex + 1) p.copy(value = 1) else p }
        }

        /**
         * 고원마다 (수 번호, 대표 표본 인덱스) 를 뽑습니다. 그 수가 변경되기 전
         * 중간 지점이 아니라, **그 값이 처음 나타난 시점**(=OCR 검출 숫자가 막 바뀐
         * 타이밍)을 대표로 씁니다.
         */
        internal fun pickRepresentativeIndices(plateaus: List<Plateau>): List<Pair<Int, Int>> =
            plateaus.map { it.value to it.sampleIndices.first() }
    }

    /** 정지 프레임 하나 - OCR로 읽은(그리고 필요시 보정한) 실제 수 번호가 붙어 있습니다. */
    data class StillFrame(val bitmap: Bitmap, val position: Int)

    /**
     * @return 수 번호마다 하나씩, 오름차순으로 정렬된 원본 해상도 프레임. 실패하면 빈 목록.
     */
    suspend fun findStillFrames(videoUri: Uri): List<StillFrame> = withContext(Dispatchers.IO) {
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
            val readings = sampleTimesMs.map { timeMs -> readPositionAt(retriever, timeMs) }
            val currents = readings.map { it?.first }

            val rawPlateaus = groupPlateaus(currents)
            val plateaus = correctFirstMoveLabel(rawPlateaus)
            val representatives = pickRepresentativeIndices(plateaus).sortedBy { it.first }

            Log.d(
                TAG,
                "Sampled ${sampleTimesMs.size} frames, ${plateaus.size} plateaus picked: " +
                    representatives.joinToString(", ") { (position, sampleIdx) ->
                        "$position@${sampleTimesMs[sampleIdx]}ms"
                    }
            )

            representatives.mapNotNull { (position, sampleIdx) ->
                val bitmap = retriever.getFrameAtTime(
                    sampleTimesMs[sampleIdx] * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: return@mapNotNull null
                StillFrame(bitmap, position)
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
     * 표본 시각의 원본 해상도 프레임에서 수 번호 표시 영역만 잘라 OCR을 돌립니다.
     * 작은 글자라 썸네일 해상도로는 못 읽으므로 원본 해상도가 필요합니다 - 그만큼
     * 표본 수가 많은 긴 영상에서는 느려질 수 있습니다.
     */
    private suspend fun readPositionAt(retriever: MediaMetadataRetriever, timeMs: Long): Pair<Int, Int>? {
        val full = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val counterCrop = cropCounterRegion(full)
        full.recycle()

        val position = replayPositionReader.readPosition(counterCrop)
        counterCrop.recycle()
        return position
    }

    /** 화면 비율로 수 번호 표시 영역만 잘라냅니다. */
    private fun cropCounterRegion(bitmap: Bitmap): Bitmap {
        val left = (bitmap.width * COUNTER_LEFT_RATIO).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * COUNTER_TOP_RATIO).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * COUNTER_RIGHT_RATIO).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * COUNTER_BOTTOM_RATIO).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}

/** OCR로 읽은 "지금 수" 값이 표본 하나 동안 계속 이어지는 구간. */
internal data class Plateau(val value: Int, val sampleIndices: List<Int>)
