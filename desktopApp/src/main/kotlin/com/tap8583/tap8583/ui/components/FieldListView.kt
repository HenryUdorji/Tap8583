package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.DecodedField
import com.tap8583.tap8583.decoder.DecodedPacket
import com.tap8583.tap8583.decoder.FieldCategory
import com.tap8583.tap8583.ui.AppColors
import com.tap8583.tap8583.ui.accentColor

/**
 * Scrollable field list for the packet inspector centre panel.
 *
 * Fields are grouped by [FieldCategory].  Each group has a subtle header.
 * Each field row shows:
 *  - Coloured left accent strip
 *  - DE number in monospace
 *  - Field name (from FieldSchema)
 *  - Decoded value (or masked "••••" for sensitive fields)
 *  - Raw value in a dim monospace font
 *  - Expansion arrow for fields with sub-fields
 */
@Composable
fun FieldListView(
    packet: DecodedPacket,
    selectedFieldNumber: Int?,
    onSelectField: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group fields by category, in natural category order
    val grouped: Map<FieldCategory, List<DecodedField>> = packet.fields
        .groupBy { it.definition.category }
        .entries
        .sortedBy { (cat, _) -> cat.ordinal }
        .associate { (cat, fields) -> cat to fields.sortedBy { it.number } }

    LazyColumn(
        modifier        = modifier,
        contentPadding  = PaddingValues(vertical = 6.dp),
    ) {
        grouped.forEach { (category, fields) ->
            // Category header
            item(key = "hdr_${category.name}") {
                CategoryHeader(category)
            }

            items(fields, key = { "f_${it.number}" }) { field ->
                FieldRow(
                    field       = field,
                    isSelected  = field.number == selectedFieldNumber,
                    onClick     = {
                        onSelectField(if (field.number == selectedFieldNumber) null else field.number)
                    }
                )
            }
        }
    }
}

// ── Category header ────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(category: FieldCategory) {
    val accent = category.accentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(10.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Text(
            text       = category.displayName(),
            color      = accent,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = accent.copy(alpha = 0.15f),
            thickness = 1.dp
        )
    }
}

// ── Individual field row ───────────────────────────────────────────────────

@Composable
private fun FieldRow(
    field: DecodedField,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent       = field.definition.category.accentColor()
    val bg           = if (isSelected) AppColors.SurfaceVariant else Color.Transparent
    val hasSubfields = !field.subFields.isNullOrEmpty()
    val hasTlv       = !field.tlvEntries.isNullOrEmpty()
    val isExpandable = hasSubfields || hasTlv

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable {
                onClick()
                if (isExpandable) expanded = !expanded
            }
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left accent strip
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )

            Spacer(Modifier.width(10.dp))

            // DE number
            Text(
                text       = "DE%-3d".format(field.number),
                color      = AppColors.TextTertiary,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.width(42.dp)
            )

            Spacer(Modifier.width(8.dp))

            // Field name + decoded value
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = field.definition.name,
                    color      = AppColors.TextSecondary,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1
                )
                val displayValue = when {
                    field.isSensitive && field.isCurrentlyMasked ->
                        "••••  ••••  ••••  ••••"
                    field.tlvEntries != null ->
                        field.decodedValue ?: "[${field.tlvEntries!!.size} TLV tags — expand to view]"
                    field.decodedValue != null ->
                        field.decodedValue
                    else ->
                        field.rawValue
                }
                Text(
                    text     = displayValue ?: field.rawValue,
                    color    = AppColors.TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    fontFamily = if (field.isBinary || field.definition.category == FieldCategory.SECURITY)
                        FontFamily.Monospace else FontFamily.Default
                )
            }

            Spacer(Modifier.width(8.dp))

            // Raw value + expand arrow
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = field.rawValue.take(20) + if (field.rawValue.length > 20) "…" else "",
                    color      = AppColors.TextTertiary,
                    fontSize   = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines   = 1
                )
                if (isExpandable) {
                    Text(
                        text     = if (expanded) "▲" else "▼",
                        color    = AppColors.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
        }

        // ── Expanded sub-fields ───────────────────────────────────────────────
        if (expanded) {
            if (hasSubfields) {
                SubFieldList(field.subFields!!, accent)
            } else if (hasTlv) {
                TlvList(field.tlvEntries!!, accent)
            }
        }
    }
}

// ── Sub-field list ─────────────────────────────────────────────────────────

@Composable
private fun SubFieldList(subFields: List<com.tap8583.tap8583.decoder.SubField>, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 23.dp, end = 8.dp, bottom = 6.dp)
    ) {
        subFields.forEach { sf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("›", color = accent, fontSize = 10.sp, modifier = Modifier.width(10.dp))
                Text(sf.name, color = AppColors.TextTertiary, fontSize = 10.sp, modifier = Modifier.width(120.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sf.decodedValue, color = AppColors.TextSecondary, fontSize = 10.sp)
                    if (sf.rawValue != sf.decodedValue) {
                        Text(sf.rawValue, color = AppColors.TextTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ── TLV list ───────────────────────────────────────────────────────────────

@Composable
private fun TlvList(entries: List<com.tap8583.tap8583.decoder.TLVEntry>, accent: Color, depth: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (23 + depth * 16).dp, end = 8.dp, bottom = if (depth == 0) 6.dp else 0.dp)
    ) {
        entries.forEach { entry ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Tag
                    Text(
                        text       = entry.tag,
                        color      = accent,
                        fontSize   = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.width(36.dp)
                    )
                    // Name
                    Text(
                        text     = entry.name ?: "[Unknown]",
                        color    = AppColors.TextTertiary,
                        fontSize = 10.sp,
                        modifier = Modifier.width(200.dp)
                    )
                    // Decoded or raw value
                    Text(
                        text     = entry.decoded ?: entry.valueHex,
                        color    = AppColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = if (entry.decoded == null) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )
                    // Length
                    Text(
                        text     = "${entry.length}B",
                        color    = AppColors.TextTertiary,
                        fontSize = 9.sp
                    )
                }
                // Recurse for constructed tags
                if (!entry.children.isNullOrEmpty()) {
                    TlvList(entry.children!!, accent, depth + 1)
                }
            }
        }
    }
}

// ── Category display name mapping ──────────────────────────────────────────

private fun FieldCategory.displayName(): String = when (this) {
    FieldCategory.CARD_DATA      -> "CARD DATA"
    FieldCategory.AMOUNT         -> "AMOUNTS & CURRENCY"
    FieldCategory.DATETIME       -> "DATE & TIME"
    FieldCategory.IDENTIFICATION -> "IDENTIFICATION"
    FieldCategory.RESPONSE       -> "RESPONSE"
    FieldCategory.POS_TERMINAL   -> "POS TERMINAL"
    FieldCategory.EMV            -> "EMV / CHIP DATA"
    FieldCategory.INSTITUTION    -> "INSTITUTION"
    FieldCategory.SECURITY       -> "SECURITY"
    FieldCategory.PRIVATE_NIBSS  -> "PRIVATE / NIBSS"
    FieldCategory.NETWORK        -> "NETWORK MANAGEMENT"
    FieldCategory.GENERAL        -> "GENERAL"
}
