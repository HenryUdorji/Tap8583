package com.tap8583.tap8583.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.ingestion.ServerStatus
import com.tap8583.tap8583.ui.*

@Composable
fun Toolbar(
    uiState: AppUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (MessageFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .border(width = 1.dp, color = AppColors.Border, shape = RoundedCornerShape(0.dp))
    ) {
        // ── Top row: controls + status pill + search ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App icon / brand
            Text(
                text = "TAP8583",
                color = AppColors.Blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.width(6.dp))

            // ── Server controls ───────────────────────────────────────────────
            val isRunning = uiState.serverStatus in listOf(ServerStatus.LISTENING, ServerStatus.CONNECTED)

            ToolbarButton(
                icon    = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                label   = if (isRunning) "Stop" else "Start",
                tint    = if (isRunning) AppColors.Red else AppColors.Green,
                onClick = if (isRunning) onStop else onStart
            )

            ToolbarButton(
                icon    = Icons.Default.DeleteOutline,
                label   = "Clear",
                tint    = AppColors.TextSecondary,
                onClick = onClear
            )

            Spacer(Modifier.width(4.dp))

            // ── Status pill ───────────────────────────────────────────────────
            StatusPill(uiState)

            Spacer(Modifier.weight(1f))

            // ── Search box ────────────────────────────────────────────────────
            SearchBox(
                value     = uiState.searchQuery,
                onChange  = onSearchChanged,
                modifier  = Modifier.width(260.dp)
            )

            // ── Stats summary ─────────────────────────────────────────────────
            Spacer(Modifier.width(8.dp))
            StatBadge("✅", uiState.stats.approved.toString(),  AppColors.Green)
            Spacer(Modifier.width(4.dp))
            StatBadge("❌", uiState.stats.declined.toString(),  AppColors.Red)
            Spacer(Modifier.width(4.dp))
            StatBadge("📦", uiState.stats.totalTransactions.toString(), AppColors.TextSecondary)
        }

        HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

        // ── Bottom row: filter chips ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MessageFilter.entries.forEach { filter ->
                FilterChip(
                    filter   = filter,
                    selected = uiState.messageFilter == filter,
                    onClick  = { onFilterChanged(filter) }
                )
            }
        }
    }
}

// ── Toolbar action button ──────────────────────────────────────────────────

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(AppColors.SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Status pill ────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(uiState: AppUiState) {
    val (color, label) = when (uiState.serverStatus) {
        ServerStatus.IDLE       -> AppColors.StatusIdle     to "IDLE"
        ServerStatus.LISTENING  -> AppColors.StatusListening to "WAITING"
        ServerStatus.CONNECTED  -> AppColors.StatusConnected to "${uiState.connectionCount} CONNECTED"
        ServerStatus.ERROR      -> AppColors.StatusError    to "ERROR"
    }

    val animatedColor by animateColorAsState(color, tween(400))

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedColor.copy(alpha = 0.18f))
            .border(1.dp, animatedColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Animated status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(animatedColor)
        )
        Text(
            text       = label,
            color      = animatedColor,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        if (uiState.serverStatus != ServerStatus.IDLE) {
            Text(
                text     = "port ${uiState.port}",
                color    = animatedColor.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}

// ── Search box ─────────────────────────────────────────────────────────────

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.SurfaceVariant)
            .border(1.dp, AppColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Search, null, tint = AppColors.TextTertiary, modifier = Modifier.size(14.dp))
        BasicTextField(
            value         = value,
            onValueChange = onChange,
            singleLine    = true,
            textStyle     = TextStyle(color = AppColors.TextPrimary, fontSize = 12.sp),
            cursorBrush   = SolidColor(AppColors.Blue),
            modifier      = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("Search STAN, PAN, amount…", color = AppColors.TextTertiary, fontSize = 12.sp)
                }
                inner()
            }
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                null,
                tint     = AppColors.TextTertiary,
                modifier = Modifier.size(12.dp).clickable { onChange("") }
            )
        }
    }
}

// ── Filter chip ────────────────────────────────────────────────────────────

@Composable
private fun FilterChip(filter: MessageFilter, selected: Boolean, onClick: () -> Unit) {
    val bg    = if (selected) AppColors.Blue.copy(alpha = 0.2f) else Color.Transparent
    val fg    = if (selected) AppColors.Blue else AppColors.TextSecondary
    val brd   = if (selected) AppColors.Blue.copy(alpha = 0.5f) else AppColors.Border

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, brd, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(filter.label, color = fg, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Stat badge ─────────────────────────────────────────────────────────────

@Composable
private fun StatBadge(emoji: String, count: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 11.sp)
        Text(count, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
