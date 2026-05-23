package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.ui.AppColors

/**
 * Left panel — scrollable list of transaction cards in arrival order (newest at bottom).
 *
 * Auto-scrolls to the latest transaction when a new one arrives and the list
 * is already at the bottom (or no selection is active).
 */
@Composable
fun TransactionTimeline(
    transactions: List<DecodedTransaction>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState       = rememberLazyListState()
    val previousCount   = remember { mutableStateOf(0) }

    // Auto-scroll when new transactions arrive
    LaunchedEffect(transactions.size) {
        if (transactions.size > previousCount.value && selectedId == null) {
            listState.animateScrollToItem(transactions.lastIndex.coerceAtLeast(0))
        }
        previousCount.value = transactions.size
    }

    Column(modifier = modifier.background(AppColors.Background)) {

        // ── Panel header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = "TRANSACTIONS",
                color      = AppColors.TextSecondary,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                text     = "${transactions.size}",
                color    = AppColors.TextTertiary,
                fontSize = 10.sp
            )
        }

        HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

        // ── Transaction list ──────────────────────────────────────────────────
        if (transactions.isEmpty()) {
            EmptyTimeline()
        } else {
            LazyColumn(
                state           = listState,
                modifier        = Modifier.fillMaxSize(),
                contentPadding  = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = transactions,
                    key   = { it.id }
                ) { tx ->
                    TransactionCard(
                        transaction = tx,
                        isSelected  = tx.id == selectedId,
                        onClick     = {
                            onSelect(if (tx.id == selectedId) null else tx.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📡", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Waiting for transactions…",
                color    = AppColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Start the server and connect your POS device",
                color    = AppColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}
