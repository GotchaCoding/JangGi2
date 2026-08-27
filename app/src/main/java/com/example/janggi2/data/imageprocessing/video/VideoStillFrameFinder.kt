package com.example.janggi2.data.imageprocessing.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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
         * 1차로 뼈대(LIS로 걸러낸 확정 수순)를 만든 뒤, 그 사이에 빠진 수 번호가 있으면
         * 그 구간만 이 간격으로 다시 훑습니다. 1차 간격([SCAN_INTERVAL_MS])보다 훨씬
         * 촘촘합니다 - 빈 구간은 대개 몇 개 안 되고 폭도 좁아서, 전체를 다시 훑는
         * 것보다 훨씬 저렴하게 훨씬 정밀히 찾을 수 있습니다.
         */
        private const val GAP_FILL_STEP_MS = 2L

        /** 이보다 넓은 구간은 재스캔하지 않습니다 - 처리 시간이 끝없이 늘어나는 것을 막습니다. */
        private const val GAP_FILL_MAX_WINDOW_MS = 3000L

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

        /**
         * 시간 순서를 유지한 채, 값이 계속 증가하는 가장 긴 부분열(최장 증가 부분열,
         * LIS)만 남깁니다. 스크러빙이 빠르거나 화질이 나쁜 영상에서는 OCR이 같은 값을
         * 시간상 여러 지점에서 중복으로 읽거나(예: "6"이 세 번 찍힘) 순간적으로 엉뚱한
         * 값을 잘못 읽어내는 경우가 있는데, 이런 값들이 섞인 채로 값 기준 정렬만 하면
         * 실제 영상 순서와 안 맞는 목록이 됩니다. LIS는 전체를 보고 시간순으로 값이
         * 계속 늘어나는 가장 긴 사슬을 고르므로, 진짜 수순처럼 촘촘하게 이어지는 긴
         * 사슬이 짧은 오독/중복 사슬을 항상 이깁니다.
         */
        internal fun dropOutOfOrderPlateaus(plateaus: List<Plateau>): List<Plateau> {
            val n = plateaus.size
            if (n == 0) return emptyList()
            val chainLength = IntArray(n) { 1 }
            val previous = IntArray(n) { -1 }
            for (i in 1 until n) {
                for (j in 0 until i) {
                    if (plateaus[j].value < plateaus[i].value && chainLength[j] + 1 > chainLength[i]) {
                        chainLength[i] = chainLength[j] + 1
                        previous[i] = j
                    }
                }
            }
            var bestEnd = 0
            for (i in 1 until n) {
                if (chainLength[i] > chainLength[bestEnd]) bestEnd = i
            }
            val chain = mutableListOf<Plateau>()
            var cur = bestEnd
            while (cur != -1) {
                chain.add(plateaus[cur])
                cur = previous[cur]
            }
            return chain.asReversed()
        }
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

            // 전체 수(분모)는 대국 내내 하나로 고정입니다. 가장 흔하게 읽힌 분모를
            // "진짜 전체 수"로 잡고, 그와 다른 분모로 읽힌 값은 애초에 리플레이
            // 카운터가 아니라 화면의 다른 텍스트를 잘못 짝지은 것으로 보고 버립니다 -
            // 안 그러면 대국이 끝난 뒤 화면(결과 요약 등)에서 우연히 숫자 두 개가
            // 찍혀 정규식에 걸리면, 실제 마지막 수보다 큰 값으로 오인해 존재하지도
            // 않는 수순을 뒤에 억지로 끼워 맞추려 듭니다.
            val expectedTotal = readings.mapNotNull { it?.second }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            val currents = readings.map { reading ->
                if (reading != null && reading.second == expectedTotal) reading.first else null
            }

            val rawPlateaus = groupPlateaus(currents)
            val corrected = correctFirstMoveLabel(rawPlateaus)
            // LIS 로 걸러낸 결과는 이미 시간순(=값도 자연히 오름차순)이라 값 기준으로
            // 다시 정렬하지 않습니다 - 정렬을 하면 중복/오독으로 시간순이 깨진 원본이
            // 뒤섞여 나올 위험이 있습니다.
            val plateaus = dropOutOfOrderPlateaus(corrected)

            // 뼈대(확정된 수순) - 시간순으로 정렬된 (수 번호, 시각) 쌍.
            val skeleton = pickRepresentativeIndices(plateaus)
                .map { (value, sampleIdx) -> value to sampleTimesMs[sampleIdx] }
            val filled = fillGaps(retriever, skeleton, expectedTotal)
            val representatives = filled.sortedBy { it.first }

            Log.d(
                TAG,
                "Sampled ${sampleTimesMs.size} frames, ${plateaus.size} plateaus picked " +
                    "(${filled.size - skeleton.size} gap-filled): " +
                    representatives.joinToString(", ") { (position, timeMs) -> "$position@${timeMs}ms" }
            )

            representatives.mapNotNull { (position, timeMs) ->
                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000,
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

    /**
     * 뼈대(확정된 수순) 사이에 빠진 수 번호가 있으면, 그 구간(양 끝 시각 사이)만 촘촘히
     * 재스캔해서 채워 넣습니다. 수 번호는 대략 균등한 속도로 흘러간다는 전제 하에,
     * 1차 훑기(10ms 간격)가 놓친 자리를 2차로 훨씬 촘촘하게(2ms 간격) 다시 찾습니다.
     */
    private suspend fun fillGaps(
        retriever: MediaMetadataRetriever,
        skeleton: List<Pair<Int, Long>>,
        expectedTotal: Int?
    ): List<Pair<Int, Long>> {
        if (skeleton.size < 2) return skeleton
        val result = mutableListOf(skeleton.first())
        for (i in 1 until skeleton.size) {
            val (prevValue, prevTime) = skeleton[i - 1]
            val (nextValue, nextTime) = skeleton[i]
            if (nextValue - prevValue > 1 && nextTime > prevTime &&
                nextTime - prevTime <= GAP_FILL_MAX_WINDOW_MS
            ) {
                result.addAll(
                    searchGapForMissingValues(retriever, prevValue, prevTime, nextValue, nextTime, expectedTotal)
                )
            }
            result.add(skeleton[i])
        }
        return result
    }

    /**
     * [prevTime]~[nextTime] 사이를 [GAP_FILL_STEP_MS] 간격으로 훑으며,
     * [prevValue]와 [nextValue] 사이에 빠진 값들을 찾는 대로 (값, 처음 나타난 시각)
     * 으로 모읍니다. 값 하나당 여러 번 나타나도 첫 등장만 씁니다 - 나머지 대표
     * 프레임과 같은 정책입니다.
     */
    private suspend fun searchGapForMissingValues(
        retriever: MediaMetadataRetriever,
        prevValue: Int,
        prevTime: Long,
        nextValue: Int,
        nextTime: Long,
        expectedTotal: Int?
    ): List<Pair<Int, Long>> {
        val targets = (prevValue + 1 until nextValue).toMutableSet()
        val found = mutableMapOf<Int, Long>()
        val dumpTag = "gap_${prevValue}to${nextValue}"
        var t = prevTime + GAP_FILL_STEP_MS
        while (t < nextTime && targets.isNotEmpty()) {
            val rawReading = readPositionAt(retriever, t, dumpTag)
            // 갭 채우기 중에도 코드 스캔과 같은 이유로 분모가 다른 값은 리플레이
            // 카운터가 아닌 것으로 보고 버립니다.
            val reading = rawReading?.takeIf { it.second == expectedTotal }?.first
            // TEMP DEBUG: 목표값이 안 나올 때 이 구간에서 실제로 뭐가 읽혔는지 보려고
            // 매 표본을 그대로 기록합니다. 확인 끝나면 지울 코드입니다.
            Log.d(TAG, "  gap-fill sample t=${t}ms targets=$targets read=$reading raw=$rawReading")
            if (reading != null && reading in targets) {
                found[reading] = t
                targets.remove(reading)
            }
            t += GAP_FILL_STEP_MS
        }
        return found.entries.sortedBy { it.value }.map { it.key to it.value }
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
    private suspend fun readPositionAt(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        dumpTag: String? = null
    ): Pair<Int, Int>? {
        val full = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val counterCrop = cropCounterRegion(full)
        full.recycle()

        // TEMP DEBUG: 갭 채우기 구간에서 실제로 OCR이 본 카운터 크롭 이미지를 눈으로
        // 확인하려고 저장합니다. 확인 끝나면 지울 코드입니다.
        if (dumpTag != null) dumpCounterCrop(dumpTag, timeMs, counterCrop)

        val position = replayPositionReader.readPosition(counterCrop) { eroded ->
            // TEMP DEBUG: 침식 처리 후 실제 OCR 입력 이미지를 눈으로 확인하려고
            // 저장합니다. 확인 끝나면 지울 코드입니다.
            if (dumpTag != null) dumpCounterCrop("${dumpTag}_eroded", timeMs, eroded)
        }

        counterCrop.recycle()
        return position
    }

    /** TEMP DEBUG: 지울 코드. */
    private fun dumpCounterCrop(dumpTag: String, timeMs: Long, bitmap: Bitmap) {
        try {
            val base = context.getExternalFilesDir(null) ?: return
            val dir = File(base, "video_import_gapfill_debug")
            dir.mkdirs()
            val target = File(dir, "${dumpTag}_${timeMs}ms.png")
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "gap-fill debug dump failed: $dumpTag@$timeMs", e)
        }
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
