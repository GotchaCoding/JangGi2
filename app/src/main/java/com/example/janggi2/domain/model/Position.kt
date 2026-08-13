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
     * Checks if this position is inside the given player's own palace.
     */
    fun isInPalaceOf(player: Player): Boolean = when (player) {
        Player.CHO -> isInChoPalace()
        Player.HAN -> isInHanPalace()
    }

    /**
     * Returns the center position of the palace this position belongs to.
     * Returns null if not in a palace.
     */
    fun getPalaceCenter(): Position? = when {
        isInChoPalace() -> Position(4, 1)
        isInHanPalace() -> Position(4, 8)
        else -> null
    }

    /**
     * 궁성 대각선이 지나는 점인지 여부.
     *
     * 궁성에 그려진 대각선은 중앙과 네 모서리를 잇는 두 줄뿐입니다. 궁성 안이라도
     * 변의 가운데 점((4,0)·(3,1) 같은 곳)에는 대각선이 없으므로 대각선 이동을
     * 할 수 없습니다.
     */
    fun isPalaceDiagonalPoint(): Boolean {
        val center = getPalaceCenter() ?: return false
        if (this == center) return true
        return (col - center.col) in setOf(-1, 1) && (row - center.row) in setOf(-1, 1)
    }

    /**
     * 이 점에서 궁성 대각선으로 한 칸 이어진 점들.
     * 중앙에서는 네 모서리, 모서리에서는 중앙뿐입니다.
     */
    fun palaceDiagonalNeighbors(): List<Position> {
        val center = getPalaceCenter() ?: return emptyList()
        if (this == center) {
            return listOf(
                Position(center.col - 1, center.row - 1),
                Position(center.col + 1, center.row - 1),
                Position(center.col - 1, center.row + 1),
                Position(center.col + 1, center.row + 1)
            )
        }
        return if (isPalaceDiagonalPoint()) listOf(center) else emptyList()
    }

    operator fun plus(other: Position) = Position(col + other.col, row + other.row)
    operator fun minus(other: Position) = Position(col - other.col, row - other.row)
}
