package com.tap8583.tap8583

import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.decoder.SemanticDecoder
import com.tap8583.tap8583.ingestion.ServerStatus
import com.tap8583.tap8583.ingestion.TcpIngestionServer
import com.tap8583.tap8583.ingestion.Transaction
import com.tap8583.tap8583.ingestion.TransactionEvent
import com.tap8583.tap8583.ingestion.TransactionMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Top-level application engine.
 *
 * Wires the ingestion pipeline to the semantic decoder and exposes
 * ready-to-consume [StateFlow]s for the UI (Phase 3+).
 *
 * Pipeline:
 * ```
 * TcpIngestionServer
 *   → (RawLogEvent) → SemanticDecoder
 *   → (DecodedPacket) → TransactionMatcher
 *   → (Transaction/DecodedTransaction) → StateFlow<List<DecodedTransaction>>
 * ```
 *
 * Usage (from the ViewModel or main window):
 * ```kotlin
 * val engine = AppEngine(port = 9999, scope = viewModelScope)
 * engine.start()
 * engine.transactions.collect { list -> updateUi(list) }
 * engine.stop()
 * ```
 */
class AppEngine(
    val port: Int = TcpIngestionServer.DEFAULT_PORT,
    private val scope: CoroutineScope
) {

    // -----------------------------------------------------------------------
    // Sub-systems
    // -----------------------------------------------------------------------

    val server  = TcpIngestionServer(port = port, scope = scope)
    val matcher = TransactionMatcher(scope = scope)

    // -----------------------------------------------------------------------
    // Public state
    // -----------------------------------------------------------------------

    /** Live server status — drives the status pill in the toolbar */
    val serverStatus: StateFlow<ServerStatus> = server.status

    /** Total number of active POS device connections */
    val connectionCount: StateFlow<Int> = server.connectionCount

    /**
     * Decoded transactions in arrival order (newest last).
     *
     * Each [DecodedTransaction] wraps the [Transaction] from the matcher with
     * fully decoded [DecodedPacket] objects for the request and response.
     */
    private val _transactions = MutableStateFlow<List<DecodedTransaction>>(emptyList())
    val transactions: StateFlow<List<DecodedTransaction>> = _transactions.asStateFlow()

    /**
     * Raw ingestion events — for consumers that need the pre-decode data
     * (e.g. the Raw XML tab in the UI).
     */
    val rawEvents: SharedFlow<*> = server.events

    /**
     * Error / alert stream — drives the toast notification system.
     * Combines errors from both the server and the matcher.
     */
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()

    /** Session statistics for the status bar */
    private val _stats = MutableStateFlow(SessionStats())
    val stats: StateFlow<SessionStats> = _stats.asStateFlow()

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Starts the TCP ingestion server and wires the processing pipeline.
     * Safe to call multiple times — subsequent calls are no-ops if already running.
     */
    fun start() {
        wirePipeline()
        server.start()
    }

    /** Stops the server and the timeout scanner. Existing data is preserved. */
    fun stop() {
        server.stop()
        matcher.stop()
    }

    /** Clears all transactions and resets stats. */
    fun clear() {
        matcher.clear()
        _transactions.value = emptyList()
        _stats.value = SessionStats()
    }

    // -----------------------------------------------------------------------
    // Pipeline wiring
    // -----------------------------------------------------------------------

    private var pipelineWired = false

    private fun wirePipeline() {
        if (pipelineWired) return
        pipelineWired = true

        // Server events → semantic decode → matcher
        server.events
            .onEach { rawEvent ->
                try {
                    val decoded = SemanticDecoder.decode(rawEvent)
                    matcher.ingest(rawEvent)  // matcher keeps raw events for pairing
                    // Publish parse errors as toasts
                    decoded.parseErrors.forEach { err ->
                        _errors.value = "DE${err.fieldNumber} parse error: ${err.message}"
                    }
                } catch (e: Exception) {
                    _errors.value = "Processing error: ${e.message}"
                }
            }
            .launchIn(scope)

        // Matcher transaction updates → rebuild decoded transaction list
        matcher.transactions
            .onEach { rawTransactions ->
                _transactions.value = rawTransactions.map { tx -> tx.toDecoded() }
                _stats.value = buildStats(_transactions.value)
            }
            .launchIn(scope)

        // Server errors → toast
        server.errors
            .onEach { _errors.value = it }
            .launchIn(scope)

        // Matcher events → toast for timeouts / high latency
        matcher.events
            .onEach { event ->
                when (event) {
                    is TransactionEvent.Timeout ->
                        _errors.value = "STAN ${event.transaction.stan}: No response received (timeout)"
                    is TransactionEvent.Updated -> {
                        val rtt = event.transaction.rttMs
                        if (rtt != null && rtt > HIGH_LATENCY_THRESHOLD_MS) {
                            _errors.value =
                                "STAN ${event.transaction.stan}: RTT ${rtt}ms — possible timeout?"
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun Transaction.toDecoded(): DecodedTransaction {
        val decodedRequest  = request?.let  { SemanticDecoder.decode(it) }
        val decodedResponse = response?.let { SemanticDecoder.decode(it) }
        return DecodedTransaction(
            id       = id,
            stan     = stan,
            request  = decodedRequest,
            response = decodedResponse,
            status   = status,
            rttMs    = rttMs
        )
    }

    private fun buildStats(txs: List<DecodedTransaction>): SessionStats {
        val approved = txs.count { it.status.name == "APPROVED" }
        val declined = txs.count { it.status.name == "DECLINED" }
        val avgRtt   = txs.mapNotNull { it.rttMs }.average().toLongOrNull()
        return SessionStats(
            totalTransactions = txs.size,
            approved          = approved,
            declined          = declined,
            avgRttMs          = avgRtt
        )
    }

    companion object {
        const val HIGH_LATENCY_THRESHOLD_MS = 3_000L
        const val DEFAULT_PORT = TcpIngestionServer.DEFAULT_PORT

        /**
         * Returns all non-loopback IPv4 addresses the machine currently has.
         * Displayed in the UI so the POS developer knows what host to configure.
         */
        fun localAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter  { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .map     { it.hostAddress }
                .toList()
        } catch (_: Exception) { emptyList() }
    }
}

// ---------------------------------------------------------------------------
// Session statistics
// ---------------------------------------------------------------------------

data class SessionStats(
    val totalTransactions: Int = 0,
    val approved: Int = 0,
    val declined: Int = 0,
    val avgRttMs: Long? = null
)

// Extension to convert Double average to Long safely
private fun Double.toLongOrNull(): Long? = if (isNaN() || isInfinite()) null else toLong()
