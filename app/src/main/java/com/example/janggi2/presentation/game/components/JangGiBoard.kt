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
 * 원본은 1480×1640 이고 격자는 왼쪽 위 (100.5, 100.5) 에서 시작해 칸이 160.1×160.1 인
 * 정사각형입니다. 판 그림을 교체하면 이 값들을 다시 재야 기물이 교차점에 놓입니다.
 */
object BoardArtwork {
    /** 그림의 가로 ÷ 세로 */
    const val ASPECT = 1480f / 1640f

    /** 왼쪽 끝 세로선까지의 거리 ÷ 그림 너비 */
    const val GRID_LEFT = 100.5f / 1480f

    /** 위쪽 끝 가로선까지의 거리 ÷ 그림 높이 */
    const val GRID_TOP = 100.5f / 1640f

    /**
     * 칸 하나의 크기 ÷ 그림 너비.
     *
     * 칸이 정사각형이고 그림 비율을 지켜 그리므로 세로 칸도 같은 값이 나옵니다
     * (160.1/1480 = 0.1082, 160.1/1640 × 1640/1480 = 0.1082).
     */
    const val CELL = 160.115f / 1480f
}

@Preview(showBackground = true)
@Composable
private fun JangGiBoardPreview() {
    JangGiBoard(modifier = Modifier.aspectRatio(BoardArtwork.ASPECT))
}
