package dev.foco.bloqueio.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.securefolder.CleanEnvironment
import dev.foco.bloqueio.service.GuardState
import dev.foco.bloqueio.ui.components.Cell
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.theme.Bloqueio

/** O que falta configurar. Relido sempre que a tela volta ao primeiro plano. */
data class SetupStatus(
    val accessibility: Boolean,
    val notifications: Boolean,
    val systemProtection: Boolean,
    val cleanEnvironment: Boolean,
) {
    /** O app funciona: acessibilidade e notificações ativas. */
    val essentialsDone get() = accessibility && notifications
    val allDone get() = essentialsDone && systemProtection && cleanEnvironment
}

@Composable
fun rememberSetupStatus(): SetupStatus {
    val context = LocalContext.current
    val accessibilityOn by GuardState.accessibilityConnected.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val policy = remember { PolicyManager(context) }
    return remember(resumeTick, accessibilityOn) {
        SetupStatus(
            accessibility = accessibilityOn,
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            systemProtection = policy.isDeviceOwner,
            cleanEnvironment = CleanEnvironment.canWriteSecureSettings(context),
        )
    }
}

/**
 * Seção de configuração. Antes de abrir as Configurações de acessibilidade mostra o que
 * o serviço lê (divulgação exigida pela Play Store e pela HIG › Privacy/Onboarding).
 */
@Composable
fun SetupSection(status: SetupStatus, header: String = "Falta configurar") {
    val context = LocalContext.current
    var disclosure by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    GroupedSection(
        header = header,
        footer = "Proteção do sistema e ambiente limpo são ativados uma vez pelo PC (passo a passo em docs/INSTALACAO.md).",
    ) {
        SetupCell("Acompanhar apps abertos", status.accessibility, Icons.Rounded.TouchApp) { disclosure = true }
        SetupCell("Notificações e Now Bar", status.notifications, Icons.Rounded.Notifications) {
            if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        SetupCell("Proteção do sistema", status.systemProtection, Icons.Rounded.AdminPanelSettings, onClick = null)
        SetupCell("Ambiente limpo", status.cleanEnvironment, Icons.Rounded.CleaningServices, last = true, onClick = null)
    }

    if (disclosure) {
        IosAlert(
            title = "Acompanhar apps abertos",
            message = "O Bloqueio usa o serviço de acessibilidade para saber qual app está na tela, " +
                "contar suas cotas e fechar Reels, Shorts e feeds quando a cota acaba. " +
                "Nada do que aparece na tela é guardado ou enviado para fora do celular.",
            onDismiss = { disclosure = false },
        ) {
            TextButtonIos("Continuar") {
                disclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            TextButtonIos("Agora não", color = Bloqueio.colors.secondaryLabel) { disclosure = false }
        }
    }
}

@Composable
private fun SetupCell(
    title: String,
    done: Boolean,
    icon: ImageVector,
    last: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Cell(
        title = title,
        leading = { IconTile(icon, if (done) Bloqueio.colors.success else Bloqueio.colors.gray) },
        value = if (done) "Ativo" else if (onClick != null) "Ativar" else "Configurar no PC",
        valueColor = if (done) Bloqueio.colors.successText else if (onClick != null) Bloqueio.colors.accent else null,
        onClick = if (done) null else onClick,
        chevron = !done && onClick != null,
        showDivider = !last,
    )
}
