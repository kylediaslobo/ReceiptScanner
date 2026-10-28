package com.kyled.receiptscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Design B, "paper receipt": a warm desk-coloured background, white paper, near-black ink.
private val Desk = Color(0xFFEFEBE3)
private val DeskDark = Color(0xFF151513)
private val Paper = Color(0xFFFFFDF8)
private val PaperDark = Color(0xFF22211E)
private val Ink = Color(0xFF1F1E1B)
private val InkDark = Color(0xFFECEAE3)
private val InkMuted = Color(0xFF6B6A64)
private val InkMutedDark = Color(0xFFA3A199)
private val Rule = Color(0xFFB4B2A9)
private val RuleDark = Color(0xFF55544F)
private val Accent = Color(0xFF185FA5)
private val AccentDark = Color(0xFF85B7EB)

/** The green Save button. Same in light and dark so it always reads as "save". */
val SaveGreen = Color(0xFF3B6D11)
val OnSaveGreen = Color(0xFFFFFFFF)

@Immutable
data class PaperColors(
    val paper: Color,
    val ink: Color,
    val inkMuted: Color,
    val rule: Color,
    val accent: Color,
)

val LocalPaperColors = staticCompositionLocalOf {
    PaperColors(Paper, Ink, InkMuted, Rule, Accent)
}

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Accent,
    onSecondary = Color.White,
    background = Desk,
    onBackground = Ink,
    surface = Desk,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    primaryContainer = Color(0xFFE2DED4),
    onPrimaryContainer = Ink,
    outline = Rule,
    outlineVariant = Color(0xFFD3D1C7),
    error = Color(0xFFA32D2D),
)

private val DarkScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = PaperDark,
    secondary = AccentDark,
    onSecondary = Color.Black,
    background = DeskDark,
    onBackground = InkDark,
    surface = DeskDark,
    onSurface = InkDark,
    surfaceVariant = PaperDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainer = PaperDark,
    surfaceContainerHigh = PaperDark,
    primaryContainer = Color(0xFF2E2D29),
    onPrimaryContainer = InkDark,
    outline = RuleDark,
    outlineVariant = Color(0xFF3A3935),
    error = Color(0xFFF09595),
)

/** Monospace styles used on the paper receipt itself. */
object ReceiptType {
    val body = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
    val heading = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    val total = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun ReceiptScannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val paperColors = if (darkTheme) {
        PaperColors(PaperDark, InkDark, InkMutedDark, RuleDark, AccentDark)
    } else {
        PaperColors(Paper, Ink, InkMuted, Rule, Accent)
    }
    CompositionLocalProvider(LocalPaperColors provides paperColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography(),
            content = content,
        )
    }
}
