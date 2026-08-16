package com.example.janggi2.presentation.game

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.initialGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * 판을 돌려 봐도 누른 자리가 그대로 잡히는지.
 *
 * 여기서 걸러내려는 버그가 실제로 두 번 났습니다. `pointerInput` 블록은 키가 바뀔 때만
 * 다시 만들어지고 그 전까지 처음 잡은 값을 계속 쓰는데, 키에 넣는 걸 빠뜨리면 화면은
 * 새 값으로 그려지고 탭만 옛 값으로 해석됩니다. 진영을 바꿨을 때 한 기물을 눌렀는데
 * 초 기물이 잡히던 게 그 증상이었습니다.
 *
 * 교차점 픽셀을 계산해 맞히는 대신, **같은 자리를 두 번 눌러** 뒤집기 전후의 결과가
 * 서로 180도 돌린 값인지만 봅니다. 판 크기 계산이 바뀌어도 이 테스트는 그대로 유효합니다.
 */
class BoardOrientationTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * 판 가운데를 피한 자리. 가운데는 180도 돌려도 제자리라 아무것도 검증하지 못합니다.
     */
    private val offCentre = Offset(0.28f, 0.2f)

    @Test
    fun tapsFollowTheBoardWhenItIsFlipped() {
        var flipped by mutableStateOf(false)
        var tapped: Position? = null

        compose.setContent {
            BoardWithPieces(
                pieces = initialGameState().board,
                selectedPiece = null,
                validMoves = emptyList(),
                onBoardTap = { tapped = it },
                flipped = flipped,
                modifier = Modifier.size(360.dp, 420.dp)
            )
        }

        compose.onRoot().performTouchInput {
            click(Offset(width * offCentre.x, height * offCentre.y))
        }
        compose.waitForIdle()
        val before = tapped
        assertNotNull("판 위를 눌렀는데 아무 자리도 잡히지 않았습니다", before)

        // 첫 그리기 이후에 바뀌는 것이 핵심입니다 - 처음부터 뒤집혀 있었다면
        // 값을 처음 잡을 때 이미 맞아서 이 버그가 드러나지 않습니다.
        tapped = null
        flipped = true
        compose.waitForIdle()

        compose.onRoot().performTouchInput {
            click(Offset(width * offCentre.x, height * offCentre.y))
        }
        compose.waitForIdle()
        val after = tapped
        assertNotNull("뒤집은 뒤 같은 자리를 눌렀는데 아무 자리도 잡히지 않았습니다", after)

        assertEquals(
            "뒤집었으면 같은 자리가 180도 돌린 칸으로 잡혀야 합니다",
            Position(8 - before!!.col, 9 - before.row),
            after
        )
    }

    @Test
    fun tapsAreUnchangedWhileTheBoardStaysPut() {
        // 위 테스트가 "무엇을 눌러도 늘 뒤집힌다" 로도 통과하지 않도록 반대쪽을 못 박습니다.
        var tapped: Position? = null

        compose.setContent {
            BoardWithPieces(
                pieces = initialGameState().board,
                selectedPiece = null,
                validMoves = emptyList(),
                onBoardTap = { tapped = it },
                flipped = false,
                modifier = Modifier.size(360.dp, 420.dp)
            )
        }

        compose.onRoot().performTouchInput {
            click(Offset(width * offCentre.x, height * offCentre.y))
        }
        compose.waitForIdle()
        val first = tapped
        assertNotNull(first)

        tapped = null
        compose.onRoot().performTouchInput {
            click(Offset(width * offCentre.x, height * offCentre.y))
        }
        compose.waitForIdle()

        assertEquals("같은 자리는 늘 같은 칸이어야 합니다", first, tapped)
    }
}
