package com.example.janggi2.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.presentation.auth.AccountMenuViewModel
import com.example.janggi2.presentation.auth.DeleteAccountFlow
import com.example.janggi2.presentation.auth.LoginScreen
import com.example.janggi2.presentation.auth.LoginViewModel
import com.example.janggi2.presentation.debug.LineDetectionDebugScreen
import com.example.janggi2.presentation.game.GameScreen
import com.example.janggi2.presentation.game.GameViewModel
import com.example.janggi2.presentation.importboard.ImportScreen
import com.example.janggi2.presentation.puzzle.PuzzleScreen
import com.example.janggi2.presentation.puzzle.PuzzleViewModel
import com.example.janggi2.presentation.savedgames.SavedGamesScreen
import com.example.janggi2.presentation.videoimport.VideoImportScreen

/**
 * Navigation host for the app.
 */
@Composable
fun JangGiNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Game.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.signedInUser) {
                if (uiState.signedInUser != null) {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }

            LoginScreen(
                uiState = uiState,
                onGoogleIdTokenReceived = viewModel::onGoogleIdTokenReceived,
                onSignInFailed = viewModel::onSignInFailed,
                onDismissError = viewModel::dismissError
            )
        }

        composable(Screen.Game.route) { backStackEntry ->
            val viewModel: GameViewModel = hiltViewModel()
            val accountMenuViewModel: AccountMenuViewModel = hiltViewModel()
            val currentUser by accountMenuViewModel.currentUser.collectAsState()
            val accountUiState by accountMenuViewModel.uiState.collectAsState()
            var showDeleteAccountConfirm by remember { mutableStateOf(false) }

            // 로그아웃과 달리 계정 삭제는 되돌릴 수 없으므로, 끝난 뒤에도 같은 자리
            // (로그인 화면)로 보내되 백스택을 통째로 비웁니다.
            LaunchedEffect(accountUiState.isDeleted) {
                if (accountUiState.isDeleted) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }

            DeleteAccountFlow(
                showConfirm = showDeleteAccountConfirm,
                uiState = accountUiState,
                onConfirmDismiss = { showDeleteAccountConfirm = false },
                onReAuthTokenReceived = accountMenuViewModel::deleteAccount,
                onReAuthFailed = accountMenuViewModel::onDeleteFailed,
                onDismissError = accountMenuViewModel::dismissDeleteError
            )

            // Handle game loading from savedStateHandle
            val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
            val loadGameId = savedStateHandle?.get<Long>("loadGameId")
            val loadGameName = savedStateHandle?.get<String>("loadGameName") ?: ""
            val loadForReplay = savedStateHandle?.get<Boolean>("loadForReplay") ?: false
            val loadReviewId = savedStateHandle?.get<Long>("loadReviewId")
            val loadReviewName = savedStateHandle?.get<String>("loadReviewName") ?: ""
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

            LaunchedEffect(loadReviewId) {
                if (loadReviewId != null) {
                    viewModel.loadReviewForReplay(loadReviewId, loadReviewName)
                    savedStateHandle?.remove<Long>("loadReviewId")
                    savedStateHandle?.remove<String>("loadReviewName")
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
                },
                onNavigateToPuzzle = { gameState, review, viewpoint ->
                    PuzzleStateHolder.pendingGameState = gameState
                    PuzzleStateHolder.pendingGameReview = review
                    PuzzleStateHolder.pendingViewpoint = viewpoint
                    navController.navigate(Screen.Puzzle.route)
                },
                currentUserEmail = currentUser?.email,
                onDeleteAccountClick = { showDeleteAccountConfirm = true },
                onSignOut = {
                    accountMenuViewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
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
                onReviewSelected = { reviewId, name ->
                    // Load the saved review for replay and navigate back
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadReviewId", reviewId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("loadReviewName", name)
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

        composable(Screen.Puzzle.route) { backStackEntry ->
            val viewModel: PuzzleViewModel = hiltViewModel()

            LaunchedEffect(backStackEntry) {
                val gameState = PuzzleStateHolder.pendingGameState
                val review = PuzzleStateHolder.pendingGameReview
                if (gameState != null && review != null) {
                    viewModel.loadPuzzles(gameState, review, PuzzleStateHolder.pendingViewpoint)
                    PuzzleStateHolder.pendingGameState = null
                    PuzzleStateHolder.pendingGameReview = null
                }
            }

            PuzzleScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
