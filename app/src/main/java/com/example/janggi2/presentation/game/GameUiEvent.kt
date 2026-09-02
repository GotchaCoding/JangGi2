package com.example.janggi2.presentation.game

import com.example.janggi2.domain.model.GameMode
import com.example.janggi2.domain.model.HorseElephantSetup
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.ReviewComment

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
     * User requested to save the game with a custom name and optional player info.
     */
    data class SaveGame(
        val name: String,
        val choPlayerName: String? = null,
        val hanPlayerName: String? = null,
        val choRank: String? = null,
        val hanRank: String? = null
    ) : GameUiEvent()

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
        /** 내가 잡을 진영. 판은 이쪽이 아래로 오게 돌아가고, AI 대국이면 AI 가 반대편입니다. */
        val myPlayer: Player = Player.HAN,
        val choSetup: HorseElephantSetup = HorseElephantSetup.defaultFor(Player.CHO),
        val hanSetup: HorseElephantSetup = HorseElephantSetup.defaultFor(Player.HAN)
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

    /**
     * 한 수 쉼 - 판을 그대로 두고 차례만 넘깁니다.
     */
    data object PassTurn : GameUiEvent()

    /**
     * 판을 위아래로 뒤집어 봅니다. 불러오기·복기처럼 viewpoint 가 실제 앉은 쪽과
     * 다르게 잡혔을 때 사용자가 직접 바로잡는 용도입니다.
     */
    data object FlipBoard : GameUiEvent()

    /**
     * 지금까지 둔 수를 AI로 리뷰합니다(최선수/좋음/부정확/실수/악수 판정).
     */
    data object RequestReview : GameUiEvent()

    /**
     * 진행 중인 AI 리뷰를 취소합니다.
     */
    data object CancelReview : GameUiEvent()

    /**
     * AI 리뷰 오류 메시지를 닫습니다.
     */
    data object DismissReviewError : GameUiEvent()

    /**
     * 수 기록에서 특정 수를 눌러 그 수를 둔 직후 국면으로 건너뜁니다.
     * @param moveIndex moveHistory 의 0부터 시작하는 인덱스
     */
    data class JumpToMove(val moveIndex: Int) : GameUiEvent()

    /**
     * 댓글 입력창의 내용이 바뀔 때.
     */
    data class CommentTextChanged(val text: String) : GameUiEvent()

    /**
     * "검토"로 갈라져 나가 둬 본 수순에, 입력해 둔 메시지로 댓글을 남깁니다.
     */
    data object SaveComment : GameUiEvent()

    /**
     * 댓글을 눌러, 그 댓글을 남길 때 봤던 수순을 그대로 봅니다.
     */
    data class OpenComment(val comment: ReviewComment) : GameUiEvent()

    /**
     * AI 리뷰를 "검토"로 갈라져 나가 수를 두는 중에 뒤로가기(제스처 포함)를 하면,
     * 그 검토를 눌렀던 지점(원래 리뷰의 그 수)으로 되돌아갑니다 - 갈라져 나가
     * 둬 본 수는 버려집니다(댓글로 저장했다면 그건 그대로 남아 있습니다).
     */
    data object BackToReviewFromBranch : GameUiEvent()
}
