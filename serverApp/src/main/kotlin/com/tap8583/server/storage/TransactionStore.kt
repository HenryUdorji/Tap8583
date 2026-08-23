package com.tap8583.server.storage

import com.tap8583.server.models.DeviceSummaryDto
import com.tap8583.tap8583.decoder.DecodedTransaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe in-memory transaction buffer with 50-item per-device capacity
 * and 7-day auto-TTL pruning.
 */
class TransactionStore(
    private val maxPerDevice: Int = 50,
    private val ttlDays: Long = 7
) {
    private val lock = Any()

    // Key: deviceSerial -> list of DecodedTransaction (ordered oldest to newest)
    private val perDeviceBuffer = ConcurrentHashMap<String, MutableList<DecodedTransaction>>()

    // Global recent transactions across all devices
    private val globalRecent = CopyOnWriteArrayList<DecodedTransaction>()

    // Device metadata
    private val deviceMetadata = ConcurrentHashMap<String, DeviceMeta>()

    private data class DeviceMeta(
        val serial: String,
        var lastSeenAt: Instant,
        var totalCount: Int
    )

    /**
     * Stores or updates a transaction in the store.
     */
    fun saveOrUpdate(tx: DecodedTransaction) {
        val serial = tx.serial ?: "DEFAULT"
        val now = Instant.now()

        synchronized(lock) {
            // Update device metadata
            val meta = deviceMetadata.getOrPut(serial) {
                DeviceMeta(serial, now, 0)
            }
            meta.lastSeenAt = now

            // Update per-device buffer
            val list = perDeviceBuffer.getOrPut(serial) { mutableListOf() }
            val existingIdx = list.indexOfFirst { it.id == tx.id }
            if (existingIdx >= 0) {
                list[existingIdx] = tx
            } else {
                list.add(tx)
                meta.totalCount++
                if (list.size > maxPerDevice) {
                    list.removeAt(0)
                }
            }

            // Update global buffer
            val globalIdx = globalRecent.indexOfFirst { it.id == tx.id }
            if (globalIdx >= 0) {
                globalRecent[globalIdx] = tx
            } else {
                globalRecent.add(tx)
                if (globalRecent.size > maxPerDevice * 4) {
                    globalRecent.removeAt(0)
                }
            }
        }
    }

    /**
     * Returns the recent transactions for a given serial (or all if serial is null / "ALL").
     */
    fun getTransactions(serial: String? = null, limit: Int = maxPerDevice): List<DecodedTransaction> {
        synchronized(lock) {
            val source = if (serial.isNullOrBlank() || serial.equals("ALL", ignoreCase = true)) {
                globalRecent.toList()
            } else {
                perDeviceBuffer[serial]?.toList() ?: emptyList()
            }
            return if (source.size > limit) source.takeLast(limit) else source
        }
    }

    /**
     * Returns the list of all active devices with summary info.
     */
    fun getDevices(): List<DeviceSummaryDto> {
        return deviceMetadata.values.map {
            DeviceSummaryDto(
                serial = it.serial,
                lastSeenAt = it.lastSeenAt.toString(),
                transactionCount = it.totalCount
            )
        }.sortedByDescending { it.lastSeenAt }
    }

    /**
     * Clears transactions for a specific serial or all.
     */
    fun clear(serial: String? = null) {
        synchronized(lock) {
            if (serial.isNullOrBlank() || serial.equals("ALL", ignoreCase = true)) {
                perDeviceBuffer.clear()
                globalRecent.clear()
                deviceMetadata.clear()
            } else {
                perDeviceBuffer.remove(serial)
                globalRecent.removeIf { it.serial == serial }
                deviceMetadata.remove(serial)
            }
        }
    }

    /**
     * Prunes transactions older than 7 days.
     */
    fun pruneExpired() {
        val cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS)
        synchronized(lock) {
            perDeviceBuffer.values.forEach { list ->
                list.removeIf { tx ->
                    val txTime = tx.request?.rawEvent?.capturedAt
                        ?: tx.response?.rawEvent?.capturedAt
                        ?: Instant.now()
                    txTime.isBefore(cutoff)
                }
            }
            globalRecent.removeIf { tx ->
                val txTime = tx.request?.rawEvent?.capturedAt
                    ?: tx.response?.rawEvent?.capturedAt
                    ?: Instant.now()
                txTime.isBefore(cutoff)
            }
        }
    }
}
