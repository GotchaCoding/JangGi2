package com.example.janggi2.presentation.navigation

import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Player

/**
 * Temporary holder for imported game state during navigation.
 * Used to pass GameState from Import screen to Game screen without serialization.
 */
object ImportStateHolder {
    var pendingImportedGameState: GameState? = null

    /** 사진에서 실제로 아래쪽에 있던 진영. 게임 화면을 사진과 같은 모습으로 그릴 때 씁니다. */
    var pendingImportedViewpoint: Player = Player.HAN
}
