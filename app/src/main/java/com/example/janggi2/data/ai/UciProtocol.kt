package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.initialGameState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats a [GameState] into the position command the native engine accepts.
 *
 * 항상 FEN으로 보냅니다. `startpos moves ...` 는 이 앱에 쓸 수 없는데,
 * 마·상 배치가 대국자 선택이라 엔진의 고정 startFen 과 다를 수 있고,
 * 사진에서 불러온 판은 수순 기록이 아예 없기 때문입니다.
 */
@Singleton
class UciProtocol @Inject constructor(
    private val fenConverter: FenConverter,
    private val notationConverter: NotationConverter
) {

    /**
     * @return e.g. "fen rbna1anbr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RBNA1ANBR w - - 0 1"
     */
    fun formatPosition(gameState: GameState): String {
        return "fen ${fenConverter.toFen(gameState)}"
    }

    /**
     * 시작 국면부터 수순을 모두 붙여 보냅니다.
     *
     * 반복 규칙은 지금 판만 봐서는 알 수 없습니다. 엔진이 `StateInfo` 사슬을 거슬러
     * 올라가며 같은 국면이 몇 번 나왔는지, 그 사이 장군이 이어졌는지를 보므로
     * 수를 실제로 재생시켜 줘야 합니다. [formatPosition] 으로는 안 됩니다.
     *
     * 한 수 쉼은 출발과 도착이 같은 수인데, 엔진도 같은 표기를 받습니다
     * (`uci.cpp` 의 `is_pass(m) && str == square(from) + square(to)`).
     *
     * @return e.g. "fen <시작 FEN> moves a1a2 i10i9"
     */
    fun formatHistory(gameState: GameState): String {
        // 시작 차례는 언제나 초, 시작 수는 언제나 1입니다.
        val startBoard = gameState.startBoard ?: initialGameState().board
        val startFen = fenConverter.toFen(startBoard, Player.CHO, fullMove = 1)
        if (gameState.moveHistory.isEmpty()) return "fen $startFen"

        val moves = gameState.moveHistory.joinToString(" ") { notationConverter.moveToUci(it) }
        return "fen $startFen moves $moves"
    }
}
