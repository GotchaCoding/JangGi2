package com.example.janggi2.data.ai

import android.util.Log
import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AiEngine using Fairy-Stockfish via JNI.
 *
 * This class manages the native Fairy-Stockfish engine through JNI calls
 * and provides a Kotlin-friendly interface for AI move calculation.
 *
 * Thread Safety: All public methods use withContext(Dispatchers.IO) to ensure
 * native calls happen on background threads.
 */
@Singleton
class FairyStockfishEngine @Inject constructor(
    private val notationConverter: NotationConverter,
    private val uciProtocol: UciProtocol
) : AiEngine {

    companion object {
        private const val TAG = "FairyStockfishEngine"

        init {
            try {
                System.loadLibrary("fairystockfish_jni")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw IllegalStateException("Failed to load Fairy-Stockfish native library", e)
            }
        }
    }

    private var enginePtr: Long = 0L
    private var isInitialized = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing Fairy-Stockfish engine")

        if (isInitialized) {
            Log.w(TAG, "Engine already initialized")
            return@withContext
        }

        enginePtr = nativeInit()

        if (enginePtr == 0L) {
            throw IllegalStateException("Failed to initialize Fairy-Stockfish engine")
        }

        isInitialized = true
        Log.d(TAG, "Engine initialized successfully (ptr: $enginePtr)")
    }

    override suspend fun setDifficulty(difficulty: Int) = withContext(Dispatchers.IO) {
        require(difficulty in 1..20) {
            "Difficulty must be between 1 and 20, got: $difficulty"
        }

        checkInitialized()
        Log.d(TAG, "Setting difficulty to $difficulty")

        nativeSetDifficulty(enginePtr, difficulty)
    }

    override suspend fun getBestMove(
        gameState: GameState,
        thinkTimeMs: Int
    ): Move? = withContext(Dispatchers.IO) {
        checkInitialized()

        try {
            // Convert game state to UCI position command
            val uciPosition = uciProtocol.formatPosition(gameState)
            Log.d(TAG, "Setting position: $uciPosition")

            nativeSetPosition(enginePtr, uciPosition)

            // Calculate best move
            Log.d(TAG, "Calculating best move (think time: ${thinkTimeMs}ms)")
            val uciMove = nativeGetBestMove(enginePtr, thinkTimeMs)

            if (uciMove.isEmpty()) {
                Log.w(TAG, "No best move returned from engine")
                return@withContext null
            }

            Log.d(TAG, "Best move: $uciMove")

            // Convert UCI move to Move object
            val move = notationConverter.uciToMove(uciMove, gameState)
            Log.d(TAG, "Converted move: from=${move.from}, to=${move.to}")

            move

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating best move", e)
            null
        }
    }

    override fun destroy() {
        if (!isInitialized) {
            Log.w(TAG, "Attempted to destroy non-initialized engine")
            return
        }

        Log.d(TAG, "Destroying engine (ptr: $enginePtr)")
        nativeDestroy(enginePtr)
        enginePtr = 0L
        isInitialized = false
        Log.d(TAG, "Engine destroyed")
    }

    override fun isReady(): Boolean = isInitialized

    private fun checkInitialized() {
        check(isInitialized) {
            "Engine not initialized. Call initialize() first."
        }
    }

    // Native methods - implemented in jni_bridge.cpp
    private external fun nativeInit(): Long
    private external fun nativeDestroy(enginePtr: Long)
    private external fun nativeSetDifficulty(enginePtr: Long, level: Int)
    private external fun nativeSetPosition(enginePtr: Long, uciPosition: String)
    private external fun nativeGetBestMove(enginePtr: Long, thinkTimeMs: Int): String
}
