package com.tap8583.server.storage

import com.tap8583.tap8583.ingestion.ChannelErrorEvent

/**
 * Keeps the most recent channel errors (timeouts, disconnects, failed
 * connects) in memory, oldest dropped first.
 */
class ChannelEventLog(private val maxEvents: Int = 500) {
    private val lock = Any()
    private val events = ArrayDeque<ChannelErrorEvent>()

    fun add(event: ChannelErrorEvent) = synchronized(lock) {
        events.addLast(event)
        while (events.size > maxEvents) events.removeFirst()
    }

    /** Newest last, optionally for one device. */
    fun recent(serial: String?, limit: Int): List<ChannelErrorEvent> = synchronized(lock) {
        events.filter { serial == null || it.serial == serial }.takeLast(limit)
    }

    fun clear(serial: String?) = synchronized(lock) {
        if (serial == null) events.clear() else events.removeAll { it.serial == serial }
    }
}
