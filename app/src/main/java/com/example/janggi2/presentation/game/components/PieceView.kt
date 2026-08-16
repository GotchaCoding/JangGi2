package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.janggi2.R
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * 장기 알 하나.
 *
 * 실제 장기가 그렇듯 알 크기가 격에 따라 다릅니다 - 왕이 가장 크고 졸·병이 가장 작습니다.
 * 그림도 그 서열대로 그려져 있어서, 한 크기로 눌러 그리면 디자인이 망가집니다.
 * 그래서 [cellSize] 를 받아 종류별 비율을 곱합니다.
 *
 * @param cellSize 판의 칸 크기. 알 지름이 아니라 기준 길이입니다.
 */
@Composable
fun PieceView(
    piece: Piece,
    modifier: Modifier = Modifier,
    cellSize: Dp = 40.dp
) {
    Image(
        painter = painterResource(drawableOf(piece)),
        contentDescription = piece.getDisplayChar(),
        modifier = modifier.size(cellSize * scaleOf(piece))
    )
}

/**
 * 알 그림이 칸 크기의 몇 배로 그려지는지.
 *
 * 원본 그림의 캔버스 크기 비율 그대로입니다(왕 268, 차·포 248, 마·상 232, 사 212,
 * 졸·병 200). 캔버스에는 그림자 여백이 7% 정도 들어 있어 눈에 보이는 알은 이 값보다
 * 조금 작습니다 - 왕이 칸의 0.94 배쯤 되어 이웃한 알과 붙지 않습니다.
 */
private fun scaleOf(piece: Piece): Float = when (piece) {
    is Piece.General -> 268f
    is Piece.Chariot, is Piece.Cannon -> 248f
    is Piece.Horse, is Piece.Elephant -> 232f
    is Piece.Guard -> 212f
    is Piece.Soldier -> 200f
} / 268f * KING_TO_CELL

/** 가장 큰 알(왕)의 캔버스를 칸 크기의 몇 배로 그릴지 */
private const val KING_TO_CELL = 1f

/** 기물 종류가 늘면 컴파일러가 잡도록 when 을 씁니다. */
private fun drawableOf(piece: Piece): Int = when (piece) {
    is Piece.General -> forPlayer(piece.player, R.drawable.piece_cho_wang, R.drawable.piece_han_wang)
    is Piece.Guard -> forPlayer(piece.player, R.drawable.piece_cho_sa, R.drawable.piece_han_sa)
    is Piece.Elephant -> forPlayer(piece.player, R.drawable.piece_cho_sang, R.drawable.piece_han_sang)
    is Piece.Horse -> forPlayer(piece.player, R.drawable.piece_cho_ma, R.drawable.piece_han_ma)
    is Piece.Chariot -> forPlayer(piece.player, R.drawable.piece_cho_cha, R.drawable.piece_han_cha)
    is Piece.Cannon -> forPlayer(piece.player, R.drawable.piece_cho_po, R.drawable.piece_han_po)
    // 초는 졸(卒), 한은 병(兵) 으로 글자가 다릅니다.
    is Piece.Soldier -> forPlayer(piece.player, R.drawable.piece_cho_jol, R.drawable.piece_han_byeong)
}

private fun forPlayer(player: Player, cho: Int, han: Int) =
    if (player == Player.CHO) cho else han

@Preview(showBackground = true)
@Composable
private fun PieceViewAllPreview() {
    val pos = Position(0, 0)
    Box {
        androidx.compose.foundation.layout.Column {
            for (player in Player.entries) {
                androidx.compose.foundation.layout.Row {
                    listOf(
                        Piece.General(player, pos),
                        Piece.Chariot(player, pos),
                        Piece.Cannon(player, pos),
                        Piece.Horse(player, pos),
                        Piece.Elephant(player, pos),
                        Piece.Guard(player, pos),
                        Piece.Soldier(player, pos)
                    ).forEach { PieceView(piece = it, cellSize = 44.dp) }
                }
            }
        }
    }
}
