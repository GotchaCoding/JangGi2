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
         * 대표 프레임을 최종적으로 다시 가져올 때(아래 [fetchVerifiedFrame]) 검증에
         * 실패하면 시도해볼 주변 시각 오프셋들입니다. 0을 먼저 시도하고, 실패하면
         * 점점 더 먼 지점을 좌우로 번갈아 찾습니다.
         */
        private val VERIFY_OFFSETS_MS = listOf(0L, -2L, 2L, -4L, 4L, -6L, 6L, -8L, 8L, -10L, 10L)

        /**
         * 서로 다른 시각에서 몇 번이나 같은 값을 읽어야 그 프레임을 인정할지. 한 번만
         * 요구하면 그 지점에서 일관되게 반복되는 오독(예: 6/9 헷갈림)을 못 거릅니다.
         */
        private const val REQUIRED_AGREEMENTS = 2

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
     * 영상의 진짜 마지막 프레임을 수 번호 OCR과 무관하게 그대로 가져옵니다.
     *
     * **마지막 수는 [findStillFrames]가 주는 프레임만으로는 안전하게 재구성할 수
     * 없습니다.** 이 클래스 전체의 원칙은 "방금 둔 쪽 자신의 위치는 안 믿고, 항상
     * 다음 수 프레임에서 그때는 안 움직인 상대 진영만 믿는다"인데, 마지막 수는
     * 그 "다음 수"가 아예 없어서 상대 진영을 비춰줄 체크포인트가 없습니다. 카운터가
     * 막 마지막 번호로 바뀐 그 프레임을 그대로 쓰면, 방금 둔 기물이 아직 착수
     * 애니메이션 중일 수 있어 다른 수들과 달리 이 원칙이 깨집니다. 영상 끝(더 이상
     * 아무 일도 안 일어나는 시점)은 그 반대로 "미래에서 본 안정된 모습"을 보장하므로,
     * 마지막 수의 체크포인트로 안전하게 쓸 수 있습니다.
     *
     * @return 원본 해상도의 `ARGB_8888` 비트맵. 실패하면 null.
     */
    suspend fun findFinalFrame(videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (durationMs == null || durationMs <= 0) {
                Log.w(TAG, "Could not read video duration for final frame")
                return@withContext null
            }
            // durationMs 그 자체로 seek하면 마지막 프레임을 넘어가 아무것도 못 가져오는
            // 기기가 있어, 살짝 앞에서 잡습니다.
            val targetMs = (durationMs - 1).coerceAtLeast(0)
            val raw = retriever.getFrameAtTime(targetMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return@withContext null
            // readPositionAt과 같은 이유로 소프트웨어 비트맵으로 확정합니다.
            val software = raw.copy(Bitmap.Config.ARGB_8888, false)
            raw.recycle()
            software
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read final frame", e)
            null
        } finally {
            retriever.release()
        }
    }

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
            val filled = fillGaps(retriever, skeleton)
            // 값 기준 정렬은 시간순이 이미 깨진 항목(아래 reconcileChronology가 다루는
            // 경우)을 숨길 수 있어 위험하다고 위에서 경고했지만, gap-fill로 새로 채운
            // 항목들은 원래 skeleton 순서에 섞여 있지 않아 값 기준 정렬이 필요합니다.
            // 그래서 정렬 직후 reconcileChronology로 시간순 불변식을 다시 검증합니다.
            val (representatives, reconciledPositions) = reconcileChronology(retriever, filled.sortedBy { it.first })

            Log.d(
                TAG,
                "Sampled ${sampleTimesMs.size} frames, ${plateaus.size} plateaus picked " +
                    "(${filled.size - skeleton.size} gap-filled): " +
                    representatives.joinToString(", ") { (position, timeMs) -> "$position@${timeMs}ms" }
            )

            representatives.mapNotNull { (position, timeMs) ->
                fetchVerifiedFrame(retriever, position, timeMs, trustDominantMisread = position in reconciledPositions)
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
        skeleton: List<Pair<Int, Long>>
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
                    searchGapForMissingValues(retriever, prevValue, prevTime, nextValue, nextTime)
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
     *
     * **문자 그대로 못 찾아도 포기하지 않는 경우가 있습니다.** 실측에서 8과 10
     * 사이(=9만 있을 수 있는 구간)를 2ms 간격으로 촘촘히 훑었는데, 그 구간 내내
     * 화면은 분명 "9"인데 OCR은 매 표본 일관되게 "6"이라고 답했습니다(6과 9는
     * 180도 뒤집힌 모양이라 이 폰트에서 서로 헷갈리기 쉬운 숫자 쌍으로 보입니다) -
     * 같은 화면을 다시 훑어봤자 같은 오독이 반복될 뿐이라 문자 일치만으로는 영원히
     * 못 찾습니다. 그런데 [prevValue]와 [nextValue] 사이에 남은 목표가 딱 하나뿐일
     * 때는, 영상이 수순대로만 흘러간다는 사실 덕분에 이 구간에 나올 수 있는 값이
     * 그 하나뿐이라는 걸 이미 압니다 - 그래서 그 목표를 문자로는 못 찾았어도, 이
     * 구간을 과반 이상 지배하는 다른 값이 있으면(우연한 잡음이 아니라 이 구간
     * 내내 일관된 오독이라는 뜻) 그 값을 목표에 대한 자릿수 오독으로 보고
     * 받아들입니다.
     */
    private suspend fun searchGapForMissingValues(
        retriever: MediaMetadataRetriever,
        prevValue: Int,
        prevTime: Long,
        nextValue: Int,
        nextTime: Long
    ): List<Pair<Int, Long>> {
        val targets = (prevValue + 1 until nextValue).toMutableSet()
        val found = mutableMapOf<Int, Long>()
        val misreadCandidates = mutableListOf<Pair<Long, Int>>()
        val dumpTag = "gap_${prevValue}to${nextValue}"
        var t = prevTime + GAP_FILL_STEP_MS
        while (t < nextTime && targets.isNotEmpty()) {
            val rawReading = readPositionAt(retriever, t, dumpTag)
            // 전체 수(분모)는 대국 내내 하나로 고정이라, 이미 훨씬 많은 표본(전체
            // 프레임)으로 다수결을 거쳐 확정해둔 expectedTotal이 이 짧은 갭 안에서
            // 매번 새로 읽는 분모보다 훨씬 신뢰도가 높습니다. 그래서 여기서는 분모
            // 값을 다시 비교하지 않고, 이미 좁혀둔 targets 안에 드는 현재 수(분자)만
            // 확인합니다 - 분모 쪽 OCR 노이즈("149"->"49"/"65"/"67" 등)에 흔들리지
            // 않기 위해서입니다.
            val reading = rawReading?.first
            // TEMP DEBUG: 목표값이 안 나올 때 이 구간에서 실제로 뭐가 읽혔는지 보려고
            // 매 표본을 그대로 기록합니다. 확인 끝나면 지울 코드입니다.
            Log.d(TAG, "  gap-fill sample t=${t}ms targets=$targets read=$reading raw=$rawReading")
            if (reading != null && reading in targets) {
                found[reading] = t
                targets.remove(reading)
            } else if (reading != null && reading != prevValue) {
                misreadCandidates.add(t to reading)
            }
            t += GAP_FILL_STEP_MS
        }

        if (targets.size == 1) {
            val onlyTarget = targets.first()
            val dominant = misreadCandidates.groupingBy { it.second }.eachCount().maxByOrNull { it.value }
            if (dominant != null && dominant.value * 2 > misreadCandidates.size) {
                val firstTime = misreadCandidates.first { it.second == dominant.key }.first
                Log.w(
                    TAG,
                    "Position $onlyTarget: never read literally in (${prevTime}ms, ${nextTime}ms), but " +
                        "${dominant.key} dominated ${dominant.value}/${misreadCandidates.size} sample(s) - " +
                        "treating it as a digit misread of $onlyTarget at ${firstTime}ms"
                )
                found[onlyTarget] = firstTime
            }
        }

        return found.entries.sortedBy { it.value }.map { it.key to it.value }
    }

    /**
     * [representatives]는 수 번호(값) 기준으로 정렬돼 있습니다 - 정상이라면 이 순서는
     * 시각 순서와도 항상 일치해야 합니다(영상은 수순대로만 흘러가므로 수 번호가
     * 커질수록 시각도 늦어져야 합니다).
     *
     * **실측 실패 사례**: 8수 확정 뒤 9수 항목이, 실제로는 훨씬 이전(6수가 진짜로
     * 화면에 떠 있던) 시각을 가리키고 있었습니다 - 스캔 단계의 드문 오독 하나가
     * 값 기준으로는 8과 10 사이에 들어맞아 보여서(LIS가 8→9→10로 이어지는 사슬로
     * 착각) 뼈대에 그대로 끼어든 것으로 보입니다. 그 결과 "9수" 프레임을 다시
     * 가져와도([fetchVerifiedFrame]) 애초에 잘못된 그 시각 근방만 반복해서 보게 되어,
     * 화면에 진짜 떠 있는 "6"을 계속 확인하는 꼴이 됩니다 - 몇 번을 다시 읽어도
     * 소용없는 이유입니다.
     *
     * 그래서 각 항목의 시각이 **직전에 이미 이 순서로 확정한** 항목의 시각보다 뒤인지만
     * 확인합니다(다음 항목의 시각은 아직 검증 전이라 기준으로 못 씁니다 - 다음 항목
     * 자체가 틀렸다면 멀쩡한 현재 항목까지 덩달아 틀렸다고 오판하게 됩니다). 벗어나
     * 있으면 그 시각 자체를 못 믿는다는 뜻이므로 버리고, **그 수가 진짜로 존재할 수
     * 있는 구간**(직전 확정 시각 ~ 다음 항목의 시각)만 다시 훑어서
     * ([searchGapForMissingValues] 재사용) 올바른 시각으로 바꿔 끼웁니다 - 6수의
     * 화면이 다시 뽑혀 나오는 것을 원천적으로 막습니다. 재검색으로도 못 찾으면 이
     * 항목은 버립니다(틀린 시각을 쓰는 것보다 건너뛰는 쪽이 낫다는 기존 정책과 동일).
     *
     * @return 시간순으로 바로잡은 목록과, 그중 이렇게 재검색으로 **다시 끼워 넣은**
     *   수 번호의 집합. 재검색으로 되찾은 시각은 이미 이웃 시각 사이(그 수가 존재할
     *   수 있는 유일한 구간)를 촘촘히 훑어 확인한 것이라, 뒤이어 [fetchVerifiedFrame]이
     *   문자 그대로 다시 일치하는지 재차 요구하면 같은 자릿수 오독에 또 걸려 버려질 수
     *   있습니다 - 그 수 번호를 호출부에 알려줘서 그런 경우엔 더 관대하게 검증하도록
     *   합니다.
     */
    private suspend fun reconcileChronology(
        retriever: MediaMetadataRetriever,
        representatives: List<Pair<Int, Long>>
    ): Pair<List<Pair<Int, Long>>, Set<Int>> {
        val result = mutableListOf<Pair<Int, Long>>()
        val reconciled = mutableSetOf<Int>()
        for (i in representatives.indices) {
            val (value, time) = representatives[i]
            val prevTime = result.lastOrNull()?.second ?: -1L
            if (time > prevTime) {
                result.add(value to time)
                continue
            }

            val prevValue = result.lastOrNull()?.first ?: (value - 1)
            val nextTime = representatives.getOrNull(i + 1)?.second?.takeIf { it > prevTime }
                ?: (prevTime + GAP_FILL_MAX_WINDOW_MS)
            val nextValue = representatives.getOrNull(i + 1)?.first?.takeIf { it > value } ?: (value + 1)
            Log.w(
                TAG,
                "Position $value: timestamp ${time}ms is not after previous confirmed ${prevTime}ms, " +
                    "re-searching (${prevTime}ms, ${nextTime}ms) for it"
            )
            val recovered = searchGapForMissingValues(retriever, prevValue, prevTime, nextValue, nextTime)
                .firstOrNull { it.first == value }
            if (recovered != null) {
                result.add(value to recovered.second)
                reconciled.add(value)
            } else {
                Log.w(TAG, "Position $value: could not recover a chronologically valid frame, dropping")
            }
        }
        return result to reconciled
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
     * [timeMs]로 원본 해상도 프레임을 다시 가져와 [position]에 대응하는 [StillFrame]을
     * 만듭니다. 카운터를 스캔할 때 이미 이 [timeMs]에서 [position]을 읽어냈지만, 그때
     * 본 프레임은 이미 recycle 돼서 재사용할 수 없어 여기서 같은 시각으로 다시
     * seek합니다.
     *
     * **단발성 검증으로는 못 잡는 실패가 있었습니다.** 처음엔 재조회한 프레임의 카운터를
     * 한 번 더 읽어 [position]과 같은지만 확인했는데, 실측에서 "9수"라고 확정된
     * 시각을 다시 seek하면 화면은 분명 "6"인데 그 크롭을 다시 읽어도 OCR이 또 "9"라고
     * 답하는 사례가 나왔습니다(6과 9는 180도 뒤집힌 모양이라 이 폰트에서 서로 헷갈리기
     * 쉬운 숫자 쌍으로 보입니다) - 즉 검증에 쓰는 OCR 자체가 그 지점에서 일관되게
     * 틀리면, 같은 OCR로 한 번 더 확인해봤자 걸러지지 않습니다. 그래서 서로 **다른
     * 시각**(=디코더가 실제로 다시 디코딩하는 별개의 프레임)에서 [REQUIRED_AGREEMENTS]번
     * 독립적으로 같은 값을 읽어야만 인정합니다 - 진짜 그 수라면 여러 시점에서 안정적으로
     * 같은 값이 나오지만, 이번처럼 한 지점에서만 나는 오독은 다른 시각에서까지 우연히
     * 똑같이 반복될 가능성이 훨씬 낮습니다. [VERIFY_OFFSETS_MS]를 순서대로 시도하고,
     * 끝내 합의에 못 이르면 이 프레임은 포기합니다(그 수는 없는 것으로 처리되고, 호출부가
     * 이미 앞뒤 체크포인트로 건너뛰는 정책을 갖고 있어 안전합니다) - 틀린 내용을 쓰는
     * 것보다 건너뛰는 쪽이 낫습니다.
     *
     * 최종적으로 반환하는 비트맵은 `ARGB_8888`로 명시해 복사한 독립된 CPU 픽셀
     * 버퍼입니다 - 원본(`Config.HARDWARE`일 수 있음)은 바로 recycle하고, 이후 이
     * retriever로 어떤 호출을 더 하든 반환한 비트맵은 영향받지 않습니다.
     *
     * **문자 그대로 일치하지 않는 값을 신뢰해도 되는 건 [trustDominantMisread]가
     * 켜져 있을 때뿐입니다.** 평범한 위치(뼈대에서 곧바로, 시간순도 멀쩡하게 나온
     * 경우)는 이 근방에 다른 값이 나타날 수도 있으므로 [position]과 문자 그대로
     * 일치하는 표본을 [REQUIRED_AGREEMENTS]번 이상 요구합니다 - 못 채우면 그냥
     * 버립니다(틀린 내용을 쓰는 것보다 건너뛰는 쪽이 낫습니다).
     *
     * 반면 [trustDominantMisread]가 켜져 있다면(호출부가 [reconcileChronology]에서
     * "이 수가 존재할 수 있는 유일한 시간대"임을 이미 넓은 구간에 걸쳐 검증해뒀다는
     * 뜻), 문자 일치를 다시 요구하면 오히려 해롭습니다 - 실측에서 8과 10 사이(=9만
     * 있을 수 있는 구간)를 재검증하는 이 지점이 화면은 분명 "9"인데 OCR은 몇 번을
     * 다시 봐도 "6"이라고 일관되게 답한 사례가 나왔습니다(6과 9는 180도 뒤집힌
     * 모양이라 이 폰트에서 서로 헷갈리기 쉬운 숫자 쌍으로 보입니다) - 문자 일치를
     * 계속 요구하면 검증이 영원히 실패합니다. 그래서 이때는 오프셋들을 전부 훑어
     * **가장 많이 나온 값**(문자 그대로 [position]이 아니어도)을 [REQUIRED_AGREEMENTS]번
     * 이상 & 과반 조건으로 신뢰합니다.
     */
    private suspend fun fetchVerifiedFrame(
        retriever: MediaMetadataRetriever,
        position: Int,
        timeMs: Long,
        trustDominantMisread: Boolean
    ): StillFrame? {
        val readings = VERIFY_OFFSETS_MS.mapNotNull { offset ->
            val t = timeMs + offset
            if (t < 0) return@mapNotNull null
            val bitmap = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return@mapNotNull null
            val reading = readCounterFrom(bitmap)?.first
            bitmap.recycle()
            t to reading
        }

        val literalTimes = readings.filter { it.second == position }.map { it.first }
        val acceptedTime = if (literalTimes.size >= REQUIRED_AGREEMENTS) {
            literalTimes.first()
        } else if (trustDominantMisread) {
            val nonNull = readings.mapNotNull { it.second }
            val dominant = nonNull.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (dominant != null && dominant.value >= REQUIRED_AGREEMENTS && dominant.value * 2 > nonNull.size) {
                Log.w(
                    TAG,
                    "Position $position: literal match never reached $REQUIRED_AGREEMENTS near ${timeMs}ms, " +
                        "but dominant reading ${dominant.key} (${dominant.value}/${nonNull.size}) did - " +
                        "trusting the timeline slot and treating it as a digit misread"
                )
                readings.first { it.second == dominant.key }.first
            } else {
                null
            }
        } else {
            null
        }

        if (acceptedTime == null) {
            Log.w(
                TAG,
                "Position $position: only ${literalTimes.size}/$REQUIRED_AGREEMENTS agreeing sample(s) " +
                    "near ${timeMs}ms, dropping"
            )
            return null
        }

        val bitmap = retriever.getFrameAtTime(acceptedTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: return null
        val independent = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        // TEMP DEBUG: 검증을 통과한 바로 그 순간의 전체 프레임을 그대로 저장해서,
        // 나중에 ImportBoardFromVideoUseCase가 저장하는 것과 픽셀 단위로 같은지
        // 비교합니다. 원인 확인 끝나면 지울 코드입니다.
        dumpVerifiedFrame(position, independent)
        bitmap.recycle()
        return StillFrame(independent, position)
    }

    /** TEMP DEBUG: 지울 코드. */
    private fun dumpVerifiedFrame(position: Int, bitmap: Bitmap) {
        try {
            val base = context.getExternalFilesDir(null) ?: return
            val dir = File(base, "video_import_verify_debug")
            dir.mkdirs()
            val target = File(dir, "%03d_verified.png".format(position))
            FileOutputStream(target).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        } catch (e: Exception) {
            Log.w(TAG, "verify debug dump failed: $position", e)
        }
    }

    private suspend fun readCounterFrom(bitmap: Bitmap): Pair<Int, Int>? {
        val counterCrop = cropCounterRegion(bitmap)
        val reading = replayPositionReader.readPosition(counterCrop)
        counterCrop.recycle()
        return reading
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
