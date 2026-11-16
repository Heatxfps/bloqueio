package dev.foco.bloqueio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.em
import dev.foco.bloqueio.R
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
    /** Preenchimento (switch, anéis). Para TEXTO use [successText]. */
    val success: Color,
    val successText: Color,
    val warning: Color,
    val warningText: Color,
    val danger: Color,
    val focus: Color,
    val gray: Color,
    /** Fundos de botão com texto branco (≥4.5:1 nos dois temas). */
    val accentFill: Color,
    val focusFill: Color,
    val grayFill: Color,
    /** Texto de botão tingido (fundo = cor-base a 10% no claro / 18% no escuro), ≥ 4,5:1. */
    val accentTintText: Color,
    val focusTintText: Color,
    val dangerTintText: Color,
)

val LightColors = BloqueioColors(
    background = Color(0xFFF2F2F7),
    cell = Color.White,
    cellSecondary = Color(0xFFF2F2F7),
    label = Color.Black,
    secondaryLabel = Color(0xFF6C6C70), // 4.69:1 sobre #F2F2F7 (antes 3.29:1)
    separator = Color(0x4A3C3C43),
    accent = Color(0xFF0066D6), // 5.42:1 com branco (antes 4.02:1)
    social = Color(0xFFFF2D55),
    games = Color(0xFF5856D6),
    success = Color(0xFF34C759),
    successText = Color(0xFF1A7F37), // 5.08:1 sobre branco
    warning = Color(0xFFFF9500),
    warningText = Color(0xFFA35200), // 5.00:1 sobre #F2F2F7
    danger = Color(0xFFC42B1C), // 5.66:1 sobre branco (antes 3.55:1)
    focus = Color(0xFF8E3FC2), // 5.77:1 com branco (antes 4.13:1)
    gray = Color(0xFF636366), // 5.99:1 (antes 3.26:1)
    accentFill = Color(0xFF0066D6),
    focusFill = Color(0xFF8E3FC2),
    grayFill = Color(0xFF636366),
    accentTintText = Color(0xFF0066D6), // 4,71:1
    focusTintText = Color(0xFF8E3FC2),
    dangerTintText = Color(0xFFC42B1C),
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
    successText = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    warningText = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    focus = Color(0xFFBF5AF2),
    gray = Color(0xFF8E8E93),
    accentFill = Color(0xFF0071E3), // 4.70:1 com branco
    focusFill = Color(0xFFA050D0), // 4.61:1 com branco
    grayFill = Color(0xFF636366), // 5.99:1 com branco
    accentTintText = Color(0xFF6CB4FF), // 6,26:1
    focusTintText = Color(0xFFD9A0FF), // 6,73:1
    dangerTintText = Color(0xFFFF8A80), // 6,06:1
)

/**
 * Inter (licença OFL, docs/licencas) no lugar da SF, que só pode ser usada em aparelhos Apple.
 * Duas famílias pelo eixo óptico "opsz", como a SF Text/Display: textos (14) e títulos (32).
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(opsz: Float) = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(
            R.font.inter_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.Setting("opsz", opsz),
            ),
        )
    },
)

val InterText = inter(14f)
val InterDisplay = inter(32f)

/**
 * Escala do iOS com espaçamento por tamanho (a SF aperta as letras nos tamanhos grandes;
 * os valores em em são o equivalente para a Inter). Números de largura fixa ("tnum") onde
 * há contagem, para os dígitos não "dançarem".
 */
object BloqueioType {
    val largeTitle = TextStyle(fontFamily = InterDisplay, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em)
    val title2 = TextStyle(fontFamily = InterDisplay, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.017).em, fontFeatureSettings = "tnum")
    val headline = TextStyle(fontFamily = InterText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em)
    val body = TextStyle(fontFamily = InterText, fontSize = 17.sp, letterSpacing = (-0.011).em)
    val callout = TextStyle(fontFamily = InterText, fontSize = 16.sp, letterSpacing = (-0.009).em)
    val footnote = TextStyle(fontFamily = InterText, fontSize = 13.sp, letterSpacing = (-0.003).em)
    val caption = TextStyle(fontFamily = InterText, fontSize = 12.sp)
    /** Cronômetro e relógio grandes: display leve com números de largura fixa. */
    val timer = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Light, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")
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

/** "Texto de alto contraste" do Android: reforça texto secundário e divisórias (HIG › Color). */
private fun BloqueioColors.highContrast(dark: Boolean) = copy(
    secondaryLabel = if (dark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C),
    separator = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
)

@Composable
fun BloqueioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val highContrast = remember { isHighContrast(context) }
    val reduceMotion = remember { isReduceMotion(context) }
    val glass = remember { isGlassAllowed(context) }
    val tilt = rememberTilt(enabled = !reduceMotion)
    val base = if (dark) DarkColors else LightColors
    val c = if (highContrast) base.highContrast(dark) else base
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, background = c.background, surface = c.cell, onSurface = c.label)
    } else {
        lightColorScheme(primary = c.accent, background = c.background, surface = c.cell, onSurface = c.label)
    }
    CompositionLocalProvider(
        LocalColors provides c,
        LocalReduceMotion provides reduceMotion,
        LocalGlassEnabled provides glass,
        LocalTilt provides tilt,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography().let { t ->
                t.copy(
                    bodyLarge = t.bodyLarge.copy(fontFamily = InterText),
                    bodyMedium = t.bodyMedium.copy(fontFamily = InterText),
                    labelLarge = t.labelLarge.copy(fontFamily = InterText),
                )
            },
            content = content,
        )
    }
}
