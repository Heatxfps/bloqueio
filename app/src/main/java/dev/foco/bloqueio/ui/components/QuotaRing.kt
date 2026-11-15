package dev.foco.bloqueio.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.LocalTilt

/**
 * Elemento-assinatura do app: anéis de cota (externo = redes, interno = jogos), progresso =
 * fração RESTANTE. No Android 13+ cada anel é um "tubo de vidro" iluminado por um shader AGSL
 * (o equivalente leve a um objeto 3D: sem motor 3D, sem peso no APK). A luz acompanha a
 * inclinação do aparelho. Abaixo do Android 13: cor sólida, como antes.
 */
@Composable
fun QuotaRing(socialLeft: Float, gamesLeft: Float, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val social = Bloqueio.colors.social
    val games = Bloqueio.colors.games
    val tilt = LocalTilt.current

    // Enchem ao aparecer e quando o valor muda (Remover animações → instantâneo).
    val s = remember { Animatable(0f) }
    val g = remember { Animatable(0f) }
    LaunchedEffect(socialLeft) {
        s.animateTo(socialLeft.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow))
    }
    LaunchedEffect(gamesLeft) {
        g.animateTo(gamesLeft.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow))
    }

    val shaders = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RingShader() to RingShader() else null
    }

    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.12f
        val light = tilt.value // lido no desenho: inclinar só redesenha
        fun DrawScope.ring(inset: Float, color: Color, fraction: Float, brush: (Offset, Float, Float) -> Brush) {
            val d = this.size.minDimension - inset * 2 - stroke
            val topLeft = Offset(inset + stroke / 2, inset + stroke / 2)
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            if (fraction > 0.002f) {
                drawArc(
                    brush(center, d / 2, stroke), -90f, 360f * fraction, false, topLeft, Size(d, d),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        ring(0f, social, s.value) { c, r, t ->
            shaders?.first?.brush(c, r, t, social, light) ?: SolidColor(social)
        }
        ring(stroke * 1.15f, games, g.value) { c, r, t ->
            shaders?.second?.brush(c, r, t, games, light) ?: SolidColor(games)
        }
    }
}

/** Tubo iluminado: normal calculada pela posição no anel, luz difusa + brilho especular. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RingShader {
    private val shader = RuntimeShader(
        """
        uniform float2 center;
        uniform float radius;
        uniform float thickness;
        uniform float4 color;
        uniform float2 light;

        half4 main(float2 p) {
            float2 d = p - center;
            float dist = length(d);
            float u = clamp((dist - radius) / (thickness * 0.5), -1.0, 1.0);
            float h = sqrt(max(0.0, 1.0 - u * u));
            float2 dir = dist > 0.0 ? d / dist : float2(0.0, 0.0);
            float3 n = normalize(float3(dir * u, h));
            float3 l = normalize(float3(-0.45 + light.x * 0.6, -0.65 + light.y * 0.6, 0.75));
            float diff = max(dot(n, l), 0.0);
            float3 hv = normalize(l + float3(0.0, 0.0, 1.0));
            float spec = pow(max(dot(n, hv), 0.0), 36.0);
            float3 c = color.rgb * (0.62 + 0.48 * diff) + float3(spec * 0.75);
            return half4(half3(c * color.a), half(color.a));
        }
        """.trimIndent(),
    )

    fun brush(center: Offset, radius: Float, thickness: Float, color: Color, light: Offset): Brush {
        shader.setFloatUniform("center", center.x, center.y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("thickness", thickness)
        shader.setFloatUniform("color", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("light", light.x, light.y)
        return ShaderBrush(shader)
    }
}
