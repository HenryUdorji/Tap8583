package com.tap8583.tap8583.ingestion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class TransactionMatcherTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun event(direction: Direction, stan: String, serial: String, mti: String, rc: String? = null) =
        RawLogEvent(
            id = UUID.randomUUID().toString(),
            realm = "channel/test",
            capturedAt = Instant.now(),
            lifespanMs = 0,
            direction = direction,
            rawXml = "",
            fields = buildMap {
                put(0, RawField(0, mti))
                put(11, RawField(11, stan))
                rc?.let { put(39, RawField(39, it)) }
            },
            serial = serial
        )

    @Test
    fun `response matching an older request returns that transaction`() = runBlocking {
        val matcher = TransactionMatcher(scope)
        val reqA = matcher.ingest(event(Direction.OUTGOING, "000001", "POS-A", "0200"))
        matcher.ingest(event(Direction.OUTGOING, "000002", "POS-B", "0200"))

        val matched = matcher.ingest(event(Direction.INCOMING, "000001", "POS-A", "0210", "00"))

        assertEquals(reqA.id, matched.id)
        assertEquals(TransactionStatus.APPROVED, matched.status)
    }

    @Test
    fun `retained transactions are capped`() = runBlocking {
        val matcher = TransactionMatcher(scope, maxRetained = 3)
        repeat(5) { matcher.ingest(event(Direction.OUTGOING, "00000$it", "POS-A", "0200")) }

        assertEquals(3, matcher.transactions.value.size)
        // A response for a dropped request becomes an orphan instead of matching a stale entry
        val late = matcher.ingest(event(Direction.INCOMING, "000000", "POS-A", "0210", "00"))
        assertEquals(TransactionStatus.ORPHAN, late.status)
    }

    @Test
    fun `clearing one device keeps the others`() = runBlocking {
        val matcher = TransactionMatcher(scope)
        matcher.ingest(event(Direction.OUTGOING, "000001", "POS-A", "0200"))
        matcher.ingest(event(Direction.OUTGOING, "000002", "POS-B", "0200"))

        matcher.clear("POS-A")

        assertEquals(listOf("POS-B"), matcher.transactions.value.map { it.serial })
        val matched = matcher.ingest(event(Direction.INCOMING, "000002", "POS-B", "0210", "00"))
        assertEquals(TransactionStatus.APPROVED, matched.status)
    }
}
