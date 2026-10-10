package com.tap8583.tap8583.ingestion

import com.tap8583.tap8583.decoder.SemanticDecoder
import com.tap8583.tap8583.decoder.TlvParser
import org.junit.Assert.*
import org.junit.Test

class CardDataMaskerTest {

    private val PAN = "5061240207416432753"
    private val TRACK2 = "5061240207416432753D2702601013660902"

    // 5A (PAN), 5F24 (expiry 270228), 57 (track 2 equivalent), 9F36 (ATC — not sensitive)
    private val EMV = "5A0A5061240207416432753F" + "5F2403270228" + "57105061240207416432753D270260101366" + "9F36020459"

    private val XML = """
        <visualizer-event realm="channel/196.6.103.18:4018" at="2026-05-21T14:20:58.266Z" lifespan="19ms" tag="send">
          <isomsg direction="outgoing">
            <field id="0" value="0200"/>
            <field id="2" value="$PAN"/>
            <field id="11" value="086503"/>
            <field id="14" value="2702"/>
            <field id="35" value="$TRACK2"/>
            <field id="52" value="26B7D3B168E69591" type="binary"/>
            <field id="55" value="$EMV" type="binary"/>
          </isomsg>
        </visualizer-event>
    """.trimIndent()

    private val masked = CardDataMasker.mask(JposXmlParser.parse(XML))

    @Test
    fun `PAN keeps BIN and last four`() {
        assertEquals("506124*********2753", masked.fields[2]!!.value)
    }

    @Test
    fun `track 2 masks PAN, expiry and discretionary but keeps service code`() {
        assertEquals("506124*********2753D****601*********", masked.fields[35]!!.value)
    }

    @Test
    fun `expiry and PIN block are fully masked`() {
        assertEquals("****", masked.fields[14]!!.value)
        assertEquals("****************", masked.fields[52]!!.value)
    }

    @Test
    fun `non-sensitive fields are untouched`() {
        assertEquals("0200", masked.fields[0]!!.value)
        assertEquals("086503", masked.fields[11]!!.value)
    }

    @Test
    fun `raw XML no longer contains clear card data`() {
        assertFalse(masked.rawXml.contains(PAN))
        assertFalse(masked.rawXml.contains("0207416432753"))
        assertFalse(masked.rawXml.contains("26B7D3B168E69591"))
        assertFalse(masked.rawXml.contains("2702601013660902"))
        assertTrue(masked.rawXml.contains("506124*********2753D****601*********"))
        assertTrue(masked.rawXml.contains("<field id=\"11\" value=\"086503\"/>"))
    }

    @Test
    fun `DE55 sensitive tags are masked and the TLV structure still parses`() {
        val emv = masked.fields[55]!!.value
        assertEquals(EMV.length, emv.length)

        val tags = TlvParser.parse(emv).associateBy { it.tag }
        assertEquals("506124FFFFFFFFFF753F", tags["5A"]!!.valueHex.uppercase())
        assertEquals("FFFFFF", tags["5F24"]!!.valueHex.uppercase())
        assertEquals("506124FFFFFFFFF2753DFFFF601FFFFF", tags["57"]!!.valueHex.uppercase())
        assertEquals("0459", tags["9F36"]!!.valueHex)
    }

    @Test
    fun `malformed DE55 is fully masked rather than leaked`() {
        assertEquals("FFFFFFFF", CardDataMasker.maskEmv("5A0A5061"))
    }

    @Test
    fun `masked event still decodes without errors`() {
        val decoded = SemanticDecoder.decode(masked)
        assertTrue(decoded.parseErrors.isEmpty())
        val pan = decoded.fields.first { it.number == 2 }
        assertTrue(pan.decodedValue!!.contains("masked"))
    }

    @Test
    fun `event without card data is returned unchanged`() {
        val plain = JposXmlParser.parse(XML.lines().filterNot {
            it.contains("id=\"2\"") || it.contains("id=\"14\"") || it.contains("id=\"35\"") ||
                it.contains("id=\"52\"") || it.contains("id=\"55\"")
        }.joinToString("\n"))
        assertSame(plain, CardDataMasker.mask(plain))
    }
}
