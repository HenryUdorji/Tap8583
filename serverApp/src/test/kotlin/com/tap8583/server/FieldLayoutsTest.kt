package com.tap8583.server

import com.tap8583.server.layouts.FieldLayouts
import com.tap8583.server.models.FieldLayoutDto
import com.tap8583.server.models.LayoutPartDto
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldLayoutsTest {

    @Test
    fun `tlv layout names known tags and keeps unknown ones`() {
        val layout = FieldLayoutDto(62, "Merchant data", format = "tlv", tagLength = 2, lengthDigits = 3,
            parts = listOf(LayoutPartDto(tag = "01", name = "Store")))
        val result = FieldLayouts.explain(layout, "01003ABC99002XY")
        assertEquals(listOf("Store (tag 01)", "Tag 99"), result.subFields.map { it.name })
        assertEquals("Store: ABC  ·  Tag 99: XY", result.decodedValue)
    }

    @Test
    fun `tlv layout reports what it could not parse`() {
        val layout = FieldLayoutDto(48, "Private", format = "tlv", tagLength = 2, lengthDigits = 2)
        val result = FieldLayouts.explain(layout, "0103ABC0599")
        assertEquals("Not matched by the layout", result.subFields.last().name)
        assertEquals("0599", result.subFields.last().rawValue)
    }

    @Test
    fun `fixed layout splits by length and the last part takes the rest`() {
        val layout = FieldLayoutDto(63, "Batch", format = "fixed",
            parts = listOf(LayoutPartDto(name = "Batch", length = 6), LayoutPartDto(name = "Notes")))
        val result = FieldLayouts.explain(layout, "000123hello world")
        assertEquals(listOf("000123", "hello world"), result.subFields.map { it.rawValue })
    }

    @Test
    fun `validation protects card fields and checks tags`() {
        assertTrue(FieldLayouts.validate(FieldLayoutDto(2, "PAN")).isFailure)
        assertTrue(FieldLayouts.validate(FieldLayoutDto(62, " ")).isFailure)
        assertTrue(FieldLayouts.validate(FieldLayoutDto(62, "x", format = "tlv", parts = listOf(LayoutPartDto(tag = "1", name = "a")))).isFailure)
        assertTrue(FieldLayouts.validate(FieldLayoutDto(62, "x", format = "fixed",
            parts = listOf(LayoutPartDto(name = "a"), LayoutPartDto(name = "b", length = 2)))).isFailure)
        val ok = FieldLayouts.validate(FieldLayoutDto(62, "  Acme  ", description = "  ", format = "text",
            parts = listOf(LayoutPartDto(tag = "01", name = "dropped")))).getOrThrow()
        assertEquals("Acme", ok.name)
        assertEquals(null, ok.description)
        assertTrue(ok.parts.isEmpty())
    }
}
