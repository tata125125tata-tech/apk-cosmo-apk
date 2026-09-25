package com.cosmogamestore.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local SQLite recent searches table.
 */
@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearchesFlow(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSearches(limit: Int = 10): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllSearches()
}
