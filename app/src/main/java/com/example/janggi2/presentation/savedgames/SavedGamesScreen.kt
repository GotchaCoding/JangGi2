package com.example.janggi2.presentation.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janggi2.presentation.common.swipeToGoBack

/** 상단 탭 - 저장된 기보 목록과 AI 리뷰 목록은 서로 독립적으로 저장되므로 따로 봅니다. */
private enum class SavedGamesTab {
    GAMES, REVIEWS
}

/**
 * Screen displaying all saved games and saved AI reviews.
 */
@Composable
fun SavedGamesScreen(
    onGameSelected: (Long, String) -> Unit,
    onGameSelectedForReplay: (Long, String) -> Unit,
    onReviewSelected: (Long, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SavedGamesViewModel = hiltViewModel()
) {
    val savedGames by viewModel.savedGames.collectAsState()
    val savedReviews by viewModel.savedReviews.collectAsState()
    var selectedTab by remember { mutableStateOf(SavedGamesTab.GAMES) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .swipeToGoBack(onNavigateBack)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Title
            Text(
                text = "저장된 게임",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == SavedGamesTab.GAMES,
                    onClick = { selectedTab = SavedGamesTab.GAMES },
                    label = { Text("기보") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == SavedGamesTab.REVIEWS,
                    onClick = { selectedTab = SavedGamesTab.REVIEWS },
                    label = { Text("AI 리뷰") },
                    modifier = Modifier.weight(1f)
                )
            }

            when (selectedTab) {
                SavedGamesTab.GAMES -> {
                    if (savedGames.isEmpty()) {
                        EmptyState("저장된 게임이 없습니다")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedGames, key = { it.id }) { gameInfo ->
                                SavedGameItem(
                                    gameInfo = gameInfo,
                                    onLoadClick = { onGameSelected(gameInfo.id, gameInfo.name) },
                                    onReplayClick = { onGameSelectedForReplay(gameInfo.id, gameInfo.name) },
                                    onDeleteClick = { viewModel.deleteGame(gameInfo.id) }
                                )
                            }
                        }
                    }
                }

                SavedGamesTab.REVIEWS -> {
                    if (savedReviews.isEmpty()) {
                        EmptyState("저장된 AI 리뷰가 없습니다")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedReviews, key = { it.id }) { reviewInfo ->
                                SavedReviewItem(
                                    reviewInfo = reviewInfo,
                                    onOpenClick = { onReviewSelected(reviewInfo.id, reviewInfo.name) },
                                    onDeleteClick = { viewModel.deleteReview(reviewInfo.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
