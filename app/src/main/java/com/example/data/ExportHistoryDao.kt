package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExportHistoryEntity): Long

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM export_history")
    suspend fun clearAll()
}
