package com.example.janggi2.domain.model

/**
 * Sealed class representing all Janggi pieces.
 * Each piece has a player (owner) and position.
 */
sealed class Piece(
    val player: Player,
    val position: Position
) {
    /**
     * Returns the Korean character representation of this piece.
     */
    abstract fun getDisplayChar(): String

    /**
     * Returns a copy of this piece at a new position.
     */
    abstract fun moveTo(newPosition: Position): Piece

    /**
     * 왕 (King/General) - 1 per player
     * Moves 1 step orthogonally or diagonally within palace only.
     */
    data class General(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "왕"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 사 (Guard/Advisor) - 2 per player
     * Moves 1 step orthogonally or diagonally within palace only.
     */
    data class Guard(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "사"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 마 (Horse) - 2 per player
     * Moves in L-shape: 1 orthogonal + 1 diagonal.
     * Can be blocked by adjacent piece.
     */
    data class Horse(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "마"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 상 (Elephant) - 2 per player
     * Moves in special pattern: 1 orthogonal + 2 diagonal (한상마).
     * Can be blocked.
     */
    data class Elephant(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "상"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 차 (Chariot/Rook) - 2 per player
     * Moves any distance orthogonally.
     * Blocked by other pieces.
     */
    data class Chariot(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "차"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 포 (Cannon) - 2 per player
     * Must jump over exactly one piece to move or capture.
     * Cannot jump over another cannon.
     */
    data class Cannon(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = "포"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }

    /**
     * 졸/병 (Soldier/Pawn) - 5 per player
     * Moves forward or sideways (forward only before crossing river).
     * After crossing river, can also move sideways.
     */
    data class Soldier(
        val owner: Player,
        val pos: Position
    ) : Piece(owner, pos) {
        override fun getDisplayChar(): String = if (owner == Player.CHO) "졸" else "병"
        override fun moveTo(newPosition: Position) = copy(pos = newPosition)
    }
}
