package com.thoragent.app

import com.thoragent.app.core.agent.AgentCore
import com.thoragent.app.core.agent.AgentEvent
import com.thoragent.app.core.agent.AgentTools
import com.thoragent.app.core.llm.LlmClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCoreTest {

    private class FakeTools : AgentTools {
        val calls = mutableListOf<Pair<String, String>>()
        override fun schemas(): JsonArray = kotlinx.serialization.json.buildJsonArray { }
        override suspend fun execute(name: String, argsJson: String, cwd: String): String {
            calls.add(name to argsJson)
            return """{"ok":true,"echo":"$name"}"""
        }
        override fun needsApproval(name: String, argsJson: String): Boolean = false
    }

    @Test
    fun `loop agen mengeksekusi tool lalu berhenti di balasan akhir`() {
        val server = MockWebServer()
        // iterasi 1: LLM minta tool
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"",
                       "tool_calls":[{"id":"t1","type":"function","function":{"name":"list_dir","arguments":"{\"path\":\"/sdcard\"}"}}]}}]}"""
                )
        )
        // iterasi 2: balasan akhir
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Ada 3 berkas."}}]}"""
                )
        )
        server.start()

        val tools = FakeTools()
        val core = AgentCore(LlmClient(OkHttpClient()), tools)
        val events = mutableListOf<AgentEvent>()
        runBlocking {
            core.run(
                baseUrl = server.url("/v1").toString().removeSuffix("/"),
                apiKey = "k",
                model = "m",
                systemPrompt = "sys",
                history = emptyList(),
                userText = "apa isi /sdcard?",
                temperature = 0.7,
                maxTokens = 100,
                stream = false,
                cwd = "/sdcard",
                approver = null
            ) { events.add(it) }
        }

        // tool dieksekusi persis sekali dengan argumen benar
        assertEquals(1, tools.calls.size)
        assertEquals("list_dir", tools.calls[0].first)
        val args = Json.parseToJsonElement(tools.calls[0].second).jsonObject()
        assertEquals("/sdcard", args["path"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })

        // balasan akhir ter-stream sebagai TextDelta lalu Finished
        val text = events.filterIsInstance<AgentEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Ada 3 berkas.", text)
        assertTrue(events.any { it is AgentEvent.ToolCallStart })
        assertTrue(events.any { it is AgentEvent.ToolResult })
        assertEquals("stop", events.filterIsInstance<AgentEvent.Finished>().single().finishReason)

        // dua permintaan: iterasi pertama membawa pertanyaan, kedua membawa hasil tool
        val first = server.takeRequest()
        val second = server.takeRequest()
        val body1 = first.body.readUtf8()
        val body2 = second.body.readUtf8()
        assertTrue(body1.contains("apa isi /sdcard?"))
        assertTrue(body2.contains("\"role\":\"tool\""))
        assertTrue(body2.contains("t1"))
        server.shutdown()
    }

    @Test
    fun `approver menolak tool berbahaya - hasil ditolak dikembalikan ke LLM`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"",
                       "tool_calls":[{"id":"t9","type":"function","function":{"name":"shell","arguments":"{\"command\":\"rm -rf /data/x\"}"}}]}}]}"""
                )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Baik, dibatalkan."}}]}""")
        )
        server.start()

        val tools = object : AgentTools {
            override fun schemas(): JsonArray = kotlinx.serialization.json.buildJsonArray { }
            override suspend fun execute(name: String, argsJson: String, cwd: String): String =
                """{"ok":true}"""
            override fun needsApproval(name: String, argsJson: String): Boolean =
                argsJson.contains("rm -rf")
        }
        val core = AgentCore(LlmClient(OkHttpClient()), tools)
        var approved = false
        val events = mutableListOf<AgentEvent>()
        runBlocking {
            core.run(
                baseUrl = server.url("/v1").toString().removeSuffix("/"),
                apiKey = "k",
                model = "m",
                systemPrompt = "sys",
                history = emptyList(),
                userText = "hapus folder",
                temperature = 0.7,
                maxTokens = 100,
                stream = false,
                approver = { _, _, _ -> approved = true; false }
            ) { events.add(it) }
        }
        // approver dipanggil, hasil penolakan dikirim sebagai pesan tool pada permintaan kedua
        assertTrue(approved)
        val first = server.takeRequest()
        val second = server.takeRequest()
        val body1 = first.body.readUtf8()
        val body2 = second.body.readUtf8()
        assertTrue(body1.contains("hapus folder"))
        assertTrue(body2.contains("pengguna menolak"))
        server.shutdown()
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObject(): JsonObject =
        this as JsonObject
}
