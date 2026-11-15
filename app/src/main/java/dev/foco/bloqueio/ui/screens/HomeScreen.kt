package dev.foco.bloqueio.ui.screens

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
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.QuotaRing
import dev.foco.bloqueio.ui.components.SegmentedControl
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius
import dev.foco.bloqueio.vpn.VpnState
import java.time.Instant
import java.time.ZoneId

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val usage by QuotaTracker.usage.collectAsState()
    val vpn by GuardState.vpn.collectAsState()
    val accessibilityOn by GuardState.accessibilityConnected.collectAsState()
    val now by rememberNow(15_000)
    val state = remember(config, usage, now) { GuardService.state() }

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val policy = remember { PolicyManager(context) }
    val isOwner = remember(resumeTick) { policy.isDeviceOwner }
    val notificationsOn = remember(resumeTick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val secureSettings = remember(resumeTick) { CleanEnvironment.canWriteSecureSettings(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    LargeTitleScaffold("Bloqueio") {
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
                QuotaLine("Redes sociais", formatMinutes(state.socialLeftSec), Bloqueio.colors.social)
                QuotaLine("Jogos", formatMinutes(state.gamesLeftSec), Bloqueio.colors.games)
                Text("restantes hoje", style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
            }
        }

        // ----- Estado -----
        GroupedSection(header = "Agora") {
            Cell(
                title = "VPN Tailscale",
                leading = { IconTile(Icons.Rounded.Shield, Bloqueio.colors.accent) },
                value = vpn.label,
                valueColor = when (vpn) {
                    VpnState.PROTECTED -> Bloqueio.colors.success
                    VpnState.FAILOVER -> Bloqueio.colors.warning
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
                PrimaryButton("Começar", color = Bloqueio.colors.focus) {
                    runGuarded(context) {
                        RulesRepository.startFocus(
                            if (level == 0) FocusLevel.PARTIAL else FocusLevel.TOTAL,
                            durations[duration],
                        )
                    }
                }
            }
        }

        // ----- Configuração -----
        val allDone = isOwner && accessibilityOn && notificationsOn && secureSettings
        if (!allDone) {
            GroupedSection(
                header = "Configuração",
                footer = "Device Owner e configurações seguras são ativados pelo PC via ADB. Veja docs/INSTALACAO.md no repositório.",
            ) {
                SetupCell("Acessibilidade", accessibilityOn, Icons.Rounded.TouchApp) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                SetupCell("Notificações (Now Bar)", notificationsOn, Icons.Rounded.Notifications) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                SetupCell("Device Owner", isOwner, Icons.Rounded.AdminPanelSettings, null)
                SetupCell("Configurações seguras", secureSettings, Icons.Rounded.Key, null, last = true)
            }
        }
    }
}

@Composable
private fun QuotaLine(title: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel)
            Text(value, style = BloqueioType.title2, color = Bloqueio.colors.label)
        }
    }
}

@Composable
private fun SetupCell(
    title: String,
    done: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)?,
    last: Boolean = false,
) {
    Cell(
        title = title,
        leading = { IconTile(icon, if (done) Bloqueio.colors.success else Bloqueio.colors.gray) },
        value = if (done) "Ativo" else if (onClick != null) "Ativar" else "Via ADB",
        valueColor = if (done) Bloqueio.colors.success else if (onClick != null) Bloqueio.colors.accent else null,
        onClick = if (done) null else onClick,
        chevron = !done && onClick != null,
        showDivider = !last,
    )
}
