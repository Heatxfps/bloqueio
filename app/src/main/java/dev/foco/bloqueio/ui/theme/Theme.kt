package dev.foco.bloqueio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens de cor — tabela em docs/DESIGN.md. */
@Immutable
data class BloqueioColors(
    val background: Color,
    val cell: Color,
    val cellSecondary: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val social: Color,
    val games: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val focus: Color,
    val gray: Color,
)

val LightColors = BloqueioColors(
    background = Color(0xFFF2F2F7),
    cell = Color.White,
    cellSecondary = Color(0xFFF2F2F7),
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    separator = Color(0x4A3C3C43),
    accent = Color(0xFF007AFF),
    social = Color(0xFFFF2D55),
    games = Color(0xFF5856D6),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    danger = Color(0xFFFF3B30),
    focus = Color(0xFFAF52DE),
    gray = Color(0xFF8E8E93),
)

val DarkColors = BloqueioColors(
    background = Color.Black,
    cell = Color(0xFF1C1C1E),
    cellSecondary = Color(0xFF2C2C2E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    separator = Color(0x99545458),
    accent = Color(0xFF0A84FF),
    social = Color(0xFFFF375F),
    games = Color(0xFF5E5CE6),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    focus = Color(0xFFBF5AF2),
    gray = Color(0xFF8E8E93),
)

/** Escala tipográfica do iOS. */
object BloqueioType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp)
    val callout = TextStyle(fontSize = 16.sp)
    val footnote = TextStyle(fontSize = 13.sp)
    val caption = TextStyle(fontSize = 12.sp, letterSpacing = 0.4.sp)
}

object Spacing {
    val screen = 16.dp
    val section = 32.dp
    val cellMinHeight = 52.dp
}

object Radius {
    val cell = 12.dp
    val card = 20.dp
    val button = 14.dp
}

val LocalColors = staticCompositionLocalOf { LightColors }

object Bloqueio {
    val colors: BloqueioColors @Composable get() = LocalColors.current
}

@Composable
fun BloqueioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, background = c.background, surface = c.cell, onSurface = c.label)
    } else {
        lightColorScheme(primary = c.accent, background = c.background, surface = c.cell, onSurface = c.label)
    }
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
