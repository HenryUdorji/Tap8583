package com.tap8583.tap8583.ingestion

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class JposXmlParserTest {

    // -----------------------------------------------------------------------
    // Sample XML from the spec §2.1 — full real-world NIBSS transaction
    // -----------------------------------------------------------------------
    private val SAMPLE_XML = """
        <visualizer-event realm="channel/196.6.103.18:4018"
                          at="2026-05-21T14:20:58.266Z"
                          lifespan="19ms"
                          tag="send">
          <isomsg direction="outgoing">
            <field id="0"   value="0200"/>
            <field id="2"   value="5061240207416432753"/>
            <field id="3"   value="000000"/>
            <field id="4"   value="000000000001"/>
            <field id="7"   value="0521142058"/>
            <field id="11"  value="086503"/>
            <field id="12"  value="142058"/>
            <field id="13"  value="0521"/>
            <field id="14"  value="2702"/>
            <field id="18"  value="6012"/>
            <field id="22"  value="051"/>
            <field id="25"  value="00"/>
            <field id="32"  value="506124"/>
            <field id="35"  value="5061240207416432753D2702601013660902"/>
            <field id="37"  value="614114086503"/>
            <field id="39"  value="00"/>
            <field id="41"  value="2214OTGF"/>
            <field id="42"  value="P332600027425  "/>
            <field id="49"  value="566"/>
            <field id="52"  value="26B7D3B168E69591" type="binary"/>
            <field id="55"  value="820258008407A0000003710001950502800480009A03260521"/>
            <field id="59"  value="2214OTGF-P332600027425-614114086503"/>
            <field id="123" value="51010151134C101"/>
            <field id="128" value="6CFDE6E02F8F5C2203B06B5F8E1AA5C8B649D7A7456A2F721EC39A55B44BC871" type="binary"/>
          </isomsg>
        </visualizer-event>
    """.trimIndent()

    @Test
    fun `parse complete outgoing transaction`() {
        val event = JposXmlParser.parse(SAMPLE_XML)

        // Metadata
        assertEquals("channel/196.6.103.18:4018", event.realm)
        assertEquals(Direction.OUTGOING, event.direction)
        assertEquals(19L, event.lifespanMs)

        // Fields present
        assertTrue("DE0 missing",  event.fields.containsKey(0))
        assertTrue("DE2 missing",  event.fields.containsKey(2))
        assertTrue("DE52 missing", event.fields.containsKey(52))
        assertTrue("DE55 missing", event.fields.containsKey(55))
        assertTrue("DE128 missing",event.fields.containsKey(128))

        // Field values
        assertEquals("0200", event.fields[0]?.value)
        assertEquals("5061240207416432753", event.fields[2]?.value)
        assertEquals("000000000001", event.fields[4]?.value)
        assertEquals("086503", event.fields[11]?.value)
        assertEquals("566", event.fields[49]?.value)
        assertEquals("2214OTGF-P332600027425-614114086503", event.fields[59]?.value)

        // Binary flag
        assertTrue("DE52 should be binary", event.fields[52]!!.isBinary)
        assertTrue("DE128 should be binary", event.fields[128]!!.isBinary)
        assertFalse("DE55 should NOT be binary (no type attr)", event.fields[55]!!.isBinary)
    }

    @Test
    fun `parse incoming response`() {
        val xml = """
            <visualizer-event realm="channel/196.6.103.18:4018"
                              at="2026-05-21T14:20:59.100Z"
                              lifespan="834ms"
                              tag="receive">
              <isomsg direction="incoming">
                <field id="0"  value="0210"/>
                <field id="11" value="086503"/>
                <field id="38" value="482901"/>
                <field id="39" value="00"/>
              </isomsg>
            </visualizer-event>
        """.trimIndent()

        val event = JposXmlParser.parse(xml)
        assertEquals(Direction.INCOMING, event.direction)
        assertEquals("0210", event.fields[0]?.value)
        assertEquals("482901", event.fields[38]?.value)
        assertEquals("00", event.fields[39]?.value)
        assertEquals(834L, event.lifespanMs)
    }

    @Test
    fun `parse timestamp correctly`() {
        val event = JposXmlParser.parse(SAMPLE_XML)
        // 2026-05-21T14:20:58.266Z
        assertTrue(event.capturedAt.isAfter(Instant.parse("2026-05-21T14:20:58Z")))
        assertTrue(event.capturedAt.isBefore(Instant.parse("2026-05-21T14:20:59Z")))
    }

    @Test
    fun `parse timestamp without Z suffix`() {
        val xml = """
            <visualizer-event realm="test" at="2026-05-21T14:20:58.266" lifespan="0ms" tag="send">
              <isomsg direction="outgoing"><field id="0" value="0200"/></isomsg>
            </visualizer-event>
        """.trimIndent()
        val event = JposXmlParser.parse(xml)
        // Should parse without throwing (lenient)
        assertNotEquals(Instant.EPOCH, event.capturedAt)
    }

    @Test
    fun `event has unique id per parse`() {
        val event1 = JposXmlParser.parse(SAMPLE_XML)
        val event2 = JposXmlParser.parse(SAMPLE_XML)
        assertNotEquals(event1.id, event2.id)
    }

    @Test(expected = JposXmlParser.ParseException::class)
    fun `throws on malformed xml`() {
        JposXmlParser.parse("<not-valid-xml")
    }

    @Test
    fun `rawXml is preserved`() {
        val event = JposXmlParser.parse(SAMPLE_XML, rawXml = SAMPLE_XML)
        assertTrue(event.rawXml.contains("visualizer-event"))
    }

    @Test
    fun `parseInstant handles various formats`() {
        val i1 = JposXmlParser.parseInstant("2026-05-21T14:20:58.266Z")
        val i2 = JposXmlParser.parseInstant("2026-05-21T14:20:58.266")
        val i3 = JposXmlParser.parseInstant("")
        // Both with and without Z should parse to roughly the same instant
        assertTrue(kotlin.math.abs(i1.epochSecond - i2.epochSecond) < 2)
        assertEquals(Instant.EPOCH, i3)
    }
}
