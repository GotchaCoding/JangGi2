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
import com.example.janggi2.domain.model.Position

/**
 * Overlay layer that renders selection highlight, valid move indicators, and check warning.
 *
 * @param selectedPosition The currently selected piece position (highlighted in yellow)
 * @param validMoves List of valid move positions (shown as green circles)
 * @param checkPosition Position of General in check (shown with pulsing red border)
 * @param cellWidth Width of each board cell
 * @param cellHeight Height of each board cell
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
    // Pulsing animation for check indicator
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
        // Draw check indicator (pulsing red border)
        checkPosition?.let { pos ->
            drawCheckIndicator(pos, cellWidth.toPx(), cellHeight.toPx(), checkAlpha)
        }

        // Draw selected piece highlight
        selectedPosition?.let { pos ->
            drawSelectionHighlight(pos, cellWidth.toPx(), cellHeight.toPx())
        }

        // Draw valid move indicators
        validMoves.forEach { pos ->
            drawValidMoveIndicator(pos, cellWidth.toPx(), cellHeight.toPx())
        }
    }
}

/**
 * Draws a yellow border around the selected piece.
 */
private fun DrawScope.drawSelectionHighlight(
    position: Position,
    cellWidth: Float,
    cellHeight: Float
) {
    val centerX = position.col * cellWidth
    val centerY = position.row * cellHeight

    val highlightColor = Color(0xFFFFEB3B) // Yellow
    val radius = 22f

    drawCircle(
        color = highlightColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 4f)
    )
}

/**
 * Draws a semi-transparent green circle for valid move positions.
 */
private fun DrawScope.drawValidMoveIndicator(
    position: Position,
    cellWidth: Float,
    cellHeight: Float
) {
    val centerX = position.col * cellWidth
    val centerY = position.row * cellHeight

    val indicatorColor = Color(0xFF4CAF50).copy(alpha = 0.5f) // Semi-transparent green
    val radius = 12f

    drawCircle(
        color = indicatorColor,
        radius = radius,
        center = Offset(centerX, centerY)
    )
}

/**
 * Draws a pulsing red border for a General in check.
 */
private fun DrawScope.drawCheckIndicator(
    position: Position,
    cellWidth: Float,
    cellHeight: Float,
    alpha: Float
) {
    val centerX = position.col * cellWidth
    val centerY = position.row * cellHeight

    val checkColor = Color(0xFFF44336).copy(alpha = alpha) // Pulsing red
    val radius = 26f

    drawCircle(
        color = checkColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 5f)
    )
}
