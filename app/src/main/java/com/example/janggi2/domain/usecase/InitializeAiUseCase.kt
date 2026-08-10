package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.ai.AiEngine
import javax.inject.Inject

/**
 * Use case for initializing the AI engine.
 *
 * This should be called once when the app starts or when AI functionality
 * is first needed.
 *
 * Thread Safety: This is a suspend function that should be called from a coroutine.
 */
class InitializeAiUseCase @Inject constructor(
    private val aiEngine: AiEngine
) {
    /**
     * Initialize the AI engine.
     *
     * @throws IllegalStateException if initialization fails
     */
    suspend operator fun invoke() {
        if (!aiEngine.isReady()) {
            aiEngine.initialize()
        }
    }
}
