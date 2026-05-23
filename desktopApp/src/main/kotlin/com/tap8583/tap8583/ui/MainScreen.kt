package com.tap8583.tap8583.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tap8583.tap8583.ui.components.*

/**
 * Root composable for the Tap8583 desktop application.
 *
 * Renders a three-panel layout:
 *  ┌─────────────────────────────────────────────────────┐
 *  │                     Toolbar                          │
 *  ├──────────────┬────────────────────────┬─────────────┤
 *  │  Transaction │   Packet Inspector     │   Field     │
 *  │  Timeline    │   (MTI + Field List)   │   Detail    │
 *  │  (left)      │   (centre, elastic)    │   (right)   │
 *  ├──────────────┴────────────────────────┴─────────────┤
 *  │                    Status Bar                        │
 *  └─────────────────────────────────────────────────────┘
 *
 * The two vertical dividers are draggable — the user can resize the panels.
 * Panel widths are tracked as fractions of the total available width.
 */
@Composable
fun MainScreen(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {

            Column(modifier = Modifier.fillMaxSize()) {

                // ── Toolbar ───────────────────────────────────────────────────
                Toolbar(
                    uiState        = uiState,
                    onStart        = viewModel::onStart,
                    onStop         = viewModel::onStop,
                    onClear        = viewModel::onClear,
                    onSearchChanged = viewModel::onSearchChanged,
                    onFilterChanged = viewModel::onFilterChanged
                )

                // ── Three-panel body ──────────────────────────────────────────
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val totalWidthDp = maxWidth   // already in Dp
                    val totalPx      = constraints.maxWidth.toFloat()   // captured for drag lambdas

                    // Panel fractions (of totalWidthDp) — clamped to sensible limits
                    var leftFraction  by remember { mutableStateOf(0.27f) }
                    var rightFraction by remember { mutableStateOf(0.27f) }

                    Row(modifier = Modifier.fillMaxSize()) {

                        // ── Left panel: Transaction timeline ───────────────────
                        TransactionTimeline(
                            transactions = uiState.transactions,
                            selectedId   = uiState.selectedTransactionId,
                            onSelect     = viewModel::onSelectTransaction,
                            modifier     = Modifier
                                .width((totalWidthDp * leftFraction).coerceAtLeast(180.dp))
                                .fillMaxHeight()
                        )

                        // ── Left divider ──────────────────────────────────────
                        ResizeDivider(
                            onDrag = { delta ->
                                leftFraction = (leftFraction + delta / totalPx).coerceIn(0.15f, 0.45f)
                            }
                        )

                        // ── Centre panel: Packet inspector ─────────────────────
                        PacketInspector(
                            uiState      = uiState,
                            onSelectTab  = viewModel::onSelectTab,
                            onSelectField = viewModel::onSelectField,
                            modifier     = Modifier.weight(1f).fillMaxHeight()
                        )

                        // ── Right divider ─────────────────────────────────────
                        ResizeDivider(
                            onDrag = { delta ->
                                rightFraction = (rightFraction - delta / totalPx).coerceIn(0.15f, 0.45f)
                            }
                        )

                        // ── Right panel: Field detail ──────────────────────────
                        FieldDetailPanel(
                            uiState  = uiState,
                            modifier = Modifier
                                .width((totalWidthDp * rightFraction).coerceAtLeast(180.dp))
                                .fillMaxHeight()
                        )
                    }
                }


                // ── Status bar ────────────────────────────────────────────────
                StatusBar(uiState = uiState)

            }

            // ── Toast overlay ─────────────────────────────────────────────────
            ToastHost(
                toasts    = uiState.toasts,
                onDismiss = viewModel::onDismissToast,
                modifier  = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.4f)
            )
        }
    }
}

// ── Resizable divider ──────────────────────────────────────────────────────

@Composable
private fun ResizeDivider(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .background(AppColors.Divider)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x)
                }
            }
    )
}
