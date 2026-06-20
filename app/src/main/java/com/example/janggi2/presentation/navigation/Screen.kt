package com.example.janggi2.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object Game : Screen("game")
    data object SavedGames : Screen("saved_games")
    data object Import : Screen("import")
}
