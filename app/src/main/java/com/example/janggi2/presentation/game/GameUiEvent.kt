package com.example.janggi2.presentation.game

import com.example.janggi2.domain.model.GameMode
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Sealed class representing user interactions with the game board.
 */
sealed class GameUiEvent {
    /**
     * User tapped on a position on the board.
     */
    data class BoardTapped(val position: Position) : GameUiEvent()

    /**
     * User requested to reset the game.
     */
    data object ResetGame : GameUiEvent()

    /**
     * User dismissed the game over dialog.
     */
    data object DismissGameOverDialog : GameUiEvent()

    /**
     * User requested to undo the last move.
     */
    data object Undo : GameUiEvent()

    /**
     * User requested to redo a previously undone move.
     */
    data object Redo : GameUiEvent()

    /**
     * User requested to save the game with a custom name.
     */
    data class SaveGame(val name: String) : GameUiEvent()

    /**
     * User requested to enter replay mode.
     */
    data object EnterReplayMode : GameUiEvent()

    /**
     * User requested to exit replay mode.
     */
    data object ExitReplayMode : GameUiEvent()

    /**
     * User requested to navigate to the first position.
     */
    data object ReplayFirst : GameUiEvent()

    /**
     * User requested to navigate to the previous position.
     */
    data object ReplayPrevious : GameUiEvent()

    /**
     * User requested to navigate to the next position.
     */
    data object ReplayNext : GameUiEvent()

    /**
     * User requested to navigate to the last position.
     */
    data object ReplayLast : GameUiEvent()

    /**
     * User requested to continue playing from current replay position.
     */
    data object ContinueFromReplay : GameUiEvent()

    /**
     * Request AI to make a move.
     */
    data object RequestAiMove : GameUiEvent()

    /**
     * Set AI difficulty level.
     */
    data class SetAiDifficulty(val difficulty: Int) : GameUiEvent()

    /**
     * Show new game dialog.
     */
    data object ShowNewGameDialog : GameUiEvent()

    /**
     * Dismiss new game dialog.
     */
    data object DismissNewGameDialog : GameUiEvent()

    /**
     * Start new game with specified settings.
     */
    data class StartNewGame(
        val gameMode: GameMode,
        val aiDifficulty: Int = 10,
        val aiPlayer: Player = Player.HAN
    ) : GameUiEvent()

    /**
     * Show AI settings dialog (for mid-game difficulty adjustment).
     */
    data object ShowAiSettingsDialog : GameUiEvent()

    /**
     * Dismiss AI settings dialog.
     */
    data object DismissAiSettingsDialog : GameUiEvent()

    /**
     * User asked the engine to suggest a move.
     */
    data object RequestHint : GameUiEvent()

    /**
     * User dismissed the hint error message.
     */
    data object DismissHintError : GameUiEvent()
}
