package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dateCreated: Long,
    val imagePaths: String, // Comma-separated paths
    val pdfPath: String?,
    val ocrText: String?,
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val sizeBytes: Long = 0L
)
