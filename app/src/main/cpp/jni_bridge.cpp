#include "jni_bridge.h"
#include "uci_engine.h"
#include <android/log.h>
#include <string>

#define LOG_TAG "JNI_Bridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Helper function to convert jlong to UciEngine pointer
 */
static inline UciEngine* toEngine(jlong ptr) {
    return reinterpret_cast<UciEngine*>(ptr);
}

/**
 * Helper function to convert jstring to std::string
 */
static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";

    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeInit(
    JNIEnv* env, jobject thiz) {

    LOGD("nativeInit called");

    try {
        UciEngine* engine = new UciEngine();

        if (!engine->initialize()) {
            LOGE("Failed to initialize engine");
            delete engine;
            return 0;
        }

        LOGD("Engine initialized successfully: %p", engine);
        return reinterpret_cast<jlong>(engine);

    } catch (const std::exception& e) {
        LOGE("Exception in nativeInit: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("Unknown exception in nativeInit");
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong enginePtr) {

    LOGD("nativeDestroy called for engine: %p", (void*)enginePtr);

    if (enginePtr == 0) {
        LOGE("Attempted to destroy null engine pointer");
        return;
    }

    try {
        UciEngine* engine = toEngine(enginePtr);
        delete engine;
        LOGD("Engine destroyed successfully");

    } catch (const std::exception& e) {
        LOGE("Exception in nativeDestroy: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception in nativeDestroy");
    }
}

JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeSetDifficulty(
    JNIEnv* env, jobject thiz, jlong enginePtr, jint level) {

    LOGD("nativeSetDifficulty called: level=%d", level);

    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeSetDifficulty");
        return;
    }

    try {
        UciEngine* engine = toEngine(enginePtr);
        engine->setSkillLevel(static_cast<int>(level));

    } catch (const std::exception& e) {
        LOGE("Exception in nativeSetDifficulty: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception in nativeSetDifficulty");
    }
}

JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeSetPosition(
    JNIEnv* env, jobject thiz, jlong enginePtr, jstring uciPosition) {

    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeSetPosition");
        return;
    }

    try {
        std::string positionStr = jstringToString(env, uciPosition);
        LOGD("nativeSetPosition called: %s", positionStr.c_str());

        UciEngine* engine = toEngine(enginePtr);
        if (!engine->setPosition(positionStr)) {
            LOGE("Failed to set position: %s", positionStr.c_str());
        }

    } catch (const std::exception& e) {
        LOGE("Exception in nativeSetPosition: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception in nativeSetPosition");
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeGetBestMove(
    JNIEnv* env, jobject thiz, jlong enginePtr, jint thinkTimeMs) {

    LOGD("nativeGetBestMove called: thinkTime=%dms", thinkTimeMs);

    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeGetBestMove");
        return env->NewStringUTF("");
    }

    try {
        UciEngine* engine = toEngine(enginePtr);
        std::string bestMove = engine->getBestMove(static_cast<int>(thinkTimeMs));

        LOGD("Best move calculated: %s", bestMove.c_str());
        return env->NewStringUTF(bestMove.c_str());

    } catch (const std::exception& e) {
        LOGE("Exception in nativeGetBestMove: %s", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        LOGE("Unknown exception in nativeGetBestMove");
        return env->NewStringUTF("");
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeGetBestMoveWithScore(
    JNIEnv* env, jobject thiz, jlong enginePtr, jint thinkTimeMs) {

    LOGD("nativeGetBestMoveWithScore called: thinkTime=%dms", thinkTimeMs);

    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeGetBestMoveWithScore");
        return env->NewStringUTF("");
    }

    try {
        UciEngine* engine = toEngine(enginePtr);
        std::string result = engine->getBestMoveWithScore(static_cast<int>(thinkTimeMs));

        LOGD("Best move with score: %s", result.c_str());
        return env->NewStringUTF(result.c_str());

    } catch (const std::exception& e) {
        LOGE("Exception in nativeGetBestMoveWithScore: %s", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        LOGE("Unknown exception in nativeGetBestMoveWithScore");
        return env->NewStringUTF("");
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeGameOutcome(
    JNIEnv* env, jobject thiz, jlong enginePtr, jstring uciPosition) {

    // 매 수마다 불리므로 성공 로그는 남기지 않습니다.
    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeGameOutcome");
        return env->NewStringUTF("none");
    }

    try {
        std::string positionStr = jstringToString(env, uciPosition);
        UciEngine* engine = toEngine(enginePtr);
        return env->NewStringUTF(engine->gameOutcome(positionStr).c_str());

    } catch (const std::exception& e) {
        LOGE("Exception in nativeGameOutcome: %s", e.what());
        return env->NewStringUTF("none");
    } catch (...) {
        LOGE("Unknown exception in nativeGameOutcome");
        return env->NewStringUTF("none");
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeLegalMoves(
    JNIEnv* env, jobject thiz, jlong enginePtr, jstring uciPosition) {

    if (enginePtr == 0) {
        LOGE("Null engine pointer in nativeLegalMoves");
        return env->NewStringUTF("");
    }

    try {
        std::string positionStr = jstringToString(env, uciPosition);
        UciEngine* engine = toEngine(enginePtr);
        return env->NewStringUTF(engine->legalMoves(positionStr).c_str());

    } catch (const std::exception& e) {
        LOGE("Exception in nativeLegalMoves: %s", e.what());
        return env->NewStringUTF("");
    } catch (...) {
        LOGE("Unknown exception in nativeLegalMoves");
        return env->NewStringUTF("");
    }
}
