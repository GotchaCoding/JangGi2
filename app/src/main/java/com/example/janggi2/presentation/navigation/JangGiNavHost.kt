package com.example.janggi2.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.presentation.debug.LineDetectionDebugScreen
import com.example.janggi2.presentation.game.GameScreen
import com.example.janggi2.presentation.game.GameViewModel
import com.example.janggi2.presentation.importboard.ImportScreen
import com.example.janggi2.presentation.savedgames.SavedGamesScreen
import com.example.janggi2.presentation.videoimport.VideoImportScreen

/**
 * Navigation host for the app.
 */
@Composable
fun JangGiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Game.route,
        modifier = modifier
    ) {
        composable(Screen.Game.route) { backStackEntry ->
            val viewModel: GameViewModel = hiltViewModel()

            // Handle game loading from savedStateHandle
            val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
            val loadGameId = savedStateHandle?.get<Long>("loadGameId")
            val loadGameName = savedStateHandle?.get<String>("loadGameName") ?: ""
            val loadForReplay = savedStateHandle?.get<Boolean>("loadForReplay") ?: false
            val importedGameState = savedStateHandle?.get<GameState>("importedGameState")

            LaunchedEffect(loadGameId) {
                if (loadGameId != null) {
                    if (loadForReplay) {
                        viewModel.loadGameForReplay(loadGameId, loadGameName)
                    } else {
                        viewModel.loadGame(loadGameId, loadGameName)
                    }
                    // Clear savedStateHandle
                    savedStateHandle?.remove<Long>("loadGameId")
                    savedStateHandle?.remove<String>("loadGameName")
                    savedStateHandle?.remove<Boolean>("loadForReplay")
                }
            }

            LaunchedEffect(importedGameState) {
                if (importedGameState != null) {
                    viewModel.loadImportedGame(importedGameState)
                    savedStateHandle?.remove<GameState>("importedGameState")
                }
            }

            // Check for imported game state from Import screen
            LaunchedEffect(backStackEntry) {
                ImportStateHolder.pendingImportedGameState?.let { gameState ->
                    viewModel.loadImportedGame(gameState, ImportStateHolder.pendingImportedViewpoint)
                    ImportStateHolder.pendingImportedGameState = null
                }
            }

            GameScreen(
                viewModel = viewModel,
                onNavigateToSavedGames = {
                    navController.navigate(Screen.SavedGames.route)
                },
                onNavigateToImport = {
                    navController.navigate(Screen.Import.route)
                },
                onNavigateToVideoImport = {
                    navController.navigate(Screen.VideoImport.route)
                },
                onNavigateToDebug = {
                    navController.navigate(Screen.LineDetectionDebug.route)
                }
            )
        }

        composable(Screen.SavedGames.route) {
            SavedGamesScreen(
                onGameSelected = { gameId, name ->
                    // Load the game normally and navigate back
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadGameId", gameId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadGameName", name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadForReplay", false)
                    navController.popBackStack()
                },
                onGameSelectedForReplay = { gameId, name ->
                    // Load the game for replay and navigate back
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadGameId", gameId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadGameName", name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadForReplay", true)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onImportComplete = { gameState, viewpoint ->
                    // Store in temporary holder and navigate back
                    ImportStateHolder.pendingImportedGameState = gameState
                    ImportStateHolder.pendingImportedViewpoint = viewpoint
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.VideoImport.route) {
            VideoImportScreen(
                onImportComplete = { gameState, viewpoint ->
                    // 사진 불러오기와 같은 방식으로 넘깁니다 - 둘 다 결국 "판+viewpoint" 하나입니다.
                    ImportStateHolder.pendingImportedGameState = gameState
                    ImportStateHolder.pendingImportedViewpoint = viewpoint
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.LineDetectionDebug.route) {
            LineDetectionDebugScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
