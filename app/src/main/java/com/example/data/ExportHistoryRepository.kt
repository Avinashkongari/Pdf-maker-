package com.example.data

import kotlinx.coroutines.flow.Flow

class ExportHistoryRepository(private val dao: ExportHistoryDao) {
    val allExports: Flow<List<ExportHistoryEntity>> = dao.observeAll()

    suspend fun recordExport(entry: ExportHistoryEntity): Long = dao.insert(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
