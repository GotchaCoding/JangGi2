package com.example.janggi2.data.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.janggi2.domain.model.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원형 기물을 검출하고 색상으로 진영을 구분하는 서비스
 */
@Singleton
class CircleDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 검출된 원형 기물 정보
     */
    data class DetectedCircle(
        val center: Point,
        val radius: Float,
        val player: Player,
        val boundingRect: Rect,
        val croppedBitmap: Bitmap
    )

    /**
     * 이미지에서 Connected Component Labeling으로 기물을 검출합니다.
     *
     * @param bitmap 입력 이미지
     * @return 검출된 기물 리스트
     */
    fun detectCircles(bitmap: Bitmap): List<DetectedCircle> {
        try {
            // Bitmap을 OpenCV Mat으로 변환
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Grayscale 변환
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            // 적응형 이진화 (Adaptive Thresholding)
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15,
                2.0
            )

            // 모폴로지 연산으로 노이즈 제거
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)

            // Connected Component Labeling
            val labels = Mat()
            val stats = Mat()
            val centroids = Mat()
            val numLabels = Imgproc.connectedComponentsWithStats(binary, labels, stats, centroids)

            Log.d("CircleDetector", "Detected $numLabels connected components")

            val detectedCircles = mutableListOf<DetectedCircle>()

            // 보드 영역 계산 (중앙 70%, 상단 10%-하단 20% 제외)
            val boardTop = (bitmap.height * 0.1).toInt()
            val boardBottom = (bitmap.height * 0.8).toInt()
            val boardLeft = (bitmap.width * 0.05).toInt()
            val boardRight = (bitmap.width * 0.95).toInt()

            // 각 Connected Component를 처리 (label 0은 배경이므로 1부터 시작)
            for (label in 1 until numLabels) {
                // 통계 정보 추출
                // stats: [LEFT, TOP, WIDTH, HEIGHT, AREA]
                val left = stats.get(label, Imgproc.CC_STAT_LEFT)[0].toInt()
                val top = stats.get(label, Imgproc.CC_STAT_TOP)[0].toInt()
                val width = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
                val height = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()
                val area = stats.get(label, Imgproc.CC_STAT_AREA)[0].toInt()

                // 중심점
                val cx = centroids.get(label, 0)[0].toInt()
                val cy = centroids.get(label, 1)[0].toInt()

                // 크기 필터링 (너무 작거나 큰 객체 제외)
                val minArea = 800  // 최소 영역 (약 28x28 픽셀)
                val maxArea = 5000 // 최대 영역 (약 70x70 픽셀)
                if (area < minArea || area > maxArea) {
                    continue
                }

                // 종횡비 체크 (원형 기물은 정사각형에 가까움)
                val aspectRatio = width.toFloat() / height.toFloat()
                if (aspectRatio < 0.5 || aspectRatio > 2.0) {
                    continue
                }

                // 보드 영역 내에 있는지 체크
                if (cx < boardLeft || cx > boardRight || cy < boardTop || cy > boardBottom) {
                    continue
                }

                // 경계 체크
                val right = left + width
                val bottom = top + height
                if (left < 0 || top < 0 || right >= bitmap.width || bottom >= bitmap.height) {
                    continue
                }

                // 색상 분석 (추정 반지름 사용)
                val estimatedRadius = Math.max(width, height) / 2
                val player = analyzeCircleColor(mat, cx, cy, estimatedRadius)

                // 영역 확장 (1.5배)
                val expandedWidth = (width * 1.5).toInt()
                val expandedHeight = (height * 1.5).toInt()
                val expandedLeft = (cx - expandedWidth / 2).coerceAtLeast(0)
                val expandedTop = (cy - expandedHeight / 2).coerceAtLeast(0)
                val expandedRight = (cx + expandedWidth / 2).coerceAtMost(mat.cols() - 1)
                val expandedBottom = (cy + expandedHeight / 2).coerceAtMost(mat.rows() - 1)
                val finalWidth = expandedRight - expandedLeft
                val finalHeight = expandedBottom - expandedTop

                if (finalWidth <= 0 || finalHeight <= 0) {
                    continue
                }

                val cvRect = org.opencv.core.Rect(expandedLeft, expandedTop, finalWidth, finalHeight)
                val croppedMat = Mat(mat, cvRect)

                // 이미지 확대 (3배) - OCR 품질 향상
                val resizedMat = Mat()
                Imgproc.resize(croppedMat, resizedMat, Size(finalWidth * 3.0, finalHeight * 3.0))

                // Grayscale 변환
                val grayMat = Mat()
                Imgproc.cvtColor(resizedMat, grayMat, Imgproc.COLOR_RGB2GRAY)

                // Gaussian blur로 노이즈 제거
                Imgproc.GaussianBlur(grayMat, grayMat, Size(3.0, 3.0), 0.0)

                // Otsu's binarization으로 흑백 변환 (문자 강조)
                val binaryMat = Mat()
                Imgproc.threshold(grayMat, binaryMat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

                // 다시 RGB로 변환 (ML Kit는 RGB 필요)
                val enhanced = Mat()
                Imgproc.cvtColor(binaryMat, enhanced, Imgproc.COLOR_GRAY2RGB)

                val croppedBitmap = Bitmap.createBitmap(finalWidth * 3, finalHeight * 3, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(enhanced, croppedBitmap)

                // Android Rect for storage
                val androidRect = Rect(expandedLeft, expandedTop, expandedRight, expandedBottom)

                detectedCircles.add(
                    DetectedCircle(
                        center = Point(cx.toDouble(), cy.toDouble()),
                        radius = estimatedRadius.toFloat(),
                        player = player,
                        boundingRect = androidRect,
                        croppedBitmap = croppedBitmap
                    )
                )

                Log.d("CircleDetector", "Component at ($cx, $cy) size=${width}x$height area=$area player=$player")
            }

            // 디버그: 검출 결과를 원본 이미지에 그리기
            val debugMat = mat.clone()
            for (circle in detectedCircles) {
                val color = if (circle.player == Player.CHO) {
                    Scalar(0.0, 0.0, 255.0)  // 파랑 = 초
                } else {
                    Scalar(255.0, 0.0, 0.0)  // 빨강 = 한
                }
                // 중심점 표시
                Imgproc.circle(debugMat, circle.center, 5, color, -1)
                // 원 테두리
                Imgproc.circle(debugMat, circle.center, circle.radius.toInt(), color, 3)
                // 바운딩 박스
                val rect = circle.boundingRect
                Imgproc.rectangle(
                    debugMat,
                    Point(rect.left.toDouble(), rect.top.toDouble()),
                    Point(rect.right.toDouble(), rect.bottom.toDouble()),
                    color,
                    2
                )
            }

            // 디버그 이미지를 Bitmap으로 변환하여 저장
            val debugBitmap = Bitmap.createBitmap(debugMat.cols(), debugMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(debugMat, debugBitmap)
            saveDebugImage(debugBitmap, "labeling_result")

            Log.d("CircleDetector", "Successfully detected ${detectedCircles.size} components")
            return detectedCircles

        } catch (e: Exception) {
            Log.e("CircleDetector", "Failed to detect circles", e)
            return emptyList()
        }
    }

    /**
     * 디버그 이미지를 앱 내부 캐시에 저장합니다.
     * 위치: /data/data/com.example.janggi2/cache/debug/
     * adb pull로 확인 가능: adb pull /data/data/com.example.janggi2/cache/debug/
     */
    private fun saveDebugImage(bitmap: Bitmap, name: String) {
        try {
            val debugDir = java.io.File(context.cacheDir, "debug")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }

            val file = java.io.File(debugDir, "${name}_${System.currentTimeMillis()}.png")
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            Log.d("CircleDetector", "Debug image saved: ${file.absolutePath}")
            Log.d("CircleDetector", "Pull with: adb pull ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CircleDetector", "Failed to save debug image", e)
        }
    }

    /**
     * 원 영역의 색상을 분석하여 진영(초/한)을 판별합니다.
     * 빨간색 = CHO, 파란색 = HAN
     */
    private fun analyzeCircleColor(mat: Mat, x: Int, y: Int, radius: Int): Player {
        try {
            // HSV로 변환
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)

            // 원의 중심 주변 영역 샘플링
            val sampleRadius = (radius * 0.5).toInt()
            var redCount = 0
            var blueCount = 0

            for (dy in -sampleRadius..sampleRadius step 5) {
                for (dx in -sampleRadius..sampleRadius step 5) {
                    val px = x + dx
                    val py = y + dy

                    if (px < 0 || px >= mat.cols() || py < 0 || py >= mat.rows()) continue

                    val hsvValue = hsv.get(py, px)
                    if (hsvValue != null && hsvValue.size >= 3) {
                        val hue = hsvValue[0]
                        val saturation = hsvValue[1]
                        val value = hsvValue[2]

                        // 채도와 명도가 충분한 경우만 색상 판별
                        if (saturation > 50 && value > 50) {
                            when {
                                // 빨강: 0-30 또는 330-360 (OpenCV에서 Hue는 0-180)
                                hue < 15 || hue > 165 -> redCount++
                                // 파랑: 180-270 (OpenCV에서 90-135)
                                hue in 90.0..135.0 -> blueCount++
                            }
                        }
                    }
                }
            }

            Log.d("CircleDetector", "Color analysis: red=$redCount, blue=$blueCount")

            return if (redCount > blueCount) Player.CHO else Player.HAN

        } catch (e: Exception) {
            Log.e("CircleDetector", "Failed to analyze color", e)
            return Player.CHO  // 기본값
        }
    }

}
