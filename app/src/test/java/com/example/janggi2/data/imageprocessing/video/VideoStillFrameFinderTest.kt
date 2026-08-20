package com.example.janggi2.data.imageprocessing.video

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [VideoStillFrameFinder] 의 순수 로직(고원 묶기·대표 표본 고르기·1수 표시 보정)만 따로
 * 검증합니다. 실제 디코딩·OCR([VideoStillFrameFinder.findStillFrames])은 안드로이드
 * 프레임워크가 있어야 해서 JVM 단위 테스트로는 못 돌리고 실기기 확인으로 대신합니다.
 */
class VideoStillFrameFinderTest {

    // --- groupPlateaus: OCR로 읽은 값이 이어지는 구간(고원)으로 묶기 ---

    @Test
    fun `같은 값이 이어지는 표본들을 하나의 고원으로 묶는다`() {
        val currents = listOf(5, 5, 5, 6, 6)

        val plateaus = VideoStillFrameFinder.groupPlateaus(currents)

        assertEquals(
            listOf(Plateau(5, listOf(0, 1, 2)), Plateau(6, listOf(3, 4))),
            plateaus
        )
    }

    @Test
    fun `읽기 실패(null)는 고원을 끊지 않고 건너뛴다`() {
        val currents = listOf(5, null, 5, null, null, 6)

        val plateaus = VideoStillFrameFinder.groupPlateaus(currents)

        assertEquals(
            listOf(Plateau(5, listOf(0, 2)), Plateau(6, listOf(5))),
            plateaus
        )
    }

    @Test
    fun `목록이 전부 null 이면 고원이 없다`() {
        val plateaus = VideoStillFrameFinder.groupPlateaus(listOf(null, null))

        assertEquals(emptyList<Plateau>(), plateaus)
    }

    // --- correctFirstMoveLabel: 대기화면(0) 다음 "전체 수로 잘못 표시된 1수" 보정 ---
    // "전체 수" OCR 값은 신뢰하지 않고(실측상 자주 오독됨), 수 번호가 항상 증가한다는
    // 사실만으로 판단합니다.

    @Test
    fun `대기화면 바로 다음 고원이 그 다음 고원보다 크면(감소) 1수로 바로잡는다`() {
        val plateaus = listOf(
            Plateau(0, listOf(0, 1)),   // 대기 화면
            Plateau(45, listOf(2, 3)),  // 실제로는 1수인데 45로 잘못 표시됨
            Plateau(2, listOf(4, 5))    // 45 -> 2 로 감소 - 비정상
        )

        val corrected = VideoStillFrameFinder.correctFirstMoveLabel(plateaus)

        assertEquals(
            listOf(
                Plateau(0, listOf(0, 1)),
                Plateau(1, listOf(2, 3)),
                Plateau(2, listOf(4, 5))
            ),
            corrected
        )
    }

    @Test
    fun `대기화면이 없으면 보정하지 않는다`() {
        val plateaus = listOf(Plateau(2, listOf(0)), Plateau(3, listOf(1)))

        val corrected = VideoStillFrameFinder.correctFirstMoveLabel(plateaus)

        assertEquals(plateaus, corrected)
    }

    @Test
    fun `대기화면 다음 고원이 이미 정상적으로 증가하면 보정하지 않는다`() {
        val plateaus = listOf(
            Plateau(0, listOf(0)),
            Plateau(1, listOf(1)),
            Plateau(2, listOf(2))
        )

        val corrected = VideoStillFrameFinder.correctFirstMoveLabel(plateaus)

        assertEquals(plateaus, corrected)
    }

    // --- pickRepresentativeIndices: 고원마다 (수 번호, 대표 표본) 뽑기 ---

    @Test
    fun `고원마다 그 값이 처음 나타난(수 번호가 막 바뀐) 표본을 대표로 뽑는다`() {
        val plateaus = listOf(
            Plateau(1, listOf(0, 1, 2)),
            Plateau(2, listOf(3, 4))
        )

        val representatives = VideoStillFrameFinder.pickRepresentativeIndices(plateaus)

        assertEquals(listOf(1 to 0, 2 to 3), representatives)
    }
}
