package com.example.janggi2.presentation.navigation

import com.example.janggi2.domain.model.GameState

/**
 * Temporary holder for imported game state during navigation.
 * Used to pass GameState from Import screen to Game screen without serialization.
 */
object ImportStateHolder {
    var pendingImportedGameState: GameState? = null
}
