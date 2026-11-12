package com.thoragent.app.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Kejadian streaming LLM untuk UI chat. */
sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LlmEvent
    data class Done(val finishReason: String?, val usage: LlmUsage? = null) : LlmEvent
    data class Failure(val message: String, val httpCode: Int? = null) : LlmEvent
}

/** Pemakaian token satu putaran — untuk /status /usage /context (ala repo sumber). */
data class LlmUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0
)

/** Pesan untuk API chat/completions (OpenAI-compatible). */
data class LlmMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCallSpec>? = null,
    val toolCallId: String? = null,
    val name: String? = null
) {
    data class ToolCallSpec(val id: String, val name: String, val arguments: String)

    fun toJson(): JsonObject = buildJsonObject {
        put("role", role)
        content?.let { put("content", it) }
        name?.let { put("name", it) }
        toolCallId?.let { put("tool_call_id", it) }
        toolCalls?.let { calls ->
            put(
                "tool_calls",
                buildJsonArray {
                    calls.forEach { c ->
                        add(
                            buildJsonObject {
                                put("id", c.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", c.name)
                                        put("arguments", c.arguments)
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    }
}

/**
 * Klien LLM OpenAI-compatible dengan streaming SSE dan akumulasi tool_calls.
 * API Key hanya disuntik ke header Authorization — tidak pernah disimpan polos.
 */
class LlmClient(private val ok: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Uji koneksi + key: GET /models. */
    suspend fun testConnection(baseUrl: String, apiKey: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                ok.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val count = try {
                            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.size ?: 0
                        } catch (e: Exception) {
                            0
                        }
                        true to "OK — endpoint merespons (≈$count model)"
                    } else {
                        false to "HTTP ${resp.code} — ${resp.message}"
                    }
                }
            } catch (e: Exception) {
                false to "Gagal terhubung: ${e.message}"
            }
        }

    /**
     * Kirim percakapan; streaming menghasilkan [LlmEvent] bertahap.
     * Bila stream=false → satu event TextDelta + Done.
     */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        tools: JsonArray?,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean,
        reasoningEffort: String = "",
        onEvent: suspend (LlmEvent) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val bodyJson = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
            tools?.let { put("tools", it) }
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", stream)
            if (reasoningEffort.isNotBlank()) put("reasoning_effort", reasoningEffort)
        }
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            ok.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty().take(400)
                    onEvent(LlmEvent.Failure("HTTP ${resp.code} — $errBody", resp.code))
                    return@withContext
                }
                val source = resp.body ?: run {
                    onEvent(LlmEvent.Failure("body kosong"))
                    return@withContext
                }
                if (!stream) {
                    val text = source.string()
                    parseFullResponse(text, onEvent)
                } else {
                    parseSseStream(source.byteStream().bufferedReader(), onEvent)
                }
            }
        } catch (e: Exception) {
            onEvent(LlmEvent.Failure("kesalahan jaringan: ${e.message}"))
        }
    }

    private suspend fun parseFullResponse(text: String, onEvent: suspend (LlmEvent) -> Unit) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: run {
                onEvent(LlmEvent.Failure("respons tanpa choices"))
                return
            }
            val msg = choice["message"]?.jsonObject ?: JsonObject(emptyMap())
            (msg["content"] as? JsonPrimitive)?.content?.let { if (it.isNotBlank()) onEvent(LlmEvent.TextDelta(it)) }
            val toolCalls = msg["tool_calls"]?.jsonArray
            toolCalls?.forEach { tcEl ->
                val tc = tcEl.jsonObject
                val id = (tc["id"] as? JsonPrimitive)?.content ?: "call_${System.nanoTime()}"
                val fn = tc["function"]?.jsonObject ?: JsonObject(emptyMap())
                val name = (fn["name"] as? JsonPrimitive)?.content ?: ""
                val args = (fn["arguments"] as? JsonPrimitive)?.content ?: "{}"
                if (name.isNotBlank()) onEvent(LlmEvent.ToolCall(id, name, args))
            }
            onEvent(LlmEvent.Done((choice["finish_reason"] as? JsonPrimitive)?.content, parseUsage(obj["usage"])))
        } catch (e: Exception) {
            onEvent(LlmEvent.Failure("respons tidak valid: ${e.message}"))
        }
    }

    /** Ekstrak objek usage OpenAI-compatible. */
    private fun parseUsage(el: JsonElement?): LlmUsage? {
        val u = el as? JsonObject ?: return null
        fun lng(k: String): Long = (u[k] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
        val p = lng("prompt_tokens"); val c = lng("completion_tokens"); val t = lng("total_tokens")
        return if (p == 0L && c == 0L && t == 0L) null else LlmUsage(p, c, if (t > 0) t else p + c)
    }

    private suspend fun parseSseStream(reader: java.io.BufferedReader, onEvent: suspend (LlmEvent) -> Unit) {
        // Akumulasi tool_calls pecahan (OpenAI memecah arguments per-chunk).
        val toolAcc = linkedMapOf<Int, ToolAccum>()
        var finish: String? = null
        var usage: LlmUsage? = null
        try {
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val chunk = try {
                    json.parseToJsonElement(data).jsonObject
                } catch (e: Exception) {
                    continue
                }
                // usage sering hadir di chunk terakhir (stream_options.include_usage)
                chunk["usage"]?.let { el -> parseUsage(el)?.let { usage = it } }
                val delta = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("delta")?.jsonObject ?: continue
                (delta["content"] as? JsonPrimitive)?.content?.let { c ->
                    if (c.isNotEmpty()) onEvent(LlmEvent.TextDelta(c))
                }
                // reasoning_content JANGAN digabung ke balasan (bug v0.4: teks
                // "berpikir" tercampur ke jawaban) — diabaikan, persis repo
                // sumber yang menyimpannya di field terpisah assistant_msg["reasoning"].
                (delta["tool_calls"] as? JsonArray)?.forEach { tcEl ->
                    val tc = tcEl.jsonObject
                    val idx = (tc["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val a = toolAcc.getOrPut(idx) { ToolAccum() }
                    (tc["id"] as? JsonPrimitive)?.content?.let { if (it.isNotBlank()) a.id = it }
                    tc["function"]?.jsonObject?.let { fn ->
                        (fn["name"] as? JsonPrimitive)?.content?.let { if (it.isNotBlank()) a.name = it }
                        (fn["arguments"] as? JsonPrimitive)?.content?.let { a.args.append(it) }
                    }
                }
                chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("finish_reason")?.let { (it as? JsonPrimitive)?.content?.let { f -> if (f != "null") finish = f } }
            }
            toolAcc.values.forEach { a ->
                if (a.name.isNotBlank()) {
                    onEvent(LlmEvent.ToolCall(a.id.ifBlank { "call_${System.nanoTime()}" }, a.name, a.args.toString().ifBlank { "{}" }))
                }
            }
            onEvent(LlmEvent.Done(finish, usage))
        } catch (e: Exception) {
            onEvent(LlmEvent.Failure("stream terputus: ${e.message}"))
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {
            }
        }
    }

    private class ToolAccum {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }
}
