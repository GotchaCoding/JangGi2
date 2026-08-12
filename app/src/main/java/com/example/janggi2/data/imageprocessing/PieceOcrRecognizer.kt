package com.example.janggi2.data.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * ML Kit OCR을 사용한 기물 문자 인식기
 *
 * 기물 영역에서 한글/한자를 인식하고 기물 종류로 변환합니다.
 */
@Singleton
class PieceOcrRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PieceOcrRecognizer"

        // OCR 신뢰도 임계값
        private const val KOREAN_CONFIDENCE_THRESHOLD = 0.7f
        private const val CHINESE_CONFIDENCE_THRESHOLD = 0.6f

        // 전처리 파라미터
        private const val TARGET_OCR_SIZE = 150  // OCR 입력 크기 (픽셀)
        private const val CROP_SCALE = 0.85f     // 기물 영역 크롭 스케일 (중앙 85%)

        // TEMP DEBUG: 전처리 결과를 눈으로 확인하기 위한 덤프 경로
        private const val DEBUG_DUMP_DIR = "/sdcard/Android/data/com.example.janggi2/files/debug_crops"
    }

    /**
     * TEMP DEBUG: 비트맵을 파일로 저장 (실패해도 무시)
     */
    private fun dumpDebugBitmap(name: String, bitmap: Bitmap) {
        try {
            java.io.File(DEBUG_DUMP_DIR).mkdirs()
            java.io.FileOutputStream("$DEBUG_DUMP_DIR/$name.png").use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "debug dump failed: $name", e)
        }
    }

    // ML Kit 인식기 (lazy 초기화)
    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * OCR 결과
     */
    data class OcrResult(
        val pieceType: PieceDetector.PieceType?,
        val recognizedText: String,
        val confidence: Float,
        val usedChineseOcr: Boolean = false
    )

    /**
     * 기물 영역에서 문자를 인식하고 기물 종류를 반환합니다.
     *
     * @param bitmap 원본 이미지
     * @param centerX 기물 중심 X 좌표
     * @param centerY 기물 중심 Y 좌표
     * @param radius 기물 반경
     * @return OCR 결과 (기물 종류, 인식된 문자, 신뢰도)
     */
    suspend fun recognizePiece(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): OcrResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "OCR: centerX=$centerX, centerY=$centerY, radius=$radius")

            // 1. 기물 영역 크롭
            val croppedBitmap = cropPieceRegion(bitmap, centerX, centerY, radius)
            Log.d(TAG, "Cropped: ${croppedBitmap.width}x${croppedBitmap.height}")

            // 2. 이미지 전처리
            val preprocessedBitmap = preprocessPieceImage(croppedBitmap)
            Log.d(TAG, "Preprocessed: ${preprocessedBitmap.width}x${preprocessedBitmap.height}")

            // TEMP DEBUG: 실제 OCR 입력 이미지 확인용
            dumpDebugBitmap("ocr_${centerX}_${centerY}", preprocessedBitmap)

            // 3. 정방향 인식 시도
            val uprightResult = recognizeOriented(preprocessedBitmap)
            if (uprightResult.pieceType != null) {
                Log.d(TAG, "Upright OCR success: ${uprightResult.recognizedText} -> ${uprightResult.pieceType}")
                return@withContext uprightResult
            }

            // 4. 정방향 실패 시 180도 회전 인식 시도
            // 상대 진영 기물은 보드 위에서 180도 뒤집혀 표시되는 경우가 많음
            val rotatedBitmap = rotateBitmap(preprocessedBitmap, 180f)
            val rotatedResult = recognizeOriented(rotatedBitmap)
            if (rotatedResult.pieceType != null) {
                Log.d(TAG, "Rotated OCR success: ${rotatedResult.recognizedText} -> ${rotatedResult.pieceType}")
                return@withContext rotatedResult
            }

            // 5. 둘 다 실패 - 더 나은 결과 반환
            val betterResult = if (uprightResult.confidence >= rotatedResult.confidence) {
                uprightResult
            } else {
                rotatedResult
            }

            Log.d(TAG, "OCR failed or low confidence: ${betterResult.recognizedText} (${betterResult.confidence})")
            return@withContext betterResult

        } catch (e: Exception) {
            Log.e(TAG, "OCR error", e)
            return@withContext OcrResult(
                pieceType = null,
                recognizedText = "",
                confidence = 0f
            )
        }
    }

    /**
     * 한글 OCR을 우선 시도하고, 실패하면 한자 OCR로 폴백합니다.
     */
    private suspend fun recognizeOriented(bitmap: Bitmap): OcrResult {
        val koreanResult = runOcr(bitmap, koreanRecognizer, isKorean = true)
        if (koreanResult.pieceType != null && koreanResult.confidence >= KOREAN_CONFIDENCE_THRESHOLD) {
            return koreanResult
        }

        val chineseResult = runOcr(bitmap, chineseRecognizer, isKorean = false)
        if (chineseResult.pieceType != null && chineseResult.confidence >= CHINESE_CONFIDENCE_THRESHOLD) {
            return chineseResult.copy(usedChineseOcr = true)
        }

        return if (koreanResult.confidence >= chineseResult.confidence) {
            koreanResult
        } else {
            chineseResult.copy(usedChineseOcr = true)
        }
    }

    /**
     * 비트맵을 주어진 각도만큼 회전합니다.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 기물 영역을 크롭합니다.
     */
    private fun cropPieceRegion(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Bitmap {
        // 기물 중앙 영역 크롭 (글자가 있는 부분)
        val cropRadius = (radius * CROP_SCALE).toInt()

        val left = max(0, centerX - cropRadius)
        val top = max(0, centerY - cropRadius)
        val right = min(bitmap.width, centerX + cropRadius)
        val bottom = min(bitmap.height, centerY + cropRadius)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * OCR을 위한 이미지 전처리
     *
     * 1. 그레이스케일 변환
     * 2. 적응형 이진화
     * 3. 노이즈 제거
     * 4. 크기 조정
     */
    private fun preprocessPieceImage(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        try {
            // 1. 그레이스케일 변환 (Android Bitmap은 RGB 형식)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            // 2. 원형 마스크 바깥(기물 팔각/원형 테두리, 격자선)을 흰색으로 눌러서
            //    이진화 시 테두리/격자선이 노이즈 점으로 섞여 들어가는 것을 방지
            val circleMask = Mat(gray.size(), gray.type(), Scalar(0.0))
            Imgproc.circle(
                circleMask,
                Point(gray.cols() / 2.0, gray.rows() / 2.0),
                (min(gray.cols(), gray.rows()) * 0.42).toInt(),
                Scalar(255.0),
                -1
            )
            val masked = Mat(gray.size(), gray.type(), Scalar(255.0))
            gray.copyTo(masked, circleMask)
            circleMask.release()

            // 3. 크기 조정을 먼저 하고 나서 이진화 (작은 원본 크기에서 바로 이진화하면
            //    격자선/테두리 경계가 계단현상으로 부서져 노이즈가 심해짐)
            val resized = Mat()
            Imgproc.resize(
                masked, resized,
                Size(TARGET_OCR_SIZE.toDouble(), TARGET_OCR_SIZE.toDouble()),
                0.0, 0.0,
                Imgproc.INTER_CUBIC
            )

            // 4. 적응형 이진화 (글자를 선명하게). blockSize를 크게 잡아 글자 획
            //    두께보다 넓은 영역에서 임계값을 계산해야 점 노이즈가 덜 생김
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                resized, binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                21, 8.0
            )

            // 5. 모폴로지 연산 (점 노이즈 제거 - OPEN이 CLOSE보다 노이즈 제거에 적합)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
            val cleaned = Mat()
            Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_OPEN, kernel)

            // Mat -> Bitmap 변환
            val result = Bitmap.createBitmap(
                cleaned.cols(), cleaned.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(cleaned, result)

            // 리소스 해제
            gray.release()
            masked.release()
            resized.release()
            binary.release()
            kernel.release()
            cleaned.release()
            mat.release()

            return result
        } catch (e: Exception) {
            mat.release()
            Log.e(TAG, "Preprocessing failed", e)
            return bitmap
        }
    }

    /**
     * ML Kit OCR 실행
     */
    private suspend fun runOcr(
        bitmap: Bitmap,
        recognizer: TextRecognizer,
        isKorean: Boolean
    ): OcrResult {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()

            // 인식된 텍스트 분석
            val text = result.text.trim()

            if (text.isEmpty()) {
                return OcrResult(
                    pieceType = null,
                    recognizedText = "",
                    confidence = 0f
                )
            }

            // 첫 글자로 기물 종류 판별 시도
            val firstChar = text.firstOrNull()?.toString() ?: ""
            val pieceType = mapCharacterToPieceType(firstChar)

            // 첫 글자 실패 시 전체 텍스트에서 찾기
            val finalPieceType = pieceType ?: findPieceTypeInText(text)

            // 신뢰도 계산 (ML Kit은 블록 수준 신뢰도만 제공)
            val confidence = if (finalPieceType != null) {
                // 기물을 찾았으면 높은 신뢰도
                result.textBlocks.firstOrNull()?.let { block ->
                    // 텍스트 블록의 박스 크기가 적당하면 높은 신뢰도
                    val boxArea = block.boundingBox?.let { it.width() * it.height() } ?: 0
                    val bitmapArea = bitmap.width * bitmap.height
                    val areaRatio = boxArea.toFloat() / bitmapArea

                    when {
                        areaRatio > 0.3f -> 0.9f
                        areaRatio > 0.1f -> 0.8f
                        else -> 0.7f
                    }
                } ?: 0.75f
            } else {
                0.3f
            }

            OcrResult(
                pieceType = finalPieceType,
                recognizedText = text,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR execution failed", e)
            OcrResult(
                pieceType = null,
                recognizedText = "",
                confidence = 0f
            )
        }
    }

    /**
     * 문자를 기물 종류로 변환
     */
    private fun mapCharacterToPieceType(char: String): PieceDetector.PieceType? {
        return when (char.trim()) {
            // 한글
            "왕" -> PieceDetector.PieceType.GENERAL
            "사" -> PieceDetector.PieceType.GUARD
            "마" -> PieceDetector.PieceType.HORSE
            "상" -> PieceDetector.PieceType.ELEPHANT
            "차" -> PieceDetector.PieceType.CHARIOT
            "포" -> PieceDetector.PieceType.CANNON
            "졸", "병" -> PieceDetector.PieceType.SOLDIER

            // 한자 (카카오장기)
            "將", "帥", "漢", "楚" -> PieceDetector.PieceType.GENERAL
            "士" -> PieceDetector.PieceType.GUARD
            "馬" -> PieceDetector.PieceType.HORSE
            "象", "相" -> PieceDetector.PieceType.ELEPHANT
            "車" -> PieceDetector.PieceType.CHARIOT
            "包", "砲" -> PieceDetector.PieceType.CANNON
            "卒", "兵" -> PieceDetector.PieceType.SOLDIER

            else -> null
        }
    }

    /**
     * 텍스트에서 기물 종류 찾기
     */
    private fun findPieceTypeInText(text: String): PieceDetector.PieceType? {
        // 모든 기물 문자 패턴
        val pieceChars = listOf(
            "왕", "사", "마", "상", "차", "포", "졸", "병",
            "將", "帥", "漢", "楚", "士", "馬", "象", "相", "車", "包", "砲", "卒", "兵"
        )

        for (char in text) {
            if (pieceChars.contains(char.toString())) {
                val pieceType = mapCharacterToPieceType(char.toString())
                if (pieceType != null) {
                    return pieceType
                }
            }
        }

        return null
    }

    /**
     * 리소스 해제 (Activity/Fragment 종료 시 호출)
     */
    fun close() {
        koreanRecognizer.close()
        chineseRecognizer.close()
    }
}
