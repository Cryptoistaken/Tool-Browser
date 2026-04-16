package com.personal.browser.data.database

import androidx.room.*
import com.personal.browser.data.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun getHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 20")
    suspend fun search(query: String): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history")
    suspend fun clearAll(): Int

    @Query("DELETE FROM history WHERE visitedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
