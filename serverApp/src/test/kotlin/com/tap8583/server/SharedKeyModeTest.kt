package com.tap8583.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Without DATABASE_URL the server keeps working with one viewer key and one ingest key. */
class SharedKeyModeTest {

    private fun config() = ServerConfig.fromEnv(org.slf4j.LoggerFactory.getLogger("test")) { name ->
        when (name) {
            "TAP8583_VIEWER_KEY" -> "view123"
            "TAP8583_INGEST_KEY" -> "ing456"
            else -> null
        }
    }

    @Test
    fun `keys gate ingest and reads`() = testApplication {
        application { module(config()) }

        assertTrue(client.get("/api/v1/auth/config").bodyAsText().contains("\"mode\":\"key\""))

        val event = """<visualizer-event realm="r" at="2026-10-10T12:00:00Z" tag="send" serial="POS-A">
            <isomsg direction="outgoing"><field id="0" value="0200"/><field id="11" value="000001"/></isomsg>
            </visualizer-event>"""
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/events") { setBody(event) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/events") { header("x-api-key", "ing456"); setBody(event) }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/transactions") { header("x-api-key", "ing456") }.status)
        var body = ""
        repeat(50) {
            body = client.get("/api/v1/transactions") { header("x-api-key", "view123") }.bodyAsText()
            if (body.contains("000001")) return@repeat
            Thread.sleep(100)
        }
        assertTrue(body.contains("000001"))
    }
}
