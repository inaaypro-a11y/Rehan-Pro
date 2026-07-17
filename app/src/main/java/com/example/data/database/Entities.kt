package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val role: String, // "USER" or "MODEL"
    val text: String,
    val attachmentPath: String? = null, // Local file path or base64 data for images
    val attachmentType: String? = null, // "image/jpeg", "application/pdf", etc.
    val timestamp: Long = System.currentTimeMillis()
)
