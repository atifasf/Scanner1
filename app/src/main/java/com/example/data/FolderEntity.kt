package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)
