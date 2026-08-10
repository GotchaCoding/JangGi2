package com.example.janggi2.data.ai

import com.example.janggi2.domain.model.GameState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats GameState into UCI protocol commands for Fairy-Stockfish.
 *
 * UCI Protocol for position:
 * - "startpos" - initial position
 * - "startpos moves a0b0 c1d2 ..." - initial position with moves applied
 *
 * Example:
 * - Empty move history: "startpos"
 * - After 2 moves: "startpos moves a0b0 c9d9"
 */
@Singleton
class UciProtocol @Inject constructor(
    private val notationConverter: NotationConverter
) {

    /**
     * Formats the current game state as a UCI position command.
     *
     * @param gameState Current game state
     * @return UCI position command string
     *
     * Examples:
     * - "startpos" (initial position)
     * - "startpos moves a0b0 c9d9" (after moves)
     */
    fun formatPosition(gameState: GameState): String {
        // Start with initial position
        val builder = StringBuilder("startpos")

        // Add moves if any
        if (gameState.moveHistory.isNotEmpty()) {
            builder.append(" moves")

            gameState.moveHistory.forEach { move ->
                val uciMove = notationConverter.moveToUci(move)
                builder.append(" ").append(uciMove)
            }
        }

        return builder.toString()
    }

    /**
     * Formats a skill level setting command.
     *
     * @param level Skill level (1-20)
     * @return UCI setoption command
     */
    fun formatSkillLevelCommand(level: Int): String {
        val clampedLevel = level.coerceIn(1, 20)
        return "setoption name Skill Level value $clampedLevel"
    }

    /**
     * Formats a time limit for move calculation.
     *
     * @param timeMs Time in milliseconds
     * @return UCI go command with movetime
     */
    fun formatMoveTimeCommand(timeMs: Int): String {
        return "go movetime $timeMs"
    }

    /**
     * Formats variant selection command.
     *
     * @return UCI setoption command for Janggi variant
     */
    fun formatVariantCommand(): String {
        return "setoption name UCI_Variant value janggi"
    }
}
