package dev.foco.bloqueio.ui.screens

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
import dev.foco.bloqueio.ui.components.SegmentedControl
import dev.foco.bloqueio.ui.components.StepperCell
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
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = Rápido, 1 = Personalizado
    var quickIdx by rememberSaveable { mutableIntStateOf(1) }
    var hours by rememberSaveable { mutableIntStateOf(1) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var confirm by remember { mutableStateOf(false) }

    val level = levels[levelIdx]
    val total = if (mode == 0) quickMinutes[quickIdx] else hours * 60 + minutes

    GroupedSection(
        header = "Foco",
        footer = when (level) {
            FocusLevel.PARTIAL -> "Parcial: jogos bloqueados; nas redes só Reels, Shorts e feeds — criar, postar e mensagens continuam liberados."
            FocusLevel.TOTAL -> "Total: redes sociais e jogos bloqueados por completo."
            else -> "Bloquear: o celular fica no Hub, só com os apps essenciais e a Pasta segura, como no descanso."
        } + " Depois de começar, nada muda até o fim; só o admin encerra antes.",
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(listOf("Parcial", "Total", "Bloquear"), levelIdx, { levelIdx = it })
            SegmentedControl(listOf("Rápido", "Personalizado"), mode, { mode = it })
            if (mode == 0) {
                SegmentedControl(quickMinutes.map { "$it min" }, quickIdx, { quickIdx = it })
            } else {
                Column {
                    StepperCell(
                        "Horas", "$hours h",
                        onMinus = { hours = (hours - 1).coerceAtLeast(0) },
                        onPlus = { hours = (hours + 1).coerceAtMost(12) },
                    )
                    StepperCell(
                        "Minutos", "$minutes min",
                        onMinus = { minutes = (minutes - 5).coerceAtLeast(0) },
                        onPlus = { minutes = (minutes + 5).coerceAtMost(55) },
                        showDivider = false,
                    )
                }
            }
            PrimaryButton(
                if (total > 0) "Começar · ${formatMinutes(total * 60L)}" else "Escolha um tempo",
                color = Bloqueio.colors.focusFill,
                enabled = total > 0,
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
