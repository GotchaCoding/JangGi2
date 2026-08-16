package com.example.janggi2.presentation.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janggi2.presentation.common.swipeToGoBack

/**
 * Screen displaying all saved games.
 */
@Composable
fun SavedGamesScreen(
    onGameSelected: (Long, String) -> Unit,
    onGameSelectedForReplay: (Long, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SavedGamesViewModel = hiltViewModel()
) {
    val savedGames by viewModel.savedGames.collectAsState()

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

            if (savedGames.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "저장된 게임이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // List of saved games
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
    }
}
