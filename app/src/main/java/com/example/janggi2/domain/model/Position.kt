package com.example.janggi2.domain.model

/**
 * Represents a position on the Janggi board.
 * Coordinates: (0,0) = top-left, (8,9) = bottom-right
 * - col: 0-8 (9 columns)
 * - row: 0-9 (10 rows)
 */
data class Position(
    val col: Int,
    val row: Int
) {
    /**
     * Checks if this position is within the board boundaries.
     */
    fun isValid(): Boolean = col in 0..8 && row in 0..9

    /**
     * Checks if this position is inside the Cho (top) palace.
     * Palace: columns 3-5, rows 0-2
     */
    fun isInChoPalace(): Boolean = col in 3..5 && row in 0..2

    /**
     * Checks if this position is inside the Han (bottom) palace.
     * Palace: columns 3-5, rows 7-9
     */
    fun isInHanPalace(): Boolean = col in 3..5 && row in 7..9

    /**
     * Checks if this position is inside any palace.
     */
    fun isInPalace(): Boolean = isInChoPalace() || isInHanPalace()

    /**
     * Returns the center position of the palace this position belongs to.
     * Returns null if not in a palace.
     */
    fun getPalaceCenter(): Position? = when {
        isInChoPalace() -> Position(4, 1)
        isInHanPalace() -> Position(4, 8)
        else -> null
    }

    operator fun plus(other: Position) = Position(col + other.col, row + other.row)
    operator fun minus(other: Position) = Position(col - other.col, row - other.row)
}
