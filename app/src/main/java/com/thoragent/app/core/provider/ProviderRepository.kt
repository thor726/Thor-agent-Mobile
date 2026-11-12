package com.thoragent.app.core.provider

import com.thoragent.app.data.db.ProviderDao
import com.thoragent.app.data.db.ProviderProfileEntity
import com.thoragent.app.data.keystore.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Profil siap pakai dengan API Key terdekripsi (hanya di memori, tidak pernah disimpan polos). */
data class ProviderProfile(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val streaming: Boolean,
    val isDefault: Boolean
)

class ProviderRepository(private val dao: ProviderDao) {

    fun observeProfiles(): Flow<List<ProviderProfileEntity>> = dao.observeAll()

    fun observeDecrypted(): Flow<List<ProviderProfile>> =
        dao.observeAll().map { list -> list.map { it.toPlain() } }

    suspend fun get(id: Long): ProviderProfile? = dao.get(id)?.toPlain()

    /** Profil aktif: default bila ada, kalau tidak ambil yang pertama. */
    suspend fun current(): ProviderProfile? {
        dao.getDefault()?.let { return it.toPlain() }
        return listAll().firstOrNull()?.toPlain()
    }

    suspend fun listAll(): List<ProviderProfileEntity> = dao.observeAll().first()

    /** Simpan profil; API key dienkripsi Android Keystore sebelum masuk DB. */
    suspend fun save(
        existingId: Long?,
        name: String,
        baseUrl: String,
        apiKeyPlain: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        streaming: Boolean,
        setDefault: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val cipher = KeystoreCrypto.encrypt(apiKeyPlain)
        val old = existingId?.let { dao.get(it) }
        val entity = ProviderProfileEntity(
            id = existingId ?: 0,
            name = name.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKeyCipher = if (apiKeyPlain.isNotBlank()) cipher else (old?.apiKeyCipher ?: ""),
            model = model.trim(),
            temperature = temperature,
            maxTokens = maxTokens,
            streaming = streaming,
            isDefault = old?.isDefault ?: false,
            createdAt = old?.createdAt ?: System.currentTimeMillis()
        )
        val id = dao.upsert(entity)
        if (setDefault) dao.setDefault(id)
        id
    }

    suspend fun setDefault(id: Long) = dao.setDefault(id)
    suspend fun delete(id: Long) = dao.delete(id)

    /** Ganti model profil (dipakai /model --global repo sumber). */
    suspend fun setModel(id: Long, model: String) = dao.setModel(id, model)

    private fun ProviderProfileEntity.toPlain(): ProviderProfile {
        val plainKey = if (apiKeyCipher.isBlank()) "" else KeystoreCrypto.decrypt(apiKeyCipher)
        return ProviderProfile(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKey = plainKey,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            streaming = streaming,
            isDefault = isDefault
        )
    }
}
