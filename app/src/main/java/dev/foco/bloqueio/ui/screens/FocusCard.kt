package dev.foco.bloqueio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import dev.foco.bloqueio.ui.components.ChoiceChips
import dev.foco.bloqueio.ui.components.Tint
import dev.foco.bloqueio.ui.theme.pressScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foco.bloqueio.data.Focus
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import java.time.Instant
import java.time.ZoneId

/** Nome do nível para textos ("Foco total", "Bloqueio"...). */
fun focusName(level: FocusLevel): String = when (level) {
    FocusLevel.OFF -> "Desligado"
    FocusLevel.PARTIAL -> "Foco parcial"
    FocusLevel.TOTAL -> "Foco total"
    FocusLevel.LOCK -> "Celular bloqueado"
}

private fun endTime(until: Long): String {
    val t = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

private val levels = listOf(FocusLevel.PARTIAL, FocusLevel.TOTAL, FocusLevel.LOCK)
private val quickMinutes = listOf(15, 30, 45, 60)

/**
 * Card do foco na Início (docs/funcionalidades/05-foco.md).
 * Sem foco: escolhe o nível e o tempo. Com foco: os controles somem e fica o cronômetro —
 * nada pode ser trocado até o fim (só o admin encerra antes).
 */
@Composable
fun FocusCard(focus: Focus) {
    val now by rememberNow(1_000)
    val active = focus.isActive(now)
    AnimatedContent(
        targetState = active,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
        label = "foco",
    ) { running ->
        if (running) FocusTimer(focus, now) else FocusSetup()
    }
}

@Composable
private fun FocusSetup() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var levelIdx by rememberSaveable { mutableIntStateOf(0) }
    var quickIdx by rememberSaveable { mutableIntStateOf(1) } // 0..3 = rápido, 4 = Outro
    var custom by rememberSaveable { mutableIntStateOf(90) }
    var confirm by remember { mutableStateOf(false) }

    val level = levels[levelIdx]
    val isCustom = quickIdx == quickMinutes.size
    val total = if (isCustom) custom else quickMinutes[quickIdx]

    GroupedSection(
        header = "Foco",
        footer = when (level) {
            FocusLevel.PARTIAL -> "Parcial: jogos bloqueados; nas redes só Reels, Shorts e feeds — criar, postar e mensagens continuam liberados."
            FocusLevel.TOTAL -> "Total: redes sociais e jogos bloqueados por completo."
            else -> "Bloquear: o celular fica no Hub, só com os apps essenciais e a Pasta segura, como no descanso."
        } + " Depois de começar, nada muda até o fim; só o admin encerra antes.",
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Modos como cartões: mais fácil de comparar do que um controle segmentado.
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                modeInfo.forEachIndexed { i, (icon, name) ->
                    ModeTile(icon, name, selected = i == levelIdx, modifier = Modifier.weight(1f)) {
                        if (i != levelIdx) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        levelIdx = i
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DURAÇÃO (MIN)", style = BloqueioType.caption, color = Bloqueio.colors.secondaryLabel)
                ChoiceChips(quickMinutes.map { "$it" } + "Outro", quickIdx, { quickIdx = it }, tint = Tint.Focus)
            }

            AnimatedVisibility(visible = isCustom) {
                DurationPicker(custom) { custom = it }
            }

            PrimaryButton(
                "Começar · ${formatMinutes(total * 60L)}",
                color = Bloqueio.colors.focusFill,
                icon = Icons.Rounded.PlayArrow,
            ) { confirm = true }
        }
    }

    if (confirm) {
        val until = System.currentTimeMillis() + total * 60_000L
        IosAlert(
            title = "Começar ${focusName(level).lowercase()}?",
            message = "Por ${formatMinutes(total * 60L)}, até ${endTime(until)}. Depois de começar não dá para trocar; só o admin encerra antes.",
            onDismiss = { confirm = false },
        ) {
            TextButtonIos("Começar", color = Bloqueio.colors.focus) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                confirm = false
                runGuarded(context) { RulesRepository.startFocus(level, total) }
            }
            TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { confirm = false }
        }
    }
}

private val modeInfo = listOf(
    Icons.Rounded.Contrast to "Parcial",
    Icons.Rounded.DoNotDisturbOn to "Total",
    Icons.Rounded.Lock to "Bloquear",
)

/** Cartão de modo: ícone em círculo + nome; o escolhido ganha a cor do foco e uma borda. */
@Composable
private fun ModeTile(icon: ImageVector, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Bloqueio.colors
    val dark = c.background == Color.Black
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .pressScale(source, pressed = 0.95f)
            .clip(shape)
            .background(if (selected) c.focus.copy(alpha = if (dark) 0.22f else 0.12f) else c.cellSecondary)
            .border(if (selected) 2.dp else 0.dp, if (selected) c.focus else Color.Transparent, shape)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 96.dp)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) c.focusFill else c.gray.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name, style = BloqueioType.callout,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = c.label, textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

/** Tempo personalizado: valor grande entre botões redondos de − e + (5 min a 12 h, de 5 em 5). */
@Composable
private fun DurationPicker(minutes: Int, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun step(delta: Int) {
        val next = (minutes + delta).coerceIn(5, 12 * 60)
        if (next != minutes) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(next)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bloqueio.colors.cellSecondary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Rounded.Remove, "Diminuir 5 minutos") { step(-5) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatMinutes(minutes * 60L), style = BloqueioType.title2, color = Bloqueio.colors.label)
            Text("de 5 em 5 min", style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
        }
        RoundIconButton(Icons.Rounded.Add, "Aumentar 5 minutos") { step(5) }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .pressScale(source, pressed = 0.9f)
            .clip(CircleShape)
            .background(Bloqueio.colors.focus.copy(alpha = 0.16f))
            .clickable(source, LocalIndication.current, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Bloqueio.colors.focusTintText, modifier = Modifier.size(24.dp))
    }
}

/** Cronômetro regressivo grande com anel; substitui os controles enquanto o foco dura. */
@Composable
private fun FocusTimer(focus: Focus, now: Long) {
    val left = ((focus.until - now) / 1000).coerceAtLeast(0)
    val totalSec = ((focus.until - (focus.startedAt.takeIf { it > 0 } ?: now)) / 1000).coerceAtLeast(1)
    val fraction by animateFloatAsState((left.toFloat() / totalSec).coerceIn(0f, 1f), label = "restante")
    val h = left / 3600
    val m = (left % 3600) / 60
    val s = left % 60
    val clock = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    val color = Bloqueio.colors.focus

    GroupedSection(header = "Foco", footer = "Só o admin pode encerrar antes (aba Admin).") {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(208.dp)) {
                    val stroke = size.minDimension * 0.07f
                    val d = size.minDimension - stroke
                    val tl = Offset(stroke / 2, stroke / 2)
                    drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    drawArc(color, -90f, 360f * fraction, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        clock,
                        // Algarismos de largura fixa: o número não "dança" a cada segundo.
                        style = BloqueioType.largeTitle.copy(
                            fontSize = if (h > 0) 40.sp else 48.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = Bloqueio.colors.label,
                        // O TalkBack lê o resumo, não cada segundo.
                        modifier = Modifier.semantics {
                            contentDescription = "Faltam ${formatMinutes(left)}"
                        },
                    )
                    Text(focusName(focus.level), style = BloqueioType.headline, color = color)
                }
            }
            Text(
                "Termina às ${endTime(focus.until)}",
                style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
