package com.tap8583.tap8583.decoder

import org.junit.Assert.*
import org.junit.Test

class MtiDecoderTest {

    @Test
    fun `decode 0200 Financial Transaction Request`() {
        val mti = MtiDecoder.decode("0200")
        assertEquals("0200", mti.raw)
        assertEquals("ISO 8583-1:1987", mti.version)
        assertEquals("Financial", mti.messageClass)
        assertEquals("Request", mti.messageFunction)
        assertEquals("Acquirer", mti.messageOrigin)
        assertEquals("Financial Transaction Request", mti.label)
        assertTrue(MtiDecoder.isRequest(mti))
        assertFalse(MtiDecoder.isResponse(mti))
    }

    @Test
    fun `decode 0210 Financial Transaction Response`() {
        val mti = MtiDecoder.decode("0210")
        assertEquals("Financial", mti.messageClass)
        assertEquals("Request Response", mti.messageFunction)
        assertEquals("Acquirer", mti.messageOrigin)
        assertEquals("Financial Transaction Response", mti.label)
        assertFalse(MtiDecoder.isRequest(mti))
        assertTrue(MtiDecoder.isResponse(mti))
    }

    @Test
    fun `decode 0100 Authorization Request`() {
        val mti = MtiDecoder.decode("0100")
        assertEquals("Authorization", mti.messageClass)
        assertEquals("Request", mti.messageFunction)
        assertEquals("Authorization Request", mti.label)
    }

    @Test
    fun `decode 0110 Authorization Response`() {
        val mti = MtiDecoder.decode("0110")
        assertEquals("Authorization Request Response", mti.label)
    }

    @Test
    fun `decode 0400 Reversal Request`() {
        val mti = MtiDecoder.decode("0400")
        assertTrue(mti.messageClass.contains("Reversal"))
        assertEquals("Reversal Request", mti.label)
    }

    @Test
    fun `decode 0410 Reversal Response`() {
        val mti = MtiDecoder.decode("0410")
        assertEquals("Reversal Request Response", mti.label)
    }

    @Test
    fun `decode 0800 Network Management Request`() {
        val mti = MtiDecoder.decode("0800")
        assertEquals("Network Management", mti.messageClass)
        assertEquals("Request", mti.messageFunction)
        assertEquals("Network Management Request", mti.label)
    }

    @Test
    fun `decode 0810 Network Management Response`() {
        val mti = MtiDecoder.decode("0810")
        assertEquals("Network Management Response", mti.label)
    }

    @Test
    fun `decode 0220 Financial Advice`() {
        val mti = MtiDecoder.decode("0220")
        assertEquals("Financial", mti.messageClass)
        assertEquals("Advice", mti.messageFunction)
    }

    @Test
    fun `decode version digits correctly`() {
        assertEquals("ISO 8583-1:1987", MtiDecoder.decode("0200").version)
        assertEquals("ISO 8583-1:1993", MtiDecoder.decode("1200").version)
        assertEquals("ISO 8583-1:2003", MtiDecoder.decode("2200").version)
    }

    @Test
    fun `decode origin digits`() {
        assertEquals("Acquirer",        MtiDecoder.decode("0200").messageOrigin)
        assertEquals("Acquirer Repeat", MtiDecoder.decode("0201").messageOrigin)
        assertEquals("Issuer",          MtiDecoder.decode("0202").messageOrigin)
        assertEquals("Issuer Repeat",   MtiDecoder.decode("0203").messageOrigin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on non-digit MTI`() {
        MtiDecoder.decode("ABCD")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on short MTI`() {
        MtiDecoder.decode("020")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on empty MTI`() {
        MtiDecoder.decode("")
    }
}
