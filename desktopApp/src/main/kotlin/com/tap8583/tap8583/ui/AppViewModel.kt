package com.tap8583.tap8583.ui

import com.tap8583.tap8583.AppEngine
import com.tap8583.tap8583.SessionStats
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.ingestion.ServerStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID


/**
 * AppViewModel
 *
 * Owns the [AppEngine] (ingestion + decoding pipeline) and exposes a single
 * [StateFlow]<[AppUiState]> for the UI.  All mutations go through the
 * `on*()` action functions — the UI never writes to state directly.
 *
 * Lifecycle:
 * - Created once in `main()` before the application block
 * - `stop()` called in `onCloseRequest`
 * - Internally uses a `SupervisorJob` + `Dispatchers.Default` so coroutine
 *   failures in one child never cancel the whole scope
 */
class AppViewModel(port: Int = AppEngine.DEFAULT_PORT) {

    private val scope  = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val engine         = AppEngine(port = port, scope = scope)

    // -----------------------------------------------------------------------
    // UI state
    // -----------------------------------------------------------------------

    private val _uiState = MutableStateFlow(
        AppUiState(port = port, localIpAddresses = AppEngine.localAddresses())
    )

    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // Internal full transaction list (unfiltered) used for re-filtering
    private val _allTransactions = MutableStateFlow<List<DecodedTransaction>>(emptyList())

    // -----------------------------------------------------------------------
    // Bootstrap
    // -----------------------------------------------------------------------

    init {
        // Merge engine flows → UI state
        scope.launch {
            combine(
                engine.transactions,
                engine.serverStatus,
                engine.connectionCount,
                engine.stats
            ) { txs, status, conns, stats ->
                // kotlinx-coroutines 4-flow combine: each value arrives as a typed param
                EngineSnapshot(txs = txs, status = status, conns = conns, stats = stats)
            }.collect { snap ->
                _allTransactions.value = snap.txs
                _uiState.update { current ->
                    current.copy(
                        serverStatus    = snap.status,
                        connectionCount = snap.conns,
                        stats           = snap.stats,
                        transactions    = applyFilter(snap.txs, current.searchQuery, current.messageFilter)
                    )
                }
            }
        }

        // Error / toast forwarding
        scope.launch {
            engine.errors.filterNotNull().collect { msg ->
                postToast(ToastMessage(id = UUID.randomUUID().toString(), message = msg, isError = true))
            }
        }
    }

    // -----------------------------------------------------------------------
    // Actions (called from UI)
    // -----------------------------------------------------------------------

    fun onStart() {
        engine.start()
        postToast(ToastMessage(id = UUID.randomUUID().toString(), message = "Server started on port ${engine.port}"))
    }

    fun onStop() {
        engine.stop()
    }

    fun onClear() {
        engine.clear()
        _allTransactions.value = emptyList()
        _uiState.update { it.copy(transactions = emptyList(), selectedTransactionId = null, selectedFieldNumber = null) }
    }

    fun onSelectTransaction(id: String?) {
        _uiState.update { it.copy(selectedTransactionId = id, selectedFieldNumber = null) }
    }

    fun onSelectTab(tab: CenterTab) {
        _uiState.update { it.copy(centerTab = tab, selectedFieldNumber = null) }
    }

    fun onSelectField(fieldNumber: Int?) {
        _uiState.update { it.copy(selectedFieldNumber = fieldNumber) }
    }

    fun onSearchChanged(query: String) {
        val filtered = applyFilter(_allTransactions.value, query, _uiState.value.messageFilter)
        _uiState.update { it.copy(searchQuery = query, transactions = filtered) }
    }

    fun onFilterChanged(filter: MessageFilter) {
        val filtered = applyFilter(_allTransactions.value, _uiState.value.searchQuery, filter)
        _uiState.update { it.copy(messageFilter = filter, transactions = filtered) }
    }

    fun onDismissToast(id: String) {
        _uiState.update { it.copy(toasts = it.toasts.filterNot { t -> t.id == id }) }
    }

    fun onToggleMaskField(fieldNumber: Int) {
        // Toggles masking on the selected field — stored in a separate map
        // Phase 4 will wire up full reveal/mask flow; here we just flip the state on the field
        // (Field immutability means we re-build the packet — deferred to Phase 4)
    }

    /** Permanently stop all coroutines and the engine. Call on window close. */
    fun stop() {
        engine.stop()
        scope.cancel()
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------

    private fun applyFilter(
        txs: List<DecodedTransaction>,
        query: String,
        filter: MessageFilter
    ): List<DecodedTransaction> {
        var result = txs

        // Text search across STAN, MTI label, and raw field values
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { tx ->
                tx.stan.lowercase().contains(q) ||
                tx.request?.mti?.label?.lowercase()?.contains(q) == true ||
                tx.request?.fields?.any { it.rawValue.lowercase().contains(q) } == true ||
                tx.response?.fields?.any { it.rawValue.lowercase().contains(q) } == true
            }
        }

        // Quick-filter by message class
        result = when (filter) {
            MessageFilter.ALL       -> result
            MessageFilter.FINANCIAL -> result.filter { it.request?.mti?.raw?.startsWith("02") == true }
            MessageFilter.AUTH      -> result.filter { it.request?.mti?.raw?.startsWith("01") == true }
            MessageFilter.REVERSAL  -> result.filter { it.request?.mti?.raw?.startsWith("04") == true }
            MessageFilter.NETWORK   -> result.filter { it.request?.mti?.raw?.startsWith("08") == true }
            MessageFilter.DECLINED  -> result.filter { it.status.name == "DECLINED" }
        }

        return result
    }

    // -----------------------------------------------------------------------
    // Toast queue management
    // -----------------------------------------------------------------------

    private fun postToast(toast: ToastMessage) {
        _uiState.update { state ->
            state.copy(toasts = (state.toasts + toast).takeLast(4))
        }
        scope.launch {
            delay(4_500)
            onDismissToast(toast.id)
        }
    }

    companion object {
        const val DEFAULT_PORT = AppEngine.DEFAULT_PORT
    }
}

/** Internal snapshot used to carry all 4 engine values through combine() */
private data class EngineSnapshot(
    val txs: List<DecodedTransaction>,
    val status: ServerStatus,
    val conns: Int,
    val stats: SessionStats
)

