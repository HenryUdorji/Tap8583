package com.tap8583.tap8583.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.ingestion.TransactionStatus
import com.tap8583.tap8583.ui.AppColors

/**
 * A single transaction row in the left-panel timeline list.
 *
 * Displays:
 * - Left accent strip coloured by transaction status
 * - MTI label (or "Pending…")
 * - Masked PAN + amount from DE2 / DE4
 * - STAN (DE11) as secondary info
 * - RTT badge and response code badge
 */
@Composable
fun TransactionCard(
    transaction: DecodedTransaction,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = transaction.statusColor()
    val bgColor     by animateColorAsState(
        if (isSelected) AppColors.SurfaceVariant else Color.Transparent,
        tween(200)
    )
    val borderColor by animateColorAsState(
        if (isSelected) AppColors.Blue.copy(alpha = 0.5f) else Color.Transparent,
        tween(200)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left accent strip ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(56.dp)
                .background(statusColor, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        )

        Spacer(Modifier.width(10.dp))

        // ── Content ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {

            // Row 1: MTI label + optional response code badge
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val mtiLabel = transaction.request?.mti?.label
                    ?: transaction.response?.mti?.label
                    ?: "Unknown"

                Text(
                    text       = mtiLabel,
                    color      = AppColors.TextPrimary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1
                )

                // Response code badge
                val rc = transaction.response?.fields?.find { it.number == 39 }?.rawValue
                if (rc != null) {
                    ResponseCodeBadge(rc = rc, statusColor = statusColor)
                } else if (transaction.status == TransactionStatus.PENDING) {
                    PendingIndicator()
                }
            }

            Spacer(Modifier.height(2.dp))

            // Row 2: Masked PAN + amount
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pan = transaction.request?.fields?.find { it.number == 2 }?.rawValue
                val maskedPan = pan?.let { maskPan(it) } ?: "—"

                Text(
                    text     = maskedPan,
                    color    = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                val amount = transaction.request
                    ?.fields?.find { it.number == 4 }
                    ?.decodedValue
                    ?.split("  ")?.firstOrNull()
                    ?: transaction.request?.fields?.find { it.number == 4 }?.rawValue?.let {
                        it.toLongOrNull()?.let { raw -> "%.2f".format(raw / 100.0) }
                    }

                if (amount != null) {
                    Text(amount, color = AppColors.Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(2.dp))

            // Row 3: STAN + RTT
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text     = "STAN ${transaction.stan}",
                    color    = AppColors.TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                val rtt = transaction.rttMs
                if (rtt != null) {
                    val rttColor = when {
                        rtt < 1_000  -> AppColors.Green
                        rtt < 3_000  -> AppColors.Amber
                        else         -> AppColors.Red
                    }
                    Text(
                        text     = "${rtt}ms",
                        color    = rttColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun ResponseCodeBadge(rc: String, statusColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(statusColor.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text       = rc,
            color      = statusColor,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PendingIndicator() {
    Text(
        text     = "⏳",
        fontSize = 12.sp
    )
}

private fun DecodedTransaction.statusColor(): Color = when (status) {
    TransactionStatus.APPROVED  -> AppColors.Green
    TransactionStatus.DECLINED  -> AppColors.Red
    TransactionStatus.PENDING   -> AppColors.Amber
    TransactionStatus.TIMEOUT   -> AppColors.Slate
    else                        -> AppColors.TextTertiary
}

private fun maskPan(pan: String): String {
    if (pan.length < 8) return "••••  ••••  ••••  ••••"
    val last4 = pan.takeLast(4)
    return "••••  ••••  ••••  $last4"
}
