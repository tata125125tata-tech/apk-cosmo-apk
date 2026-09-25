package com.cosmogamestore.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQLite database entity representing a recent search query entered by the user.
 */
@Entity(tableName = "recent_searches")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
