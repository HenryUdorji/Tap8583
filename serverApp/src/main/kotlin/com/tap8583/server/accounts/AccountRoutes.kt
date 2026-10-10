package com.tap8583.server.accounts

import com.tap8583.server.ServerConfig
import com.tap8583.server.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val MAX_NAME_LENGTH = 80
private const val EMAIL_RATE_LIMIT_MS = 60_000L
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Only same-site paths are allowed as a post-sign-in destination. */
private fun safeNext(next: String?): String =
    next?.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.contains('\\') } ?: "/"

private fun cleanName(name: String): String? = name.trim().take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }

private fun User.toDto() = UserDto(id.toString(), email, name, avatarUrl)
private fun WorkspaceSummary.toDto() = WorkspaceDto(id.toString(), name, role)
private fun Member.toDto() = MemberDto(userId.toString(), email, name, avatarUrl, role, joinedAt.toString())
private fun Invite.toDto() = InviteDto(id.toString(), email, createdAt.toString(), expiresAt.toString())
private fun IngestKey.toDto() = IngestKeyDto(id.toString(), name, prefix, createdAt.toString(), lastUsedAt?.toString(), revokedAt?.toString())

/**
 * Email-link sign-in, the current user, and workspace management: members,
 * invites and terminal keys.
 */
fun Route.accountRoutes(
    config: ServerConfig,
    access: AccessControl,
    mailer: Mailer,
    log: Logger
) {
    val accounts = access.accounts

    get("/api/v1/auth/config") {
        call.respond(
            AuthConfigDto(
                mode = if (accounts != null) "accounts" else "key",
                emailSignIn = accounts != null && mailer.canSend
            )
        )
    }

    if (accounts == null) return

    suspend fun ApplicationCall.startSession(user: User) {
        val token = accounts.createSession(user.id)
        response.cookies.append(
            Cookie(
                name = SESSION_COOKIE,
                value = token,
                maxAge = Accounts.SESSION_TTL.seconds.toInt(),
                path = "/",
                httpOnly = true,
                secure = config.secureCookies,
                extensions = mapOf("SameSite" to "Lax")
            )
        )
    }

    // --- Email link ----------------------------------------------------------

    val lastEmailSent = ConcurrentHashMap<String, Long>()

    post("/api/v1/auth/email") {
        if (!mailer.canSend) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Email sign-in is not set up on this server"))
        val body = runCatching { call.receive<EmailRequest>() }.getOrNull()
        val email = body?.email?.trim()?.lowercase()
        if (email == null || email.length > 254 || !EMAIL_PATTERN.matches(email)) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Enter a valid email address"))
        }
        val now = System.currentTimeMillis()
        if (now - (lastEmailSent[email] ?: 0L) < EMAIL_RATE_LIMIT_MS) {
            return@post call.respond(HttpStatusCode.TooManyRequests, ErrorDto("A link was just sent. Check your inbox, or try again in a minute."))
        }
        lastEmailSent[email] = now

        // The link opens the dashboard, which confirms it with a POST, so mail
        // scanners that prefetch links cannot use up the token.
        val token = accounts.createEmailLoginToken(email)
        val link = "${config.appUrl}${safeNext(body.next)}".let { base ->
            val sep = if ('?' in base) '&' else '?'
            "$base${sep}emailToken=$token"
        }
        try {
            mailer.send(email, EmailTemplates.signIn(link))
        } catch (e: Exception) {
            log.warn("Could not send sign-in email: {}", e.message)
            return@post call.respond(HttpStatusCode.BadGateway, ErrorDto("The sign-in email could not be sent. Try again shortly."))
        }
        call.respond(mapOf("sent" to true))
    }

    post("/api/v1/auth/email/verify") {
        val token = runCatching { call.receive<TokenRequest>() }.getOrNull()?.token
        val email = token?.let { accounts.consumeEmailLoginToken(it) }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("This sign-in link has expired or was already used. Request a new one."))
        val user = accounts.signIn(email)
        call.startSession(user)
        call.respond(MeDto(user.toDto(), accounts.workspacesFor(user.id).map { it.toDto() }))
    }

    post("/api/v1/auth/logout") {
        call.request.cookies[SESSION_COOKIE]?.let { accounts.deleteSession(it) }
        call.response.cookies.append(Cookie(SESSION_COOKIE, "", maxAge = 0, path = "/", httpOnly = true, secure = config.secureCookies))
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/v1/me") {
        val user = access.requireUser(call) ?: return@get
        call.respond(MeDto(user.toDto(), accounts.workspacesFor(user.id).map { it.toDto() }))
    }

    patch("/api/v1/me") {
        val user = access.requireUser(call) ?: return@patch
        val name = runCatching { call.receive<NameRequest>() }.getOrNull()?.name?.let(::cleanName)
            ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorDto("Enter your name"))
        accounts.setName(user.id, name)
        call.respond(user.copy(name = name).toDto())
    }

    // --- Workspaces ----------------------------------------------------------

    post("/api/v1/workspaces") {
        val user = access.requireUser(call) ?: return@post
        val name = runCatching { call.receive<NameRequest>() }.getOrNull()?.name?.let(::cleanName)
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Give the workspace a name"))
        call.respond(HttpStatusCode.Created, accounts.createWorkspace(user.id, name).toDto())
    }

    patch("/api/v1/workspaces/{id}") {
        val user = access.requireUser(call) ?: return@patch
        val (id, role) = access.requireRole(call, user, call.parameters["id"], ownerOnly = true) ?: return@patch
        val name = runCatching { call.receive<NameRequest>() }.getOrNull()?.name?.let(::cleanName)
            ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorDto("Give the workspace a name"))
        accounts.renameWorkspace(id, name)
        call.respond(WorkspaceDto(id.toString(), name, role))
    }

    get("/api/v1/workspaces/{id}/members") {
        val user = access.requireUser(call) ?: return@get
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@get
        call.respond(
            MembersDto(
                members = accounts.members(id).map { it.toDto() },
                invites = accounts.pendingInvites(id).map { it.toDto() },
                canEmail = mailer.canSend
            )
        )
    }

    delete("/api/v1/workspaces/{id}/members/{userId}") {
        val user = access.requireUser(call) ?: return@delete
        val target = call.parameters["userId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorDto("Member not found"))
        // Owners can remove anyone; members can remove themselves (leave).
        val (id) = access.requireRole(call, user, call.parameters["id"], ownerOnly = target != user.id) ?: return@delete
        when (accounts.removeMember(id, target)) {
            RemoveMemberResult.REMOVED -> call.respond(HttpStatusCode.NoContent)
            RemoveMemberResult.NOT_A_MEMBER -> call.respond(HttpStatusCode.NotFound, ErrorDto("Member not found"))
            RemoveMemberResult.LAST_OWNER -> call.respond(
                HttpStatusCode.Conflict,
                ErrorDto("A workspace needs at least one owner. Make someone else an owner first.")
            )
        }
    }

    // --- Invites -------------------------------------------------------------

    post("/api/v1/workspaces/{id}/invites") {
        val user = access.requireUser(call) ?: return@post
        val (id) = access.requireRole(call, user, call.parameters["id"], ownerOnly = true) ?: return@post
        val email = runCatching { call.receive<EmailRequest>() }.getOrNull()?.email?.trim()
        if (email == null || email.length > 254 || !EMAIL_PATTERN.matches(email)) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Enter a valid email address"))
        }
        val (invite, token) = accounts.createInvite(id, email, user.id)
        val link = "${config.appUrl}/?invite=$token"
        val workspaceName = accounts.workspacesFor(user.id).firstOrNull { it.id == id }?.name ?: "a workspace"
        val emailed = mailer.canSend && runCatching {
            mailer.send(email, EmailTemplates.invite(user.name ?: user.email, workspaceName, link))
        }.onFailure { log.warn("Could not send invite email: {}", it.message) }.isSuccess
        call.respond(HttpStatusCode.Created, CreatedInviteDto(invite.toDto(), link, emailed))
    }

    delete("/api/v1/workspaces/{id}/invites/{inviteId}") {
        val user = access.requireUser(call) ?: return@delete
        val (id) = access.requireRole(call, user, call.parameters["id"], ownerOnly = true) ?: return@delete
        val inviteId = call.parameters["inviteId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (inviteId == null || !accounts.revokeInvite(id, inviteId)) {
            return@delete call.respond(HttpStatusCode.NotFound, ErrorDto("Invite not found"))
        }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/v1/invites/{token}") {
        val (workspaceName, email) = call.parameters["token"]?.let { accounts.inviteDetails(it) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorDto("This invite has expired or was already used"))
        call.respond(InviteDetailsDto(workspaceName, email))
    }

    post("/api/v1/invites/accept") {
        val user = access.requireUser(call) ?: return@post
        val token = runCatching { call.receive<TokenRequest>() }.getOrNull()?.token
        val workspace = token?.let { accounts.acceptInvite(it, user.id) }
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorDto("This invite has expired or was already used"))
        call.respond(workspace.toDto())
    }

    // --- Terminal keys -------------------------------------------------------

    get("/api/v1/workspaces/{id}/keys") {
        val user = access.requireUser(call) ?: return@get
        val (id) = access.requireRole(call, user, call.parameters["id"]) ?: return@get
        call.respond(accounts.ingestKeys(id).map { it.toDto() })
    }

    post("/api/v1/workspaces/{id}/keys") {
        val user = access.requireUser(call) ?: return@post
        val (id) = access.requireRole(call, user, call.parameters["id"], ownerOnly = true) ?: return@post
        val name = runCatching { call.receive<NameRequest>() }.getOrNull()?.name?.let(::cleanName)
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Name the key, e.g. the app or terminal it's for"))
        val (key, value) = accounts.createIngestKey(id, name, user.id)
        call.respond(HttpStatusCode.Created, CreatedIngestKeyDto(key.toDto(), value))
    }

    delete("/api/v1/workspaces/{id}/keys/{keyId}") {
        val user = access.requireUser(call) ?: return@delete
        val (id) = access.requireRole(call, user, call.parameters["id"], ownerOnly = true) ?: return@delete
        val keyId = call.parameters["keyId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (keyId == null || !accounts.revokeIngestKey(id, keyId)) {
            return@delete call.respond(HttpStatusCode.NotFound, ErrorDto("Key not found"))
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
