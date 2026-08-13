package com.example.janggi2.data.imageprocessing

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

/**
 * 기물 이미지를 이진(글자 획=흰색, 배경=검정) 마스크로 변환합니다.
 *
 * 원형 마스크로 팔각/원형 테두리와 격자선을 제거한 뒤 적응형 이진화를 적용해서
 * 배경 질감이나 조명 차이에 흔들리지 않는 "글자 모양"만 남깁니다.
 * 템플릿 저장([TemplateExtractor])과 매칭 입력([TemplateMatcher])이 항상 이
 * 함수를 거쳐야 두 이미지가 같은 기준으로 비교됩니다.
 */
object TemplateBinarizer {
    // 저장되는 템플릿 크기
    const val TEMPLATE_SIZE = 100

    // 매칭 입력 크기 (템플릿보다 크게 잡아 슬라이딩 탐색으로 정렬 오차를 흡수할 여지를 둠)
    const val INPUT_SIZE = 130

    /**
     * 크롭·마스크 기하 규격. 학습 데이터 생성(파이썬)과 추론(여기)이 반드시 같은
     * 값을 써야 하므로 한곳에 모아둡니다. 셀 크기를 1로 봤을 때:
     *
     *  - 기물 팔각형의 반지름은 변 기준 0.42, 꼭짓점 기준 0.48
     *  - 이웃 기물의 가장 가까운 가장자리는 1 - 0.48 = 0.52
     *
     * 따라서 마스크 반지름 0.45면 기물 전체를 담으면서 이웃은 물지 않습니다.
     */
    const val CELL_RADIUS_RATIO = 0.35f      // IntersectionCalculator 의 cellRadius = 0.35 * cell
    const val CROP_RADIUS_IN_CELLS = 0.55f   // 크롭 한 변 = 1.10 * cell
    private const val MASK_RADIUS_IN_CELLS = 0.45f
    private const val MASK_RATIO = MASK_RADIUS_IN_CELLS / (2 * CROP_RADIUS_IN_CELLS)

    fun binarize(mat: Mat, targetSize: Int): Mat {
        val gray = Mat()
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            mat.copyTo(gray)
        }

        // 원형 마스크 바깥(팔각/원형 테두리, 격자선)을 흰색으로 눌러서
        // 이진화 시 노이즈 점으로 섞여 들어가는 것을 방지
        val circleMask = Mat(gray.size(), gray.type(), Scalar(0.0))
        Imgproc.circle(
            circleMask,
            Point(gray.cols() / 2.0, gray.rows() / 2.0),
            (min(gray.cols(), gray.rows()) * MASK_RATIO).toInt(),
            Scalar(255.0),
            -1
        )
        val masked = Mat(gray.size(), gray.type(), Scalar(255.0))
        gray.copyTo(masked, circleMask)
        circleMask.release()
        gray.release()

        // 크기 조정을 먼저 하고 이진화 (작은 원본에서 바로 이진화하면 경계가
        // 계단현상으로 부서져 점 노이즈가 심해짐)
        val resized = Mat()
        Imgproc.resize(
            masked, resized,
            Size(targetSize.toDouble(), targetSize.toDouble()),
            0.0, 0.0, Imgproc.INTER_CUBIC
        )
        masked.release()

        // THRESH_BINARY_INV: 어두운 글자 획이 255(흰색=전경)가 되고 밝은 배경이
        // 0(검정)이 되도록 — 분류기 학습 데이터와 동일한 극성이어야 함
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            resized, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            21, 8.0
        )
        resized.release()

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        val cleaned = Mat()
        Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_OPEN, kernel)
        binary.release()
        kernel.release()

        return cleaned
    }
}
