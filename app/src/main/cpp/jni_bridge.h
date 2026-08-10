#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize Fairy-Stockfish engine
 * @return Engine pointer (as long)
 */
JNIEXPORT jlong JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeInit(
    JNIEnv* env, jobject thiz);

/**
 * Destroy engine and free resources
 * @param enginePtr Engine pointer returned from nativeInit
 */
JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong enginePtr);

/**
 * Set engine skill level (1-20)
 * @param enginePtr Engine pointer
 * @param level Skill level (1=beginner, 20=expert)
 */
JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeSetDifficulty(
    JNIEnv* env, jobject thiz, jlong enginePtr, jint level);

/**
 * Set board position using UCI notation
 * @param enginePtr Engine pointer
 * @param uciPosition Position command (e.g., "startpos moves a0b0 c1d2")
 */
JNIEXPORT void JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeSetPosition(
    JNIEnv* env, jobject thiz, jlong enginePtr, jstring uciPosition);

/**
 * Calculate best move
 * @param enginePtr Engine pointer
 * @param thinkTimeMs Time to think in milliseconds
 * @return Best move in UCI notation (e.g., "a0b0") or empty string on error
 */
JNIEXPORT jstring JNICALL
Java_com_example_janggi2_data_ai_FairyStockfishEngine_nativeGetBestMove(
    JNIEnv* env, jobject thiz, jlong enginePtr, jint thinkTimeMs);

#ifdef __cplusplus
}
#endif

#endif // JNI_BRIDGE_H
