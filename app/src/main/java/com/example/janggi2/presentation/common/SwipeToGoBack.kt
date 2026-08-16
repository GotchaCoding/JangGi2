package com.example.janggi2.presentation.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
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
 */
fun Modifier.swipeToGoBack(onNavigateBack: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(onNavigateBack) {
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
                    onNavigateBack()
                }
                totalDrag = 0f
            }
        )
    }
)
