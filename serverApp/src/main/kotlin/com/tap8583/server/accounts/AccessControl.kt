package com.tap8583.server.accounts

import com.tap8583.server.ServerConfig
import com.tap8583.server.keyMatches
import com.tap8583.server.models.ErrorDto
import com.tap8583.server.originAllowed
import com.tap8583.server.providedKey
import com.tap8583.server.storage.Storage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.util.UUID

const val SESSION_COOKIE = "tap8583_session"

/**
 * Decides which workspace a request may act on.
 *
 * Shared-key mode has one workspace and two keys. Accounts mode uses the
 * session cookie plus a `workspace` query parameter for people, and
 * per-workspace keys for terminals.
 */
class AccessControl(private val config: ServerConfig, val accounts: Accounts?) {

    val accountsMode: Boolean get() = accounts != null

    /**
     * The signed-in user, or null. Cookie-authenticated requests that change
     * something must come from the dashboard's own origin.
     */
    suspend fun currentUser(call: ApplicationCall): User? {
        val accounts = accounts ?: return null
        val token = call.request.cookies[SESSION_COOKIE] ?: return null
        if (call.request.httpMethod != HttpMethod.Get && !call.originAllowed(config)) return null
        return accounts.userForSession(token)
    }

    /** Responds 401 and returns null unless someone is signed in. */
    suspend fun requireUser(call: ApplicationCall): User? =
        currentUser(call) ?: run {
            call.respond(HttpStatusCode.Unauthorized, ErrorDto("Sign in first"))
            null
        }

    /** The member's role in a workspace, or a 403/404 response and null. */
    suspend fun requireRole(call: ApplicationCall, user: User, workspaceId: String?, ownerOnly: Boolean = false): Pair<UUID, String>? {
        val id = workspaceId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val role = id?.let { accounts?.roleIn(user.id, it) }
        if (id == null || role == null) {
            call.respond(HttpStatusCode.NotFound, ErrorDto("Workspace not found"))
            return null
        }
        if (ownerOnly && role != Accounts.OWNER) {
            call.respond(HttpStatusCode.Forbidden, ErrorDto("Only workspace owners can do this"))
            return null
        }
        return id to role
    }

    /** Workspace for dashboard reads and clears, or a 401/404 response and null. */
    suspend fun requireViewer(call: ApplicationCall): String? {
        if (!accountsMode) {
            if (keyMatches(call.providedKey(), config.viewerKey)) return Storage.DEFAULT_WORKSPACE
            call.respond(HttpStatusCode.Unauthorized, ErrorDto("Invalid or missing API key"))
            return null
        }
        val user = requireUser(call) ?: return null
        return requireRole(call, user, call.request.queryParameters["workspace"])?.first?.toString()
    }

    /**
     * Workspace for POST /events: a terminal's key, or (for "Simulate") the
     * viewer key or a signed-in member.
     */
    suspend fun requireIngest(call: ApplicationCall): String? {
        val key = call.providedKey()
        if (!accountsMode) {
            if (keyMatches(key, config.ingestKey) || keyMatches(key, config.viewerKey)) return Storage.DEFAULT_WORKSPACE
            call.respond(HttpStatusCode.Unauthorized, ErrorDto("Invalid or missing API key"))
            return null
        }
        if (!key.isNullOrBlank()) {
            accounts!!.workspaceForIngestKey(key)?.let { return it }
            call.respond(HttpStatusCode.Unauthorized, ErrorDto("Unknown or revoked terminal key"))
            return null
        }
        return requireViewer(call)
    }

    /** Workspace for a WebSocket client, from its AUTH frame (and session cookie in accounts mode). */
    suspend fun streamWorkspace(call: ApplicationCall, apiKey: String?, workspace: String?): String? {
        if (!accountsMode) {
            return if (keyMatches(apiKey, config.viewerKey)) Storage.DEFAULT_WORKSPACE else null
        }
        if (!call.originAllowed(config)) return null
        val user = currentUser(call) ?: return null
        val id = workspace?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return if (accounts!!.roleIn(user.id, id) != null) id.toString() else null
    }
}
