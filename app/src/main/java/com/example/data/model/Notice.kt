package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notices")
data class Notice(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val date: String,
    val category: String, // "Notice", "Counselling", "Schedule", "General"
    val scannedAt: Long,
    val isNew: Boolean = true,
    val description: String? = null
)
