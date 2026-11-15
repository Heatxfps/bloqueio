package dev.foco.bloqueio.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Sinais de acessibilidade e de material (docs/DESIGN.md › Movimento e vidro).
 *
 * - [LocalReduceMotion]: "Remover animações" do Android (escala de animação = 0). As animações
 *   do Compose já respeitam essa escala; aqui desligamos o que não é animação do Compose
 *   (paralaxe pelo giroscópio).
 * - [LocalGlassEnabled]: vidro só no Android 12+ e sem "Texto de alto contraste" (o Android não
 *   tem "reduzir transparência"; o alto contraste é o sinal, como recomenda a HIG › Liquid Glass).
 * - [LocalTilt]: inclinação do aparelho em [-1, 1], relativa à posição de repouso.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

/** Espaço que as telas reservam no fim para o conteúdo não ficar sob a barra de abas flutuante. */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

fun isReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

fun isHighContrast(context: Context): Boolean =
    Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1

fun isGlassAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isHighContrast(context)

/**
 * Inclinação suave do aparelho (sensor de gravidade). Só escuta enquanto a tela está em
 * primeiro plano; parada quando [enabled] é falso (Remover animações).
 */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    LifecycleResumeEffect(enabled) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var baseX = Float.NaN
        var baseY = Float.NaN
        var x = 0f
        var y = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0] / SensorManager.GRAVITY_EARTH
                val gy = e.values[1] / SensorManager.GRAVITY_EARTH
                if (baseX.isNaN()) { baseX = gx; baseY = gy }
                // A base acompanha devagar: a paralaxe reage ao gesto e volta ao repouso.
                baseX += (gx - baseX) * 0.02f
                baseY += (gy - baseY) * 0.02f
                x += (((gx - baseX) * 4f).coerceIn(-1f, 1f) - x) * 0.25f
                y += (((gy - baseY) * 4f).coerceIn(-1f, 1f) - y) * 0.25f
                state.value = Offset(x, y)
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose {
            sm?.unregisterListener(listener)
            state.value = Offset.Zero
        }
    }
    return state
}

/** Leve "afundar" ao pressionar (0,97), com mola. Respeita Remover animações via Compose. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
