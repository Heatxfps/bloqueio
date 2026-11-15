package dev.foco.bloqueio.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.QuotaTracker
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.securefolder.CleanEnvironment
import dev.foco.bloqueio.service.GuardService
import dev.foco.bloqueio.service.GuardState
import dev.foco.bloqueio.ui.components.Cell
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.QuotaRing
import dev.foco.bloqueio.ui.components.SegmentedControl
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius
import dev.foco.bloqueio.vpn.VpnState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val usage by QuotaTracker.usage.collectAsState()
    val vpn by GuardState.vpn.collectAsState()
    val now by rememberNow(15_000)
    val state = remember(config, usage, now) { GuardService.state() }

    val setup = rememberSetupStatus()
    val haptic = LocalHapticFeedback.current

    LargeTitleScaffold("Bloqueio") {
        // Sem acessibilidade/notificações o app não funciona: configuração vem primeiro.
        if (!setup.essentialsDone) SetupSection(setup)

        // ----- Cotas -----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(Bloqueio.colors.cell)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sTotal = config.quotas.socialMinutes * 60f
            val gTotal = config.quotas.gamesMinutes * 60f
            QuotaRing(
                socialLeft = if (sTotal > 0) state.socialLeftSec / sTotal else 0f,
                gamesLeft = if (gTotal > 0) state.gamesLeftSec / gTotal else 0f,
                size = 112.dp,
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuotaLine("Redes sociais", state.socialLeftSec, Bloqueio.colors.social)
                QuotaLine("Jogos", state.gamesLeftSec, Bloqueio.colors.games)
                Text("restantes hoje", style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
            }
        }

        // ----- Estado -----
        GroupedSection(header = "Agora") {
            Cell(
                title = "VPN Tailscale",
                leading = { IconTile(Icons.Rounded.Shield, Bloqueio.colors.success) /* estado, não ação: azul fica só para o que é clicável */ },
                value = vpn.label,
                valueColor = when (vpn) {
                    VpnState.PROTECTED -> Bloqueio.colors.successText
                    VpnState.FAILOVER -> Bloqueio.colors.warningText
                    VpnState.PROBING, VpnState.CONNECTING -> Bloqueio.colors.accent
                    else -> Bloqueio.colors.secondaryLabel
                },
            )
            val sch = config.schedule
            Cell(
                title = "Horário de descanso",
                subtitle = if (sch.enabled) "${ScheduleWindow.format(sch.startMinute)} – ${ScheduleWindow.format(sch.endMinute)}" else "Desligado",
                leading = { IconTile(Icons.Rounded.Bedtime, Bloqueio.colors.games) },
                value = if (state.inSchedule) "Ativo" else null,
                valueColor = Bloqueio.colors.games,
            )
            Cell(
                title = "Foco",
                leading = { IconTile(Icons.Rounded.DoNotDisturbOn, Bloqueio.colors.focus) },
                value = when (state.focusLevel) {
                    FocusLevel.OFF -> "Desligado"
                    else -> {
                        val t = Instant.ofEpochMilli(config.focus.until).atZone(ZoneId.systemDefault()).toLocalTime()
                        val n = if (state.focusLevel == FocusLevel.TOTAL) "Total" else "Parcial"
                        "$n até %02d:%02d".format(t.hour, t.minute)
                    }
                },
                showDivider = false,
            )
        }

        // ----- Iniciar foco -----
        var level by remember { mutableIntStateOf(0) }
        var duration by remember { mutableIntStateOf(1) }
        val durations = listOf(25, 60, 120, 240)
        var confirmFocus by remember { mutableStateOf(false) }
        GroupedSection(
            header = "Iniciar foco",
            footer = (
                if (level == 0) {
                    "Parcial: jogos bloqueados; nas redes só Reels, Shorts e feeds ficam bloqueados — criar, postar e mensagens continuam liberados."
                } else {
                    "Total: redes sociais e jogos bloqueados por completo."
                }
                ) + " Só o admin pode encerrar antes.",
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedControl(listOf("Parcial", "Total"), level, { level = it })
                SegmentedControl(listOf("25 min", "1 h", "2 h", "4 h"), duration, { duration = it })
                PrimaryButton("Começar", color = Bloqueio.colors.focusFill) { confirmFocus = true }
            }
        }
        if (confirmFocus) {
            val total = level == 1
            val minutes = durations[duration]
            val until = LocalTime.now().plusMinutes(minutes.toLong())
            IosAlert(
                title = if (total) "Começar foco total?" else "Começar foco parcial?",
                message = "Até %02d:%02d. Só o admin pode encerrar antes.".format(until.hour, until.minute),
                onDismiss = { confirmFocus = false },
            ) {
                TextButtonIos("Começar", color = Bloqueio.colors.focus) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    confirmFocus = false
                    runGuarded(context) {
                        RulesRepository.startFocus(if (total) FocusLevel.TOTAL else FocusLevel.PARTIAL, minutes)
                    }
                }
                TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { confirmFocus = false }
            }
        }

        if (setup.essentialsDone && !setup.allDone) SetupSection(setup, header = "Configuração")
    }
}

@Composable
private fun QuotaLine(title: String, secondsLeft: Long, color: Color) {
    // Conta a partir de zero ao abrir a tela (acompanha o enchimento dos anéis).
    val shown = remember { Animatable(0f) }
    LaunchedEffect(secondsLeft) {
        shown.animateTo(secondsLeft.toFloat(), tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
            // Cota zerada também em texto: a informação não depende só do anel/cor.
            Text(
                if (secondsLeft <= 0) "Esgotada" else formatMinutes(shown.value.toLong()),
                style = BloqueioType.title2, color = Bloqueio.colors.label,
            )
        }
    }
}
