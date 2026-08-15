package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a Janggi FEN string from a [GameState].
 *
 * FEN을 쓰는 이유는 `startpos moves ...`로는 이 앱의 판을 표현할 수 없기 때문입니다.
 * 마·상 배치는 대국자가 고르는 것이라 엔진의 고정 startFen(`rnba1abnr/...`)과 다를 수
 * 있고, 사진에서 불러온 판은 수순 기록 자체가 없습니다.
 *
 * 규칙(Fairy-Stockfish `variant.cpp`의 janggi 정의 기준):
 * - 첫 줄이 랭크 10 = 앱의 row 9, 마지막 줄이 랭크 1 = 앱의 row 0
 * - 대문자가 white = 선수 = 초(CHO)
 * - k왕 a사 n마 b상 r차 c포 p졸
 */
@Singleton
class FenConverter @Inject constructor() {

    companion object {
        private const val COLS = 9
        private const val ROWS = 10
    }

    /**
     * @return e.g. "rbna1anbr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RBNA1ANBR w - - 0 1"
     */
    fun toFen(gameState: GameState): String = toFen(
        board = gameState.board,
        sideToMove = gameState.currentPlayer,
        fullMove = gameState.moveHistory.size / 2 + 1
    )

    /**
     * 판만 가지고 FEN 을 만듭니다. 대국이 시작된 국면처럼 [GameState] 가 없는 판에 씁니다.
     */
    fun toFen(board: Map<Position, Piece>, sideToMove: Player, fullMove: Int): String {
        val placement = (ROWS - 1 downTo 0).joinToString("/") { row -> fenRow(board, row) }
        val side = if (sideToMove == Player.CHO) "w" else "b"

        // 캐슬링·앙파상 자리는 장기에서 의미가 없지만, Position::set 이 필드 개수를
        // 기대하므로 엔진의 startFen 과 같은 모양으로 채워 보냅니다.
        return "$placement $side - - 0 $fullMove"
    }

    private fun fenRow(board: Map<Position, Piece>, row: Int): String {
        val sb = StringBuilder()
        var empty = 0

        for (col in 0 until COLS) {
            val piece = board[Position(col, row)]

            if (piece == null) {
                empty++
            } else {
                if (empty > 0) {
                    sb.append(empty)
                    empty = 0
                }
                sb.append(pieceChar(piece))
            }
        }
        if (empty > 0) sb.append(empty)

        return sb.toString()
    }

    private fun pieceChar(piece: Piece): Char {
        val letter = when (piece) {
            is Piece.General -> 'k'
            is Piece.Guard -> 'a'
            is Piece.Horse -> 'n'
            is Piece.Elephant -> 'b'
            is Piece.Chariot -> 'r'
            is Piece.Cannon -> 'c'
            is Piece.Soldier -> 'p'
        }
        return if (piece.player == Player.CHO) letter.uppercaseChar() else letter
    }
}
