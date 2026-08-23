package com.tap8583.tap8583.decoder

import org.junit.Assert.*
import org.junit.Test

class FieldDecodersTest {

    // -----------------------------------------------------------------------
    // DE3 — Processing Code
    // -----------------------------------------------------------------------

    @Test
    fun `DE3 000000 = Purchase Default Default`() {
        val result = FieldDecoders.decodeProcessingCode("000000")
        assertTrue(result.decodedValue.contains("Purchase"))
        assertTrue(result.decodedValue.contains("Default"))
        assertEquals(3, result.subFields.size)
        assertEquals("Purchase", result.subFields[0].decodedValue)
    }

    @Test
    fun `DE3 010010 = Cash Savings Default`() {
        val result = FieldDecoders.decodeProcessingCode("010010")
        assertTrue(result.decodedValue.contains("Cash"))
        assertTrue(result.decodedValue.contains("Savings"))
    }

    @Test
    fun `DE3 200000 = Refund`() {
        val result = FieldDecoders.decodeProcessingCode("200000")
        assertTrue(result.decodedValue.lowercase().contains("refund"))
    }

    @Test
    fun `DE3 invalid length returns error message`() {
        val result = FieldDecoders.decodeProcessingCode("12345")
        assertTrue(result.decodedValue.contains("Invalid"))
    }

    // -----------------------------------------------------------------------
    // DE4 — Amount
    // -----------------------------------------------------------------------

    @Test
    fun `DE4 with NGN currency formats correctly`() {
        val result = FieldDecoders.decodeAmount("000000000001", "566")
        // 1 minor unit of NGN (2 decimal places) = ₦ 0.01
        assertTrue(result.decodedValue.contains("₦"))
        assertTrue(result.decodedValue.contains("0.01"))
    }

    @Test
    fun `DE4 large NGN amount formatted with thousands separator`() {
        val result = FieldDecoders.decodeAmount("000000500000", "566")
        // 500000 minor units = ₦ 5,000.00
        assertTrue(result.decodedValue.contains("5,000"))
    }

    @Test
    fun `DE4 without currency code uses question mark symbol`() {
        val result = FieldDecoders.decodeAmount("000000000100", null)
        assertTrue(result.decodedValue.contains("?"))
    }

    @Test
    fun `DE4 USD formatting`() {
        val result = FieldDecoders.decodeAmount("000000010000", "840")
        assertTrue(result.decodedValue.contains("$"))
        assertTrue(result.decodedValue.contains("100.00"))
    }

    // -----------------------------------------------------------------------
    // DE7 — Transmission Date/Time
    // -----------------------------------------------------------------------

    @Test
    fun `DE7 0521142037 = May 21, 14-20-37`() {
        val result = FieldDecoders.decodeTransmissionDateTime("0521142037")
        assertTrue(result.decodedValue.contains("May"))
        assertTrue(result.decodedValue.contains("21"))
        assertTrue(result.decodedValue.contains("14:20:37"))
    }

    @Test
    fun `DE7 invalid length returns error`() {
        val result = FieldDecoders.decodeTransmissionDateTime("052114")
        assertTrue(result.decodedValue.contains("Invalid"))
    }

    // -----------------------------------------------------------------------
    // DE14 — Expiry Date
    // -----------------------------------------------------------------------

    @Test
    fun `DE14 2702 = February 2027`() {
        val result = FieldDecoders.decodeExpiryDate("2702")
        assertTrue(result.decodedValue.contains("February"))
        assertTrue(result.decodedValue.contains("2027"))
    }

    // -----------------------------------------------------------------------
    // DE18 — MCC
    // -----------------------------------------------------------------------

    @Test
    fun `DE18 6012 = Financial Institutions`() {
        val result = FieldDecoders.decodeMcc("6012")
        assertTrue(result.decodedValue.contains("Financial"))
    }

    @Test
    fun `DE18 5411 = Grocery Stores`() {
        val result = FieldDecoders.decodeMcc("5411")
        assertTrue(result.decodedValue.lowercase().contains("grocery"))
    }

    @Test
    fun `DE18 unknown MCC returns fallback`() {
        val result = FieldDecoders.decodeMcc("9999")
        assertTrue(result.decodedValue.contains("9999"))
    }

    // -----------------------------------------------------------------------
    // DE22 — POS Entry Mode
    // -----------------------------------------------------------------------

    @Test
    fun `DE22 051 = Chip + PIN Entered`() {
        val result = FieldDecoders.decodePosEntryMode("051")
        assertTrue(result.decodedValue.contains("Chip") || result.decodedValue.contains("ICC"))
        assertTrue(result.decodedValue.contains("PIN"))
        assertEquals(2, result.subFields.size)
    }

    @Test
    fun `DE22 901 = Magnetic Stripe`() {
        val result = FieldDecoders.decodePosEntryMode("901")
        assertTrue(result.decodedValue.lowercase().contains("magnetic") ||
                   result.decodedValue.lowercase().contains("stripe"))
    }

    @Test
    fun `DE22 071 = Contactless`() {
        val result = FieldDecoders.decodePosEntryMode("071")
        assertTrue(result.decodedValue.lowercase().contains("contactless"))
    }

    // -----------------------------------------------------------------------
    // DE32 — Acquiring Institution
    // -----------------------------------------------------------------------

    @Test
    fun `DE32 506124 = Verve Interswitch`() {
        val result = FieldDecoders.decodeAcquiringInstitution("506124")
        assertTrue(result.decodedValue.contains("Verve") || result.decodedValue.contains("Interswitch"))
    }

    @Test
    fun `DE32 000006 = First Bank`() {
        val result = FieldDecoders.decodeAcquiringInstitution("000006")
        assertTrue(result.decodedValue.lowercase().contains("first"))
    }

    // -----------------------------------------------------------------------
    // DE35 — Track 2
    // -----------------------------------------------------------------------

    @Test
    fun `DE35 full track 2 from spec`() {
        val result = FieldDecoders.decodeTrack2("5061240207416432753D2702601013660902")
        assertTrue(result.decodedValue.contains("PAN") || result.decodedValue.contains("••"))
        assertTrue(result.decodedValue.contains("February") || result.decodedValue.contains("2027"))
        assertEquals(4, result.subFields.size)
    }

    @Test
    fun `DE35 with = separator`() {
        val result = FieldDecoders.decodeTrack2("4111111111111111=2012101000000000")
        // Should not crash; separator = is also valid
        assertNotNull(result.decodedValue)
    }

    @Test
    fun `DE35 without separator returns error`() {
        val result = FieldDecoders.decodeTrack2("4111111111111111")
        assertTrue(result.decodedValue.contains("separator") || result.decodedValue.contains("not found"))
    }

    // -----------------------------------------------------------------------
    // DE39 — Response Code
    // -----------------------------------------------------------------------

    @Test
    fun `DE39 00 = Approved`() {
        val result = FieldDecoders.decodeResponseCode("00")
        assertTrue(result.decodedValue.contains("Approved"))
        assertEquals(FieldDecoders.ResponseSeverity.SUCCESS, FieldDecoders.responseSeverity("00"))
    }

    @Test
    fun `DE39 51 = Insufficient Funds`() {
        val result = FieldDecoders.decodeResponseCode("51")
        assertTrue(result.decodedValue.contains("Insufficient"))
        assertEquals(FieldDecoders.ResponseSeverity.DECLINED, FieldDecoders.responseSeverity("51"))
    }

    @Test
    fun `DE39 68 = Timeout`() {
        assertEquals(FieldDecoders.ResponseSeverity.TIMEOUT, FieldDecoders.responseSeverity("68"))
    }

    @Test
    fun `DE39 91 = Network Error`() {
        val result = FieldDecoders.decodeResponseCode("91")
        assertTrue(result.decodedValue.contains("Inoperative") || result.decodedValue.contains("Switch"))
    }

    @Test
    fun `DE39 05 = Do Not Honour`() {
        val result = FieldDecoders.decodeResponseCode("05")
        assertTrue(result.decodedValue.contains("Honour") || result.decodedValue.contains("Honor"))
    }

    @Test
    fun `DE39 unknown code returns warning severity`() {
        assertEquals(FieldDecoders.ResponseSeverity.WARNING, FieldDecoders.responseSeverity("XX"))
    }

    // -----------------------------------------------------------------------
    // DE49 — Currency
    // -----------------------------------------------------------------------

    @Test
    fun `DE49 566 = NGN Nigerian Naira`() {
        val result = FieldDecoders.decodeCurrency("566")
        assertTrue(result.decodedValue.contains("Naira") || result.decodedValue.contains("NGN"))
        assertTrue(result.decodedValue.contains("₦"))
    }

    @Test
    fun `DE49 840 = USD`() {
        val result = FieldDecoders.decodeCurrency("840")
        assertTrue(result.decodedValue.contains("Dollar") || result.decodedValue.contains("USD"))
    }

    @Test
    fun `DE49 unknown code returns fallback`() {
        val result = FieldDecoders.decodeCurrency("999")
        assertTrue(result.decodedValue.contains("999") || result.decodedValue.contains("Unknown"))
    }

    // -----------------------------------------------------------------------
    // DE52 — PIN Block
    // -----------------------------------------------------------------------

    @Test
    fun `DE52 always masked`() {
        val result = FieldDecoders.decode(52, "26B7D3B168E69591", emptyMap())
        assertNotNull(result)
        assertTrue(result!!.decodedValue.contains("████"))
        assertTrue(result.decodedValue.contains("3DES") || result.decodedValue.contains("PIN"))
    }

    // -----------------------------------------------------------------------
    // DE59 — NIBSS Reference
    // -----------------------------------------------------------------------

    @Test
    fun `DE59 three-part NIBSS reference`() {
        val result = FieldDecoders.decodeNibssReference("2214OTGF-P332600027425-614114086503")
        assertTrue(result.decodedValue.contains("2214OTGF"))
        assertTrue(result.decodedValue.contains("P332600027425"))
        assertTrue(result.decodedValue.contains("614114086503"))
        assertEquals(3, result.subFields.size)
    }

    // -----------------------------------------------------------------------
    // PAN masking and Luhn
    // -----------------------------------------------------------------------

    @Test
    fun `PAN Luhn check passes for valid card`() {
        // Verve test PAN from spec
        val result = FieldDecoders.decodePan("5061240207416432753")
        assertTrue(result.decodedValue.contains("✓"))
    }

    @Test
    fun `PAN scheme detection Verve`() {
        val result = FieldDecoders.decodePan("5061240207416432753")
        assertTrue(result.decodedValue.contains("Verve"))
    }

    @Test
    fun `PAN scheme detection Visa`() {
        val result = FieldDecoders.decodePan("4111111111111111")
        assertTrue(result.decodedValue.contains("Visa"))
    }

    // -----------------------------------------------------------------------
    // DE62 — Custom TLV Field
    // -----------------------------------------------------------------------

    @Test
    fun `DE62 parses tags 03, 52, 05, 08 correctly`() {
        // Tag 03 (Merchant ID) length 015: "2214LA565107542"
        // Tag 52 (Location) length 040: "ETRANZACT INTERNATIONALFORTUNE TOWERLANG"
        // Tag 05 (Currency) length 003: "566"
        // Tag 08 (MCC) length 004: "6012"
        val rawValue = "030152214LA56510754252040ETRANZACT INTERNATIONALFORTUNE TOWERLANG05003566080046012"
        val result = FieldDecoders.decodeField62(rawValue)

        assertTrue(result.decodedValue.contains("2214LA565107542"))
        assertTrue(result.decodedValue.contains("ETRANZACT INTERNATIONAL"))
        
        // Assert subfields were extracted correctly
        val midSub = result.subFields.first { it.name.contains("Merchant ID") }
        assertEquals("2214LA565107542", midSub.decodedValue)

        val locSub = result.subFields.first { it.name.contains("Merchant Location") }
        assertEquals("ETRANZACT INTERNATIONALFORTUNE TOWERLANG", locSub.decodedValue)

        val curSub = result.subFields.first { it.name.contains("Currency Code") }
        assertTrue(curSub.decodedValue.contains("Naira") || curSub.decodedValue.contains("NGN"))

        val mccSub = result.subFields.first { it.name.contains("Category Code") }
        assertTrue(mccSub.decodedValue.contains("Financial"))
    }

    // -----------------------------------------------------------------------
    // DE54 — Additional Amounts
    // -----------------------------------------------------------------------

    @Test
    fun `DE54 extracts and decodes additional amount correctly`() {
        val result = FieldDecoders.decodeField54("1002566C000000012345")
        assertTrue(result.decodedValue.contains("Available Balance"))
        assertTrue(result.decodedValue.contains("₦123.45") || result.decodedValue.contains("₦ 123.45"))
        assertTrue(result.decodedValue.contains("Savings"))

        assertEquals(5, result.subFields.size)
        assertEquals("Savings", result.subFields.first { it.name == "Account Type" }.decodedValue)
        assertEquals("Available Balance", result.subFields.first { it.name == "Amount Type" }.decodedValue)
        assertEquals("Credit", result.subFields.first { it.name == "Sign" }.decodedValue)
        assertTrue(result.subFields.first { it.name == "Currency" }.decodedValue.contains("Naira"))
    }

    // -----------------------------------------------------------------------
    // DE120 — POS Terminal Location Data
    // -----------------------------------------------------------------------

    @Test
    fun `DE120 decodes standard positive latitude and longitude correctly`() {
        val raw = "06.5244,003.3792"
        val result = FieldDecoders.decode(120, raw, emptyMap())
        assertNotNull(result)
        assertEquals("06.5244° N, 003.3792° E", result!!.decodedValue)

        assertEquals(4, result.subFields.size)
        val latSub = result.subFields.first { it.name == "Latitude" }
        assertEquals("06.5244° N (North (N))", latSub.decodedValue)

        val lonSub = result.subFields.first { it.name == "Longitude" }
        assertEquals("003.3792° E (East (E))", lonSub.decodedValue)

        val mapSub = result.subFields.first { it.name == "Map Query" }
        assertTrue(mapSub.rawValue.contains("06.5244,003.3792"))
    }

    @Test
    fun `DE120 decodes negative southern and western coordinates correctly`() {
        val raw = "-33.8688,-151.2093"
        val result = FieldDecoders.decodePosLocation(raw)
        assertEquals("33.8688° S, 151.2093° W", result.decodedValue)

        val latSub = result.subFields.first { it.name == "Latitude" }
        assertTrue(latSub.decodedValue.contains("South"))

        val lonSub = result.subFields.first { it.name == "Longitude" }
        assertTrue(lonSub.decodedValue.contains("West"))

        val mapSub = result.subFields.first { it.name == "Map Query" }
        assertTrue(mapSub.rawValue.contains("-33.8688,-151.2093"))
    }

    @Test
    fun `DE120 handles zero coordinates at equator and meridian`() {
        val raw = "00.0000,000.0000"
        val result = FieldDecoders.decodePosLocation(raw)
        assertEquals("00.0000° N, 000.0000° E", result.decodedValue)
    }

    @Test
    fun `DE120 handles single coordinate string gracefully`() {
        val raw = "06.5244"
        val result = FieldDecoders.decodePosLocation(raw)
        assertTrue(result.decodedValue.contains("06.5244"))
    }
}
