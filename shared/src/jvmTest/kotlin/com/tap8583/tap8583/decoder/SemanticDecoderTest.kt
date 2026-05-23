package com.tap8583.tap8583.decoder

import com.tap8583.tap8583.ingestion.Direction
import com.tap8583.tap8583.ingestion.JposXmlParser
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end semantic decoder test using the full NIBSS sample transaction from the spec.
 */
class SemanticDecoderTest {

    private val REQUEST_XML = """
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

    private val RESPONSE_XML = """
        <visualizer-event realm="channel/196.6.103.18:4018"
                          at="2026-05-21T14:20:59.100Z"
                          lifespan="834ms"
                          tag="receive">
          <isomsg direction="incoming">
            <field id="0"  value="0210"/>
            <field id="2"  value="5061240207416432753"/>
            <field id="3"  value="000000"/>
            <field id="4"  value="000000000001"/>
            <field id="11" value="086503"/>
            <field id="37" value="614114086503"/>
            <field id="38" value="482901"/>
            <field id="39" value="00"/>
            <field id="44" value="ND"/>
            <field id="49" value="566"/>
          </isomsg>
        </visualizer-event>
    """.trimIndent()

    // -----------------------------------------------------------------------
    // Request decoding
    // -----------------------------------------------------------------------

    @Test
    fun `decode request produces DecodedPacket`() {
        val rawEvent = JposXmlParser.parse(REQUEST_XML)
        val packet   = SemanticDecoder.decode(rawEvent)

        assertNotNull(packet)
        assertEquals(rawEvent, packet.rawEvent)
    }

    @Test
    fun `MTI decoded correctly for request`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        assertEquals("0200", packet.mti.raw)
        assertEquals("Financial Transaction Request", packet.mti.label)
    }

    @Test
    fun `MTI decoded correctly for response`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(RESPONSE_XML))
        assertEquals("0210", packet.mti.raw)
        assertEquals("Financial Transaction Response", packet.mti.label)
    }

    @Test
    fun `all request fields are decoded`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val fieldNumbers = packet.fields.map { it.number }.toSet()

        assertTrue("DE2 missing",  2 in fieldNumbers)
        assertTrue("DE3 missing",  3 in fieldNumbers)
        assertTrue("DE4 missing",  4 in fieldNumbers)
        assertTrue("DE11 missing", 11 in fieldNumbers)
        assertTrue("DE49 missing", 49 in fieldNumbers)
        assertTrue("DE52 missing", 52 in fieldNumbers)
        assertTrue("DE55 missing", 55 in fieldNumbers)
        assertTrue("DE128 missing",128 in fieldNumbers)
    }

    @Test
    fun `DE2 PAN field has category CARD_DATA and is sensitive`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de2 = packet.fields.find { it.number == 2 }
        assertNotNull("DE2 not decoded", de2)
        assertEquals(FieldCategory.CARD_DATA, de2!!.definition.category)
        assertTrue("DE2 should be sensitive", de2.isSensitive)
        assertTrue("DE2 should be masked by default", de2.isCurrentlyMasked)
    }

    @Test
    fun `DE4 amount decoded with NGN currency`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de4 = packet.fields.find { it.number == 4 }
        assertNotNull("DE4 not decoded", de4)
        assertNotNull("DE4 decoded value null", de4!!.decodedValue)
        assertTrue("Should contain ₦", de4.decodedValue!!.contains("₦"))
        assertTrue("Should show 0.01", de4.decodedValue!!.contains("0.01"))
    }

    @Test
    fun `DE3 has sub-fields`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de3 = packet.fields.find { it.number == 3 }
        assertNotNull("DE3 not decoded", de3)
        assertNotNull("DE3 sub-fields null", de3!!.subFields)
        assertEquals(3, de3.subFields!!.size)
    }

    @Test
    fun `DE52 PIN is always masked`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de52 = packet.fields.find { it.number == 52 }
        assertNotNull("DE52 not decoded", de52)
        assertNotNull(de52!!.decodedValue)
        assertTrue("DE52 should show mask blocks", de52.decodedValue!!.contains("████"))
    }

    @Test
    fun `DE55 has TLV entries`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de55 = packet.fields.find { it.number == 55 }
        assertNotNull("DE55 not decoded", de55)
        assertNotNull("DE55 TLV entries null", de55!!.tlvEntries)
        assertTrue("DE55 should have at least 3 TLV entries", de55.tlvEntries!!.size >= 3)
    }

    @Test
    fun `DE128 recognized as NIBSS MAC`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de128 = packet.fields.find { it.number == 128 }
        assertNotNull("DE128 not decoded", de128)
        assertTrue("DE128 should be binary", de128!!.isBinary)
        assertEquals(FieldCategory.SECURITY, de128.definition.category)
        assertTrue("DE128 decoded should mention MAC",
            de128.decodedValue?.lowercase()?.contains("mac") == true)
    }

    @Test
    fun `DE59 NIBSS reference decoded`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val de59 = packet.fields.find { it.number == 59 }
        assertNotNull("DE59 not decoded", de59)
        assertNotNull(de59!!.decodedValue)
        assertTrue("Should contain terminal ID",
            de59.decodedValue!!.contains("2214OTGF"))
    }

    @Test
    fun `response DE39 00 = Approved`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(RESPONSE_XML))
        val de39 = packet.fields.find { it.number == 39 }
        assertNotNull("DE39 missing from response", de39)
        assertNotNull(de39!!.decodedValue)
        assertTrue("Should say Approved", de39.decodedValue!!.contains("Approved"))
    }

    @Test
    fun `no parse errors for valid transaction`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        assertTrue(
            "Unexpected parse errors: ${packet.parseErrors}",
            packet.parseErrors.isEmpty()
        )
    }

    @Test
    fun `field definitions have correct categories`() {
        val packet = SemanticDecoder.decode(JposXmlParser.parse(REQUEST_XML))
        val byNumber = packet.fields.associateBy { it.number }

        assertEquals(FieldCategory.IDENTIFICATION, byNumber[11]?.definition?.category) // STAN
        assertEquals(FieldCategory.AMOUNT,         byNumber[4]?.definition?.category)  // Amount
        assertEquals(FieldCategory.SECURITY,       byNumber[52]?.definition?.category) // PIN
        assertEquals(FieldCategory.EMV,            byNumber[55]?.definition?.category) // ICC
        assertEquals(FieldCategory.INSTITUTION,    byNumber[32]?.definition?.category) // Acq ID
        assertEquals(FieldCategory.PRIVATE_NIBSS,  byNumber[59]?.definition?.category) // NIBSS ref
    }
}
