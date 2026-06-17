package com.example.janggi2.domain.model

/**
 * Represents the two players in Janggi.
 * - CHO (Red/Blue): Starts at top (rows 0-4)
 * - HAN (Green/Red): Starts at bottom (rows 5-9)
 */
enum class Player {
    CHO,  // First player (top)
    HAN;  // Second player (bottom)

    /**
     * Returns the opponent of this player.
     */
    fun opponent(): Player = when (this) {
        CHO -> HAN
        HAN -> CHO
    }

    /**
     * Returns the display name for this player.
     */
    fun displayName(): String = when (this) {
        CHO -> "초"  // Cho in Korean
        HAN -> "한"  // Han in Korean
    }
}
