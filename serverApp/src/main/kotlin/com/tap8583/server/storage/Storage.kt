package com.tap8583.server.storage

import com.tap8583.server.models.ChannelEventDto
import com.tap8583.server.models.DecodedTransactionDto
import com.tap8583.server.models.DeviceSummaryDto
import com.tap8583.server.models.toDto
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.ingestion.ChannelErrorEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Where decoded transactions and channel errors live, per workspace.
 * In shared-key mode there is a single workspace, [DEFAULT_WORKSPACE].
 */
interface Storage {
    suspend fun saveTransaction(workspaceId: String, tx: DecodedTransaction)
    /** Newest last. [serial] null means every device. */
    suspend fun recentTransactions(workspaceId: String, serial: String?, limit: Int): List<DecodedTransactionDto>
    /** One transaction by id, if this workspace still has it. */
    suspend fun transaction(workspaceId: String, id: String): DecodedTransactionDto?
    suspend fun devices(workspaceId: String): List<DeviceSummaryDto>
    suspend fun saveChannelEvent(workspaceId: String, event: ChannelErrorEvent)
    /** Newest last. */
    suspend fun recentChannelEvents(workspaceId: String, serial: String?, limit: Int): List<ChannelEventDto>
    /** Deletes one device's data, or the whole workspace's when [serial] is null. */
    suspend fun clear(workspaceId: String, serial: String?)
    suspend fun pruneOlderThan(days: Long)

    companion object {
        const val DEFAULT_WORKSPACE = "default"
    }
}

/** Memory-only storage for shared-key mode: recent history, lost on restart. */
class InMemoryStorage(private val maxPerDevice: Int = 50) : Storage {
    private val transactions = ConcurrentHashMap<String, TransactionStore>()
    private val channelEvents = ConcurrentHashMap<String, ChannelEventLog>()

    private fun txStore(ws: String) = transactions.getOrPut(ws) { TransactionStore(maxPerDevice = maxPerDevice) }
    private fun eventLog(ws: String) = channelEvents.getOrPut(ws) { ChannelEventLog(maxEvents = 500) }

    override suspend fun saveTransaction(workspaceId: String, tx: DecodedTransaction) =
        txStore(workspaceId).saveOrUpdate(tx)

    override suspend fun recentTransactions(workspaceId: String, serial: String?, limit: Int) =
        txStore(workspaceId).getTransactions(serial, limit).map { it.toDto() }

    override suspend fun transaction(workspaceId: String, id: String) =
        txStore(workspaceId).getTransactions(null, Int.MAX_VALUE).firstOrNull { it.id == id }?.toDto()

    override suspend fun devices(workspaceId: String) = txStore(workspaceId).getDevices()

    override suspend fun saveChannelEvent(workspaceId: String, event: ChannelErrorEvent) =
        eventLog(workspaceId).add(event)

    override suspend fun recentChannelEvents(workspaceId: String, serial: String?, limit: Int) =
        eventLog(workspaceId).recent(serial, limit).map { it.toDto() }

    override suspend fun clear(workspaceId: String, serial: String?) {
        txStore(workspaceId).clear(serial)
        eventLog(workspaceId).clear(serial)
    }

    override suspend fun pruneOlderThan(days: Long) {
        transactions.values.forEach { it.pruneExpired() }
    }
}
