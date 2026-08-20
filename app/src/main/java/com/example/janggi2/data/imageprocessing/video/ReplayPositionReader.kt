package com.example.janggi2.data.imageprocessing.video

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * 동영상 불러오기가 다루는 영상은 기보 리플레이 화면을 "다음 수" 버튼으로 넘기며 녹화한
 * 것이라, 화면에 **"14" 위에 "/49"** 같은 지금 수/전체 수 표시가 그대로 찍혀 있습니다.
 * 이 번호를 직접 읽으면 지금 프레임이 실제 게임의 정확히 몇 번째 수인지 압니다.
 */
@Singleton
class ReplayPositionReader @Inject constructor() {
    companion object {
        private const val TAG = "ReplayPositionReader"

        // 지금 수와 전체 수 사이에 줄바꿈이나 "/" 등 숫자 아닌 문자가 하나 이상 있는
        // 패턴을 찾습니다. ML Kit 이 "14"와 "/49"를 한 줄로 합치든 따로 인식하든(줄
        // 구분은 인식기마다 다르게 나뉠 수 있음) 전체 인식 텍스트를 한 문자열로
        // 이어붙여 검색하므로 상관없습니다.
        private val POSITION_PATTERN = Regex("(\\d{1,3})\\D+(\\d{1,3})")
    }

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /** @return (지금 수, 전체 수) - 못 읽으면 null. */
    suspend fun readPosition(bitmap: Bitmap): Pair<Int, Int>? {
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val position = parsePosition(result)
            if (position == null) {
                Log.d(TAG, "No position match in OCR text: ${result.text.replace("\n", " | ")}")
            }
            position
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed", e)
            null
        }
    }

    private fun parsePosition(text: Text): Pair<Int, Int>? {
        val flat = text.text.replace("\n", " ")
        val match = POSITION_PATTERN.find(flat) ?: return null
        val numerator = match.groupValues[1].toIntOrNull() ?: return null
        val denominator = match.groupValues[2].toIntOrNull() ?: return null
        return numerator to denominator
    }

    fun close() {
        recognizer.close()
    }
}
