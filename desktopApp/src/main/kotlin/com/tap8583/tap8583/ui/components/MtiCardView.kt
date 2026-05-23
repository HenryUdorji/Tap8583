package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.MTI
import com.tap8583.tap8583.ui.AppColors

/**
 * MTI summary card shown at the top of the centre panel.
 *
 * Displays:
 * - Raw 4-digit MTI in monospace
 * - Full human-readable label
 * - Direction badge (REQUEST / RESPONSE)
 * - Version / Class / Function / Origin chips in a horizontal row
 */
@Composable
fun MtiCardView(mti: MTI, modifier: Modifier = Modifier) {
    val isRequest  = mti.raw.getOrNull(2) in listOf('0', '2', '4')
    val dirColor   = if (isRequest) AppColors.Blue else AppColors.Green
    val dirLabel   = if (isRequest) "↑  REQUEST" else "↓  RESPONSE"
    val gradEnd    = if (isRequest) AppColors.BlueDim else AppColors.GreenDim

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(listOf(AppColors.SurfaceVariant, gradEnd))
            )
            .border(1.dp, dirColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // ── Row 1: MTI + direction badge ─────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment  = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text       = mti.raw,
                        color      = dirColor,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text       = mti.label,
                        color      = AppColors.TextPrimary,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Direction badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(dirColor.copy(alpha = 0.18f))
                        .border(1.dp, dirColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = dirLabel,
                        color      = dirColor,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // ── Row 2: breakdown chips ───────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MtiChip("Version",  mti.version)
                MtiChip("Class",    mti.messageClass)
                MtiChip("Function", mti.messageFunction)
                MtiChip("Origin",   mti.messageOrigin)
            }
        }
    }
}

@Composable
private fun MtiChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Background.copy(alpha = 0.6f))
            .border(1.dp, AppColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text       = label,
            color      = AppColors.TextTertiary,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Text(
            text     = value,
            color    = AppColors.TextSecondary,
            fontSize = 10.sp
        )
    }
}
