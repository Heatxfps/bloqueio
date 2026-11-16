package dev.foco.bloqueio.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.pressScale

/** Família de cor de um botão tingido. */
enum class Tint { Accent, Focus, Danger }

@Composable
private fun Tint.base(): Color = when (this) {
    Tint.Accent -> Bloqueio.colors.accent
    Tint.Focus -> Bloqueio.colors.focus
    Tint.Danger -> Bloqueio.colors.danger
}

@Composable
private fun Tint.text(): Color = when (this) {
    Tint.Accent -> Bloqueio.colors.accentTintText
    Tint.Focus -> Bloqueio.colors.focusTintText
    Tint.Danger -> Bloqueio.colors.dangerTintText
}

/**
 * Botão secundário tingido (cápsula com a cor a 10–18%), estilo iOS "tinted".
 * HIG › Buttons: o estilo, não o tamanho, diferencia a ação preferida — por isso a principal é
 * [PrimaryButton] (preenchida) e as demais usam este. Destrutivo = [Tint.Danger], nunca principal.
 */
@Composable
fun TintedButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Tint = Tint.Accent,
    icon: ImageVector? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val dark = Bloqueio.colors.background == Color.Black
    val fg = tint.text()
    Row(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .pressScale(source)
            .clip(CircleShape)
            .background(tint.base().copy(alpha = if (dark) 0.18f else 0.10f))
            .clickable(source, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (compact) 40.dp else 50.dp)
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, style = if (compact) BloqueioType.callout else BloqueioType.headline,
            fontWeight = FontWeight.SemiBold, color = if (enabled) fg else fg.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Conteúdo do botão principal: ícone + texto, ou indicador de atividade (HIG › Buttons). */
@Composable
internal fun PrimaryButtonContent(text: String, icon: ImageVector?, loading: Boolean, iconSize: Dp = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = BloqueioType.headline, color = Color.White, textAlign = TextAlign.Center)
    }
}

/**
 * Escolha única em pílulas (tempos, durações). Cada pílula ≥ 44 dp, com tique de vibração,
 * semântica de grupo selecionável e a escolhida preenchida.
 */
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tint: Tint = Tint.Accent,
) {
    val haptic = LocalHapticFeedback.current
    // Seleção neutra (texto sobre preto/branco): a cor fica para a ação principal (HIG › Branding).
    val label = Bloqueio.colors.label
    val fill = label
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            val source = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .weight(1f)
                    .pressScale(source, pressed = 0.94f)
                    .clip(CircleShape)
                    .background(if (isSel) fill else label.copy(alpha = 0.08f))
                    .selectable(
                        selected = isSel,
                        interactionSource = source,
                        indication = LocalIndication.current,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSel) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        },
                    )
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label, style = BloqueioType.callout,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSel) Bloqueio.colors.background else label,
                    textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}
