package com.tap8583.server.storage

import com.tap8583.server.db.Db
import com.tap8583.server.db.Json
import com.tap8583.server.db.instant
import com.tap8583.server.db.query
import com.tap8583.server.db.update
import com.tap8583.server.models.ChannelEventDto
import com.tap8583.server.models.DecodedTransactionDto
import com.tap8583.server.models.DeviceSummaryDto
import com.tap8583.server.models.toDto
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.ingestion.ChannelErrorEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json as KJson
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Transaction history in Postgres, kept for the configured retention period. */
class PostgresStorage(private val db: Db, private val json: KJson) : Storage {

    override suspend fun saveTransaction(workspaceId: String, tx: DecodedTransaction) {
        val dto = tx.toDto()
        val createdAt = dto.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        db.tx {
            update(
                """
                INSERT INTO transactions (id, workspace_id, serial, stan, status, created_at, updated_at, data)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                ON CONFLICT (id) DO UPDATE
                   SET status = EXCLUDED.status, updated_at = now(), data = EXCLUDED.data
                 WHERE transactions.workspace_id = EXCLUDED.workspace_id
                """.trimIndent(),
                dto.id, UUID.fromString(workspaceId), dto.serial ?: "DEFAULT", dto.stan, dto.status,
                createdAt, Json(json.encodeToString(dto))
            )
        }
    }

    override suspend fun recentTransactions(workspaceId: String, serial: String?, limit: Int): List<DecodedTransactionDto> =
        db.tx {
            val rows = if (serial == null) {
                query(
                    "SELECT data FROM transactions WHERE workspace_id = ? ORDER BY created_at DESC LIMIT ?",
                    UUID.fromString(workspaceId), limit
                ) { it.getString("data") }
            } else {
                query(
                    "SELECT data FROM transactions WHERE workspace_id = ? AND serial = ? ORDER BY created_at DESC LIMIT ?",
                    UUID.fromString(workspaceId), serial, limit
                ) { it.getString("data") }
            }
            rows.map { json.decodeFromString<DecodedTransactionDto>(it) }.reversed()
        }

    override suspend fun devices(workspaceId: String): List<DeviceSummaryDto> = db.tx {
        query(
            """
            SELECT serial, max(updated_at) AS last_seen, count(*) AS n
              FROM transactions WHERE workspace_id = ?
             GROUP BY serial ORDER BY last_seen DESC
            """.trimIndent(),
            UUID.fromString(workspaceId)
        ) { DeviceSummaryDto(it.getString("serial"), it.instant("last_seen").toString(), it.getInt("n")) }
    }

    override suspend fun saveChannelEvent(workspaceId: String, event: ChannelErrorEvent) {
        val dto = event.toDto()
        db.tx {
            update(
                "INSERT INTO channel_events (id, workspace_id, serial, captured_at, data) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                dto.id, UUID.fromString(workspaceId), dto.serial ?: "DEFAULT", event.capturedAt, Json(json.encodeToString(dto))
            )
        }
    }

    override suspend fun recentChannelEvents(workspaceId: String, serial: String?, limit: Int): List<ChannelEventDto> =
        db.tx {
            val rows = if (serial == null) {
                query(
                    "SELECT data FROM channel_events WHERE workspace_id = ? ORDER BY captured_at DESC LIMIT ?",
                    UUID.fromString(workspaceId), limit
                ) { it.getString("data") }
            } else {
                query(
                    "SELECT data FROM channel_events WHERE workspace_id = ? AND serial = ? ORDER BY captured_at DESC LIMIT ?",
                    UUID.fromString(workspaceId), serial, limit
                ) { it.getString("data") }
            }
            rows.map { json.decodeFromString<ChannelEventDto>(it) }.reversed()
        }

    override suspend fun clear(workspaceId: String, serial: String?) {
        val ws = UUID.fromString(workspaceId)
        db.tx {
            if (serial == null) {
                update("DELETE FROM transactions WHERE workspace_id = ?", ws)
                update("DELETE FROM channel_events WHERE workspace_id = ?", ws)
            } else {
                update("DELETE FROM transactions WHERE workspace_id = ? AND serial = ?", ws, serial)
                update("DELETE FROM channel_events WHERE workspace_id = ? AND serial = ?", ws, serial)
            }
        }
    }

    override suspend fun pruneOlderThan(days: Long) {
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        db.tx {
            update("DELETE FROM transactions WHERE created_at < ?", cutoff)
            update("DELETE FROM channel_events WHERE captured_at < ?", cutoff)
        }
    }
}
