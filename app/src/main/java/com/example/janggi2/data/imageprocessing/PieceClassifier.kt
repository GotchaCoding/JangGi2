package com.example.janggi2.data.imageprocessing

import android.content.Context
import android.util.Log
import com.example.janggi2.domain.model.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opencv.core.Mat
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 학습된 소형 CNN(TFLite)으로 기물 종류(7종)를 분류합니다.
 *
 * 진영(CHO/HAN)은 이미 색상으로 판별되어 있다는 전제 하에, 진영별로 따로
 * 학습된 7-클래스 모델을 사용합니다 (같은 기물이라도 진영마다 글자체가
 * 달라서 하나로 합치는 것보다 정확도가 높습니다). 입력은 [TemplateBinarizer]로
 * 이진화한 64x64 이미지입니다.
 */
@Singleton
class PieceClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PieceClassifier"
        private const val INPUT_SIZE = 64

        // 학습 시 클래스 순서와 반드시 일치해야 함 (PieceDetector.PieceType 선언 순서와 동일)
        private val PIECE_TYPES = listOf(
            PieceDetector.PieceType.GENERAL,
            PieceDetector.PieceType.GUARD,
            PieceDetector.PieceType.HORSE,
            PieceDetector.PieceType.ELEPHANT,
            PieceDetector.PieceType.CHARIOT,
            PieceDetector.PieceType.CANNON,
            PieceDetector.PieceType.SOLDIER
        )
    }

    data class ClassificationResult(
        val pieceType: PieceDetector.PieceType,
        val confidence: Float
    )

    private val choInterpreter: Interpreter? by lazy { loadInterpreter("piece_classifier_CHO.tflite") }
    private val hanInterpreter: Interpreter? by lazy { loadInterpreter("piece_classifier_HAN.tflite") }

    private fun loadInterpreter(assetName: String): Interpreter? {
        return try {
            val afd = context.assets.openFd(assetName)
            val buffer: MappedByteBuffer = afd.createInputStream().channel.map(
                FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
            )
            Interpreter(buffer).also {
                Log.i(TAG, "Loaded $assetName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $assetName", e)
            null
        }
    }

    /**
     * 기물 영역을 분류합니다.
     *
     * @param croppedMat 기물 주변을 크롭한 컬러 Mat (내부에서 이진화 처리)
     * @param player 색상으로 이미 판별된 진영
     * @return 분류 결과 (모델 로드 실패 시 null)
     */
    fun classify(croppedMat: Mat, player: Player): ClassificationResult? {
        val interpreter = when (player) {
            Player.CHO -> choInterpreter
            Player.HAN -> hanInterpreter
        } ?: return null

        val binary = TemplateBinarizer.binarize(croppedMat, INPUT_SIZE)

        // TEMP DEBUG: 실제 분류기 입력 이미지 확인용
        try {
            org.opencv.imgcodecs.Imgcodecs.imwrite(
                "/sdcard/Android/data/com.example.janggi2/files/debug_crops/clf_${player}_${System.nanoTime()}.png",
                binary
            )
        } catch (e: Exception) {
            Log.w(TAG, "debug dump failed", e)
        }

        val input = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val v = binary.get(y, x)[0]
                input.putFloat((v / 255.0).toFloat())
            }
        }
        input.rewind()
        binary.release()

        val output = Array(1) { FloatArray(PIECE_TYPES.size) }
        interpreter.run(input, output)

        val probs = output[0]
        var bestIdx = 0
        for (i in probs.indices) {
            if (probs[i] > probs[bestIdx]) bestIdx = i
        }

        return ClassificationResult(PIECE_TYPES[bestIdx], probs[bestIdx])
    }
}
