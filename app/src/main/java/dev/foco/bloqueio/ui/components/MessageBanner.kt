package dev.foco.bloqueio.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avisos do app (sucesso, erro, "só o admin pode"). Ficam na tela até a pessoa tocar em OK
 * ou até o próximo aviso — nada de Toast com prazo (HIG › Accessibility › Cognitive).
 */
object UiMessages {
    data class Message(val text: String, val isError: Boolean)

    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()

    fun info(text: String) { _current.value = Message(text, isError = false) }
    fun error(text: String) { _current.value = Message(text, isError = true) }
    fun dismiss() { _current.value = null }
}

@Composable
fun MessageBanner(modifier: Modifier = Modifier) {
    val message by UiMessages.current.collectAsState()
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        val m = message ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Bloqueio.colors.cell)
                // O TalkBack lê o aviso assim que ele aparece.
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!m.isError) {
                DrawOnCheck(Bloqueio.colors.successText)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                m.text,
                style = BloqueioType.callout,
                color = if (m.isError) Bloqueio.colors.danger else Bloqueio.colors.label,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            Text(
                "OK",
                style = BloqueioType.headline,
                color = Bloqueio.colors.accent,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "Fechar aviso") { UiMessages.dismiss() }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Marca de confirmação que "se desenha" (como o Draw On dos SF Symbols). */
@Composable
private fun DrawOnCheck(color: androidx.compose.ui.graphics.Color) {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.size(22.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.74f)
            lineTo(size.width * 0.84f, size.height * 0.28f)
        }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val partial = androidx.compose.ui.graphics.Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(
            partial, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
