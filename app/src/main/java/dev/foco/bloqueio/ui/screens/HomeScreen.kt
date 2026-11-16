package dev.foco.bloqueio.ui.screens

import dev.foco.bloqueio.ui.components.RollingText
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    val sTotalAura = config.quotas.socialMinutes * 60f
    val gTotalAura = config.quotas.gamesMinutes * 60f
    LargeTitleScaffold(
        "Bloqueio",
        aura = {
            QuotaAura(
                social = if (sTotalAura > 0) state.socialLeftSec / sTotalAura else 0f,
                games = if (gTotalAura > 0) state.gamesLeftSec / gTotalAura else 0f,
            )
        },
    ) {
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
                // Cor só quando há estado; neutro no resto (cor com significado, HIG › Color).
                leading = { StatusTile(Icons.Rounded.Shield, Bloqueio.colors.success, active = vpn == VpnState.PROTECTED) },
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
                leading = { StatusTile(Icons.Rounded.Bedtime, Bloqueio.colors.games, active = state.inSchedule) },
                value = if (state.inSchedule) "Ativo" else null,
                valueColor = Bloqueio.colors.games,
            )
            Cell(
                title = "Foco",
                leading = { StatusTile(Icons.Rounded.DoNotDisturbOn, Bloqueio.colors.focus, active = state.focusLevel != FocusLevel.OFF) },
                value = when (state.focusLevel) {
                    FocusLevel.OFF -> "Desligado"
                    else -> {
                        val t = Instant.ofEpochMilli(config.focus.until).atZone(ZoneId.systemDefault()).toLocalTime()
                        "Até %02d:%02d".format(t.hour, t.minute)
                    }
                },
                showDivider = false,
            )
        }

        // ----- Foco: escolher (antes) ou cronômetro (durante) -----
        FocusCard(config.focus)

        if (setup.essentialsDone && !setup.allDone) SetupSection(setup, header = "Configuração")
    }
}

@Composable
private fun QuotaLine(title: String, secondsLeft: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
            // Cota zerada também em texto: a informação não depende só do anel/cor.
            RollingText(
                if (secondsLeft <= 0) "Esgotada" else formatClock(secondsLeft),
                style = BloqueioType.title2, color = Bloqueio.colors.label,
            )
        }
    }
}

@Composable
private fun StatusTile(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, active: Boolean) {
    if (active) IconTile(icon, color) else IconTile(icon, Bloqueio.colors.cellSecondary, glyph = Bloqueio.colors.secondaryLabel)
}

/**
 * Aura das cotas: a cor vive no conteúdo, atrás dos anéis (como no app Fitness), e o vidro
 * da barra a reflete ao rolar. Mais forte quanto mais cota sobra; respira devagar.
 */
@Composable
private fun QuotaAura(social: Float, games: Float) {
    val c = Bloqueio.colors
    val dark = c.background == Color.Black
    val breathe = rememberInfiniteTransition(label = "aura")
    val pulse by breathe.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "respira",
    )
    val strength = if (dark) 0.42f else 0.26f
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(520.dp)) {
        val w = size.width
        fun glow(center: Offset, radius: Float, color: Color, amount: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = strength * (0.35f + 0.65f * amount)), Color.Transparent),
                    center = center, radius = radius,
                ),
                radius = radius, center = center,
            )
        }
        glow(Offset(w * 0.18f, 150.dp.toPx()), 260.dp.toPx() * pulse, c.social, social.coerceIn(0f, 1f))
        glow(Offset(w * 0.86f, 210.dp.toPx()), 240.dp.toPx() * (2f - pulse), c.games, games.coerceIn(0f, 1f))
        // Esvanece para o fundo: o resto da tela continua neutro.
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to c.background))
    }
}
