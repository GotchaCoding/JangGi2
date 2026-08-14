package com.example.janggi2.presentation.game.components

import androidx.compose.ui.graphics.Color
import com.example.janggi2.domain.model.Player

/**
 * 진영 색. 보드의 기물, 차례 표시, 수 기록이 모두 같은 색을 써야 화면이 읽힙니다.
 */
object PlayerColors {
    /** 기물 글자·점수 등 진한 색 */
    fun of(player: Player): Color = when (player) {
        Player.CHO -> Color(0xFF1976D2)
        Player.HAN -> Color(0xFFC2185B)
    }

    /** 기물 바탕 등 옅은 색 */
    fun background(player: Player): Color = when (player) {
        Player.CHO -> Color(0xFFE3F2FD)
        Player.HAN -> Color(0xFFFCE4EC)
    }
}
