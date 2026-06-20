package com.example.janggi2.data.imageprocessing

import android.graphics.Color
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Maps Korean/Chinese characters to Janggi pieces and infers player from color.
 */
object KoreanPieceMapper {
    /**
     * Maps a character (Korean or Chinese) to its piece type.
     * Returns null if character doesn't match any piece.
     */
    fun mapCharacterToPieceType(char: String): PieceType? {
        return when (char.trim()) {
            // 한글
            "왕" -> PieceType.GENERAL
            "사" -> PieceType.GUARD
            "마" -> PieceType.HORSE
            "상" -> PieceType.ELEPHANT
            "차" -> PieceType.CHARIOT
            "포" -> PieceType.CANNON
            "졸" -> PieceType.SOLDIER_CHO
            "병" -> PieceType.SOLDIER_HAN

            // 한자 (카카오장기)
            "將", "帥", "漢", "楚" -> PieceType.GENERAL  // 장/수/한/초 (General)
            "士" -> PieceType.GUARD           // 사 (Guard)
            "馬" -> PieceType.HORSE           // 마 (Horse)
            "象", "相" -> PieceType.ELEPHANT  // 상 (Elephant)
            "車" -> PieceType.CHARIOT         // 차 (Chariot)
            "包", "砲" -> PieceType.CANNON    // 포 (Cannon)
            "卒" -> PieceType.SOLDIER_CHO     // 졸 (Soldier - CHO)
            "兵" -> PieceType.SOLDIER_HAN     // 병 (Soldier - HAN)

            else -> null
        }
    }

    /**
     * Infers the player from the character itself.
     * Some characters explicitly indicate which player they belong to.
     *
     * @param char The piece character
     * @return Player if determinable from character, null otherwise
     */
    fun inferPlayerFromCharacter(char: String): Player? {
        return when (char.trim()) {
            "楚" -> Player.CHO    // 초나라 = CHO
            "漢" -> Player.HAN    // 한나라 = HAN
            "졸", "卒" -> Player.CHO  // 졸 = CHO soldier
            "병", "兵" -> Player.HAN  // 병 = HAN soldier
            else -> null
        }
    }

    /**
     * Infers the player (CHO or HAN) from RGB color values.
     * CHO pieces are typically red/warm colors.
     * HAN pieces are typically blue/cool colors.
     *
     * @param rgb Array of [r, g, b] values (0-255)
     * @return Player.CHO for warm colors, Player.HAN for cool colors
     */
    fun inferPlayerFromColor(rgb: IntArray): Player {
        if (rgb.size != 3) return Player.CHO // Default fallback

        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]

        // Convert to HSV for better color analysis
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)

        val hue = hsv[0]
        val saturation = hsv[1]

        // Red hues: 0-30 and 330-360 degrees
        // Blue hues: 180-270 degrees
        return when {
            saturation < 0.2f -> {
                // Low saturation (grayscale) - use position-based inference
                // This is a fallback for poorly colored pieces
                Player.CHO
            }
            hue in 0f..30f || hue >= 330f -> Player.CHO // Red/warm
            hue in 180f..270f -> Player.HAN // Blue/cool
            hue in 30f..60f -> Player.HAN // Green (sometimes used for HAN)
            else -> Player.CHO // Default to CHO
        }
    }

    /**
     * Creates a Piece instance from detected information.
     *
     * @param pieceType The type of piece
     * @param position The position on the board
     * @param player The player who owns the piece (can be null for auto-detection)
     * @param rgb RGB color values for player inference (if player is null)
     * @return Piece instance, or null if creation fails
     */
    fun createPiece(
        pieceType: PieceType,
        position: Position,
        player: Player? = null,
        rgb: IntArray? = null
    ): Piece? {
        // Determine player
        val finalPlayer = when (pieceType) {
            PieceType.SOLDIER_CHO -> Player.CHO
            PieceType.SOLDIER_HAN -> Player.HAN
            else -> {
                player ?: rgb?.let { inferPlayerFromColor(it) } ?: inferPlayerFromPosition(position)
            }
        }

        // Create piece based on type
        return when (pieceType) {
            PieceType.GENERAL -> Piece.General(finalPlayer, position)
            PieceType.GUARD -> Piece.Guard(finalPlayer, position)
            PieceType.HORSE -> Piece.Horse(finalPlayer, position)
            PieceType.ELEPHANT -> Piece.Elephant(finalPlayer, position)
            PieceType.CHARIOT -> Piece.Chariot(finalPlayer, position)
            PieceType.CANNON -> Piece.Cannon(finalPlayer, position)
            PieceType.SOLDIER_CHO -> Piece.Soldier(Player.CHO, position)
            PieceType.SOLDIER_HAN -> Piece.Soldier(Player.HAN, position)
        }
    }

    /**
     * Infers player from board position as a fallback.
     * Top half (rows 0-4) = CHO, bottom half (rows 5-9) = HAN
     */
    private fun inferPlayerFromPosition(position: Position): Player {
        return if (position.row <= 4) Player.CHO else Player.HAN
    }

    /**
     * Enum representing piece types (without player information).
     */
    enum class PieceType {
        GENERAL,
        GUARD,
        HORSE,
        ELEPHANT,
        CHARIOT,
        CANNON,
        SOLDIER_CHO,  // 졸 (always CHO)
        SOLDIER_HAN   // 병 (always HAN)
    }
}
