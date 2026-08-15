package com.example.janggi2.presentation.game.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Position
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 표시 크기는 칸 크기에 비례합니다. 고정 픽셀을 쓰면 기물보다 작아져서 가려집니다.
private const val SELECTION_RADIUS = 0.50f   // 기물을 감싸는 크기
private const val CHECK_RADIUS = 0.54f
private const val HINT_RADIUS = 0.50f
private const val VALID_MOVE_RADIUS = 0.16f

/**
 * 선택·이동 가능 위치·장군 표시를 그립니다. 기물 **아래** 층입니다.
 *
 * @param selectedPosition 선택된 기물 위치 (노란 테두리)
 * @param validMoves 갈 수 있는 위치 (초록 점)
 * @param checkPosition 장군 맞은 궁의 위치 (빨간 테두리, 깜빡임)
 */
@Composable
fun HighlightLayer(
    selectedPosition: Position?,
    validMoves: List<Position>,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: Modifier = Modifier,
    checkPosition: Position? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "checkPulse")
    val checkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = cellWidth.toPx()
        val ch = cellHeight.toPx()
        val unit = min(cw, ch)

        checkPosition?.let {
            drawCircle(
                color = Color(0xFFF44336).copy(alpha = checkAlpha),
                radius = unit * CHECK_RADIUS,
                center = center(it, cw, ch),
                style = Stroke(width = unit * 0.06f)
            )
        }

        selectedPosition?.let {
            drawCircle(
                color = Color(0xFFFFEB3B),
                radius = unit * SELECTION_RADIUS,
                center = center(it, cw, ch),
                style = Stroke(width = unit * 0.07f)
            )
        }

        validMoves.forEach {
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.65f),
                radius = unit * VALID_MOVE_RADIUS,
                center = center(it, cw, ch)
            )
        }
    }
}

/**
 * 엔진이 추천한 수를 화살표로 그립니다. 기물 **위** 층이라 가려지지 않습니다.
 *
 * 장기의 한 수 쉼은 출발과 도착이 같으므로, 그때는 화살표 대신 링만 그립니다.
 */
@Composable
fun HintLayer(
    hintMove: Move?,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (hintMove == null) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = cellWidth.toPx()
        val ch = cellHeight.toPx()
        val unit = min(cw, ch)

        val color = Color(0xFF2196F3) // 노랑(선택)·초록(이동)·빨강(장군)과 구분되는 파랑
        val ring = unit * HINT_RADIUS
        val stroke = unit * 0.07f
        val from = center(hintMove.from, cw, ch)
        val to = center(hintMove.to, cw, ch)

        drawCircle(color, ring, from, style = Stroke(width = stroke))
        if (hintMove.from == hintMove.to) return@Canvas
        drawCircle(color, ring, to, style = Stroke(width = stroke))

        // 선은 양 끝 링 바깥에서 시작하고 끝내 링 안을 파고들지 않게 합니다.
        val angle = atan2(to.y - from.y, to.x - from.x)
        val start = Offset(from.x + cos(angle) * ring, from.y + sin(angle) * ring)
        val end = Offset(to.x - cos(angle) * ring, to.y - sin(angle) * ring)

        // 링끼리 붙어 있으면 화살표를 그릴 자리가 없습니다(옆칸으로 한 칸 이동 등).
        val span = kotlin.math.hypot(end.x - start.x, end.y - start.y)
        if (span < unit * 0.15f) return@Canvas

        drawLine(color, start, end, strokeWidth = stroke)

        val head = min(unit * 0.30f, span)
        val spread = 0.5f
        drawLine(
            color, end,
            Offset(end.x - cos(angle - spread) * head, end.y - sin(angle - spread) * head),
            strokeWidth = stroke
        )
        drawLine(
            color, end,
            Offset(end.x - cos(angle + spread) * head, end.y - sin(angle + spread) * head),
            strokeWidth = stroke
        )
    }
}

private fun center(position: Position, cellWidth: Float, cellHeight: Float) =
    Offset(position.col * cellWidth, position.row * cellHeight)
