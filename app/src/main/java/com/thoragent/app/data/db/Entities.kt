package com.thoragent.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Sesi percakapan (Direct Chat maupun mode Engine). */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val mode: String, // "direct" | "engine"
    val providerId: Long, // 0 = provider bawaan belum dipilih
    val cwd: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Pesan / kejadian percakapan: teks, panggilan tool, hasil tool, error. */
@Entity(tableName = "messages", indices = [androidx.room.Index("sessionId")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // user | assistant | tool | system | error
    val kind: String, // text | tool_call | tool_result | error
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null, // JSON args
    val exitCode: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Profil provider LLM kustom (OpenAI-compatible). */
@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKeyCipher: String, // AES/GCM AndroidKeyStore
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val streaming: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
