package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Interface for calculating valid moves for a piece.
 */
interface PieceMovement {
    /**
     * Returns a list of valid destination positions for the given piece.
     * Does not consider check/checkmate - only basic movement rules.
     *
     * @param piece The piece to calculate moves for
     * @param board Current board state (all pieces)
     * @return List of valid destination positions
     */
    fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position>
}

/** 상하좌우 네 방향. */
private val ORTHOGONAL = listOf(
    Position(-1, 0), Position(1, 0), Position(0, -1), Position(0, 1)
)

/** 빈 칸이거나 상대 기물이면 그 칸으로 갈 수 있습니다. */
private fun canLandOn(piece: Piece, target: Position, board: Map<Position, Piece>): Boolean {
    if (!target.isValid()) return false
    val occupant = board[target] ?: return true
    return occupant.player != piece.player
}

/**
 * Movement rules for General (왕) and Guard (사).
 *
 * 자기 궁성 안에서만 한 칸 움직입니다. 상하좌우는 언제나 가능하고, 대각선은
 * 궁성에 그려진 선 위(중앙↔모서리)에서만 가능합니다. 궁성 변의 가운데 점에는
 * 대각선이 없습니다.
 */
private class PalaceStepMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val moves = mutableListOf<Position>()

        for (dir in ORTHOGONAL) {
            val target = pos + dir
            if (target.isInPalaceOf(piece.player) && canLandOn(piece, target, board)) {
                moves.add(target)
            }
        }

        for (target in pos.palaceDiagonalNeighbors()) {
            if (target.isInPalaceOf(piece.player) && canLandOn(piece, target, board)) {
                moves.add(target)
            }
        }

        return moves
    }
}

/** Movement rules for General (왕). */
class GeneralMovement : PieceMovement {
    private val palaceStep = PalaceStepMovement()
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> =
        palaceStep.getValidMoves(piece, board)
}

/** Movement rules for Guard (사) - identical to the General. */
class GuardMovement : PieceMovement {
    private val palaceStep = PalaceStepMovement()
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> =
        palaceStep.getValidMoves(piece, board)
}

/**
 * Movement rules for Horse (마).
 *
 * 직선 한 칸 뒤 대각선 한 칸. 직선 첫 칸에 기물이 있으면 막힙니다(멱).
 */
class HorseMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val moves = mutableListOf<Position>()

        for (straight in ORTHOGONAL) {
            val block = pos + straight
            if (!block.isValid() || board[block] != null) continue

            // 직선으로 나아간 방향을 유지하는 두 대각선
            for (diagonal in diagonalsAfter(straight)) {
                val target = block + diagonal
                if (canLandOn(piece, target, board)) {
                    moves.add(target)
                }
            }
        }

        return moves
    }
}

/**
 * Movement rules for Elephant (상).
 *
 * 직선 한 칸 뒤 대각선 두 칸, 모두 합쳐 세 칸입니다. 지나가는 두 칸 중 하나라도
 * 기물이 있으면 막힙니다.
 */
class ElephantMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val moves = mutableListOf<Position>()

        for (straight in ORTHOGONAL) {
            val step1 = pos + straight
            if (!step1.isValid() || board[step1] != null) continue

            for (diagonal in diagonalsAfter(straight)) {
                val step2 = step1 + diagonal
                if (!step2.isValid() || board[step2] != null) continue

                val target = step2 + diagonal
                if (canLandOn(piece, target, board)) {
                    moves.add(target)
                }
            }
        }

        return moves
    }
}

/**
 * 직선 한 칸을 간 뒤 이어서 갈 수 있는 두 대각선 방향.
 * 세로로 나아갔으면 좌·우 대각선, 가로로 나아갔으면 위·아래 대각선입니다.
 */
private fun diagonalsAfter(straight: Position): List<Position> =
    if (straight.col == 0) {
        listOf(Position(-1, straight.row), Position(1, straight.row))
    } else {
        listOf(Position(straight.col, -1), Position(straight.col, 1))
    }

/**
 * Movement rules for Chariot (차).
 *
 * 직선으로 막힐 때까지 갑니다. 궁성 대각선 위에 있으면 그 선을 따라서도 갑니다
 * (모서리에서 중앙을 지나 반대편 모서리까지).
 */
class ChariotMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val moves = mutableListOf<Position>()

        for (dir in ORTHOGONAL) {
            var current = pos + dir
            while (current.isValid()) {
                val occupant = board[current]
                if (occupant == null) {
                    moves.add(current)
                } else {
                    if (occupant.player != piece.player) moves.add(current)
                    break
                }
                current += dir
            }
        }

        moves.addAll(palaceDiagonalSlides(piece, board))
        return moves
    }

    /** 궁성 대각선을 따라 미끄러져 가는 칸들. */
    private fun palaceDiagonalSlides(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        if (!pos.isPalaceDiagonalPoint()) return emptyList()

        val moves = mutableListOf<Position>()
        for (next in pos.palaceDiagonalNeighbors()) {
            val occupant = board[next]
            if (occupant == null) {
                moves.add(next)
                // 중앙이 비어 있으면 그대로 지나 반대편 모서리까지 갈 수 있습니다.
                val beyond = next + (next - pos)
                if (beyond.isPalaceDiagonalPoint() && beyond.getPalaceCenter() == pos.getPalaceCenter() &&
                    canLandOn(piece, beyond, board)
                ) {
                    moves.add(beyond)
                }
            } else if (occupant.player != piece.player) {
                moves.add(next)
            }
        }
        return moves
    }
}

/**
 * Movement rules for Cannon (포).
 *
 * 사이에 기물 하나를 정확히 하나 넘어서 움직이거나 잡습니다. 포는 넘을 수도 없고
 * 잡을 수도 없으므로, 넘을 자리에 있는 첫 기물이 포라면 그 방향은 막힙니다.
 */
class CannonMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val moves = mutableListOf<Position>()

        for (dir in ORTHOGONAL) {
            var current = pos + dir
            var screen: Piece? = null

            while (current.isValid()) {
                val occupant = board[current]

                if (screen == null) {
                    if (occupant != null) {
                        // 첫 기물이 넘을 대상입니다. 포는 넘지 못하므로 이 방향은 끝입니다.
                        if (occupant is Piece.Cannon) break
                        screen = occupant
                    }
                } else {
                    if (occupant == null) {
                        moves.add(current)
                    } else {
                        if (occupant.player != piece.player && occupant !is Piece.Cannon) {
                            moves.add(current)
                        }
                        break
                    }
                }

                current += dir
            }
        }

        moves.addAll(palaceDiagonalJump(piece, board))
        return moves
    }

    /**
     * 궁성 대각선을 따라 중앙의 기물을 넘어 반대편 모서리로 가는 수.
     * 대각선은 모서리-중앙-모서리 세 점뿐이라 넘을 자리는 중앙 하나입니다.
     */
    private fun palaceDiagonalJump(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val center = pos.getPalaceCenter() ?: return emptyList()
        if (!pos.isPalaceDiagonalPoint() || pos == center) return emptyList()

        val screen = board[center] ?: return emptyList()
        if (screen is Piece.Cannon) return emptyList()

        val opposite = center + (center - pos)
        if (opposite.getPalaceCenter() != center) return emptyList()

        val occupant = board[opposite]
        return when {
            occupant == null -> listOf(opposite)
            occupant.player != piece.player && occupant !is Piece.Cannon -> listOf(opposite)
            else -> emptyList()
        }
    }
}

/**
 * Movement rules for Soldier (졸/병).
 *
 * 앞·좌·우로 한 칸씩 갑니다. 뒤로는 못 갑니다. 장기의 졸은 상대 진영에 들어가기
 * 전에도 옆으로 갈 수 있습니다(장기와 달리 중국 장기에는 강을 건너야 한다는
 * 제한이 있는데, 여기에는 없습니다).
 *
 * 궁성 대각선 위에 있으면 앞쪽 대각선으로도 갈 수 있습니다.
 */
class SoldierMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val pos = piece.position
        val forward = if (piece.player == Player.CHO) 1 else -1
        val moves = mutableListOf<Position>()

        val steps = listOf(
            Position(pos.col, pos.row + forward),  // 앞
            Position(pos.col - 1, pos.row),        // 좌
            Position(pos.col + 1, pos.row)         // 우
        )
        for (target in steps) {
            if (canLandOn(piece, target, board)) moves.add(target)
        }

        // 궁성 대각선은 앞으로 가는 방향으로만 씁니다.
        for (target in pos.palaceDiagonalNeighbors()) {
            if (target.row - pos.row == forward && canLandOn(piece, target, board)) {
                moves.add(target)
            }
        }

        return moves
    }
}
