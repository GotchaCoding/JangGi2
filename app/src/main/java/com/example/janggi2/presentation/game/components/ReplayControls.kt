package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * Replay mode control buttons.
 * Shows position indicator and navigation controls.
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Position indicator
        Text(
            text = "복기 모드: ${currentPosition}수 / ${totalMoves}수",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Navigation controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First button
            Button(
                onClick = onFirstClick,
                enabled = canPrevious
            ) {
                Text("⏮")
            }

            // Previous button
            Button(
                onClick = onPreviousClick,
                enabled = canPrevious
            ) {
                Text("◀")
            }

            // Next button
            Button(
                onClick = onNextClick,
                enabled = canNext
            ) {
                Text("▶")
            }

            // Last button
            Button(
                onClick = onLastClick,
                enabled = canNext
            ) {
                Text("⏭")
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = onContinueClick) {
                Text("여기서 계속")
            }

            TextButton(onClick = onExitReplayClick) {
                Text("복기 종료")
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
