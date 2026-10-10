package com.tap8583.server

import com.tap8583.server.accounts.Mailer
import com.tap8583.server.accounts.SESSION_COOKIE
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests of accounts mode against a real Postgres.
 * Set TAP8583_TEST_DATABASE_URL (a JDBC URL to a throwaway database) to run them.
 */
class AccountsModeTest {

    private val dbUrl = System.getenv("TAP8583_TEST_DATABASE_URL")

    private class CapturingMailer : Mailer {
        val sent = mutableListOf<Pair<String, String>>()
        override val canSend = true
        override suspend fun send(to: String, subject: String, text: String) { sent += to to text }
        fun lastLinkParam(to: String, param: String): String =
            Regex("[?&]$param=([^\\s&]+)").find(sent.last { it.first == to }.second)!!.groupValues[1]
    }

    private fun config() = ServerConfig.fromEnv(org.slf4j.LoggerFactory.getLogger("test")) { name ->
        when (name) {
            "DATABASE_URL" -> dbUrl
            "TAP8583_PUBLIC_URL" -> "https://tap.example.com"
            "TAP8583_ALLOWED_ORIGINS" -> "https://tap.example.com"
            else -> null
        }
    }

    @Before
    fun resetDatabase() {
        assumeTrue("TAP8583_TEST_DATABASE_URL not set", dbUrl != null)
        DriverManager.getConnection(dbUrl).use {
            it.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
        }
    }

    private val origin = "https://tap.example.com"

    private suspend fun ApplicationTestBuilder.signIn(mailer: CapturingMailer, email: String): String {
        val sent = client.post("/api/v1/auth/email") {
            header(HttpHeaders.Origin, origin)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        assertEquals(HttpStatusCode.OK, sent.status, sent.bodyAsText())
        val token = mailer.lastLinkParam(email, "emailToken")
        val verified = client.post("/api/v1/auth/email/verify") {
            header(HttpHeaders.Origin, origin)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token"}""")
        }
        assertEquals(HttpStatusCode.OK, verified.status, verified.bodyAsText())
        val cookie = verified.setCookie().first { it.name == SESSION_COOKIE }
        assertTrue(cookie.httpOnly && cookie.secure)

        // The link works once.
        val again = client.post("/api/v1/auth/email/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, again.status)
        return cookie.value
    }

    private fun HttpRequestBuilder.session(token: String) {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=$token")
        header(HttpHeaders.Origin, origin)
    }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `sign in, create a workspace, ingest with its key, and keep other people out`() = testApplication {
        val mailer = CapturingMailer()
        application { module(config(), mailer) }

        val config = json(client.get("/api/v1/auth/config").bodyAsText())
        assertEquals("accounts", config["mode"]!!.jsonPrimitive.content)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)

        val alice = signIn(mailer, "alice@example.com")
        val ws = json(client.post("/api/v1/workspaces") {
            session(alice)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Acme QA"}""")
        }.bodyAsText())
        val wsId = ws["id"]!!.jsonPrimitive.content
        assertEquals("owner", ws["role"]!!.jsonPrimitive.content)

        val created = json(client.post("/api/v1/workspaces/$wsId/keys") {
            session(alice)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"PAX debug build"}""")
        }.bodyAsText())
        val key = created["value"]!!.jsonPrimitive.content
        val keyId = created["key"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertTrue(key.startsWith("t8k_"))

        val event = """<visualizer-event realm="r" at="2026-10-10T12:00:00Z" tag="send" serial="POS-A">
            <isomsg direction="outgoing"><field id="0" value="0200"/><field id="2" value="5061240207416432753"/><field id="11" value="000001"/></isomsg>
            </visualizer-event>"""
        val ingest = client.post("/api/v1/events") { header("x-api-key", key); setBody(event) }
        assertEquals(HttpStatusCode.OK, ingest.status, ingest.bodyAsText())

        // Stored in Postgres (asynchronously), card number masked.
        var txs = JsonArray(emptyList())
        repeat(50) {
            txs = Json.parseToJsonElement(client.get("/api/v1/transactions?workspace=$wsId") { session(alice) }.bodyAsText()).jsonArray
            if (txs.isNotEmpty()) return@repeat
            Thread.sleep(100)
        }
        assertEquals(1, txs.size)
        assertTrue("2753" in txs[0].toString() && "0207416432753" !in txs[0].toString())

        // Bob is signed in but not a member.
        val bob = signIn(mailer, "bob@example.com")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/transactions?workspace=$wsId") { session(bob) }.status)

        // Cookie requests that change things must come from the dashboard's origin.
        val forged = client.post("/api/v1/clear?workspace=$wsId") {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$alice")
            header(HttpHeaders.Origin, "https://evil.example")
        }
        // CORS answers 403 for an unknown origin; the session check would answer 401.
        assertTrue(forged.status == HttpStatusCode.Forbidden || forged.status == HttpStatusCode.Unauthorized, forged.status.toString())

        // Alice invites Bob; Bob accepts and can read.
        val invite = client.post("/api/v1/workspaces/$wsId/invites") {
            session(alice)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"bob@example.com"}""")
        }
        assertEquals(HttpStatusCode.Created, invite.status)
        val inviteToken = mailer.lastLinkParam("bob@example.com", "invite")
        assertEquals("Acme QA", json(client.get("/api/v1/invites/$inviteToken").bodyAsText())["workspaceName"]!!.jsonPrimitive.content)
        val accepted = client.post("/api/v1/invites/accept") {
            session(bob)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$inviteToken"}""")
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals("member", json(accepted.bodyAsText())["role"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/transactions?workspace=$wsId") { session(bob) }.status)

        // Members can't manage keys; the last owner can't be removed.
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/v1/workspaces/$wsId/keys/$keyId") { session(bob) }.status)
        val aliceId = json(client.get("/api/v1/me") { session(alice) }.bodyAsText())["user"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/v1/workspaces/$wsId/members/$aliceId") { session(alice) }.status)

        // A revoked key stops working straight away.
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/workspaces/$wsId/keys/$keyId") { session(alice) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/events") { header("x-api-key", key); setBody(event) }.status)

        // Signing out ends the session.
        client.post("/api/v1/auth/logout") { session(alice) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { session(alice) }.status)
    }
}
