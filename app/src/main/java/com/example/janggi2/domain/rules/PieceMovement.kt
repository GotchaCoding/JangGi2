package com.example.janggi2.domain.rules

import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import kotlin.math.abs

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

/**
 * Movement rules for General (왕).
 * Moves 1 step orthogonally or diagonally, but only within the palace.
 */
class GeneralMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        // 8 directions: orthogonal + diagonal
        val directions = listOf(
            Position(-1, 0), Position(1, 0), Position(0, -1), Position(0, 1), // orthogonal
            Position(-1, -1), Position(-1, 1), Position(1, -1), Position(1, 1) // diagonal
        )

        for (dir in directions) {
            val newPos = pos + dir
            if (newPos.isValid() && newPos.isInPalace()) {
                val targetPiece = board[newPos]
                // Can move if empty or occupied by opponent
                if (targetPiece == null || targetPiece.player != piece.player) {
                    validMoves.add(newPos)
                }
            }
        }

        return validMoves
    }
}

/**
 * Movement rules for Guard (사).
 * Same as General - moves 1 step orthogonally or diagonally within palace.
 */
class GuardMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        return GeneralMovement().getValidMoves(piece, board)
    }
}

/**
 * Movement rules for Horse (마).
 * Moves in L-shape: 1 step orthogonal, then 1 step diagonal.
 * Can be blocked by piece in orthogonal direction.
 */
class HorseMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        // 8 possible L-shaped moves
        // Format: (orthogonal step, then diagonal step)
        val moves = listOf(
            // Up then diagonal
            Pair(Position(0, -1), Position(-1, -1)),
            Pair(Position(0, -1), Position(1, -1)),
            // Down then diagonal
            Pair(Position(0, 1), Position(-1, 1)),
            Pair(Position(0, 1), Position(1, 1)),
            // Left then diagonal
            Pair(Position(-1, 0), Position(-1, -1)),
            Pair(Position(-1, 0), Position(-1, 1)),
            // Right then diagonal
            Pair(Position(1, 0), Position(1, -1)),
            Pair(Position(1, 0), Position(1, 1))
        )

        for ((orthogonal, diagonal) in moves) {
            val blockPos = pos + orthogonal
            // Check if path is blocked
            if (board[blockPos] != null) continue

            val newPos = pos + orthogonal + diagonal
            if (newPos.isValid()) {
                val targetPiece = board[newPos]
                if (targetPiece == null || targetPiece.player != piece.player) {
                    validMoves.add(newPos)
                }
            }
        }

        return validMoves
    }
}

/**
 * Movement rules for Elephant (상).
 * Moves in special pattern: 1 orthogonal + 2 diagonal (한상마).
 * Can be blocked at each step.
 */
class ElephantMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        // 8 possible elephant moves
        // Format: (orthogonal, first diagonal, second diagonal)
        val moves = listOf(
            // Up-left
            Triple(Position(0, -1), Position(-1, -1), Position(-1, -1)),
            // Up-right
            Triple(Position(0, -1), Position(1, -1), Position(1, -1)),
            // Down-left
            Triple(Position(0, 1), Position(-1, 1), Position(-1, 1)),
            // Down-right
            Triple(Position(0, 1), Position(1, 1), Position(1, 1)),
            // Left-up
            Triple(Position(-1, 0), Position(-1, -1), Position(-1, -1)),
            // Left-down
            Triple(Position(-1, 0), Position(-1, 1), Position(-1, 1)),
            // Right-up
            Triple(Position(1, 0), Position(1, -1), Position(1, -1)),
            // Right-down
            Triple(Position(1, 0), Position(1, 1), Position(1, 1))
        )

        for ((orthogonal, diag1, diag2) in moves) {
            // Check first step (orthogonal)
            val step1 = pos + orthogonal
            if (board[step1] != null) continue

            // Check second step (first diagonal)
            val step2 = step1 + diag1
            if (board[step2] != null) continue

            // Check final position (second diagonal)
            val newPos = step2 + diag2
            if (newPos.isValid()) {
                val targetPiece = board[newPos]
                if (targetPiece == null || targetPiece.player != piece.player) {
                    validMoves.add(newPos)
                }
            }
        }

        return validMoves
    }
}

/**
 * Movement rules for Chariot (차).
 * Moves any distance orthogonally (horizontal or vertical).
 * Blocked by other pieces. Can also move along palace diagonals.
 */
class ChariotMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        // Orthogonal directions
        val directions = listOf(
            Position(-1, 0), Position(1, 0), Position(0, -1), Position(0, 1)
        )

        for (dir in directions) {
            var current = pos + dir
            while (current.isValid()) {
                val targetPiece = board[current]
                if (targetPiece == null) {
                    validMoves.add(current)
                    current += dir
                } else {
                    // Can capture opponent piece
                    if (targetPiece.player != piece.player) {
                        validMoves.add(current)
                    }
                    break // Blocked
                }
            }
        }

        // Add palace diagonal moves if in palace
        if (pos.isInPalace()) {
            val palaceCenter = pos.getPalaceCenter()
            if (palaceCenter != null) {
                validMoves.addAll(getPalaceDiagonalMoves(piece, board, palaceCenter))
            }
        }

        return validMoves
    }

    private fun getPalaceDiagonalMoves(
        piece: Piece,
        board: Map<Position, Piece>,
        center: Position
    ): List<Position> {
        val moves = mutableListOf<Position>()
        val pos = piece.position

        // Palace corners relative to center
        val corners = listOf(
            Position(center.col - 1, center.row - 1),
            Position(center.col + 1, center.row - 1),
            Position(center.col - 1, center.row + 1),
            Position(center.col + 1, center.row + 1)
        )

        // Check diagonal paths through center
        for (corner in corners) {
            if (!corner.isValid()) continue

            // Check if we can reach this corner via center diagonal
            val isOnDiagonal = abs(pos.col - center.col) == abs(pos.row - center.row)
            if (isOnDiagonal) {
                val targetPiece = board[corner]
                if (targetPiece == null || targetPiece.player != piece.player) {
                    // Check if path is clear
                    if (pos != center && board[center] == null) {
                        moves.add(corner)
                    } else if (pos == corner) {
                        // Already handled
                    }
                }
            }
        }

        return moves
    }
}

/**
 * Movement rules for Cannon (포).
 * Must jump over exactly one piece (not another cannon) to move or capture.
 * Cannot capture another cannon.
 */
class CannonMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        // Orthogonal directions
        val directions = listOf(
            Position(-1, 0), Position(1, 0), Position(0, -1), Position(0, 1)
        )

        for (dir in directions) {
            var current = pos + dir
            var jumpedPiece: Piece? = null

            while (current.isValid()) {
                val targetPiece = board[current]

                if (jumpedPiece == null) {
                    // Looking for piece to jump over
                    if (targetPiece != null && targetPiece !is Piece.Cannon) {
                        jumpedPiece = targetPiece
                    }
                } else {
                    // Already jumped, looking for landing spot
                    if (targetPiece == null) {
                        validMoves.add(current)
                    } else if (targetPiece.player != piece.player && targetPiece !is Piece.Cannon) {
                        // Can capture opponent (but not another cannon)
                        validMoves.add(current)
                        break
                    } else {
                        // Blocked by own piece or another cannon
                        break
                    }
                }

                current += dir
            }
        }

        return validMoves
    }
}

/**
 * Movement rules for Soldier (졸/병).
 * Before crossing river: moves forward only.
 * After crossing river: moves forward or sideways.
 * In palace: can also move diagonally.
 */
class SoldierMovement : PieceMovement {
    override fun getValidMoves(piece: Piece, board: Map<Position, Piece>): List<Position> {
        val soldier = piece as Piece.Soldier
        val validMoves = mutableListOf<Position>()
        val pos = piece.position

        val forwardDir = if (piece.player == Player.CHO) 1 else -1

        // Forward move (always available)
        val forward = Position(pos.col, pos.row + forwardDir)
        if (forward.isValid()) {
            val targetPiece = board[forward]
            if (targetPiece == null || targetPiece.player != piece.player) {
                validMoves.add(forward)
            }
        }

        // Sideways moves (only after crossing river)
        if (soldier.hasCrossedRiver()) {
            val left = Position(pos.col - 1, pos.row)
            if (left.isValid()) {
                val targetPiece = board[left]
                if (targetPiece == null || targetPiece.player != piece.player) {
                    validMoves.add(left)
                }
            }

            val right = Position(pos.col + 1, pos.row)
            if (right.isValid()) {
                val targetPiece = board[right]
                if (targetPiece == null || targetPiece.player != piece.player) {
                    validMoves.add(right)
                }
            }
        }

        // Diagonal moves in palace
        if (pos.isInPalace()) {
            val palaceCenter = pos.getPalaceCenter()
            if (palaceCenter != null) {
                val diagonals = listOf(
                    Position(pos.col - 1, pos.row + forwardDir),
                    Position(pos.col + 1, pos.row + forwardDir)
                )

                for (diag in diagonals) {
                    if (diag.isValid() && diag.isInPalace()) {
                        val targetPiece = board[diag]
                        if (targetPiece == null || targetPiece.player != piece.player) {
                            validMoves.add(diag)
                        }
                    }
                }
            }
        }

        return validMoves
    }
}
