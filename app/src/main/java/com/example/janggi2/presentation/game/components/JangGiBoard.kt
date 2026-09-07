package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.janggi2.R

/**
 * 장기판 그림.
 *
 * 격자·궁성 대각선·나뭇결이 모두 그림에 들어 있어 따로 그리지 않습니다. 대신 기물을
 * 얹으려면 그림 안에서 격자가 정확히 어디인지 알아야 하는데, 그 값이 [BoardArtwork]
 * 입니다. 그림을 바꾸면 그 상수도 함께 재야 합니다.
 */
@Composable
fun JangGiBoard(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.board),
        contentDescription = null,
        // 격자 좌표가 그림 비율에 매여 있으므로 늘이거나 잘라내면 안 됩니다.
        contentScale = ContentScale.FillBounds,
        modifier = modifier.fillMaxSize()
    )
}

/**
 * `res/drawable-nodpi/board.png` 안에서 격자가 놓인 자리. 모두 그림 크기에 대한 비율입니다.
 *
 * 원본은 2126×2139 이고 격자는 왼쪽 위 (135.5, 135.5) 에서 시작합니다. 칸은 가로
 * 231.75 × 세로 207.44 로 **정사각형이 아니라서** 가로·세로 비율을 따로 둡니다.
 * 판 그림을 교체하면 이 값들을 다시 재야 기물이 교차점에 놓입니다.
 */
object BoardArtwork {
    /** 그림의 가로 ÷ 세로 */
    const val ASPECT = 2126f / 2139f

    /** 왼쪽 끝 세로선까지의 거리 ÷ 그림 너비 */
    const val GRID_LEFT = 135.5f / 2126f

    /** 위쪽 끝 가로선까지의 거리 ÷ 그림 높이 */
    const val GRID_TOP = 135.5f / 2139f

    /** 칸 하나의 가로 크기 ÷ 그림 너비 - 세로선 8칸(135.5~1989.5)을 나눈 값입니다. */
    const val CELL_WIDTH = (1989.5f - 135.5f) / 8f / 2126f

    /** 칸 하나의 세로 크기 ÷ 그림 높이 - 가로선 9칸(135.5~2002.5)을 나눈 값입니다. */
    const val CELL_HEIGHT = (2002.5f - 135.5f) / 9f / 2139f
}

@Preview(showBackground = true)
@Composable
private fun JangGiBoardPreview() {
    JangGiBoard(modifier = Modifier.aspectRatio(BoardArtwork.ASPECT))
}
