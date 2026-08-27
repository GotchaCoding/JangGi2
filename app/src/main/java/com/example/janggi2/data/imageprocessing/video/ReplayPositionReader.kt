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
import org.opencv.android.Utils as CvUtils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

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

        /**
         * 크롭한 카운터 영역은 원본이 작은 글씨라, 그대로 넣으면 ML Kit이 압축
         * 아티팩트 때문에 숫자를 가끔 완전히 다른 숫자로 잘못 읽습니다(실측: "30"을
         * "98"로 오독, 확대 없이는 재현이 100%였고 3배 확대 후에는 같은 프레임에서
         * 매번 정확히 읽힘). 그래서 OCR 전에 항상 이 배수로 키웁니다.
         */
        private const val OCR_UPSCALE_FACTOR = 3

        /**
         * 침식(erode) 커널 크기. 이 앱 폰트는 "4" 같은 숫자의 위쪽이 완전히 닫힌
         * 삼각형이라, 같은 숫자가 두 번 붙으면("44") 획이 서로 닿아 ML Kit이 "H"나
         * "0L" 같은 완전히 다른 문자로 읽습니다(실측 확인, 확대해도 안 고쳐짐 - 해상도
         * 문제가 아니라 글꼴 자체의 모양 문제). 획을 살짝 깎아 붙은 부분을 떼어냅니다.
         */
        private val ERODE_KERNEL = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
    }

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /**
     * @param onEroded TEMP DEBUG: 보조 시도(침식 처리 후) 실제로 OCR에 들어가는 이미지를
     *   눈으로 확인하려는 콜백. 확인 끝나면 지울 코드입니다.
     * @return (지금 수, 전체 수) - 못 읽으면 null.
     */
    suspend fun readPosition(bitmap: Bitmap, onEroded: ((Bitmap) -> Unit)? = null): Pair<Int, Int>? {
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            bitmap.width * OCR_UPSCALE_FACTOR,
            bitmap.height * OCR_UPSCALE_FACTOR,
            true
        )
        try {
            runOcr(scaled)?.let { return it }

            // 확대만으로 못 읽었을 때만 침식을 보조로 씁니다 - 침식은 획이 가는
            // 숫자(예: "0", "1")에서는 오히려 글자를 지워버릴 수 있어서, 항상
            // 적용하면 이미 잘 읽히던 자리를 새로 망가뜨립니다(실측 확인).
            val eroded = erodeStrokes(scaled)
            onEroded?.invoke(eroded)
            return try {
                runOcr(eroded)
            } finally {
                eroded.recycle()
            }
        } finally {
            scaled.recycle()
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): Pair<Int, Int>? {
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

    /**
     * 글자 획을 살짝 깎아, 붙어 보이는 숫자들을 떼어냅니다. 이진화(오츠) -> 침식 ->
     * 다시 원래 극성(흰 배경에 검은 글씨)으로 되돌리는 순서입니다.
     */
    private fun erodeStrokes(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        CvUtils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        mat.release()

        // 글씨(어두움)를 흰색 전경으로 뒤집어야 erode가 획을 "깎는" 방향으로 작동합니다.
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        gray.release()

        val erodedBinary = Mat()
        Imgproc.erode(binary, erodedBinary, ERODE_KERNEL)
        binary.release()

        // ML Kit은 보통 문서처럼 밝은 배경에 어두운 글씨를 기대하므로 되돌립니다.
        val normalPolarity = Mat()
        Core.bitwise_not(erodedBinary, normalPolarity)
        erodedBinary.release()

        val result = Bitmap.createBitmap(normalPolarity.cols(), normalPolarity.rows(), Bitmap.Config.ARGB_8888)
        CvUtils.matToBitmap(normalPolarity, result)
        normalPolarity.release()

        return result
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
