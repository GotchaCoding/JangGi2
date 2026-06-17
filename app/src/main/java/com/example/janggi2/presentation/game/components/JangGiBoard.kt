package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview

/**
 * Composable that renders the Janggi board using Canvas.
 * - 9 columns × 10 rows grid
 * - Palace diagonal lines in top (Cho) and bottom (Han) palaces
 * - River marking between rows 4 and 5
 */
@Composable
fun JangGiBoard(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val width = size.width
        val height = size.height

        // Calculate cell size
        val cellWidth = width / 8f  // 9 columns means 8 spaces
        val cellHeight = height / 9f  // 10 rows means 9 spaces

        // Draw wood-colored background
        drawRect(
            color = Color(0xFFD2B48C) // Tan/Wood color
        )

        // Draw main grid lines
        drawGrid(cellWidth, cellHeight)

        // Draw palace diagonals
        drawPalaceDiagonals(cellWidth, cellHeight)

        // Draw river marking
        drawRiver(cellWidth, cellHeight)
    }
}

/**
 * Draws the main 9×10 grid lines.
 */
private fun DrawScope.drawGrid(cellWidth: Float, cellHeight: Float) {
    val gridColor = Color(0xFF654321) // Dark brown for wood board
    val strokeWidth = 2f

    // Draw vertical lines (9 lines for 9 columns)
    for (col in 0..8) {
        val x = col * cellWidth
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Square
        )
    }

    // Draw horizontal lines (10 lines for 10 rows)
    for (row in 0..9) {
        val y = row * cellHeight
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Square
        )
    }
}

/**
 * Draws diagonal lines in both palaces.
 * Cho palace: rows 0-2, cols 3-5
 * Han palace: rows 7-9, cols 3-5
 */
private fun DrawScope.drawPalaceDiagonals(cellWidth: Float, cellHeight: Float) {
    val gridColor = Color(0xFF654321) // Dark brown for wood board
    val strokeWidth = 2f

    // Cho palace diagonals (top)
    val choLeft = 3 * cellWidth
    val choRight = 5 * cellWidth
    val choTop = 0f
    val choBottom = 2 * cellHeight

    // Top-left to bottom-right
    drawLine(
        color = gridColor,
        start = Offset(choLeft, choTop),
        end = Offset(choRight, choBottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )

    // Top-right to bottom-left
    drawLine(
        color = gridColor,
        start = Offset(choRight, choTop),
        end = Offset(choLeft, choBottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )

    // Han palace diagonals (bottom)
    val hanLeft = 3 * cellWidth
    val hanRight = 5 * cellWidth
    val hanTop = 7 * cellHeight
    val hanBottom = 9 * cellHeight

    // Top-left to bottom-right
    drawLine(
        color = gridColor,
        start = Offset(hanLeft, hanTop),
        end = Offset(hanRight, hanBottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )

    // Top-right to bottom-left
    drawLine(
        color = gridColor,
        start = Offset(hanRight, hanTop),
        end = Offset(hanLeft, hanBottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )
}

/**
 * Draws the river marking between rows 4 and 5.
 */
private fun DrawScope.drawRiver(cellWidth: Float, cellHeight: Float) {
    val riverColor = Color(0xFF8B4513).copy(alpha = 0.3f) // Saddle brown, subtle
    val strokeWidth = 1f

    val riverY = 4.5f * cellHeight

    drawLine(
        color = riverColor,
        start = Offset(0f, riverY),
        end = Offset(size.width, riverY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )
}

@Preview(showBackground = true)
@Composable
fun JangGiBoardPreview() {
    JangGiBoard()
}
