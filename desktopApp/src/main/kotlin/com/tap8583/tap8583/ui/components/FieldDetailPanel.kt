package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.DecodedField
import com.tap8583.tap8583.decoder.FieldCategory
import com.tap8583.tap8583.ui.AppColors
import com.tap8583.tap8583.ui.AppUiState
import com.tap8583.tap8583.ui.accentColor

/**
 * Right panel — detailed view for the selected field.
 *
 * Displays:
 *  - Field number and name in a header card
 *  - Full decoded value (untruncated)
 *  - All sub-fields in a table
 *  - Raw value in monospace
 *  - Metadata (category, type, length, sensitivity)
 */
@Composable
fun FieldDetailPanel(uiState: AppUiState, modifier: Modifier = Modifier) {
    val field = uiState.selectedField

    Column(modifier = modifier.background(AppColors.Background)) {

        // Panel header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "FIELD DETAIL",
                color      = AppColors.TextSecondary,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

        if (field == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔢", fontSize = 32.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Select a field", color = AppColors.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Click a row in the field list", color = AppColors.TextTertiary, fontSize = 10.sp)
                }
            }
        } else {
            FieldDetail(field)
        }
    }
}

@Composable
private fun FieldDetail(field: DecodedField) {
    val accent  = field.definition.category.accentColor()
    val scroll  = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header card ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceVariant)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "DE ${field.number}",
                            color      = accent,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        field.definition.name,
                        color      = AppColors.TextPrimary,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                CategoryPill(field.definition.category, accent)
            }
        }

        // ── Decoded value ─────────────────────────────────────────────────────
        DetailSection("Decoded Value") {
            val displayValue = when {
                field.isSensitive && field.isCurrentlyMasked -> "••••  ••••  ••••  ••••"
                field.decodedValue != null                   -> field.decodedValue
                else                                          -> field.rawValue
            }
            Text(
                text     = displayValue ?: field.rawValue,
                color    = AppColors.TextPrimary,
                fontSize = 12.sp,
                fontFamily = if (field.definition.category in listOf(FieldCategory.SECURITY, FieldCategory.CARD_DATA))
                    FontFamily.Monospace else FontFamily.Default,
                lineHeight = 18.sp
            )
        }

        // ── Sub-fields table ──────────────────────────────────────────────────
        if (!field.subFields.isNullOrEmpty()) {
            DetailSection("Sub-Fields  (${field.subFields!!.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    field.subFields!!.forEach { sf ->
                        SubFieldDetailRow(sf.name, sf.rawValue, sf.decodedValue, accent)
                    }
                }
            }
        }

        // ── TLV summary ───────────────────────────────────────────────────────
        if (!field.tlvEntries.isNullOrEmpty()) {
            DetailSection("EMV Tags  (${field.tlvEntries!!.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    field.tlvEntries!!.forEach { tag ->
                        SubFieldDetailRow(
                            name    = "${tag.tag}  ${tag.name ?: ""}",
                            raw     = tag.valueHex,
                            decoded = tag.decoded ?: tag.valueHex,
                            accent  = accent
                        )
                    }
                }
            }
        }

        // ── Raw value ─────────────────────────────────────────────────────────
        DetailSection("Raw Value") {
            Text(
                text       = field.rawValue,
                color      = AppColors.TextSecondary,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }

        // ── Metadata ──────────────────────────────────────────────────────────
        DetailSection("Metadata") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaRow("Category",  field.definition.category.name.replace("_", " "))
                MetaRow("Type",      if (field.isBinary) "Binary (hex)" else "Alphanumeric")
                MetaRow("Length",    "${field.rawValue.length} chars${if (field.isBinary) " (${field.rawValue.length / 2} bytes)" else ""}")
                MetaRow("Sensitive", if (field.isSensitive) "Yes — masked by default" else "No")
                if (field.definition.description.isNotBlank()) {
                    MetaRow("Spec",  field.definition.description)
                }
            }
        }
    }
}


@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color      = AppColors.TextTertiary,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.SurfaceVariant)
                .padding(10.dp)
        ) { content() }
    }
}

@Composable
private fun SubFieldDetailRow(name: String, raw: String, decoded: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp).height(30.dp)
                .background(accent.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = AppColors.TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(decoded, color = AppColors.TextPrimary, fontSize = 10.sp)
            if (raw != decoded) {
                Text(raw, color = AppColors.TextTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.TextTertiary, fontSize = 10.sp)
        Text(value, color = AppColors.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun CategoryPill(category: FieldCategory, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            category.name.replace("_", " "),
            color    = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}
