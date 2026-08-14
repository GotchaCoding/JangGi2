package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * 장기 기물 점수(점수제).
 *
 * 값은 엔진이 승부를 가릴 때 쓰는 표와 같습니다 —
 * `fairystockfish/src/position.h` 의 `Position::material_counting_result()`,
 * `JANGGI_MATERIAL` 분기: 차13 포7 마5 상3 사3 졸·병2, 궁은 점수 없음.
 *
 * 주의: 엔진에는 이것과 **별개인** 탐색용 평가값이 있습니다(`types.h` 의
 * `RookValueMg 1276` 등). 그건 탐색 내부 단위라 점수제에 쓰면 안 됩니다.
 */
object MaterialScore {

    /**
     * 초가 선수이므로 한에게 얹어 주는 덤.
     *
     * 엔진은 이걸 `materialCount - 1 > 0` 으로 씁니다. 기물 값이 모두 정수라
     * 두 표현은 모든 국면에서 같은 판정을 냅니다 — `d = 초 - 한` 이라 할 때
     * 엔진은 `d >= 2`, 이쪽은 `d > 1.5` 즉 `d >= 2` 입니다.
     * 0.5 덕분에 동점이 없어 [leader] 가 항상 한쪽을 가리킵니다.
     */
    const val HAN_HANDICAP = 1.5

    /** 잡힌 기물은 판 위에 없으므로 판 밖 좌표를 줍니다. */
    private val CAPTURED = Position(-1, -1)

    /** 기물 종류가 늘면 컴파일러가 잡도록 when 을 씁니다. */
    fun valueOf(piece: Piece): Int = when (piece) {
        is Piece.Chariot -> 13
        is Piece.Cannon -> 7
        is Piece.Horse -> 5
        is Piece.Elephant -> 3
        is Piece.Guard -> 3
        is Piece.Soldier -> 2
        is Piece.General -> 0
    }

    /** 점수 높은 순. 잡은 기물을 큰 것부터 보여주기 위한 순서이기도 합니다. */
    private val PIECE_TYPES: List<Pair<Int, (Player) -> Piece>> = listOf(
        2 to { p: Player -> Piece.Chariot(p, CAPTURED) },
        2 to { p: Player -> Piece.Cannon(p, CAPTURED) },
        2 to { p: Player -> Piece.Horse(p, CAPTURED) },
        2 to { p: Player -> Piece.Elephant(p, CAPTURED) },
        2 to { p: Player -> Piece.Guard(p, CAPTURED) },
        5 to { p: Player -> Piece.Soldier(p, CAPTURED) }
    )

    /**
     * 판 위에 남은 기물로 양측 점수와 잡은 기물을 계산합니다.
     *
     * 수 기록이 아니라 판에서 세는 이유가 있습니다. 사진에서 불러온 판은 수 기록이
     * 비어 있고, 복기 중에는 수 기록에 최종 수까지 들어 있어 어느 시점을 보든 최종
     * 점수가 나옵니다. 판에서 세면 두 경우가 저절로 맞고, 엔진이 `count(WHITE, pt)`
     * 로 세는 방식과도 같습니다.
     */
    fun of(gameState: GameState): MaterialScoreboard {
        val board = gameState.board.values
        return MaterialScoreboard(
            choScore = rawScore(board, Player.CHO).toDouble(),
            hanScore = rawScore(board, Player.HAN) + HAN_HANDICAP,
            choCaptured = capturedBy(board, Player.CHO),
            hanCaptured = capturedBy(board, Player.HAN)
        )
    }

    /** 점수제 승자. 덤이 0.5라 동점이 없습니다. */
    fun leader(gameState: GameState): Player = of(gameState).winner

    private fun rawScore(board: Collection<Piece>, player: Player): Int =
        board.filter { it.player == player }.sumOf { valueOf(it) }

    /**
     * [player] 가 잡은 상대 기물. 시작 개수에서 남은 개수를 뺀 값입니다.
     *
     * 사진에서 불러온 판은 표준 배치에서 시작하지 않았을 수 있어 이 목록은 추정이며,
     * 한쪽에 차가 셋인 판이면 음수가 나올 수 있으므로 0으로 자릅니다.
     * 점수 자체는 판 위 기물의 합이라 그런 판에서도 언제나 정확합니다.
     */
    private fun capturedBy(board: Collection<Piece>, player: Player): List<Piece> {
        val opponent = player.opponent()
        val captured = mutableListOf<Piece>()

        for ((startCount, make) in PIECE_TYPES) {
            val prototype = make(opponent)
            val alive = board.count { it.player == opponent && it::class == prototype::class }
            repeat((startCount - alive).coerceAtLeast(0)) { captured.add(prototype) }
        }
        return captured
    }
}

/**
 * 양측 점수와 잡은 기물.
 *
 * [GameState.board] 에서 그때그때 계산하는 값이라 저장하지 않습니다.
 * (`GameState.applyMove` 가 아닌 다른 경로로도 판이 만들어지므로, 저장했다가는
 * 판과 어긋난 점수를 보게 됩니다.)
 */
data class MaterialScoreboard(
    /** 초기 배치에서 72.0 */
    val choScore: Double,
    /** 초기 배치에서 73.5 (덤 포함) */
    val hanScore: Double,
    /** 초가 잡은 한 기물, 점수 높은 순 */
    val choCaptured: List<Piece>,
    /** 한이 잡은 초 기물, 점수 높은 순 */
    val hanCaptured: List<Piece>
) {
    fun scoreFor(player: Player): Double =
        if (player == Player.CHO) choScore else hanScore

    fun capturedBy(player: Player): List<Piece> =
        if (player == Player.CHO) choCaptured else hanCaptured

    /** 덤이 0.5라 동점이 없습니다. */
    val winner: Player
        get() = if (choScore > hanScore) Player.CHO else Player.HAN
}
