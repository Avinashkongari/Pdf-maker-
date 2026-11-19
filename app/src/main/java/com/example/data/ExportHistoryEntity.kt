package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val outputUriString: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val imageModeName: String,
    val pageSizeName: String,
    val ocrEnabled: Boolean,
    val ocrExtractedCharCount: Int,
    val createdAtMillis: Long = System.currentTimeMillis()
)
