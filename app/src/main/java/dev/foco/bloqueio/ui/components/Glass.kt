package dev.foco.bloqueio.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.LocalGlassEnabled

/**
 * Liquid Glass da camada funcional (HIG › Materials › Liquid Glass): o conteúdo por baixo é
 * **refratado** nas bordas (lente), desfocado e com cor reforçada (vibrancy); um brilho
 * especular contorna a forma. Só para barras e controles flutuantes — nunca em cards.
 *
 * Sem vidro (Android 12-, ou "Texto de alto contraste" ligado) vira superfície opaca.
 * A lente exige Android 13+; no 12 fica só o desfoque.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    /** Tinta por cima do vidro. null = vidro "regular" neutro. */
    tint: Color? = null,
    /** Bolha pequena (ex.: seleção da aba): mais refração, sem desfoque e sem sombra. */
    bubble: Boolean = false,
): Modifier {
    val c = Bloqueio.colors
    val dark = c.background == Color.Black
    if (!LocalGlassEnabled.current) {
        return this.clip(shape).background(tint ?: c.cell)
    }
    val surface = tint ?: if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.42f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (!bubble) blur(6.dp.toPx())
            lens(
                refractionHeight = (if (bubble) 10.dp else 18.dp).toPx(),
                refractionAmount = (if (bubble) 22.dp else 32.dp).toPx(),
                depthEffect = true,
                chromaticAberration = bubble,
            )
        },
        highlight = { Highlight.Default },
        shadow = if (bubble) null else ({ Shadow(radius = 20.dp, color = Color.Black.copy(alpha = if (dark) 0.4f else 0.12f)) }),
        onDrawSurface = { drawRect(surface) },
    )
}
