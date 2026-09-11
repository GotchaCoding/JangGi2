package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * Use case for pulling the signed-in user's saved games from the cloud after login.
 */
class SyncGamesUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(): Int = repository.pullFromCloud()
}
