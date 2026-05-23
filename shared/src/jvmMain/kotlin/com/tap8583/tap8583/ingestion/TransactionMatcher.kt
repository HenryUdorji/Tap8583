package com.tap8583.tap8583.ingestion

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/**
 * Matches ISO 8583 request and response messages by STAN (DE11).
 *
 * Per the spec §7.4.4:
 *  - Request and response are matched by STAN (DE11)
 *  - Matching window: [matchWindowMs] milliseconds (default 60 seconds)
 *  - Orphan responses (no matching request) shown as "Unmatched Response"
 *  - Missing DE11 → card shown as "Unmatched Request"
 *  - Timeout transitions pending cards to TIMEOUT state
 *
 * Usage:
 * ```kotlin
 * val matcher = TransactionMatcher(scope = viewModelScope)
 * ingestionServer.events.collect { event ->
 *     matcher.ingest(event)
 * }
 * matcher.transactions.collect { list -> updateUi(list) }
 * ```
 */
class TransactionMatcher(
    private val scope: CoroutineScope,
    val matchWindowMs: Long = DEFAULT_MATCH_WINDOW_MS,
    private val timeoutScanIntervalMs: Long = DEFAULT_SCAN_INTERVAL_MS
) {

    companion object {
        const val DEFAULT_MATCH_WINDOW_MS = 60_000L   // 60 seconds
        const val DEFAULT_SCAN_INTERVAL_MS = 2_000L    // scan every 2 seconds
    }

    // -----------------------------------------------------------------------
    // Public state
    // -----------------------------------------------------------------------

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    /** Emitted whenever a transaction changes state (for toast / alert consumers) */
    private val _events = MutableSharedFlow<TransactionEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<TransactionEvent> = _events.asSharedFlow()

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    /** Pending requests keyed by STAN */
    private val pendingByStan = mutableMapOf<String, Transaction>()

    /** All transactions in arrival order (newest last) */
    private val allTransactions = mutableListOf<Transaction>()

    private val lock = Any()

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    private var timeoutJob: Job? = null

    init {
        startTimeoutScanner()
    }

    fun stop() {
        timeoutJob?.cancel()
    }

    // -----------------------------------------------------------------------
    // Ingestion
    // -----------------------------------------------------------------------

    /**
     * Processes a newly received [RawLogEvent] and updates the transaction list.
     * Thread-safe. Returns the affected [Transaction].
     */
    suspend fun ingest(event: RawLogEvent): Transaction {
        val stan = event.fields[11]?.value
        return synchronized(lock) {
            when (event.direction) {
                Direction.OUTGOING -> handleOutgoing(event, stan)
                Direction.INCOMING -> handleIncoming(event, stan)
            }
        }.also { tx ->
            _transactions.value = synchronized(lock) { allTransactions.toList() }
            _events.emit(TransactionEvent.Updated(tx))
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun handleOutgoing(event: RawLogEvent, stan: String?): Transaction {
        val tx = Transaction(
            id        = event.id,
            stan      = stan ?: "",
            request   = event,
            response  = null,
            status    = TransactionStatus.PENDING,
            rttMs     = null,
            createdAt = event.capturedAt
        )
        allTransactions.add(tx)
        if (!stan.isNullOrBlank()) {
            pendingByStan[stan] = tx
        }
        return tx
    }

    private fun handleIncoming(event: RawLogEvent, stan: String?): Transaction {
        if (!stan.isNullOrBlank()) {
            val pending = pendingByStan.remove(stan)
            if (pending != null) {
                // Matched pair
                val rtt = java.time.Duration.between(
                    pending.request!!.capturedAt, event.capturedAt
                ).toMillis()
                val responseCode = event.fields[39]?.value
                val status = resolveStatus(responseCode)
                val matched = pending.copy(
                    response = event,
                    status   = status,
                    rttMs    = rtt
                )
                val idx = allTransactions.indexOfFirst { it.id == pending.id }
                if (idx >= 0) allTransactions[idx] = matched
                return matched
            }
        }

        // Orphan response — no matching request found
        val orphan = Transaction(
            id        = event.id,
            stan      = stan ?: "",
            request   = null,
            response  = event,
            status    = TransactionStatus.ORPHAN,
            rttMs     = null,
            createdAt = event.capturedAt
        )
        allTransactions.add(orphan)
        return orphan
    }

    private fun resolveStatus(responseCode: String?): TransactionStatus {
        if (responseCode == null) return TransactionStatus.PENDING
        return when (responseCode.trim()) {
            "00"             -> TransactionStatus.APPROVED
            "68", "91", "96" -> TransactionStatus.NETWORK_ERROR
            else             -> TransactionStatus.DECLINED
        }
    }

    // -----------------------------------------------------------------------
    // Timeout scanner
    // -----------------------------------------------------------------------

    private fun startTimeoutScanner() {
        timeoutJob = scope.launch {
            while (isActive) {
                delay(timeoutScanIntervalMs)
                val now = Instant.now()
                val timedOut = synchronized(lock) {
                    val expired = pendingByStan.entries
                        .filter { (_, tx) ->
                            java.time.Duration.between(tx.createdAt, now).toMillis() > matchWindowMs
                        }
                    expired.map { (stan, tx) ->
                        pendingByStan.remove(stan)
                        val updated = tx.copy(status = TransactionStatus.TIMEOUT)
                        val idx = allTransactions.indexOfFirst { it.id == tx.id }
                        if (idx >= 0) allTransactions[idx] = updated
                        updated
                    }
                }
                if (timedOut.isNotEmpty()) {
                    _transactions.value = synchronized(lock) { allTransactions.toList() }
                    timedOut.forEach { _events.emit(TransactionEvent.Timeout(it)) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Public helpers
    // -----------------------------------------------------------------------

    fun clear() {
        synchronized(lock) {
            allTransactions.clear()
            pendingByStan.clear()
        }
        _transactions.value = emptyList()
    }
}

// ---------------------------------------------------------------------------
// Transaction model (used by matcher — decoded version added in Phase 2)
// ---------------------------------------------------------------------------

enum class TransactionStatus {
    PENDING,        // Request sent, response not yet received
    APPROVED,       // Response DE39 = "00"
    DECLINED,       // Response DE39 = any non-00 non-network code
    TIMEOUT,        // No response within matching window
    NETWORK_ERROR,  // DE39 = 68/91/96 or similar
    ORPHAN          // Incoming message with no matching request
}

/**
 * Represents a matched (or pending) ISO 8583 transaction pair.
 *
 * [request] and [response] are raw events. The semantic decoder (Phase 2)
 * will produce [com.tap8583.tap8583.decoder.DecodedPacket] wrappers on top.
 */
data class Transaction(
    val id: String,
    val stan: String,
    val request: RawLogEvent?,
    val response: RawLogEvent?,
    val status: TransactionStatus,
    val rttMs: Long?,
    val createdAt: Instant
)

/** Events emitted by the matcher for toast/alert consumers */
sealed class TransactionEvent {
    data class Updated(val transaction: Transaction) : TransactionEvent()
    data class Timeout(val transaction: Transaction) : TransactionEvent()
}
