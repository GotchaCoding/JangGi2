package com.example.janggi2.data.imageprocessing.video

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [VideoStillFrameFinder.pickSettledFrameIndices] 만 따로 검증합니다 - 실제 디코딩·판
 * 탐지([VideoStillFrameFinder.findStillFrames])는 안드로이드 프레임워크가 있어야 해서
 * JVM 단위 테스트로는 못 돌리고 실기기 확인으로 대신합니다. 순수 로직은 companion
 * object 에 있어 인스턴스(= Context) 없이도 테스트할 수 있습니다.
 */
class VideoStillFrameFinderTest {

    private fun cell(col: Int, row: Int) = CellCoordinate(col, row)

    @Test
    fun `같은 칸이 계속 바뀌는 동안은 한 사건으로 묶고, 칸이 넘어가면 그 직전 프레임을 고른다`() {
        // 칸(0,0) 근처가 프레임 0->3 사이 계속 바뀌다가, 프레임 3->4 에서 칸(8,9)로 넘어갑니다.
        val diffCells = listOf(
            cell(0, 0), // 프레임0->1
            cell(0, 0), // 프레임1->2
            cell(1, 0), // 프레임2->3 - 허용 범위(1칸) 안이라 같은 사건
            cell(8, 9), // 프레임3->4 - 칸이 확실히 넘어감
            cell(8, 9)  // 프레임4->5
        )

        val picked = VideoStillFrameFinder.pickSettledFrameIndices(diffCells, cellTolerance = 1)

        // 0(시작) · 3(칸(0,0) 쪽의 마지막 정지) · 5(영상 끝)
        assertEquals(listOf(0, 3, 5), picked)
    }

    @Test
    fun `바뀐 게 없으면(null) 그 프레임은 건너뛰고 사건을 끊지 않는다`() {
        val diffCells = listOf(
            cell(0, 0),
            null,
            null,
            cell(8, 9)
        )

        val picked = VideoStillFrameFinder.pickSettledFrameIndices(diffCells, cellTolerance = 1)

        // null 두 개는 그냥 지나가고, 칸(0,0)에서 칸(8,9)로 바로 넘어간 것으로 봅니다.
        assertEquals(listOf(0, 1, 4), picked)
    }

    @Test
    fun `내내 한 칸만 바뀌면 처음과 끝만 고른다`() {
        val diffCells = listOf(cell(4, 4), cell(4, 4), cell(4, 4))

        val picked = VideoStillFrameFinder.pickSettledFrameIndices(diffCells, cellTolerance = 1)

        assertEquals(listOf(0, 3), picked)
    }

    @Test
    fun `목록이 비어 있으면 처음 프레임만 고른다`() {
        val picked = VideoStillFrameFinder.pickSettledFrameIndices(emptyList())

        assertEquals(listOf(0), picked)
    }

    @Test
    fun `허용 범위를 벗어나면 한 칸만 옮겨도 사건이 끊긴다`() {
        val diffCells = listOf(
            cell(4, 4),
            cell(4, 4),
            cell(6, 4) // tolerance=1 이면 2칸 차이라 넘어간 것으로 봄
        )

        val picked = VideoStillFrameFinder.pickSettledFrameIndices(diffCells, cellTolerance = 1)

        assertEquals(listOf(0, 2, 3), picked)
    }

    @Test
    fun `연속으로 살짝씩 옮겨가면(대각선 등) 매번 최신 칸 기준으로 계속 같은 사건이다`() {
        // (0,0) -> (1,0) -> (2,0) 처럼 한 칸씩 이어지면 각 단계는 이전 단계와 tolerance
        // 안이라 끊기지 않아야 합니다(누적 거리가 아니라 직전 칸과만 비교).
        val diffCells = listOf(
            cell(0, 0),
            cell(1, 0),
            cell(2, 0),
            cell(3, 0),
            cell(8, 9) // 확실히 다른 자리
        )

        val picked = VideoStillFrameFinder.pickSettledFrameIndices(diffCells, cellTolerance = 1)

        assertEquals(listOf(0, 4, 5), picked)
    }
}
