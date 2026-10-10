package com.tap8583.server

import java.security.SecureRandom

/**
 * Server configuration, read from the environment.
 *
 * Two modes:
 *  - **Accounts** (DATABASE_URL set): people sign in with a link sent to their
 *    email, belong to workspaces, and terminals send with per-workspace
 *    ingest keys. Transactions are stored in Postgres.
 *  - **Shared key** (no DATABASE_URL): the original single-team setup with one
 *    viewer key and one ingest key, data held in memory. Handy for local runs.
 */
class ServerConfig(
    val viewerKey: String,
    val ingestKey: String,
    val maskCardData: Boolean,
    val allowedOrigins: List<String>,
    val database: DatabaseConfig?,
    /** Where this server is reachable from browsers, e.g. https://tap8583.example.com. */
    val publicUrl: String,
    /** Where the dashboard is served; sign-in redirects back here. Same as [publicUrl] behind Caddy. */
    val appUrl: String,
    val smtp: SmtpConfig?,
    /** Log email sign-in and invite links instead of requiring SMTP (local development only). */
    val logEmailLinks: Boolean,
    val retentionDays: Long
) {
    val accountsEnabled: Boolean get() = database != null
    val secureCookies: Boolean get() = publicUrl.startsWith("https://")

    companion object {
        fun fromEnv(log: org.slf4j.Logger, env: (String) -> String? = System::getenv): ServerConfig {
            fun get(name: String) = env(name)?.trim()?.takeIf { it.isNotEmpty() }

            val database = get("DATABASE_URL")?.let {
                DatabaseConfig(url = it, user = get("DATABASE_USER"), password = get("DATABASE_PASSWORD"))
            }

            val legacyKey = get("TAP8583_API_KEY")
            val viewerKey = get("TAP8583_VIEWER_KEY") ?: legacyKey ?: generateToken("tap_").also {
                if (database == null) {
                    log.warn("TAP8583_VIEWER_KEY is not set. Generated a temporary key for this run: {}", it)
                }
            }
            val ingestKey = get("TAP8583_INGEST_KEY") ?: legacyKey ?: viewerKey

            val allowedOrigins = (get("TAP8583_ALLOWED_ORIGINS") ?: "http://localhost:3000")
                .split(",").map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

            val publicUrl = (get("TAP8583_PUBLIC_URL") ?: "http://localhost:${get("PORT") ?: "8080"}").trimEnd('/')
            val appUrl = (get("TAP8583_APP_URL") ?: publicUrl).trimEnd('/')

            val smtp = get("SMTP_HOST")?.let { host ->
                SmtpConfig(
                    host = host,
                    port = get("SMTP_PORT")?.toIntOrNull() ?: 587,
                    user = get("SMTP_USER"),
                    password = get("SMTP_PASSWORD"),
                    from = get("SMTP_FROM") ?: "Tap8583 <no-reply@${host}>"
                )
            }

            return ServerConfig(
                viewerKey = viewerKey,
                ingestKey = ingestKey,
                maskCardData = get("TAP8583_MASK_CARD_DATA")?.lowercase() != "false",
                allowedOrigins = allowedOrigins,
                database = database,
                publicUrl = publicUrl,
                appUrl = appUrl,
                smtp = smtp,
                logEmailLinks = get("TAP8583_LOG_EMAIL_LINKS")?.lowercase() == "true",
                retentionDays = get("TAP8583_RETENTION_DAYS")?.toLongOrNull()?.coerceAtLeast(1) ?: 30
            )
        }
    }
}

data class DatabaseConfig(val url: String, val user: String?, val password: String?)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val user: String?,
    val password: String?,
    val from: String
)

private val secureRandom = SecureRandom()

/** A random URL-safe token with the given prefix, e.g. "t8k_3f9c…" (192 bits). */
fun generateToken(prefix: String = "", bytes: Int = 24): String {
    val raw = ByteArray(bytes).also { secureRandom.nextBytes(it) }
    return prefix + raw.joinToString("") { "%02x".format(it) }
}
