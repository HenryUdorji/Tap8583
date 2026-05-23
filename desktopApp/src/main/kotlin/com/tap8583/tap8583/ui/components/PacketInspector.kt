package com.tap8583.tap8583.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.DecodedPacket
import com.tap8583.tap8583.ui.AppColors
import com.tap8583.tap8583.ui.AppUiState
import com.tap8583.tap8583.ui.CenterTab

/**
 * Centre panel — packet inspector.
 *
 * Contains:
 * - Tab bar: Request | Response | Raw
 * - MTI hero card (for Request / Response tabs)
 * - Field list (for Request / Response tabs)
 * - Syntax-highlighted raw XML (for Raw tab)
 */
@Composable
fun PacketInspector(
    uiState: AppUiState,
    onSelectTab: (CenterTab) -> Unit,
    onSelectField: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = uiState.selectedTransaction

    Column(modifier = modifier.background(AppColors.Background)) {

        // ── Panel header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "PACKET INSPECTOR",
                color      = AppColors.TextSecondary,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

        if (tx == null) {
            // Nothing selected
            NoSelectionPlaceholder()
        } else {
            // ── Tab bar ───────────────────────────────────────────────────────
            TabBar(
                selected      = uiState.centerTab,
                hasResponse   = tx.response != null,
                onSelectTab   = onSelectTab
            )

            HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

            // ── Tab content ───────────────────────────────────────────────────
            AnimatedContent(
                targetState   = uiState.centerTab,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                modifier      = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    CenterTab.REQUEST -> {
                        PacketTabContent(
                            packet              = tx.request,
                            selectedFieldNumber = uiState.selectedFieldNumber,
                            onSelectField       = onSelectField,
                            emptyLabel          = "No request captured yet"
                        )
                    }
                    CenterTab.RESPONSE -> {
                        PacketTabContent(
                            packet              = tx.response,
                            selectedFieldNumber = uiState.selectedFieldNumber,
                            onSelectField       = onSelectField,
                            emptyLabel          = "Awaiting response…"
                        )
                    }
                    CenterTab.RAW -> {
                        val rawXml = buildString {
                            val req = tx.request
                            val resp = tx.response

                            if (req != null) {
                                append("<!-- ")
                                append(req.rawEvent.direction.name)
                                append(" REQUEST (")
                                append(req.mti.raw)
                                append(") -->\n")
                                append(req.rawEvent.rawXml)
                            }

                            if (req != null && resp != null) {
                                append("\n\n")
                            }

                            if (resp != null) {
                                append("<!-- ")
                                append(resp.rawEvent.direction.name)
                                append(" RESPONSE (")
                                append(resp.mti.raw)
                                append(") -->\n")
                                append(resp.rawEvent.rawXml)
                            }
                        }
                        RawXmlView(
                            rawXml   = rawXml,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ── Tab bar ────────────────────────────────────────────────────────────────

@Composable
private fun TabBar(
    selected: CenterTab,
    hasResponse: Boolean,
    onSelectTab: (CenterTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
    ) {
        InspectorTab(
            label    = "Request",
            selected = selected == CenterTab.REQUEST,
            onClick  = { onSelectTab(CenterTab.REQUEST) }
        )
        InspectorTab(
            label    = if (hasResponse) "Response" else "Response  ⏳",
            selected = selected == CenterTab.RESPONSE,
            onClick  = { onSelectTab(CenterTab.RESPONSE) }
        )
        InspectorTab(
            label    = "Raw XML",
            selected = selected == CenterTab.RAW,
            onClick  = { onSelectTab(CenterTab.RAW) }
        )
    }
}

@Composable
private fun InspectorTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val fg  = if (selected) AppColors.Blue else AppColors.TextSecondary
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = fg, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.height(4.dp))
            // Active underline
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .width(if (selected) 40.dp else 0.dp)
                    .background(AppColors.Blue, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ── Packet tab content ─────────────────────────────────────────────────────

@Composable
private fun PacketTabContent(
    packet: DecodedPacket?,
    selectedFieldNumber: Int?,
    onSelectField: (Int?) -> Unit,
    emptyLabel: String
) {
    if (packet == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyLabel, color = AppColors.TextSecondary, fontSize = 13.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // MTI card
        MtiCardView(
            mti      = packet.mti,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )

        // Field count / error info
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text     = "${packet.fields.size} fields",
                color    = AppColors.TextTertiary,
                fontSize = 10.sp
            )
            if (packet.parseErrors.isNotEmpty()) {
                Text(
                    text     = "⚠ ${packet.parseErrors.size} parse error(s)",
                    color    = AppColors.Amber,
                    fontSize = 10.sp
                )
            }
        }

        HorizontalDivider(color = AppColors.Border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))

        // Field list
        FieldListView(
            packet              = packet,
            selectedFieldNumber = selectedFieldNumber,
            onSelectField       = onSelectField,
            modifier            = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun NoSelectionPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔍", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Select a transaction to inspect",
                color    = AppColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Click any transaction in the timeline",
                color    = AppColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}
