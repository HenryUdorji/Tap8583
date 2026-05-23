package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.ingestion.ServerStatus
import com.tap8583.tap8583.ui.AppColors
import com.tap8583.tap8583.ui.AppUiState

/**
 * Status bar — fixed footer showing connection info, session stats, and timing.
 */
@Composable
fun StatusBar(uiState: AppUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppColors.Border, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Server status
            val (statusColor, statusText) = when (uiState.serverStatus) {
                ServerStatus.IDLE      -> AppColors.StatusIdle      to "● Idle"
                ServerStatus.LISTENING -> AppColors.StatusListening to "● Listening"
                ServerStatus.CONNECTED -> AppColors.StatusConnected to "● Connected"
                ServerStatus.ERROR     -> AppColors.StatusError     to "● Error"
            }
            Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)

            StatusDivider()

            // Port
            Text(
                "Port  ${uiState.port}",
                color      = AppColors.TextTertiary,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            // Local IP addresses (shown when server is active so POS dev can see what to enter)
            if (uiState.localIpAddresses.isNotEmpty() &&
                uiState.serverStatus != ServerStatus.IDLE) {
                StatusDivider()
                Text(
                    "Connect to:",
                    color    = AppColors.TextTertiary,
                    fontSize = 10.sp
                )
                uiState.localIpAddresses.forEach { ip ->
                    Text(
                        "$ip:${uiState.port}",
                        color      = AppColors.Green,
                        fontSize   = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            StatusDivider()

            // Connections
            if (uiState.connectionCount > 0) {
                Text(
                    "${uiState.connectionCount} POS device${if (uiState.connectionCount != 1) "s" else ""}",
                    color    = AppColors.TextSecondary,
                    fontSize = 10.sp
                )
                StatusDivider()
            }

            // Session stats
            Text(
                "✅ ${uiState.stats.approved}  ❌ ${uiState.stats.declined}  📦 ${uiState.stats.totalTransactions}",
                color    = AppColors.TextSecondary,
                fontSize = 10.sp
            )

            // Average RTT
            uiState.stats.avgRttMs?.let { rtt ->
                StatusDivider()
                val rttColor = when {
                    rtt < 1_000 -> AppColors.Green
                    rtt < 3_000 -> AppColors.Amber
                    else        -> AppColors.Red
                }
                Text("avg RTT  ${rtt}ms", color = rttColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(Modifier.weight(1f))

            // Selected transaction STAN
            uiState.selectedTransaction?.let { tx ->
                Text(
                    "STAN ${tx.stan}",
                    color      = AppColors.TextTertiary,
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                StatusDivider()
                Text(
                    tx.request?.mti?.label ?: tx.response?.mti?.label ?: "",
                    color    = AppColors.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}


@Composable
private fun StatusDivider() {
    Text("│", color = AppColors.Border, fontSize = 10.sp)
}
