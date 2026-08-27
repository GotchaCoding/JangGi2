package com.example.janggi2.presentation.puzzle

import com.example.janggi2.domain.model.Position

/**
 * Sealed class representing user interactions with the puzzle screen.
 */
sealed class PuzzleUiEvent {
    data class BoardTapped(val position: Position) : PuzzleUiEvent()
    data object NextPuzzle : PuzzleUiEvent()
    data object FlipBoard : PuzzleUiEvent()
    data object DismissError : PuzzleUiEvent()
}
