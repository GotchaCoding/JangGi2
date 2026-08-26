package com.example.janggi2.presentation.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.repository.SavedReviewInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Individual saved AI review item in the list.
 */
@Composable
fun SavedReviewItem(
    reviewInfo: SavedReviewInfo,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = reviewInfo.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "${reviewInfo.moveCount} 수",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val dateString = dateFormat.format(Date(reviewInfo.savedDate))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                TextButton(onClick = onOpenClick) {
                    Text("열기")
                }

                TextButton(onClick = onDeleteClick) {
                    Text(
                        "삭제",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
