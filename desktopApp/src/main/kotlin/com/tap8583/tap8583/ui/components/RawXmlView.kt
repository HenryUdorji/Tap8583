package com.tap8583.tap8583.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tap8583.tap8583.ui.AppColors

/**
 * Raw XML viewer for the "Raw" tab in the centre panel.
 *
 * Renders the raw jPOS `<visualizer-event>` XML with simple syntax colouring:
 *  - Tag names: purple
 *  - Attribute names: blue
 *  - Attribute values (in quotes): green
 *  - Angle brackets / punctuation: dim
 */
@Composable
fun RawXmlView(rawXml: String, modifier: Modifier = Modifier) {
    @Suppress("DEPRECATION")
    val clipboard       = LocalClipboardManager.current

    val hScroll         = rememberScrollState()
    val vScroll         = rememberScrollState()
    var copyConfirmed   by remember { mutableStateOf(false) }

    LaunchedEffect(rawXml) {
        // Scroll back to top when XML changes (new transaction selected)
        vScroll.scrollTo(0)
        hScroll.scrollTo(0)
    }

    Box(modifier = modifier.background(AppColors.Background)) {
        // ── Copy button ───────────────────────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(rawXml))
                    copyConfirmed = true
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy XML",
                    tint   = if (copyConfirmed) AppColors.Green else AppColors.TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // ── Scrollable XML content ────────────────────────────────────────────
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .padding(16.dp)
            ) {
                Text(
                    text       = syntaxHighlight(rawXml),
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }

        // Horizontal scrollbar
        HorizontalScrollbar(
            adapter  = rememberScrollbarAdapter(hScroll),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp)
        )
    }
}

// ── Syntax highlighter ─────────────────────────────────────────────────────

private val ColorPunct     = AppColors.TextTertiary
private val ColorTagName   = Color(0xFFBC8CFF)   // purple
private val ColorAttrName  = Color(0xFF79C0FF)   // light blue
private val ColorAttrValue = Color(0xFFA5D6FF)   // pale blue-green for quoted values
private val ColorString    = Color(0xFF96D0FF)

/**
 * Applies minimal XML syntax colouring using [AnnotatedString].
 *
 * The parser is intentionally simple (regex-style state machine) — it handles
 * the jPOS `<visualizer-event>` format reliably without a full XML parser.
 */
private fun syntaxHighlight(xml: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = xml.length

    while (i < n) {
        when {
            // Comment
            xml[i] == '<' && i + 3 < n && xml[i + 1] == '!' && xml[i + 2] == '-' && xml[i + 3] == '-' -> {
                val commentStart = i
                i += 4
                while (i + 2 < n && !(xml[i] == '-' && xml[i + 1] == '-' && xml[i + 2] == '>')) {
                    i++
                }
                if (i + 2 < n) i += 3 else i = n
                pushStyle(SpanStyle(color = ColorPunct, fontWeight = FontWeight.Normal))
                append(xml.substring(commentStart, i))
                pop()
            }

            // Opening tag
            xml[i] == '<' && i + 1 < n && xml[i + 1] != '/' -> {
                // Angle bracket
                pushStyle(SpanStyle(color = ColorPunct))
                append('<')
                pop(); i++

                // Tag name
                val tagStart = i
                while (i < n && xml[i] != ' ' && xml[i] != '>' && xml[i] != '/') i++
                pushStyle(SpanStyle(color = ColorTagName, fontWeight = FontWeight.SemiBold))
                append(xml.substring(tagStart, i))
                pop()

                // Attributes
                while (i < n && xml[i] != '>' && !(xml[i] == '/' && i + 1 < n && xml[i + 1] == '>')) {
                    if (xml[i] == ' ') {
                        pushStyle(SpanStyle(color = ColorPunct)); append(' '); pop(); i++
                    } else if (xml[i] == '"') {
                        // Quoted value
                        pushStyle(SpanStyle(color = ColorAttrValue))
                        append('"'); i++
                        while (i < n && xml[i] != '"') { append(xml[i]); i++ }
                        if (i < n) { append('"'); i++ }
                        pop()
                    } else if (xml[i] == '=') {
                        pushStyle(SpanStyle(color = ColorPunct)); append('='); pop(); i++
                    } else {
                        // Attribute name
                        val attrStart = i
                        while (i < n && xml[i] != '=' && xml[i] != ' ' && xml[i] != '>') i++
                        pushStyle(SpanStyle(color = ColorAttrName))
                        append(xml.substring(attrStart, i))
                        pop()
                    }
                }

                // Self-close or close bracket
                if (i < n) {
                    pushStyle(SpanStyle(color = ColorPunct))
                    if (xml[i] == '/') { append('/'); i++ }
                    if (i < n) { append(xml[i]); i++ }
                    pop()
                }
            }

            // Closing tag
            xml[i] == '<' && i + 1 < n && xml[i + 1] == '/' -> {
                pushStyle(SpanStyle(color = ColorPunct)); append("</"); pop(); i += 2
                val tagStart = i
                while (i < n && xml[i] != '>') i++
                pushStyle(SpanStyle(color = ColorTagName))
                append(xml.substring(tagStart, i))
                pop()
                if (i < n) {
                    pushStyle(SpanStyle(color = ColorPunct)); append('>'); pop(); i++
                }
            }

            // Everything else (text content, newlines)
            else -> {
                pushStyle(SpanStyle(color = AppColors.TextSecondary))
                append(xml[i])
                pop(); i++
            }
        }
    }
}
