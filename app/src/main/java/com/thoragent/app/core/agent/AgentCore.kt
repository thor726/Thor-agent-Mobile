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
    data class IterationWarning(val used: Int, val max: Int, val remaining: Int) : AgentEvent
    data class Finished(
        val finishReason: String?,
        val iterations: Int,
        val usage: com.thoragent.app.core.llm.LlmUsage? = null
    ) : AgentEvent
}

/** Gerbang persetujuan sebelum eksekusi tool berbahaya. */
typealias ToolApprover = suspend (id: String, name: String, argsJson: String) -> Boolean

/**
 * IterationBudget — PORT `agent/iteration_budget.py`.
 *
 * Counter thread-safe: `consume()` menolak setelah `maxTotal` habis, `refund()`
 * mengembalikan satu iterasi. `used`/`remaining` dipakai loop untuk memutuskan
 * grace call dan nudge ringkasan.
 */
class IterationBudget(val maxTotal: Int) {
    private var usedCount = 0
    private val lock = Any()

    fun consume(): Boolean = synchronized(lock) {
        if (usedCount >= maxTotal) false else {
            usedCount++
            true
        }
    }

    fun refund() = synchronized(lock) {
        if (usedCount > 0) usedCount--
    }

    val used: Int get() = synchronized(lock) { usedCount }
    val remaining: Int get() = synchronized(lock) { maxOf(0, maxTotal - usedCount) }
}

/**
 * Jantung Thor Agent Mobile — loop agen yang SAMA dengan Thor desktop
 * (`agent/conversation_loop.py` + `agent/chat_completion_helpers.py`):
 *
 *   system prompt → LLM streaming → tool_calls dieksekusi on-device →
 *   hasil dikembalikan ke LLM → ulangi sampai jawaban akhir.
 *
 * Semantik yang dipertahankan dari sumber:
 *  - [IterationBudget] per giliran (bawaan **500**, setara `max_iterations`
 *    Thor desktop).
 *  - **Grace call**: setelah budget habis, satu panggilan terakhir TANPA tool
 *    agar model menutup dengan ringkasan (`_budget_grace_call`).
 *  - **Nudge ringkasan**: bila iterasi terakhir masih memanggil tool,
 *    `MAX_ITERATIONS_SUMMARY_REQUEST` disisipkan dan model diminta menyimpulkan
 *    tanpa tool (port `handle_max_iterations`).
 *  - Hasil tool dipotong ke [TOOL_RESULT_CHAR_BUDGET] karakter (kepala + ekor)
 *    sebelum kembali ke model.
 *  - Peringatan dini pada 80% budget ([AgentEvent.IterationWarning]).
 */
class AgentCore(
    private val llm: LlmClient,
    private val tools: AgentTools
) {

    /** Satu putaran run(): hasilkan balasan penuh (dengan eksekusi tool antara). */
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
        maxIterations: Int = DEFAULT_MAX_ITERATIONS,
        reasoningEffort: String = "",
        approver: ToolApprover? = null,
        onEvent: suspend (AgentEvent) -> Unit
    ) {
        val apiMessages = mutableListOf<LlmMessage>()
        apiMessages.add(LlmMessage(role = "system", content = systemPrompt))
        apiMessages.addAll(history)
        apiMessages.add(LlmMessage(role = "user", content = userText))
        val toolSchemas = tools.schemas()
        val budget = IterationBudget(maxIterations)

        var iteration = 0
        var totalUsage: com.thoragent.app.core.llm.LlmUsage? = null
        var graceCallUsed = false
        var summaryNudgeSent = false
        var warned = false

        while (true) {
            // Port kondisi loop sumber: budget tersisa ATAU grace call tersedia.
            val canCall = budget.consume()
            val grace = !canCall && !graceCallUsed
            if (!canCall && !grace) break
            if (grace) graceCallUsed = true

            iteration++
            // Grace call / nudge ringkasan → minta jawaban akhir TANPA tool.
            val finalOnly = grace || summaryNudgeSent

            if (!warned && budget.maxTotal > 0 && budget.used.toDouble() / budget.maxTotal >= WARN_RATIO) {
                warned = true
                onEvent(AgentEvent.IterationWarning(budget.used, budget.maxTotal, budget.remaining))
            }

            val text = StringBuilder()
            val toolCalls = mutableListOf<LlmEvent.ToolCall>()
            var finish: String? = null
            var usage: com.thoragent.app.core.llm.LlmUsage? = null

            llm.chat(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                messages = apiMessages.toList(),
                tools = if (finalOnly) null else toolSchemas,
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
                    is LlmEvent.Done -> {
                        finish = ev.finishReason
                        usage = ev.usage
                    }
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

            if (toolCalls.isEmpty() || finalOnly) {
                if (finalOnly && toolCalls.isNotEmpty()) {
                    onEvent(AgentEvent.Failure("batas iterasi tercapai — agent diminta menutup dengan ringkasan"))
                }
                onEvent(AgentEvent.Finished(finish, iteration, totalUsage))
                return
            }

            // Budget habis tetapi model masih memanggil tool → nudge ringkasan
            // (port handle_max_iterations), lalu satu putaran tanpa tool.
            if (budget.remaining == 0 && !summaryNudgeSent) {
                summaryNudgeSent = true
                apiMessages.add(
                    LlmMessage(
                        role = "assistant",
                        content = text.toString().ifBlank { "…" },
                        toolCalls = toolCalls.map { LlmMessage.ToolCallSpec(it.id, it.name, it.argsJson) }
                    )
                )
                // Tool yang tidak dieksekusi wajib punya baris hasil agar API konsisten.
                for (tc in toolCalls) {
                    apiMessages.add(
                        LlmMessage(
                            role = "tool",
                            content = "{\"ok\":false,\"error\":\"tidak dieksekusi — batas iterasi tercapai, minta ringkasan akhir\"}",
                            toolCallId = tc.id,
                            name = tc.name
                        )
                    )
                }
                apiMessages.add(LlmMessage(role = "user", content = MAX_ITERATIONS_SUMMARY_REQUEST))
                continue
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
                    } catch (t: Throwable) {
                        // Throwable, bukan hanya Exception: OutOfMemoryError /
                        // StackOverflowError dari output perintah raksasa juga
                        // harus jadi hasil tool, bukan force close.
                        "{\"ok\":false,\"error\":${jsonEscape(t.message ?: t::class.simpleName ?: "eksekusi gagal")}}"
                    }
                } else {
                    "{\"ok\":false,\"error\":\"pengguna menolak eksekusi perintah\"}"
                }
                val ok = result.contains("\"ok\":true")
                onEvent(AgentEvent.ToolResult(tc.id, tc.name, result, ok))
                apiMessages.add(
                    LlmMessage(
                        role = "tool",
                        content = truncateToolResult(result),
                        toolCallId = tc.id,
                        name = tc.name
                    )
                )
            }
        }
        onEvent(AgentEvent.Finished("max_iterations", iteration, totalUsage))
    }

    /** Potong hasil tool panjang dari tengah (kepala + ekor) seperti sisi desktop. */
    private fun truncateToolResult(result: String): String {
        if (result.length <= TOOL_RESULT_CHAR_BUDGET) return result
        val head = TOOL_RESULT_CHAR_BUDGET * 3 / 4
        val tail = TOOL_RESULT_CHAR_BUDGET / 4
        val omitted = result.length - head - tail
        return result.take(head) + "\n… [$omitted karakter dipotong] …\n" + result.takeLast(tail)
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        /** `max_iterations` Thor desktop. */
        const val DEFAULT_MAX_ITERATIONS = 500
        const val TOOL_RESULT_CHAR_BUDGET = 12_000
        const val WARN_RATIO = 0.8

        /** Port `MAX_ITERATIONS_SUMMARY_REQUEST` (context_compressor.py). */
        const val MAX_ITERATIONS_SUMMARY_REQUEST =
            "You've reached the maximum number of tool-calling iterations allowed. Please provide a final response " +
                "summarizing what you've found and accomplished so far, without calling any more tools."
    }
}
