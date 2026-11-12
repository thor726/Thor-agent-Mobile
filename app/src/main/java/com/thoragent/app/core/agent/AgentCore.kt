package com.thoragent.app.core.agent

import com.thoragent.app.core.llm.LlmClient
import com.thoragent.app.core.llm.LlmEvent
import com.thoragent.app.core.llm.LlmMessage
import kotlinx.coroutines.CancellationException

/** Kejadian loop agen untuk UI (bubble streaming + kartu tool dilipat). */
sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolCallStart(val id: String, val name: String, val argsJson: String) : AgentEvent
    data class ToolResult(val id: String, val name: String, val resultJson: String, val ok: Boolean) : AgentEvent
    data class Failure(val message: String, val httpCode: Int? = null) : AgentEvent
    data class Finished(
        val finishReason: String?,
        val iterations: Int,
        val usage: com.thoragent.app.core.llm.LlmUsage? = null
    ) : AgentEvent
}

/** Gerbang persetujuan sebelum eksekusi tool berbahaya. */
typealias ToolApprover = suspend (id: String, name: String, argsJson: String) -> Boolean

/**
 * Jantung Thor Agent Mobile — loop agen NATIF di Kotlin:
 * system prompt (identitas + skill repo Thor-agent) → LLM streaming →
 * tool_calls dieksekusi on-device → hasil dikembalikan ke LLM → ulangi
 * sampai jawaban akhir. Tidak ada engine/bridge eksternal.
 */
class AgentCore(
    private val llm: LlmClient,
    private val tools: AgentTools
) {

    /** Satu putaran send(): hasilkan balasan penuh (dengan eksekusi tool antara). */
    suspend fun run(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<LlmMessage>,
        userText: String,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean,
        cwd: String = "/sdcard",
        maxIterations: Int = 12,
        reasoningEffort: String = "",
        approver: ToolApprover? = null,
        onEvent: suspend (AgentEvent) -> Unit
    ) {
        val apiMessages = mutableListOf<LlmMessage>()
        apiMessages.add(LlmMessage(role = "system", content = systemPrompt))
        apiMessages.addAll(history)
        apiMessages.add(LlmMessage(role = "user", content = userText))
        val toolSchemas = tools.schemas()

        var iteration = 0
        var totalUsage: com.thoragent.app.core.llm.LlmUsage? = null
        while (iteration < maxIterations) {
            iteration++
            // iterasi terakhir: minta ringkasan akhir TANPA tool (grace call
            // ala repo sumber — agent dipaksa menutup pekerjaan dengan laporan).
            val lastChance = iteration == maxIterations
            val text = StringBuilder()
            val toolCalls = mutableListOf<LlmEvent.ToolCall>()
            var finish: String? = null
            var usage: com.thoragent.app.core.llm.LlmUsage? = null

            llm.chat(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                messages = apiMessages.toList(),
                tools = if (lastChance) null else toolSchemas,
                temperature = temperature,
                maxTokens = maxTokens,
                stream = stream,
                reasoningEffort = reasoningEffort
            ) { ev ->
                when (ev) {
                    is LlmEvent.TextDelta -> {
                        text.append(ev.text)
                        onEvent(AgentEvent.TextDelta(ev.text))
                    }
                    is LlmEvent.ToolCall -> toolCalls.add(ev)
                    is LlmEvent.Done -> { finish = ev.finishReason; usage = ev.usage }
                    is LlmEvent.Failure -> onEvent(AgentEvent.Failure(ev.message, ev.httpCode))
                }
            }

            usage?.let { u ->
                val prev = totalUsage ?: com.thoragent.app.core.llm.LlmUsage()
                totalUsage = com.thoragent.app.core.llm.LlmUsage(
                    prev.promptTokens + u.promptTokens,
                    prev.completionTokens + u.completionTokens,
                    prev.totalTokens + u.totalTokens
                )
            }

            if (toolCalls.isEmpty() || lastChance) {
                // balasan akhir (mungkin teks kosong bila finish_reason panjang);
                // lastChance memaksa laporan penutup tanpa tool lagi.
                if (lastChance && toolCalls.isNotEmpty()) {
                    onEvent(AgentEvent.Failure("batas iterasi tercapai — agent diminta menutup dengan ringkasan"))
                }
                onEvent(AgentEvent.Finished(finish, iteration, totalUsage))
                return
            }

            // pesan assistant dengan tool_calls (untuk API berikutnya)
            apiMessages.add(
                LlmMessage(
                    role = "assistant",
                    content = text.toString().ifBlank { null },
                    toolCalls = toolCalls.map { LlmMessage.ToolCallSpec(it.id, it.name, it.argsJson) }
                )
            )

            // eksekusi tiap tool native on-device — gerbang persetujuan bila berbahaya
            for (tc in toolCalls) {
                onEvent(AgentEvent.ToolCallStart(tc.id, tc.name, tc.argsJson))
                val allowed = if (tools.needsApproval(tc.name, tc.argsJson)) {
                    approver?.invoke(tc.id, tc.name, tc.argsJson) ?: true
                } else true
                val result: String = if (allowed) {
                    try {
                        tools.execute(tc.name, tc.argsJson, cwd)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        """{"ok":false,"error":"${e.message ?: "eksekusi gagal"}"}"""
                    }
                } else {
                    """{"ok":false,"error":"pengguna menolak eksekusi perintah"}"""
                }
                val ok = result.contains("\"ok\":true")
                onEvent(AgentEvent.ToolResult(tc.id, tc.name, result, ok))
                apiMessages.add(
                    LlmMessage(
                        role = "tool",
                        content = result.take(12_000),
                        toolCallId = tc.id,
                        name = tc.name
                    )
                )
            }
        }
        // tak terjangkau (lastChance selalu return), dijaga untuk kejadian aneh.
        onEvent(AgentEvent.Finished("max_iterations", iteration, totalUsage))
    }
}
