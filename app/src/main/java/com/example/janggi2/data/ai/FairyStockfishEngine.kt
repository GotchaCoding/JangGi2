package com.example.janggi2.data.ai

import android.util.Log
import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AiEngine using Fairy-Stockfish via JNI.
 *
 * Thread safety: native 호출은 Dispatchers.IO 에서 하고, 탐색 한 건 전체를
 * [mutex] 로 감쌉니다. Fairy-Stockfish 의 Threads / Search::Limits / Options 는
 * 프로세스 전역이라, 힌트와 AI 착수가 동시에 탐색하면 서로를 덮어씁니다.
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

    @Volatile
    private var isInitialized = false

    /** 엔진 전역 상태를 건드리는 구간을 직렬화합니다. */
    private val mutex = Mutex()

    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isInitialized) {
                    Log.w(TAG, "Engine already initialized")
                    return@withLock
                }

                Log.d(TAG, "Initializing Fairy-Stockfish engine")
                enginePtr = nativeInit()
                if (enginePtr == 0L) {
                    throw IllegalStateException("Failed to initialize Fairy-Stockfish engine")
                }

                isInitialized = true
                Log.d(TAG, "Engine initialized successfully (ptr: $enginePtr)")
            }
        }
    }

    override suspend fun getBestMove(
        gameState: GameState,
        thinkTimeMs: Int,
        skillLevel: Int
    ): Move? {
        require(skillLevel in 1..20) {
            "Skill level must be between 1 and 20, got: $skillLevel"
        }
        checkInitialized()

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    nativeSetDifficulty(enginePtr, skillLevel)

                    val position = uciProtocol.formatPosition(gameState)
                    Log.d(TAG, "Setting position: $position")
                    nativeSetPosition(enginePtr, position)

                    Log.d(TAG, "Searching (skill=$skillLevel, ${thinkTimeMs}ms)")
                    val uciMove = nativeGetBestMove(enginePtr, thinkTimeMs)
                    if (uciMove.isEmpty()) {
                        Log.w(TAG, "No best move returned from engine")
                        return@withLock null
                    }

                    val move = notationConverter.uciToMove(uciMove, gameState)
                    Log.d(TAG, "Best move: $uciMove -> from=${move.from}, to=${move.to}")
                    move
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating best move", e)
                    null
                }
            }
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
