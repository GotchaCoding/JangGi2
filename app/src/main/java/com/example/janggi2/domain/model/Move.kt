package com.example.janggi2.domain.model

/**
 * Represents a move in Janggi.
 * A move consists of a starting position, ending position, and optionally a captured piece.
 */
data class Move(
    val from: Position,
    val to: Position,
    val capturedPiece: Piece? = null,
    /** 이 수를 둔 기물. 수 기록 표시에 씁니다. 예전에 저장된 대국에는 없을 수 있습니다. */
    val movedPiece: Piece? = null
) {
    /**
     * Returns true if this move results in a capture.
     */
    fun isCapture(): Boolean = capturedPiece != null

    /**
     * 한 수 쉼인지 여부. 엔진과 같은 표현으로, 출발과 도착이 같은 수입니다.
     */
    fun isPass(): Boolean = from == to

    /**
     * Returns the distance moved in columns.
     */
    fun colDistance(): Int = kotlin.math.abs(to.col - from.col)

    /**
     * Returns the distance moved in rows.
     */
    fun rowDistance(): Int = kotlin.math.abs(to.row - from.row)

    /**
     * Returns true if this is a diagonal move.
     */
    fun isDiagonal(): Boolean = colDistance() == rowDistance() && colDistance() > 0

    /**
     * Returns true if this is an orthogonal (horizontal or vertical) move.
     */
    fun isOrthogonal(): Boolean = (from.col == to.col) != (from.row == to.row)
}
