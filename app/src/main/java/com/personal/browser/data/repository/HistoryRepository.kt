package com.personal.browser.data.repository

import com.personal.browser.data.database.HistoryDao
import com.personal.browser.data.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    fun getHistory(): Flow<List<HistoryEntry>> = historyDao.getHistory()

    suspend fun search(query: String): List<HistoryEntry> = historyDao.search(query)

    suspend fun addEntry(title: String, url: String) {
        historyDao.insert(HistoryEntry(title = title, url = url))
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }
}
