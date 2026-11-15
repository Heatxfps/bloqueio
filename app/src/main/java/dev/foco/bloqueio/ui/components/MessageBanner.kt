package dev.foco.bloqueio.ui.components

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
