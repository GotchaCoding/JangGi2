package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
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
    private val fenConverter: FenConverter
) {

    /**
     * @return e.g. "fen rbna1anbr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RBNA1ANBR w - - 0 1"
     */
    fun formatPosition(gameState: GameState): String {
        return "fen ${fenConverter.toFen(gameState)}"
    }
}
