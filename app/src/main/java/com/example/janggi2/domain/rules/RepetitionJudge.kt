package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.GameState

/**
 * 반복 규칙 판정 결과. 모두 **둘 차례인 쪽** 관점입니다.
 */
enum class RepetitionOutcome {
    /** 반복으로 끝날 국면이 아님 */
    NONE,

    /** 둘 차례인 쪽이 반칙패 - 장군을 반복해 부른 쪽이 여기 걸립니다 */
    SIDE_TO_MOVE_LOSES,

    /** 둘 차례인 쪽이 이김 - 상대가 반복을 만든 경우 */
    SIDE_TO_MOVE_WINS,

    /** 반복으로 비김. 이 앱은 무승부가 없으므로 기물 점수로 넘깁니다 */
    DRAW
}

/**
 * 장군 반복·수 반복을 가립니다.
 *
 * 이 판정만 엔진에 맡기는 이유는 판 하나로는 알 수 없기 때문입니다. 국면 이력을
 * 거슬러 올라가야 하고, Fairy-Stockfish 의 `janggimodern` 변형에 이미
 * `perpetualCheckIllegal`·`moveRepetitionIllegal`·`nFoldRule` 로 들어 있습니다.
 * 나머지 이동 규칙은 [PieceMovement] 가 그대로 담당합니다.
 *
 * 구현이 `data/` 에 있으므로 여기서는 인터페이스만 둡니다. 덕분에 [GameRules] 를
 * 판정자 없이도 만들 수 있고, JVM 단위 테스트가 네이티브 라이브러리 없이 돕니다.
 */
interface RepetitionJudge {

    /**
     * @return 판정 결과, 엔진이 준비되지 않았거나 판단할 수 없으면 [RepetitionOutcome.NONE]
     */
    fun judge(gameState: GameState): RepetitionOutcome
}
