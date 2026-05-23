package com.tap8583.tap8583.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Tap8583 dark design system.
 *
 * Palette inspired by JetBrains IDE + GitHub dark mode.
 * All UI components reference these tokens — never raw hex literals.
 */
object AppColors {

    // ── Backgrounds ──────────────────────────────────────────────────────────
    val Background           = Color(0xFF0D1117)   // Darkest — window background
    val Surface              = Color(0xFF161B22)   // Panel backgrounds
    val SurfaceVariant       = Color(0xFF21262D)   // Cards, selected items
    val SurfaceElevated      = Color(0xFF2D333B)   // Hovered rows, tooltips

    // ── Borders ───────────────────────────────────────────────────────────────
    val Border               = Color(0xFF30363D)
    val BorderSubtle         = Color(0xFF21262D)
    val Divider              = Color(0xFF30363D)

    // ── Text ─────────────────────────────────────────────────────────────────
    val TextPrimary          = Color(0xFFF0F6FC)
    val TextSecondary        = Color(0xFF8B949E)
    val TextTertiary         = Color(0xFF484F58)
    val TextOnAccent         = Color(0xFFFFFFFF)

    // ── Accent — status colours ───────────────────────────────────────────────
    val Green                = Color(0xFF3FB950)   // Approved
    val GreenDim             = Color(0xFF1A3E27)   // Approved row bg
    val Red                  = Color(0xFFF85149)   // Declined / error
    val RedDim               = Color(0xFF3E1A1A)   // Declined row bg
    val Amber                = Color(0xFFE3B341)   // Warning / pending
    val AmberDim             = Color(0xFF3E3018)   // Pending row bg
    val Blue                 = Color(0xFF58A6FF)   // Info / links
    val BlueDim              = Color(0xFF1A2E40)
    val Purple               = Color(0xFFBC8CFF)   // Auth
    val PurpleDim            = Color(0xFF2D1A3E)
    val Teal                 = Color(0xFF3FBBA6)   // Reversal
    val TealDim              = Color(0xFF1A3A38)
    val Slate                = Color(0xFF6E7681)   // Network mgmt

    // ── Field category accent strips ──────────────────────────────────────────
    val CatCardData          = Color(0xFFF85149)   // DE2, DE35, DE45
    val CatAmount            = Color(0xFF3FB950)   // DE4, DE5, DE6
    val CatDatetime          = Color(0xFF58A6FF)   // DE7, DE12, DE13, DE14
    val CatIdentification    = Color(0xFFBC8CFF)   // DE11, DE37, DE38
    val CatResponse          = Color(0xFFE3B341)   // DE39
    val CatPosTerminal       = Color(0xFF3FBBA6)   // DE22, DE25, DE41, DE42
    val CatEmv               = Color(0xFFFF9A3C)   // DE55
    val CatInstitution       = Color(0xFF7B8FFF)   // DE32, DE33
    val CatSecurity          = Color(0xFFDC143C)   // DE52, DE64, DE128
    val CatPrivateNibss      = Color(0xFF8B949E)   // DE59–DE95, DE123
    val CatNetwork           = Color(0xFF708090)   // DE70
    val CatGeneral           = Color(0xFF484F58)   // everything else

    // ── Server status colours ─────────────────────────────────────────────────
    val StatusIdle           = Color(0xFF484F58)
    val StatusListening      = Color(0xFFE3B341)
    val StatusConnected      = Color(0xFF3FB950)
    val StatusError          = Color(0xFFF85149)
}

private val darkColorScheme = darkColorScheme(
    primary            = AppColors.Blue,
    onPrimary          = AppColors.TextOnAccent,
    primaryContainer   = AppColors.BlueDim,
    secondary          = AppColors.Purple,
    onSecondary        = AppColors.TextOnAccent,
    background         = AppColors.Background,
    onBackground       = AppColors.TextPrimary,
    surface            = AppColors.Surface,
    onSurface          = AppColors.TextPrimary,
    surfaceVariant     = AppColors.SurfaceVariant,
    onSurfaceVariant   = AppColors.TextSecondary,
    outline            = AppColors.Border,
    error              = AppColors.Red,
    onError            = AppColors.TextOnAccent,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme,
        content     = content
    )
}
