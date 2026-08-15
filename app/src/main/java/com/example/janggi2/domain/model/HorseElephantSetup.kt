package com.example.janggi2.domain.model

/**
 * 대국 전에 고르는 마·상 배치.
 *
 * 장기는 궁 앞 뒷줄의 네 자리(차와 사 사이)에 마 둘과 상 둘을 어떻게 놓을지
 * 대국자가 각자 정하고 시작합니다. 그래서 네 가지가 나옵니다.
 *
 * 이름은 **자기 진영에서 봤을 때 왼쪽부터** 읽은 것입니다. 판을 사이에 두고 마주
 * 앉으므로 위쪽 대국자의 왼쪽은 화면의 오른쪽이 됩니다 - [backRankColumns] 가
 * 그 방향을 뒤집어 줍니다.
 */
enum class HorseElephantSetup(val displayName: String) {

    /** 마상마상 */
    HORSE_FIRST_OUTER("마상마상"),

    /** 상마상마 - 기존 기본 배치 */
    ELEPHANT_FIRST_OUTER("상마상마"),

    /** 마상상마 (양귀마) */
    HORSES_OUTSIDE("마상상마"),

    /** 상마마상 (원앙마) */
    HORSES_INSIDE("상마마상");

    /** 자기 왼쪽부터 네 자리에 놓이는 기물. true 면 마, false 면 상입니다. */
    private val horseFromOwnLeft: List<Boolean>
        get() = when (this) {
            HORSE_FIRST_OUTER -> listOf(true, false, true, false)
            ELEPHANT_FIRST_OUTER -> listOf(false, true, false, true)
            HORSES_OUTSIDE -> listOf(true, false, false, true)
            HORSES_INSIDE -> listOf(false, true, true, false)
        }

    /**
     * 이 배치를 [player] 의 뒷줄에 놓았을 때 각 칸에 무엇이 오는지.
     *
     * @return 열 번호 → 마인지 여부. 열은 언제나 1·2·6·7 입니다.
     */
    fun backRank(player: Player): Map<Int, Boolean> =
        backRankColumns(player).zip(horseFromOwnLeft).toMap()

    /**
     * [player] 가 자기 왼쪽부터 헤아릴 때 나오는 열 순서.
     *
     * 한은 아래쪽이라 자기 왼쪽이 곧 1열이지만, 초는 마주 보고 앉으므로 7열부터입니다.
     */
    private fun backRankColumns(player: Player): List<Int> =
        if (player == Player.HAN) listOf(1, 2, 6, 7) else listOf(7, 6, 2, 1)

    companion object {
        /** 뒷줄에서 마·상이 놓이는 네 자리 */
        val COLUMNS = listOf(1, 2, 6, 7)

        /**
         * 고르지 않았을 때 쓰는 배치. 두 진영의 값이 다른 건 실수가 아닙니다 -
         * 예전부터 쓰던 판을 그대로 두려는 것인데, 그 판은 두 진영의 열 배치가
         * 똑같아서(둘 다 1열 상, 2열 마) 각자 관점에서 읽으면 서로 다른 배치입니다.
         */
        fun defaultFor(player: Player): HorseElephantSetup =
            if (player == Player.CHO) HORSE_FIRST_OUTER else ELEPHANT_FIRST_OUTER
    }
}
