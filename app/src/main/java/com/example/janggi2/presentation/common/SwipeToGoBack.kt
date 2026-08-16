package com.example.janggi2.presentation.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 이 정도는 옆으로 끌어야 뒤로가기로 칩니다 - 살짝 스친 것까지 반응하면 오작동이 잦습니다. */
private val SWIPE_BACK_THRESHOLD = 96.dp

/**
 * 화면을 좌우로 스와이프하면 뒤로갑니다. 3버튼 내비게이션처럼 제스처 뒤로가기가 없는
 * 기기에서도 화면 아무 곳이나 옆으로 밀어서 뒤로 갈 수 있게 합니다.
 *
 * 방향은 가리지 않습니다 - 왼쪽이든 오른쪽이든 [SWIPE_BACK_THRESHOLD] 만큼 끌면 뒤로갑니다.
 *
 * `pointerInput` 의 키를 `onNavigateBack` 람다 자체로 두면, 호출부가 매번 새 람다를
 * 넘길 때(대부분 인라인 람다라 그렇습니다) 제스처 인식이 스와이프 도중에도 재시작돼
 * 끌던 거리를 잃어버립니다. `rememberUpdatedState` 로 최신 람다만 갈아끼우고
 * `pointerInput` 은 한 번만 시작합니다.
 */
fun Modifier.swipeToGoBack(onNavigateBack: () -> Unit): Modifier = composed {
    val latestOnNavigateBack by rememberUpdatedState(onNavigateBack)

    this.pointerInput(Unit) {
        val thresholdPx = SWIPE_BACK_THRESHOLD.toPx()
        var totalDrag = 0f

        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDrag += dragAmount
            },
            onDragEnd = {
                if (abs(totalDrag) >= thresholdPx) {
                    latestOnNavigateBack()
                }
                totalDrag = 0f
            }
        )
    }
}
