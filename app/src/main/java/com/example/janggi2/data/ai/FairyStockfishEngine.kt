package com.example.janggi2.data.ai

import android.util.Log
import com.example.janggi2.domain.ai.AiEngine
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.rules.RepetitionJudge
import com.example.janggi2.domain.rules.RepetitionOutcome
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
) : AiEngine, RepetitionJudge {

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

    /**
     * 장군 반복·수 반복 판정.
     *
     * 화면이 수를 둘 때마다 곧바로 답이 필요해 suspend 가 아닙니다. [mutex] 를 잡지 않는
     * 것도 그래서인데, 안전한 이유가 있습니다 - 이 경로는 네이티브에서 **지역** Position
     * 을 세워 답하고 버리므로 탐색이 쓰는 엔진 상태를 건드리지 않습니다.
     * 수를 재생하는 비용뿐이라 200수를 다 채워도 1ms 아래입니다.
     */
    override fun judge(gameState: GameState): RepetitionOutcome {
        // 초기화 전 몇 수는 판정이 꺼집니다. 반복은 최소 4반수 뒤에나 성립하므로
        // 실제 대국에서 놓치는 경우는 없습니다.
        if (!isInitialized) return RepetitionOutcome.NONE

        return try {
            when (val outcome = nativeGameOutcome(enginePtr, uciProtocol.formatHistory(gameState))) {
                "loss" -> RepetitionOutcome.SIDE_TO_MOVE_LOSES
                "win" -> RepetitionOutcome.SIDE_TO_MOVE_WINS
                "draw" -> RepetitionOutcome.DRAW
                "none" -> RepetitionOutcome.NONE
                else -> {
                    Log.w(TAG, "Unknown outcome from engine: $outcome")
                    RepetitionOutcome.NONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error judging repetition", e)
            RepetitionOutcome.NONE
        }
    }

    /**
     * 엔진이 보는 합법 수. **대조 테스트 전용**입니다 - 화면은 코틀린 규칙을 씁니다.
     *
     * @return UCI 표기 목록, 한 수 쉼(출발 == 도착)도 포함됩니다
     */
    fun legalMoves(gameState: GameState): List<String> {
        if (!isInitialized) return emptyList()
        return nativeLegalMoves(enginePtr, uciProtocol.formatPosition(gameState))
            .split(' ')
            .filter { it.isNotEmpty() }
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
    private external fun nativeGameOutcome(enginePtr: Long, uciPosition: String): String
    private external fun nativeLegalMoves(enginePtr: Long, uciPosition: String): String
}
