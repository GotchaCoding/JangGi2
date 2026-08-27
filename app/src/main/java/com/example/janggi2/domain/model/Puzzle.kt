package com.example.janggi2.domain.model

/**
 * 악수 직전 국면 하나. [position]은 [blunderMove]를 두기 직전 상태이고,
 * 되돌리기·복기 이력은 비워 사용자가 정상적으로 수를 둘 수 있게 만든 것입니다.
 */
data class Puzzle(
    val position: GameState,
    val moveIndex: Int,
    val player: Player,
    val blunderMove: Move
)
