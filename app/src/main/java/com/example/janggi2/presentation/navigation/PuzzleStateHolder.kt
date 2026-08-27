package com.example.janggi2.presentation.navigation

import com.example.janggi2.domain.model.GameReview
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Player

/**
 * Temporary holder for the game state/review passed from Game screen to the puzzle screen
 * without serialization. Mirrors [ImportStateHolder].
 */
object PuzzleStateHolder {
    var pendingGameState: GameState? = null
    var pendingGameReview: GameReview? = null
    var pendingViewpoint: Player = Player.HAN
}
