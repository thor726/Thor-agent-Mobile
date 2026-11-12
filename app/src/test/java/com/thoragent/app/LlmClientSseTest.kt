package com.thoragent.app

import com.thoragent.app.core.llm.LlmClient
import com.thoragent.app.core.llm.LlmEvent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class LlmClientSseTest {

    private val ok = OkHttpClient()

    @Test
    fun `sse stream menumpuk teks dan tool_calls terpecah`() {
        val server = MockWebServer()
        val sse = listOf(
            """data: {"choices":[{"delta":{"role":"assistant","content":"Hal"}}]}""",
            """data: {"choices":[{"delta":{"content":"o"}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read_file","arguments":"{\"pa"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\":\"/tmp/a.txt\"}"}}]}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            """data: [DONE]"""
        ).joinToString("\n\n") + "\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse)
        )
        server.start()
        val client = LlmClient(ok)
        val events = mutableListOf<LlmEvent>()
        runBlocking {
            client.chat(
                baseUrl = server.url("/v1").toString().removeSuffix("/"),
                apiKey = "k",
                model = "m",
                messages = emptyList(),
                tools = null,
                temperature = 0.7,
                maxTokens = 100,
                stream = true
            ) { events.add(it) }
        }
        val text = events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Halo", text)
        val tool = events.filterIsInstance<LlmEvent.ToolCall>().single()
        assertEquals("call_1", tool.id)
        assertEquals("read_file", tool.name)
        assertEquals("""{"path":"/tmp/a.txt"}""", tool.argsJson)
        val done = events.filterIsInstance<LlmEvent.Done>().single()
        assertEquals("tool_calls", done.finishReason)
        server.shutdown()
    }

    @Test
    fun `respons penuh non-stream memuat content dan tool_calls`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Selesai.","tool_calls":[{"id":"c9","type":"function","function":{"name":"list_dir","arguments":"{\"path\":\"/sd\"}"}}]}}]}"""
                )
        )
        server.start()
        val client = LlmClient(ok)
        val events = mutableListOf<LlmEvent>()
        runBlocking {
            client.chat(
                baseUrl = server.url("/v1").toString().removeSuffix("/"),
                apiKey = "k",
                model = "m",
                messages = emptyList(),
                tools = null,
                temperature = 0.7,
                maxTokens = 100,
                stream = false
            ) { events.add(it) }
        }
        assertTrue(events.filterIsInstance<LlmEvent.TextDelta>().any { it.text == "Selesai." })
        assertEquals(1, events.filterIsInstance<LlmEvent.ToolCall>().size)
        assertEquals("stop", events.filterIsInstance<LlmEvent.Done>().single().finishReason)
        server.shutdown()
    }
}
