package com.tap8583.tap8583.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.ui.AppColors
import com.tap8583.tap8583.ui.ToastMessage

/**
 * Slide-up toast notification overlay.
 *
 * Renders on top of all other content (use in a [Box] as last child).
 * Each toast slides up on entry and fades out on removal.
 */
@Composable
fun ToastHost(
    toasts: List<ToastMessage>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier               = modifier.padding(bottom = 24.dp, end = 16.dp),
        verticalArrangement    = Arrangement.spacedBy(8.dp),
        horizontalAlignment    = Alignment.End
    ) {
        toasts.forEach { toast ->
            key(toast.id) {
                AnimatedVisibility(
                    visible = true,
                    enter   = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                    exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
                ) {
                    ToastItem(toast = toast, onDismiss = { onDismiss(toast.id) })
                }
            }
        }
    }
}

@Composable
private fun ToastItem(toast: ToastMessage, onDismiss: () -> Unit) {
    val bg     = if (toast.isError) AppColors.RedDim else AppColors.SurfaceVariant
    val border = if (toast.isError) AppColors.Red.copy(alpha = 0.4f) else AppColors.Border

    Row(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 400.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text       = if (toast.isError) "⚠" else "ℹ",
            fontSize   = 13.sp
        )
        Text(
            text       = toast.message,
            color      = if (toast.isError) AppColors.Red else AppColors.TextPrimary,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint     = AppColors.TextTertiary,
            modifier = Modifier.size(12.dp).clickable(onClick = onDismiss)
        )
    }
}
