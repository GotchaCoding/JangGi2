package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * Composable that displays a single Janggi piece.
 * Shows the piece's Korean character with appropriate coloring based on the player.
 *
 * @param piece The piece to display
 * @param size The diameter of the piece circle
 * @param fontSize The size of the Korean character text
 */
@Composable
fun PieceView(
    piece: Piece,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: TextUnit = 24.sp
) {
    val backgroundColor = when (piece.player) {
        Player.CHO -> Color(0xFFE3F2FD) // Light blue for Cho
        Player.HAN -> Color(0xFFFCE4EC) // Light pink for Han
    }

    val textColor = when (piece.player) {
        Player.CHO -> Color(0xFF1976D2) // Dark blue for Cho
        Player.HAN -> Color(0xFFC2185B) // Dark pink for Han
    }

    Box(
        modifier = modifier
            .size(size)
            .background(color = backgroundColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = piece.getDisplayChar(),
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PieceViewPreview() {
    // Preview CHO pieces
    Box {
        PieceView(
            piece = Piece.General(Player.CHO, Position(4, 1))
        )
    }
}

@Preview(showBackground = true, name = "HAN Piece")
@Composable
fun PieceViewHanPreview() {
    Box {
        PieceView(
            piece = Piece.General(Player.HAN, Position(4, 8))
        )
    }
}
