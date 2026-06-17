package com.example.janggi2.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved game.
 */
@Entity(tableName = "saved_games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val savedDate: Long,
    val boardStateJson: String, // JSON representation of the board
    val currentPlayer: String, // "CHO" or "HAN"
    val moveCount: Int,
    val gameStatus: String, // "ONGOING", "CHECK", "CHECKMATE", etc.
    val winner: String?, // "CHO", "HAN", or null
    val moveHistoryJson: String = "[]" // JSON representation of move history
)
