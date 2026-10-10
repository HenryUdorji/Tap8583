package com.tap8583.server

import com.tap8583.server.models.DecodedTransactionDto
import com.tap8583.server.models.WebSocketMessage
import com.tap8583.server.models.toDto
import com.tap8583.server.storage.Storage
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.decoder.SemanticDecoder
import com.tap8583.tap8583.ingestion.ChannelErrorEvent
import com.tap8583.tap8583.ingestion.RawLogEvent
import com.tap8583.tap8583.ingestion.Transaction
import com.tap8583.tap8583.ingestion.TransactionEvent
import com.tap8583.tap8583.ingestion.TransactionMatcher
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** Runs the semantic decoder over both sides of a matched transaction. */
fun decode(tx: Transaction): DecodedTransaction = DecodedTransaction(
    id = tx.id,
    stan = tx.stan,
    request = tx.request?.let { SemanticDecoder.decode(it) },
    response = tx.response?.let { SemanticDecoder.decode(it) },
    status = tx.status,
    rttMs = tx.rttMs,
    serial = tx.serial
)

/**
 * Live state per workspace: a request/response matcher, and the dashboards
 * watching it. Every change is decoded, stored, then sent to that
 * workspace's dashboards only.
 */
class LiveHub(
    private val scope: CoroutineScope,
    private val storage: Storage,
    private val json: Json,
    private val log: Logger,
    /** Applies the workspace's private-field layouts before anything is sent. */
    private val explain: suspend (String, DecodedTransactionDto) -> DecodedTransactionDto = { _, tx -> tx }
) {
    class Client(val session: DefaultWebSocketSession, val workspaceId: String, val filterSerial: String?)

    private val matchers = ConcurrentHashMap<String, TransactionMatcher>()
    private val clients = CopyOnWriteArraySet<Client>()

    val clientCount: Int get() = clients.size

    private fun matcherFor(workspaceId: String): TransactionMatcher = matchers.computeIfAbsent(workspaceId) {
        TransactionMatcher(scope = scope).also { matcher ->
            matcher.events
                .onEach { event ->
                    val tx = when (event) {
                        is TransactionEvent.Updated -> event.transaction
                        is TransactionEvent.Timeout -> event.transaction
                    }
                    val decoded = decode(tx)
                    try {
                        storage.saveTransaction(workspaceId, decoded)
                    } catch (e: Exception) {
                        log.error("Could not store transaction {}: {}", tx.id, e.message)
                    }
                    broadcast(workspaceId, WebSocketMessage(type = "TRANSACTION_UPDATED", transaction = explain(workspaceId, decoded.toDto())))
                }
                .launchIn(scope)
        }
    }

    suspend fun ingest(workspaceId: String, event: RawLogEvent) {
        matcherFor(workspaceId).ingest(event)
    }

    suspend fun channelError(workspaceId: String, event: ChannelErrorEvent) {
        storage.saveChannelEvent(workspaceId, event)
        broadcast(workspaceId, WebSocketMessage(type = "CHANNEL_EVENT", channelEvent = event.toDto()))
    }

    suspend fun clear(workspaceId: String, serial: String?) {
        matchers[workspaceId]?.clear(serial)
        storage.clear(workspaceId, serial)
        broadcast(workspaceId, WebSocketMessage(type = "CLEAR", serial = serial))
    }

    /** Tells a workspace's dashboards to reload, e.g. after its field layouts change. */
    suspend fun fieldsChanged(workspaceId: String) {
        broadcast(workspaceId, WebSocketMessage(type = "FIELDS_UPDATED"))
    }

    fun add(client: Client) = clients.add(client)
    fun remove(client: Client) = clients.remove(client)

    /** Sends sequentially so clients see updates in order (PENDING before APPROVED). */
    private suspend fun broadcast(workspaceId: String, msg: WebSocketMessage) {
        val text = json.encodeToString(msg)
        val serial = msg.transaction?.serial ?: msg.channelEvent?.serial
        clients.forEach { client ->
            if (client.workspaceId != workspaceId) return@forEach
            val relevant = serial == null || client.filterSerial == null || client.filterSerial == serial
            if (!relevant) return@forEach
            try {
                client.session.send(Frame.Text(text))
            } catch (e: Exception) {
                log.debug("Error sending frame to WebSocket client: {}", e.message)
            }
        }
    }
}
