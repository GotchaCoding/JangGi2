package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val NAV_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
private val ACTION_BUTTON_PADDING = PaddingValues(horizontal = 10.dp, vertical = 0.dp)

/**
 * Replay mode control buttons.
 *
 * 아래 수 기록 목록이 화면에 보여야 해서, 내비게이션 버튼·위치 표시·이어하기/종료를
 * 두 줄에 압축해 담습니다 - 예전엔 세 줄이라 짧은 화면에서 수 기록이 다 밀려났습니다.
 */
@Composable
fun ReplayControls(
    currentPosition: Int,
    totalMoves: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    onFirstClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onLastClick: () -> Unit,
    onContinueClick: () -> Unit,
    onExitReplayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation controls + position indicator, one row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFirstClick,
                enabled = canPrevious,
                contentPadding = NAV_BUTTON_PADDING
            ) {
                Text("⏮")
            }
            Button(
                onClick = onPreviousClick,
                enabled = canPrevious,
                contentPadding = NAV_BUTTON_PADDING
            ) {
                Text("◀")
            }

            Text(
                text = "${currentPosition}/${totalMoves}수",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onNextClick,
                enabled = canNext,
                contentPadding = NAV_BUTTON_PADDING
            ) {
                Text("▶")
            }
            Button(
                onClick = onLastClick,
                enabled = canNext,
                contentPadding = NAV_BUTTON_PADDING
            ) {
                Text("⏭")
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = onContinueClick, contentPadding = ACTION_BUTTON_PADDING) {
                Text("검토", style = MaterialTheme.typography.labelMedium)
            }

            TextButton(onClick = onExitReplayClick, contentPadding = ACTION_BUTTON_PADDING) {
                Text("복기 종료", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReplayControlsPreview() {
    ReplayControls(
        currentPosition = 5,
        totalMoves = 10,
        canPrevious = true,
        canNext = true,
        onFirstClick = {},
        onPreviousClick = {},
        onNextClick = {},
        onLastClick = {},
        onContinueClick = {},
        onExitReplayClick = {}
    )
}
