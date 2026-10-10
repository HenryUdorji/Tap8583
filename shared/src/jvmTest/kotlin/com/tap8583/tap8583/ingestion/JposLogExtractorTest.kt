package com.tap8583.tap8583.ingestion

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class JposLogExtractorTest {

    private val JPOS_LOG = """
        <log realm="channel/196.6.103.18:4018" at="2026-05-21T14:20:58.266" lifespan="19ms">
          <send>
            <isomsg direction="outgoing">
              <field id="0" value="0200"/>
              <field id="11" value="086503"/>
              <field id="127" value="nested">
                <isomsg>
                  <field id="2" value="inner"/>
                </isomsg>
              </field>
            </isomsg>
          </send>
        </log>
        <log realm="channel/196.6.103.18:4018" at="2026-05-21T14:20:58.700" lifespan="2ms">
          <receive>
            <isomsg direction="incoming">
              <field id="0" value="0210"/>
              <field id="11" value="086503"/>
              <field id="39" value="00"/>
            </isomsg>
          </receive>
        </log>
        <log realm="channel/196.6.103.18:4018" at="2026-05-21T14:21:30.000" lifespan="30001ms">
          <receive>
            <io-timeout/>
          </receive>
        </log>
    """.trimIndent()

    @Test
    fun `extracts send, receive and io-timeout from jPOS logger output`() {
        val events = JposLogExtractor.extract(JPOS_LOG, serial = "pasted")
        assertEquals(3, events.size)

        val entries = events.map { JposXmlParser.parseEntry(it) }
        val send = (entries[0] as LogEntry.Message).event
        assertEquals(Direction.OUTGOING, send.direction)
        assertEquals("0200", send.fields[0]?.value)
        assertEquals("086503", send.fields[11]?.value)
        assertEquals("pasted", send.serial)
        assertEquals(Instant.parse("2026-05-21T14:20:58.266Z"), send.capturedAt)
        assertEquals(19L, send.lifespanMs)

        val receive = (entries[1] as LogEntry.Message).event
        assertEquals(Direction.INCOMING, receive.direction)
        assertEquals("00", receive.fields[39]?.value)

        val timeout = (entries[2] as LogEntry.ChannelError).event
        assertEquals("io-timeout", timeout.kind)
        assertEquals("receive", timeout.tag)
        assertEquals("pasted", timeout.serial)
    }

    @Test
    fun `strips logcat prefixes`() {
        val logcat = """
            05-21 14:20:58.266  1234  1250 I System.out: <log realm="channel/1.2.3.4:1" at="2026-05-21T14:20:58.266">
            05-21 14:20:58.266  1234  1250 I System.out:   <send>
            05-21 14:20:58.266  1234  1250 I System.out:     <isomsg direction="outgoing">
            05-21 14:20:58.266  1234  1250 I System.out:       <field id="0" value="0800"/>
            05-21 14:20:58.266  1234  1250 I System.out:     </isomsg>
            05-21 14:20:58.266  1234  1250 I System.out:   </send>
            05-21 14:20:58.266  1234  1250 I System.out: </log>
        """.trimIndent()

        val events = JposLogExtractor.extract(logcat, serial = "pasted")
        assertEquals(1, events.size)
        assertEquals("0800", JposXmlParser.parse(events[0]).fields[0]?.value)
    }

    @Test
    fun `accepts listener output and bare isomsg dumps`() {
        val visualizer = """
            <visualizer-event realm="r" at="2026-05-21T14:20:58.266Z" tag="send" serial="POS-A">
              <isomsg direction="outgoing"><field id="0" value="0200"/></isomsg>
            </visualizer-event>
        """.trimIndent()
        assertEquals("POS-A", JposXmlParser.parse(JposLogExtractor.extract(visualizer, "pasted").single()).serial)

        val bare = """
            <isomsg direction="incoming"><field id="0" value="0210"/></isomsg>
        """.trimIndent()
        val parsed = JposXmlParser.parse(JposLogExtractor.extract(bare, "pasted").single())
        assertEquals(Direction.INCOMING, parsed.direction)
    }

    @Test
    fun `peer disconnect and exception details are escaped and kept`() {
        val log = """
            <log realm="channel/1.2.3.4:1" at="2026-05-21T14:20:58.266">
              <receive>
                <peer-disconnect>Connection reset &amp; closed</peer-disconnect>
              </receive>
            </log>
            <log realm="channel/1.2.3.4:1" at="2026-05-21T14:20:59.000">
              <connect>
                <exception name="Connection refused">java.net.ConnectException: Connection refused</exception>
              </connect>
            </log>
        """.trimIndent()
        val errors = JposLogExtractor.extract(log, "pasted")
            .map { (JposXmlParser.parseEntry(it) as LogEntry.ChannelError).event }
        assertEquals(listOf("peer-disconnect", "connect-failed"), errors.map { it.kind })
        assertEquals("Connection reset & closed", errors[0].message)
        assertTrue(errors[1].message.contains("ConnectException"))
    }

    @Test
    fun `nothing recognisable gives no events`() {
        assertTrue(JposLogExtractor.extract("hello world", "pasted").isEmpty())
    }

    @Test
    fun `free text masking hides card-number-like digit runs`() {
        assertEquals(
            "bad PAN 506124*********2753 in field 2",
            CardDataMasker.maskText("bad PAN 5061240207416432753 in field 2")
        )
        assertEquals("STAN 086503", CardDataMasker.maskText("STAN 086503"))
    }
}
