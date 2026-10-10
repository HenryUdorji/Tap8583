package com.tap8583.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.security.MessageDigest

/** Constant-time comparison of a provided secret with the expected one. */
fun keyMatches(provided: String?, expected: String): Boolean {
    if (provided.isNullOrEmpty()) return false
    return MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())
}

/** SHA-256 hex of a secret. Tokens and keys are stored only as this hash. */
fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** The API key a terminal or script sent, from x-api-key or a Bearer token. */
fun ApplicationCall.providedKey(): String? =
    request.headers["x-api-key"]
        ?: request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()

/**
 * True when the request has no Origin header (terminals, curl) or comes from
 * an allowed dashboard origin. Browsers always send Origin on cross-site
 * POSTs and WebSocket upgrades, so this blocks cross-site request forgery
 * against cookie-authenticated routes.
 */
fun ApplicationCall.originAllowed(config: ServerConfig): Boolean {
    val origin = request.headers[HttpHeaders.Origin]?.trimEnd('/') ?: return true
    return origin == config.publicUrl || origin == config.appUrl || origin in config.allowedOrigins
}
