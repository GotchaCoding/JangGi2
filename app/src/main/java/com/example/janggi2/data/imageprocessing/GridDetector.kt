package com.example.janggi2.data.imageprocessing

import android.graphics.Point
import android.graphics.Rect
import com.example.janggi2.domain.model.Position
import com.google.mlkit.vision.text.Text

/**
 * Detects the Janggi board grid from image dimensions and text positions.
 */
class GridDetector {
    /**
     * Represents a detected board grid with cell dimensions and origin.
     */
    data class Grid(
        val cellWidth: Float,
        val cellHeight: Float,
        val topLeft: Point,
        val bottomRight: Point
    ) {
        /**
         * Maps a point in image coordinates to a board position.
         * Returns null if the point is outside the grid.
         */
        fun mapPointToPosition(x: Int, y: Int): Position? {
            if (x < topLeft.x || x > bottomRight.x || y < topLeft.y || y > bottomRight.y) {
                return null
            }

            val relativeX = x - topLeft.x
            val relativeY = y - topLeft.y

            val col = (relativeX / cellWidth).toInt().coerceIn(0, 8)
            val row = (relativeY / cellHeight).toInt().coerceIn(0, 9)

            return Position(col, row)
        }

        /**
         * Gets the center point of a specific board position.
         */
        fun getPositionCenter(position: Position): Point {
            val x = topLeft.x + (position.col * cellWidth + cellWidth / 2).toInt()
            val y = topLeft.y + (position.row * cellHeight + cellHeight / 2).toInt()
            return Point(x, y)
        }
    }

    /**
     * Detects the board grid from image dimensions and detected text elements.
     *
     * Strategy:
     * 1. Assume board occupies center 80% of image
     * 2. Board aspect ratio is 9:10 (9 columns, 10 rows)
     * 3. Calculate cell dimensions
     * 4. Validate with text positions if available
     *
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @param texts Optional list of detected text elements for validation
     * @return Detected Grid, or null if detection fails
     */
    fun detectGrid(
        imageWidth: Int,
        imageHeight: Int,
        texts: List<Text.Element>? = null
    ): Grid? {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return null
        }

        // Calculate board dimensions (center 80% of image)
        val boardWidth = (imageWidth * 0.8f).toInt()
        val boardHeight = (imageHeight * 0.8f).toInt()

        // Adjust to maintain 9:10 aspect ratio
        val targetAspectRatio = 9f / 10f
        val actualAspectRatio = boardWidth.toFloat() / boardHeight

        val adjustedBoardWidth: Int
        val adjustedBoardHeight: Int

        if (actualAspectRatio > targetAspectRatio) {
            // Width is too large, adjust it
            adjustedBoardHeight = boardHeight
            adjustedBoardWidth = (boardHeight * targetAspectRatio).toInt()
        } else {
            // Height is too large, adjust it
            adjustedBoardWidth = boardWidth
            adjustedBoardHeight = (boardWidth / targetAspectRatio).toInt()
        }

        // Calculate cell dimensions
        val cellWidth = adjustedBoardWidth / 9f
        val cellHeight = adjustedBoardHeight / 10f

        // Center the board in the image
        val topLeftX = (imageWidth - adjustedBoardWidth) / 2
        val topLeftY = (imageHeight - adjustedBoardHeight) / 2

        val grid = Grid(
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            topLeft = Point(topLeftX, topLeftY),
            bottomRight = Point(topLeftX + adjustedBoardWidth, topLeftY + adjustedBoardHeight)
        )

        // Validate with text positions if available
        if (texts != null && texts.isNotEmpty()) {
            val validTexts = texts.count { text ->
                val center = text.boundingBox?.centerPoint()
                center?.let { grid.mapPointToPosition(it.x, it.y) != null } ?: false
            }

            // If less than 30% of texts are within the grid, detection might be off
            if (validTexts.toFloat() / texts.size < 0.3f) {
                // Try adjusting the grid based on actual text positions
                return detectGridFromTextPositions(imageWidth, imageHeight, texts) ?: grid
            }
        }

        return grid
    }

    /**
     * Attempts to detect grid by analyzing actual text positions.
     * This is a fallback method when default grid detection doesn't align well.
     */
    private fun detectGridFromTextPositions(
        imageWidth: Int,
        imageHeight: Int,
        texts: List<Text.Element>
    ): Grid? {
        if (texts.isEmpty()) return null

        val textBounds = texts.mapNotNull { it.boundingBox }
        if (textBounds.isEmpty()) return null

        // Find bounding box of all texts
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        textBounds.forEach { rect ->
            minX = minOf(minX, rect.left)
            minY = minOf(minY, rect.top)
            maxX = maxOf(maxX, rect.right)
            maxY = maxOf(maxY, rect.bottom)
        }

        // Add margin around text bounds (10% on each side)
        val margin = 0.1f
        val textWidth = maxX - minX
        val textHeight = maxY - minY

        val boardLeft = (minX - textWidth * margin).toInt().coerceAtLeast(0)
        val boardTop = (minY - textHeight * margin).toInt().coerceAtLeast(0)
        val boardRight = (maxX + textWidth * margin).toInt().coerceAtMost(imageWidth)
        val boardBottom = (maxY + textHeight * margin).toInt().coerceAtMost(imageHeight)

        val boardWidth = boardRight - boardLeft
        val boardHeight = boardBottom - boardTop

        val cellWidth = boardWidth / 9f
        val cellHeight = boardHeight / 10f

        return Grid(
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            topLeft = Point(boardLeft, boardTop),
            bottomRight = Point(boardRight, boardBottom)
        )
    }

    /**
     * Extension function to get center point of a Rect.
     */
    private fun Rect.centerPoint(): Point = Point(centerX(), centerY())
}
