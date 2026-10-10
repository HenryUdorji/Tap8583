package com.tap8583.server.layouts

import com.tap8583.server.db.Db
import com.tap8583.server.db.Json
import com.tap8583.server.db.query
import com.tap8583.server.db.update
import com.tap8583.server.models.DecodedFieldDto
import com.tap8583.server.models.DecodedPacketDto
import com.tap8583.server.models.DecodedTransactionDto
import com.tap8583.server.models.FieldLayoutDto
import com.tap8583.server.models.FieldPreviewDto
import com.tap8583.server.models.LayoutPartDto
import com.tap8583.server.models.SubFieldDto
import kotlinx.serialization.encodeToString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json as KJson

/**
 * Workspace-defined layouts for private fields. The standard decoder runs at
 * ingest; layouts are applied when transactions are read, so a change also
 * explains transactions already stored.
 */
object FieldLayouts {
    const val FORMAT_TEXT = "text"
    const val FORMAT_TLV = "tlv"
    const val FORMAT_FIXED = "fixed"

    /** Card data and EMV keep their built-in decoding. */
    private val PROTECTED_FIELDS = setOf(0, 1, 2, 14, 34, 35, 36, 45, 52, 55)
    private const val MAX_PARTS = 100

    /** Returns the cleaned layout, or why it can't be saved. */
    fun validate(layout: FieldLayoutDto): Result<FieldLayoutDto> {
        fun fail(message: String) = Result.failure<FieldLayoutDto>(IllegalArgumentException(message))
        if (layout.field !in 2..128) return fail("Field number must be between 2 and 128")
        if (layout.field in PROTECTED_FIELDS) return fail("DE${layout.field} holds card or chip data and keeps its built-in decoding")
        val name = layout.name.trim().take(80)
        if (name.isEmpty()) return fail("Give the field a name")
        if (layout.format !in setOf(FORMAT_TEXT, FORMAT_TLV, FORMAT_FIXED)) return fail("Unknown format ${layout.format}")
        if (layout.tagLength !in 1..4) return fail("Tag length must be 1 to 4 characters")
        if (layout.lengthDigits !in 1..3) return fail("Length must be 1 to 3 digits")
        if (layout.parts.size > MAX_PARTS) return fail("At most $MAX_PARTS parts")

        val parts = layout.parts.map { part ->
            val partName = part.name.trim().take(80)
            if (partName.isEmpty()) return fail("Every part needs a name")
            when (layout.format) {
                FORMAT_TLV -> {
                    val tag = part.tag?.trim().orEmpty()
                    if (tag.length != layout.tagLength) return fail("Tag \"$tag\" should be ${layout.tagLength} characters")
                    LayoutPartDto(tag = tag, name = partName)
                }
                FORMAT_FIXED -> {
                    if (part.length != null && part.length !in 1..999) return fail("Part lengths must be 1 to 999")
                    LayoutPartDto(name = partName, length = part.length)
                }
                else -> LayoutPartDto(name = partName)
            }
        }
        if (layout.format == FORMAT_TLV && parts.map { it.tag }.toSet().size != parts.size) return fail("Each tag can only be named once")
        if (layout.format == FORMAT_FIXED && parts.dropLast(1).any { it.length == null }) {
            return fail("Only the last part can leave its length empty")
        }
        return Result.success(
            layout.copy(
                name = name,
                description = layout.description?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                parts = if (layout.format == FORMAT_TEXT) emptyList() else parts
            )
        )
    }

    /** Splits [raw] the way [layout] describes. */
    fun explain(layout: FieldLayoutDto, raw: String): FieldPreviewDto = when (layout.format) {
        FORMAT_TLV -> explainTlv(layout, raw)
        FORMAT_FIXED -> explainFixed(layout, raw)
        else -> FieldPreviewDto(null, emptyList())
    }

    private fun explainTlv(layout: FieldLayoutDto, raw: String): FieldPreviewDto {
        val names = layout.parts.associate { it.tag to it.name }
        val subFields = mutableListOf<SubFieldDto>()
        val header = layout.tagLength + layout.lengthDigits
        var pos = 0
        while (pos + header <= raw.length) {
            val tag = raw.substring(pos, pos + layout.tagLength)
            val len = raw.substring(pos + layout.tagLength, pos + header).toIntOrNull() ?: break
            if (pos + header + len > raw.length) break
            val value = raw.substring(pos + header, pos + header + len)
            subFields += SubFieldDto(names[tag]?.let { "$it (tag $tag)" } ?: "Tag $tag", value, value)
            pos += header + len
        }
        if (pos < raw.length) subFields += SubFieldDto("Not matched by the layout", raw.substring(pos), raw.substring(pos))
        return FieldPreviewDto(summary(subFields), subFields)
    }

    private fun explainFixed(layout: FieldLayoutDto, raw: String): FieldPreviewDto {
        val subFields = mutableListOf<SubFieldDto>()
        var pos = 0
        for (part in layout.parts) {
            if (pos >= raw.length) break
            val end = part.length?.let { (pos + it).coerceAtMost(raw.length) } ?: raw.length
            val value = raw.substring(pos, end)
            subFields += SubFieldDto(part.name, value, value)
            pos = end
        }
        if (pos < raw.length) subFields += SubFieldDto("Not matched by the layout", raw.substring(pos), raw.substring(pos))
        return FieldPreviewDto(summary(subFields), subFields)
    }

    private fun summary(subFields: List<SubFieldDto>): String? = subFields
        .filter { !it.name.startsWith("Not matched") }
        .take(3)
        .joinToString("  ·  ") { "${it.name.substringBefore(" (tag")}: ${it.decodedValue.trim()}" }
        .takeIf { it.isNotEmpty() }

    fun apply(field: DecodedFieldDto, layout: FieldLayoutDto): DecodedFieldDto {
        val explained = explain(layout, field.rawValue)
        return field.copy(
            name = layout.name,
            shortName = layout.name.uppercase().take(40),
            description = layout.description,
            decodedValue = explained.decodedValue ?: field.decodedValue,
            subFields = explained.subFields.takeIf { it.isNotEmpty() } ?: field.subFields,
            workspaceLayout = true
        )
    }

    fun apply(tx: DecodedTransactionDto, layouts: Map<Int, FieldLayoutDto>): DecodedTransactionDto {
        if (layouts.isEmpty()) return tx
        fun DecodedPacketDto.withLayouts() = copy(fields = fields.map { f -> layouts[f.number]?.let { apply(f, it) } ?: f })
        return tx.copy(request = tx.request?.withLayouts(), response = tx.response?.withLayouts())
    }
}

/** Layouts in Postgres, cached per workspace. */
class FieldLayoutStore(private val db: Db, private val json: KJson) {
    private val cache = ConcurrentHashMap<String, Map<Int, FieldLayoutDto>>()

    suspend fun layouts(workspaceId: String): Map<Int, FieldLayoutDto> {
        cache[workspaceId]?.let { return it }
        val id = runCatching { UUID.fromString(workspaceId) }.getOrNull() ?: return emptyMap()
        val loaded = db.tx {
            query("SELECT layout FROM field_layouts WHERE workspace_id = ? ORDER BY field_number", id) {
                json.decodeFromString<FieldLayoutDto>(it.getString("layout"))
            }
        }.associateBy { it.field }
        cache[workspaceId] = loaded
        return loaded
    }

    suspend fun save(workspaceId: UUID, layout: FieldLayoutDto) {
        db.tx {
            update(
                """
                INSERT INTO field_layouts (workspace_id, field_number, layout) VALUES (?, ?, ?)
                ON CONFLICT (workspace_id, field_number) DO UPDATE SET layout = EXCLUDED.layout, updated_at = now()
                """.trimIndent(),
                workspaceId, layout.field, Json(json.encodeToString(layout))
            )
        }
        cache.remove(workspaceId.toString())
    }

    suspend fun delete(workspaceId: UUID, field: Int): Boolean {
        val deleted = db.tx { update("DELETE FROM field_layouts WHERE workspace_id = ? AND field_number = ?", workspaceId, field) }
        cache.remove(workspaceId.toString())
        return deleted > 0
    }

    suspend fun apply(workspaceId: String, tx: DecodedTransactionDto): DecodedTransactionDto =
        FieldLayouts.apply(tx, layouts(workspaceId))

    suspend fun apply(workspaceId: String, txs: List<DecodedTransactionDto>): List<DecodedTransactionDto> {
        val layouts = layouts(workspaceId)
        return if (layouts.isEmpty()) txs else txs.map { FieldLayouts.apply(it, layouts) }
    }
}
