package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Position
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts between Kotlin Position objects and the square notation Fairy-Stockfish uses.
 *
 * 장기판은 9열(0-8) × 10행(0-9)이고, 엔진 표기는 파일(a-i) + 랭크(1-10)입니다.
 * 랭크가 1부터 시작하므로 `row + 1`이며, 10번째 행은 `a10`처럼 **세 글자**가 됩니다.
 *
 * 이 규칙은 [UCI::square]에서 옵니다. `CurrentProtocol`은 `UCI_GENERAL`로 초기화되고
 * 이 값을 바꾸는 코드는 `UCI::loop` 안에만 있는데, 이 빌드는 main.cpp를 제외하므로
 * loop가 돌지 않습니다. 따라서 `a0`부터 세는 분기가 아니라 `a1`부터 세는 분기가
 * 적용됩니다.
 *
 * Examples:
 * - Position(0, 0) = "a1"  (CHO 왼쪽 차)
 * - Position(4, 1) = "e2"  (CHO 왕)
 * - Position(4, 8) = "e9"  (HAN 왕)
 * - Position(8, 9) = "i10" (HAN 오른쪽 차)
 */
@Singleton
class NotationConverter @Inject constructor() {

    companion object {
        /** 한 칸을 나타내는 부분: 파일 a-i + 랭크 1-10. 10을 먼저 시도해야 "a10"이 "a1"로 잘리지 않습니다. */
        private val SQUARE = Regex("[a-i](?:10|[1-9])")

        /** 이동 = 출발 칸 + 도착 칸. 길이가 4~6자로 달라지므로 고정 위치로 자를 수 없습니다. */
        private val MOVE = Regex("^(${SQUARE.pattern})(${SQUARE.pattern})$")
    }

    /**
     * Converts a Position to engine square notation.
     * @return "a1" ~ "i10"
     */
    fun positionToUci(position: Position): String {
        require(position.col in 0..8) { "Invalid column: ${position.col}" }
        require(position.row in 0..9) { "Invalid row: ${position.row}" }

        val file = 'a' + position.col
        val rank = position.row + 1
        return "$file$rank"
    }

    /**
     * Converts engine square notation to a Position.
     * @throws IllegalArgumentException if the notation is malformed
     */
    fun uciToPosition(uci: String): Position {
        require(SQUARE.matches(uci)) { "Invalid square notation: $uci" }

        val col = uci[0] - 'a'
        val rank = uci.substring(1).toInt()
        return Position(col, rank - 1)
    }

    /**
     * Converts a Move to engine move notation.
     * @return e.g. "a1b1", "e2e3", "a10b10"
     */
    fun moveToUci(move: Move): String {
        return positionToUci(move.from) + positionToUci(move.to)
    }

    /**
     * Splits a move string into its two squares.
     *
     * 랭크 10 때문에 각 칸이 2자 또는 3자라, 이동 문자열 길이는 4~6자로 달라집니다.
     * 고정 위치로 자르면 10번째 행이 섞인 순간 어긋나므로 정규식으로 나눕니다.
     */
    fun splitUciMove(uciMove: String): Pair<String, String> {
        val match = MOVE.matchEntire(uciMove)
            ?: throw IllegalArgumentException("Invalid move notation: $uciMove")
        return match.groupValues[1] to match.groupValues[2]
    }

    /**
     * Converts engine move notation to a Move object.
     * @param gameState 도착 칸의 기물(잡히는 기물)을 알아내는 데 씁니다
     */
    fun uciToMove(uciMove: String, gameState: GameState): Move {
        val (fromUci, toUci) = splitUciMove(uciMove)
        val from = uciToPosition(fromUci)
        val to = uciToPosition(toUci)
        return Move(from, to, gameState.getPieceAt(to), gameState.getPieceAt(from))
    }

    /**
     * Validates square notation (format only).
     */
    fun isValidUci(uci: String): Boolean = SQUARE.matches(uci)

    /**
     * Validates move notation (format only, not game legality).
     */
    fun isValidUciMove(uciMove: String): Boolean = MOVE.matches(uciMove)
}
