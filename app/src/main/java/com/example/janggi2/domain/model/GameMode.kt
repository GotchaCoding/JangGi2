package com.example.janggi2.domain.model

/**
 * Represents the game mode - either player vs player or player vs AI.
 */
enum class GameMode {
    /** Traditional two-player local game */
    PLAYER_VS_PLAYER,

    /** Player plays against AI engine */
    PLAYER_VS_AI;

    /**
     * Returns true if this is an AI game.
     */
    fun isAiGame(): Boolean = this == PLAYER_VS_AI

    /**
     * Returns a human-readable name for the game mode.
     */
    fun displayName(): String = when (this) {
        PLAYER_VS_PLAYER -> "Player vs Player"
        PLAYER_VS_AI -> "Player vs AI"
    }
}
