package com.tap8583.tap8583.decoder

import org.junit.Assert.*
import org.junit.Test

class TlvParserTest {

    // Sample DE55 hex from the spec §2.5 (partial, for testing)
    // 82 02 5800 84 07 A0000003710001 95 05 0502800480 00 9A 03 260521
    private val SAMPLE_DE55_HEX = "820258008407A0000003710001950502800480009A03260521"

    // Longer DE55 from a real Verve chip transaction
    private val FULL_DE55_HEX = "820258008407A0000003710001950502800480009A03260521" +
            "9C01009F02060000000000019F03060000000000009F1A0205669F" +
            "2608B08F298D5A26BECD9F2701809F360204599F37046A04A143"

    @Test
    fun `parse sample DE55 extracts known tags`() {
        val tlv = TlvParser.parse(SAMPLE_DE55_HEX)

        val tags = tlv.map { it.tag }
        assertTrue("Tag 82 (AIP) missing", tags.contains("82"))
        assertTrue("Tag 84 (AID) missing", tags.contains("84"))
        assertTrue("Tag 95 (TVR) missing", tags.contains("95"))
        assertTrue("Tag 9A (Tx Date) missing", tags.contains("9A"))
    }

    @Test
    fun `AID tag 84 decoded to Verve`() {
        val tlv = TlvParser.parse(SAMPLE_DE55_HEX)
        val aidEntry = tlv.find { it.tag == "84" }
        assertNotNull("AID entry not found", aidEntry)
        assertEquals("Dedicated File (DF) Name", aidEntry!!.name)
        assertNotNull("AID decoded value should not be null", aidEntry.decoded)
        assertTrue("Should identify as Verve", aidEntry.decoded!!.contains("Verve"))
    }

    @Test
    fun `tag 82 AIP is named`() {
        val tlv = TlvParser.parse(SAMPLE_DE55_HEX)
        val aip = tlv.find { it.tag == "82" }
        assertNotNull(aip)
        assertEquals("Application Interchange Profile", aip!!.name)
        assertNotNull(aip.decoded)
    }

    @Test
    fun `tag 9A transaction date decoded`() {
        val tlv = TlvParser.parse(SAMPLE_DE55_HEX)
        val txDate = tlv.find { it.tag == "9A" }
        assertNotNull("Transaction date tag 9A missing", txDate)
        // 260521 → May 21, 2026
        assertNotNull(txDate!!.decoded)
        assertTrue("Should contain May", txDate.decoded!!.contains("May"))
        assertTrue("Should contain 2026", txDate.decoded!!.contains("2026"))
    }

    @Test
    fun `tag 95 TVR decoded with bit flags`() {
        val tlv = TlvParser.parse(SAMPLE_DE55_HEX)
        val tvr = tlv.find { it.tag == "95" }
        assertNotNull("TVR tag 95 missing", tvr)
        assertNotNull("TVR decoded value should not be null", tvr!!.decoded)
        // 0502800480 — should have some decoded content
        assertTrue("TVR should have non-empty decode", tvr.decoded!!.isNotBlank())
    }

    @Test
    fun `parse full DE55 with cryptogram tags`() {
        val tlv = TlvParser.parse(FULL_DE55_HEX)
        val tags = tlv.map { it.tag }

        // Check for additional tags in full DE55
        assertTrue("Should have tag 9F26 (ARQC)", tags.contains("9F26"))
        assertTrue("Should have tag 9F27 (CID)",  tags.contains("9F27"))
        assertTrue("Should have tag 9F36 (ATC)",  tags.contains("9F36"))
    }

    @Test
    fun `tag 9F27 cryptogram type decoded`() {
        val tlv = TlvParser.parse(FULL_DE55_HEX)
        val cid = tlv.find { it.tag == "9F27" }
        assertNotNull("CID tag 9F27 missing", cid)
        // 80 = ARQC
        assertNotNull(cid!!.decoded)
        assertTrue("Should contain ARQC", cid.decoded!!.contains("ARQC"))
    }

    @Test
    fun `tag 9F36 ATC decoded as decimal`() {
        val tlv = TlvParser.parse(FULL_DE55_HEX)
        val atc = tlv.find { it.tag == "9F36" }
        assertNotNull("ATC tag 9F36 missing", atc)
        // 0459 hex = 1113 decimal
        assertNotNull(atc!!.decoded)
        assertEquals("1113", atc.decoded)
    }

    @Test
    fun `unknown tag shown with raw hex - no crash`() {
        // Fabricated TLV with a non-standard tag (DF99)
        val fabricated = "DF990301020382025800"
        val tlv = TlvParser.parse(fabricated)
        assertTrue("Should parse at least one tag", tlv.isNotEmpty())
        val unknown = tlv.find { it.name == null }
        assertNotNull("Unknown tag should be present with null name", unknown)
    }

    @Test
    fun `empty hex returns empty list`() {
        val tlv = TlvParser.parse("")
        assertTrue(tlv.isEmpty())
    }

    @Test
    fun `invalid hex does not crash`() {
        val tlv = TlvParser.parse("ZZZZ")  // not valid hex
        // Should return a single raw entry or empty — not throw
        assertNotNull(tlv)
    }

    @Test
    fun `hexToBytes utility works correctly`() {
        val bytes = TlvParser.hexToBytes("820258008407")
        assertNotNull(bytes)
        assertEquals(6, bytes!!.size)
        assertEquals(0x82.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
    }

    @Test
    fun `bytesToHex utility roundtrips`() {
        val hex = "820258008407A0000003710001"
        val bytes = TlvParser.hexToBytes(hex)!!
        assertEquals(hex.uppercase(), TlvParser.bytesToHex(bytes))
    }
}
