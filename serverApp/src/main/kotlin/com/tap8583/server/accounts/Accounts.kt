package com.tap8583.server.accounts

import com.tap8583.server.db.Db
import com.tap8583.server.db.instant
import com.tap8583.server.db.query
import com.tap8583.server.db.queryOne
import com.tap8583.server.db.update
import com.tap8583.server.db.uuid
import com.tap8583.server.generateToken
import com.tap8583.server.sha256Hex
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class User(val id: UUID, val email: String, val name: String?, val avatarUrl: String?)

data class WorkspaceSummary(val id: UUID, val name: String, val role: String)

data class Member(
    val userId: UUID,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val role: String,
    val joinedAt: Instant
)

data class Invite(val id: UUID, val email: String, val createdAt: Instant, val expiresAt: Instant)

data class IngestKey(
    val id: UUID,
    val name: String,
    val prefix: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?
)

enum class RemoveMemberResult { REMOVED, NOT_A_MEMBER, LAST_OWNER }

/** Users, sessions, workspaces, invites and terminal keys, all in Postgres. */
class Accounts(private val db: Db) {

    companion object {
        val SESSION_TTL: Duration = Duration.ofDays(30)
        val EMAIL_LINK_TTL: Duration = Duration.ofMinutes(15)
        val INVITE_TTL: Duration = Duration.ofDays(7)
        const val INGEST_KEY_PREFIX = "t8k_"
        const val OWNER = "owner"
        const val MEMBER = "member"

        /** How long a resolved ingest key is trusted before the database is asked again. */
        private const val KEY_CACHE_MS = 60_000L
    }

    // ---------------------------------------------------------------------
    // Users and sign-in
    // ---------------------------------------------------------------------

    /** Finds or creates the user for an email address whose sign-in link was just used. */
    suspend fun signIn(email: String): User = db.tx {
        queryOne("SELECT * FROM users WHERE lower(email) = lower(?)", email) { it.toUser() }
            ?: User(UUID.randomUUID(), email.trim().lowercase(), null, null).also {
                update("INSERT INTO users (id, email) VALUES (?, ?)", it.id, it.email)
            }
    }

    /** Sets the name shown to teammates. */
    suspend fun setName(userId: UUID, name: String) {
        db.tx { update("UPDATE users SET name = ? WHERE id = ?", name, userId) }
    }

    /** Creates a session and returns the cookie value. */
    suspend fun createSession(userId: UUID): String {
        val token = generateToken("s_", 32)
        db.tx {
            update(
                "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                sha256Hex(token), userId, Instant.now().plus(SESSION_TTL)
            )
        }
        return token
    }

    suspend fun userForSession(token: String): User? = db.tx {
        queryOne(
            """
            SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id
             WHERE s.token_hash = ? AND s.expires_at > now()
            """.trimIndent(),
            sha256Hex(token)
        ) { it.toUser() }
    }

    suspend fun deleteSession(token: String) {
        db.tx { update("DELETE FROM sessions WHERE token_hash = ?", sha256Hex(token)) }
    }

    /** Creates a single-use email sign-in token. */
    suspend fun createEmailLoginToken(email: String): String {
        val token = generateToken("e_", 32)
        db.tx {
            update(
                "INSERT INTO email_login_tokens (token_hash, email, expires_at) VALUES (?, ?, ?)",
                sha256Hex(token), email.trim(), Instant.now().plus(EMAIL_LINK_TTL)
            )
        }
        return token
    }

    /** Returns the email the token was sent to, once; null when unknown, used or expired. */
    suspend fun consumeEmailLoginToken(token: String): String? = db.tx {
        queryOne(
            """
            UPDATE email_login_tokens SET used_at = now()
             WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
            RETURNING email
            """.trimIndent(),
            sha256Hex(token)
        ) { it.getString("email") }
    }

    // ---------------------------------------------------------------------
    // Workspaces and members
    // ---------------------------------------------------------------------

    suspend fun workspacesFor(userId: UUID): List<WorkspaceSummary> = db.tx {
        query(
            """
            SELECT w.id, w.name, m.role FROM memberships m JOIN workspaces w ON w.id = m.workspace_id
             WHERE m.user_id = ? ORDER BY w.created_at
            """.trimIndent(),
            userId
        ) { WorkspaceSummary(it.uuid("id"), it.getString("name"), it.getString("role")) }
    }

    /** The user's role in the workspace, or null when they are not a member. */
    suspend fun roleIn(userId: UUID, workspaceId: UUID): String? = db.tx { role(userId, workspaceId) }

    private fun Connection.role(userId: UUID, workspaceId: UUID): String? =
        queryOne("SELECT role FROM memberships WHERE user_id = ? AND workspace_id = ?", userId, workspaceId) {
            it.getString("role")
        }

    suspend fun createWorkspace(ownerId: UUID, name: String): WorkspaceSummary = db.tx {
        val id = UUID.randomUUID()
        update("INSERT INTO workspaces (id, name) VALUES (?, ?)", id, name)
        update("INSERT INTO memberships (workspace_id, user_id, role) VALUES (?, ?, ?)", id, ownerId, OWNER)
        WorkspaceSummary(id, name, OWNER)
    }

    suspend fun renameWorkspace(workspaceId: UUID, name: String) {
        db.tx { update("UPDATE workspaces SET name = ? WHERE id = ?", name, workspaceId) }
    }

    suspend fun members(workspaceId: UUID): List<Member> = db.tx {
        query(
            """
            SELECT u.id, u.email, u.name, u.avatar_url, m.role, m.created_at
              FROM memberships m JOIN users u ON u.id = m.user_id
             WHERE m.workspace_id = ? ORDER BY m.created_at
            """.trimIndent(),
            workspaceId
        ) {
            Member(
                it.uuid("id"), it.getString("email"), it.getString("name"), it.getString("avatar_url"),
                it.getString("role"), it.instant("created_at")!!
            )
        }
    }

    /** Removes a member. The last owner cannot be removed, so a workspace is never orphaned. */
    suspend fun removeMember(workspaceId: UUID, userId: UUID): RemoveMemberResult = db.tx {
        val role = role(userId, workspaceId) ?: return@tx RemoveMemberResult.NOT_A_MEMBER
        if (role == OWNER) {
            val owners = queryOne(
                "SELECT count(*) AS n FROM memberships WHERE workspace_id = ? AND role = ?", workspaceId, OWNER
            ) { it.getInt("n") } ?: 0
            if (owners <= 1) return@tx RemoveMemberResult.LAST_OWNER
        }
        update("DELETE FROM memberships WHERE workspace_id = ? AND user_id = ?", workspaceId, userId)
        RemoveMemberResult.REMOVED
    }

    // ---------------------------------------------------------------------
    // Invites
    // ---------------------------------------------------------------------

    /** Creates an invite and returns it with the raw token for the link. */
    suspend fun createInvite(workspaceId: UUID, email: String, invitedBy: UUID): Pair<Invite, String> {
        val token = generateToken("i_", 32)
        val invite = Invite(UUID.randomUUID(), email.trim(), Instant.now(), Instant.now().plus(INVITE_TTL))
        db.tx {
            update(
                "INSERT INTO invites (id, workspace_id, email, token_hash, invited_by, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                invite.id, workspaceId, invite.email, sha256Hex(token), invitedBy, invite.expiresAt
            )
        }
        return invite to token
    }

    suspend fun pendingInvites(workspaceId: UUID): List<Invite> = db.tx {
        query(
            """
            SELECT id, email, created_at, expires_at FROM invites
             WHERE workspace_id = ? AND accepted_at IS NULL AND expires_at > now()
             ORDER BY created_at
            """.trimIndent(),
            workspaceId
        ) { Invite(it.uuid("id"), it.getString("email"), it.instant("created_at")!!, it.instant("expires_at")!!) }
    }

    suspend fun revokeInvite(workspaceId: UUID, inviteId: UUID): Boolean = db.tx {
        update("DELETE FROM invites WHERE id = ? AND workspace_id = ? AND accepted_at IS NULL", inviteId, workspaceId) > 0
    }

    /** Workspace name and invited email for a valid invite link, for the landing page. */
    suspend fun inviteDetails(token: String): Pair<String, String>? = db.tx {
        queryOne(
            """
            SELECT w.name, i.email FROM invites i JOIN workspaces w ON w.id = i.workspace_id
             WHERE i.token_hash = ? AND i.accepted_at IS NULL AND i.expires_at > now()
            """.trimIndent(),
            sha256Hex(token)
        ) { it.getString("name") to it.getString("email") }
    }

    /**
     * Adds the signed-in user to the invite's workspace. Anyone holding the
     * link can accept it once, the way most team tools work.
     */
    suspend fun acceptInvite(token: String, userId: UUID): WorkspaceSummary? = db.tx {
        val workspaceId = queryOne(
            """
            UPDATE invites SET accepted_at = now(), accepted_by = ?
             WHERE token_hash = ? AND accepted_at IS NULL AND expires_at > now()
            RETURNING workspace_id
            """.trimIndent(),
            userId, sha256Hex(token)
        ) { it.uuid("workspace_id") } ?: return@tx null
        update(
            "INSERT INTO memberships (workspace_id, user_id, role) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            workspaceId, userId, MEMBER
        )
        queryOne(
            "SELECT w.id, w.name, m.role FROM workspaces w JOIN memberships m ON m.workspace_id = w.id WHERE w.id = ? AND m.user_id = ?",
            workspaceId, userId
        ) { WorkspaceSummary(it.uuid("id"), it.getString("name"), it.getString("role")) }
    }

    // ---------------------------------------------------------------------
    // Terminal (ingest) keys
    // ---------------------------------------------------------------------

    private data class CachedKey(val workspaceId: String?, val keyId: UUID?, val at: Long)
    private val keyCache = ConcurrentHashMap<String, CachedKey>()
    private val lastTouched = ConcurrentHashMap<UUID, Long>()

    /** Creates a key and returns it with the raw value, which is shown once. */
    suspend fun createIngestKey(workspaceId: UUID, name: String, createdBy: UUID): Pair<IngestKey, String> {
        val raw = generateToken(INGEST_KEY_PREFIX, 24)
        val key = IngestKey(UUID.randomUUID(), name, raw.take(12), Instant.now(), null, null)
        db.tx {
            update(
                "INSERT INTO ingest_keys (id, workspace_id, name, key_hash, key_prefix, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                key.id, workspaceId, key.name, sha256Hex(raw), key.prefix, createdBy
            )
        }
        return key to raw
    }

    suspend fun ingestKeys(workspaceId: UUID): List<IngestKey> = db.tx {
        query(
            "SELECT * FROM ingest_keys WHERE workspace_id = ? ORDER BY revoked_at IS NOT NULL, created_at DESC",
            workspaceId
        ) {
            IngestKey(
                it.uuid("id"), it.getString("name"), it.getString("key_prefix"),
                it.instant("created_at")!!, it.instant("last_used_at"), it.instant("revoked_at")
            )
        }
    }

    suspend fun revokeIngestKey(workspaceId: UUID, keyId: UUID): Boolean {
        val revoked = db.tx {
            update(
                "UPDATE ingest_keys SET revoked_at = now() WHERE id = ? AND workspace_id = ? AND revoked_at IS NULL",
                keyId, workspaceId
            ) > 0
        }
        // Revocation takes effect immediately on this server.
        keyCache.entries.removeIf { it.value.keyId == keyId }
        return revoked
    }

    /** The workspace a terminal's key belongs to, or null for an unknown or revoked key. */
    suspend fun workspaceForIngestKey(raw: String?): String? {
        if (raw.isNullOrBlank() || !raw.startsWith(INGEST_KEY_PREFIX)) return null
        val hash = sha256Hex(raw)
        val now = System.currentTimeMillis()
        val cached = keyCache[hash]?.takeIf { now - it.at < KEY_CACHE_MS }
        val resolved = cached ?: db.tx {
            queryOne("SELECT id, workspace_id FROM ingest_keys WHERE key_hash = ? AND revoked_at IS NULL", hash) {
                CachedKey(it.uuid("workspace_id").toString(), it.uuid("id"), now)
            } ?: CachedKey(null, null, now)
        }.also { keyCache[hash] = it }

        val keyId = resolved.keyId ?: return null
        // Record use at most once a minute per key.
        if (now - (lastTouched[keyId] ?: 0L) > KEY_CACHE_MS) {
            lastTouched[keyId] = now
            db.tx { update("UPDATE ingest_keys SET last_used_at = now() WHERE id = ?", keyId) }
        }
        return resolved.workspaceId
    }

    /** Deletes expired sessions, sign-in links and invites. */
    suspend fun pruneExpired() {
        db.tx {
            update("DELETE FROM sessions WHERE expires_at < now()")
            update("DELETE FROM email_login_tokens WHERE expires_at < now() - interval '1 day'")
            update("DELETE FROM invites WHERE accepted_at IS NULL AND expires_at < now() - interval '30 days'")
        }
    }

    private fun java.sql.ResultSet.toUser() =
        User(uuid("id"), getString("email"), getString("name"), getString("avatar_url"))
}
