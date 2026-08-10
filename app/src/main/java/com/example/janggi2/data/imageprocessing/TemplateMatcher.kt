package com.example.janggi2.data.imageprocessing

import android.util.Log
import com.example.janggi2.domain.model.Player
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 템플릿 매칭 기반 기물 인식기
 *
 * 미리 추출한 기물 템플릿과 입력 이미지를 비교하여
 * 기물의 종류와 진영을 인식합니다.
 */
@Singleton
class TemplateMatcher @Inject constructor() {

    companion object {
        private const val TAG = "TemplateMatcher"

        // 매칭 임계값 (이 값 이상이면 매칭 성공)
        private const val MATCH_THRESHOLD = 0.65

        // 최소 신뢰도 차이 (2위 후보와의 최소 차이)
        private const val MIN_CONFIDENCE_GAP = 0.05

        // 템플릿 매칭 방법
        private const val MATCH_METHOD = Imgproc.TM_CCOEFF_NORMED
    }

    /**
     * 매칭 결과
     */
    data class MatchResult(
        val pieceType: PieceDetector.PieceType,
        val player: Player,
        val confidence: Float,
        val matchScore: Double,
        val secondBestScore: Double = 0.0,  // 2위 후보 점수 (디버깅용)
        val secondBestType: PieceDetector.PieceType? = null
    )

    /**
     * 크롭된 이미지에서 기물 매칭
     *
     * @param croppedMat 교차점에서 크롭한 이미지
     * @param templates 기물 템플릿 저장소
     * @param expectedPlayer 예상 진영 (색상 분석으로 미리 판별된 경우)
     * @return 매칭 결과 (임계값 이하면 null)
     */
    fun matchPiece(
        croppedMat: Mat,
        templates: PieceTemplates,
        expectedPlayer: Player? = null
    ): MatchResult? {
        if (croppedMat.empty()) {
            Log.w(TAG, "Empty input mat")
            return null
        }

        // 입력 이미지 전처리
        val preprocessed = preprocessInput(croppedMat)

        // 후보 목록
        val candidates = mutableListOf<MatchResult>()

        // 진영이 지정된 경우 해당 진영만 검사
        val templatesToCheck = if (expectedPlayer != null) {
            templates.getTemplatesForPlayer(expectedPlayer)
        } else {
            templates.getAllTemplates()
        }

        for (template in templatesToCheck) {
            val score = matchTemplate(preprocessed, template)

            if (score > MATCH_THRESHOLD) {
                candidates.add(
                    MatchResult(
                        pieceType = template.pieceType,
                        player = template.player,
                        confidence = score.toFloat(),
                        matchScore = score
                    )
                )
            }
        }

        preprocessed.release()

        if (candidates.isEmpty()) {
            Log.v(TAG, "No matching template found (threshold: $MATCH_THRESHOLD)")
            return null
        }

        // 점수 순 정렬
        candidates.sortByDescending { it.matchScore }

        val best = candidates.first()
        val secondBest = candidates.getOrNull(1)

        // 2위 후보와의 차이가 너무 작으면 불확실한 결과
        if (secondBest != null) {
            val gap = best.matchScore - secondBest.matchScore
            if (gap < MIN_CONFIDENCE_GAP && best.pieceType != secondBest.pieceType) {
                Log.v(TAG, "Ambiguous match: ${best.pieceType}(${best.matchScore}) vs " +
                        "${secondBest.pieceType}(${secondBest.matchScore}), gap=$gap")
            }
        }

        Log.v(TAG, "Best match: ${best.player} ${best.pieceType} (score: ${String.format("%.3f", best.matchScore)})")

        return best.copy(
            secondBestScore = secondBest?.matchScore ?: 0.0,
            secondBestType = secondBest?.pieceType
        )
    }

    /**
     * 단일 템플릿과 매칭
     */
    private fun matchTemplate(inputMat: Mat, template: PieceTemplate): Double {
        // 템플릿 크기에 맞게 입력 이미지 리사이즈
        val resized = resizeToTemplate(inputMat, template.originalSize)

        // 템플릿 매칭 수행
        val result = Mat()
        Imgproc.matchTemplate(resized, template.templateMat, result, MATCH_METHOD)

        // 최대 매칭 점수 추출
        val minMaxLoc = Core.minMaxLoc(result)
        val maxScore = minMaxLoc.maxVal

        resized.release()
        result.release()

        return maxScore
    }

    /**
     * 입력 이미지 전처리
     */
    private fun preprocessInput(mat: Mat): Mat {
        val result = Mat()

        // 그레이스케일 변환
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, result, Imgproc.COLOR_RGBA2GRAY)
        } else {
            mat.copyTo(result)
        }

        // 히스토그램 균등화
        Imgproc.equalizeHist(result, result)

        return result
    }

    /**
     * 템플릿 크기에 맞게 리사이즈
     */
    private fun resizeToTemplate(mat: Mat, targetSize: Size): Mat {
        val result = Mat()

        // 크기가 같으면 복사만
        if (abs(mat.cols() - targetSize.width) < 2 &&
            abs(mat.rows() - targetSize.height) < 2) {
            mat.copyTo(result)
            return result
        }

        Imgproc.resize(mat, result, targetSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
        return result
    }

    /**
     * 다중 스케일 템플릿 매칭 (선택사항)
     *
     * 기물 크기가 살짝 다를 수 있는 경우 여러 스케일로 매칭합니다.
     * 정확도를 높이지만 속도가 느려집니다.
     */
    fun matchPieceMultiScale(
        croppedMat: Mat,
        templates: PieceTemplates,
        expectedPlayer: Player? = null,
        scales: List<Double> = listOf(0.9, 1.0, 1.1)
    ): MatchResult? {
        var bestResult: MatchResult? = null
        var bestScore = MATCH_THRESHOLD

        val preprocessed = preprocessInput(croppedMat)

        val templatesToCheck = if (expectedPlayer != null) {
            templates.getTemplatesForPlayer(expectedPlayer)
        } else {
            templates.getAllTemplates()
        }

        for (scale in scales) {
            val scaledMat = if (scale != 1.0) {
                val scaled = Mat()
                val newSize = Size(
                    preprocessed.cols() * scale,
                    preprocessed.rows() * scale
                )
                Imgproc.resize(preprocessed, scaled, newSize)
                scaled
            } else {
                preprocessed
            }

            for (template in templatesToCheck) {
                val score = matchTemplate(scaledMat, template)

                if (score > bestScore) {
                    bestScore = score
                    bestResult = MatchResult(
                        pieceType = template.pieceType,
                        player = template.player,
                        confidence = score.toFloat(),
                        matchScore = score
                    )
                }
            }

            if (scale != 1.0) {
                scaledMat.release()
            }
        }

        preprocessed.release()

        return bestResult
    }

    /**
     * 매칭 임계값 조정
     */
    fun adjustThreshold(newThreshold: Double): Double {
        val adjusted = newThreshold.coerceIn(0.3, 0.95)
        Log.d(TAG, "Threshold adjusted to: $adjusted")
        return adjusted
    }
}
