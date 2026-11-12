package com.thoragent.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Baris agregat tool-usage untuk /insights. */
data class ToolCount(val toolName: String?, val n: Int)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SessionEntity): Long

    @Update
    suspend fun update(s: SessionEntity)

    @Query("UPDATE sessions SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sessions SET cwd = :cwd, updatedAt = :now WHERE id = :id")
    suspend fun setCwd(id: Long, cwd: String, now: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Update
    suspend fun update(m: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: Long)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun listSession(sessionId: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id >= :fromId")
    suspend fun deleteFrom(sessionId: Long, fromId: Long)

    // ==== v0.4.0: statistik lintas sesi untuk /insights ====

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun countSessions(): Int

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int

    @Query("SELECT toolName, COUNT(*) as n FROM messages WHERE kind = 'tool_call' AND toolName IS NOT NULL GROUP BY toolName ORDER BY n DESC LIMIT 10")
    suspend fun topTools(): List<ToolCount>
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider_profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE id = :id")
    suspend fun get(id: Long): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ProviderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProviderProfileEntity): Long

    @Query("UPDATE provider_profiles SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefault(id: Long)

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE provider_profiles SET model = :model WHERE id = :id")
    suspend fun setModel(id: Long, model: String)
}
