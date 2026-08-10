package com.example.janggi2.data.imageprocessing

import android.util.Log
import com.example.janggi2.domain.model.Player
import org.opencv.core.Mat
import org.opencv.core.Size

/**
 * 기물 템플릿 데이터
 *
 * 0수 이미지에서 추출한 기물 템플릿을 저장합니다.
 */
data class PieceTemplate(
    val pieceType: PieceDetector.PieceType,
    val player: Player,
    val templateMat: Mat,           // 그레이스케일 템플릿
    val originalSize: Size          // 원본 크기
) {
    /**
     * 템플릿 메모리 해제
     */
    fun release() {
        if (!templateMat.empty()) {
            templateMat.release()
        }
    }
}

/**
 * 기물 템플릿 저장소
 *
 * 14종의 기물 템플릿을 관리합니다:
 * - 楚 (빨강): 將, 士×2, 象×2, 馬×2, 車×2, 包×2, 卒×5
 * - 漢 (파랑): 漢, 士×2, 象×2, 馬×2, 車×2, 包×2, 兵×5
 *
 * 같은 종류의 기물은 동일한 템플릿을 공유합니다.
 */
class PieceTemplates {

    companion object {
        private const val TAG = "PieceTemplates"
    }

    private val templates = mutableMapOf<Pair<PieceDetector.PieceType, Player>, PieceTemplate>()

    /**
     * 템플릿 추가
     */
    fun addTemplate(pieceType: PieceDetector.PieceType, player: Player, mat: Mat, size: Size) {
        val key = Pair(pieceType, player)

        // 기존 템플릿이 있으면 해제
        templates[key]?.release()

        templates[key] = PieceTemplate(
            pieceType = pieceType,
            player = player,
            templateMat = mat.clone(),  // 복사본 저장
            originalSize = size
        )

        Log.d(TAG, "Template added: $player $pieceType (${size.width}x${size.height})")
    }

    /**
     * 템플릿 조회
     */
    fun getTemplate(pieceType: PieceDetector.PieceType, player: Player): PieceTemplate? {
        return templates[Pair(pieceType, player)]
    }

    /**
     * 모든 템플릿 목록
     */
    fun getAllTemplates(): List<PieceTemplate> {
        return templates.values.toList()
    }

    /**
     * 특정 진영의 모든 템플릿
     */
    fun getTemplatesForPlayer(player: Player): List<PieceTemplate> {
        return templates.values.filter { it.player == player }
    }

    /**
     * 모든 템플릿 초기화
     */
    fun clear() {
        templates.values.forEach { it.release() }
        templates.clear()
        Log.d(TAG, "All templates cleared")
    }

    /**
     * 템플릿 초기화 여부
     */
    fun isInitialized(): Boolean {
        // 최소 기본 기물들(왕, 사, 상, 마, 차, 포, 졸)이 양 진영에 있어야 함
        val requiredTypes = listOf(
            PieceDetector.PieceType.GENERAL,
            PieceDetector.PieceType.GUARD,
            PieceDetector.PieceType.ELEPHANT,
            PieceDetector.PieceType.HORSE,
            PieceDetector.PieceType.CHARIOT,
            PieceDetector.PieceType.CANNON,
            PieceDetector.PieceType.SOLDIER
        )

        for (player in Player.values()) {
            for (type in requiredTypes) {
                if (!templates.containsKey(Pair(type, player))) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * 템플릿 개수
     */
    fun size(): Int = templates.size

    /**
     * 템플릿 상태 요약
     */
    fun getSummary(): String {
        val choTemplates = templates.count { it.key.second == Player.CHO }
        val hanTemplates = templates.count { it.key.second == Player.HAN }
        return "Templates: CHO=$choTemplates, HAN=$hanTemplates, Total=${templates.size}"
    }
}
