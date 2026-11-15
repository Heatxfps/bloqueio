package dev.foco.bloqueio.ui.screens

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.semantics.Role
import dev.foco.bloqueio.ui.theme.Radius
import dev.foco.bloqueio.ui.components.UiMessages
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.data.AppConfig
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.security.AdminSession
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.ui.components.AlertField
import dev.foco.bloqueio.ui.components.Cell
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.SwitchCell
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.SegmentedControl
import dev.foco.bloqueio.ui.components.StepperCell
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.components.TextFieldCell
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminScreen(vault: PasswordVault) {
    if (rememberAdminUnlocked()) AdminPanel(vault) else AdminLogin(vault)
}

// ---------------------------------------------------------------- Login

@Composable
private fun AdminLogin(vault: PasswordVault) {
    var pw by remember { mutableStateOf("") }
    var minutesIdx by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }
    val minutes = listOf(5, 15, 30)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // PBKDF2 (120 mil iterações) roda fora da thread principal; o botão mostra o progresso.
    fun tryUnlock() {
        if (pw.isEmpty() || busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { vault.verify(pw) }
            busy = false
            when (r) {
                PasswordVault.Result.Ok -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    AdminSession.unlock(minutes[minutesIdx])
                    pw = ""
                }
                PasswordVault.Result.Wrong -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    error = "Senha incorreta."
                }
                is PasswordVault.Result.LockedOut -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    error = "Muitas tentativas. Tente em ${r.seconds} s."
                }
            }
        }
    }

    LargeTitleScaffold("Admin") {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Rounded.Lock, Bloqueio.colors.gray, size = 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Digite a senha mestra para editar as regras temporariamente.",
                style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel, textAlign = TextAlign.Center,
            )
        }
        GroupedSection(footer = error) {
            TextFieldCell(
                pw, { pw = it; error = null }, "Senha mestra", secure = true,
                imeAction = ImeAction.Done, onImeAction = ::tryUnlock,
            )
        }
        GroupedSection(header = "Desbloquear por") {
            Column(Modifier.padding(16.dp)) {
                SegmentedControl(minutes.map { "$it min" }, minutesIdx, { minutesIdx = it })
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "Verificando…" else "Desbloquear", enabled = pw.isNotEmpty() && !busy, onClick = ::tryUnlock)
        TextButtonIos("Esqueci a senha") { recovering = true }
    }

    if (recovering) {
        var code by remember { mutableStateOf("") }
        var np by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        IosAlert(
            title = "Redefinir senha",
            message = msg ?: "Use o código de recuperação que você anotou ao configurar o app.",
            onDismiss = { recovering = false },
            content = {
                AlertField(code, { code = it }, "XXXXX-XXXXX-XXXXX-XXXXX")
                AlertField(np, { np = it }, "Nova senha", secure = true)
            },
        ) {
            TextButtonIos(if (busy) "Verificando…" else "Redefinir") {
                if (busy) return@TextButtonIos
                if (np.length < PasswordVault.MIN_LENGTH) {
                    msg = "A nova senha precisa de ${PasswordVault.MIN_LENGTH}+ caracteres."
                } else {
                    busy = true
                    scope.launch {
                        val c = withContext(Dispatchers.Default) { vault.resetWithRecovery(code, np) }
                        busy = false
                        if (c == null) msg = "Código inválido (ou muitas tentativas)." else {
                            recovering = false
                            newCode = c
                        }
                    }
                }
            }
            TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { recovering = false }
        }
    }

    newCode?.let { code ->
        NewRecoveryCodeAlert(code) { newCode = null }
    }
}

@Composable
private fun NewRecoveryCodeAlert(code: String, onDismiss: () -> Unit) {
    IosAlert(
        title = "Novo código de recuperação",
        message = "O código antigo deixou de valer. Anote este:",
        onDismiss = {},
        content = {
            SelectionContainer {
                Text(code, style = BloqueioType.headline.copy(fontFamily = FontFamily.Monospace), color = Bloqueio.colors.label)
            }
        },
    ) {
        TextButtonIos("Já anotei", onClick = onDismiss)
    }
}

// ---------------------------------------------------------------- Painel

@Composable
private fun AdminPanel(vault: PasswordVault) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val config by RulesRepository.config.collectAsState()
    val until by AdminSession.unlockedUntil.collectAsState()
    val now by rememberNow()
    val policy = remember { PolicyManager(context) }
    var changingPw by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }

    fun update(t: (AppConfig) -> AppConfig) = runGuarded(context) { RulesRepository.adminUpdate(t) }

    val left = ((until - now) / 1000).coerceAtLeast(0)
    LargeTitleScaffold("Admin") {
        // Estado da sessão com rótulo; "Bloquear" é secundário, não o botão principal da tela.
        UnlockedChip("%d:%02d".format(left / 60, left % 60)) { AdminSession.lock() }

        // Cotas
        GroupedSection(header = "Cotas diárias") {
            StepperCell("Redes sociais", "${config.quotas.socialMinutes} min", {
                update { it.copy(quotas = it.quotas.copy(socialMinutes = (it.quotas.socialMinutes - 5).coerceAtLeast(0))) }
            }, {
                update { it.copy(quotas = it.quotas.copy(socialMinutes = (it.quotas.socialMinutes + 5).coerceAtMost(240))) }
            })
            StepperCell("Jogos", "${config.quotas.gamesMinutes} min", {
                update { it.copy(quotas = it.quotas.copy(gamesMinutes = (it.quotas.gamesMinutes - 5).coerceAtLeast(0))) }
            }, {
                update { it.copy(quotas = it.quotas.copy(gamesMinutes = (it.quotas.gamesMinutes + 5).coerceAtMost(240))) }
            }, showDivider = false)
        }

        // Horário
        val sch = config.schedule
        GroupedSection(header = "Horário de descanso", footer = "Só apps essenciais funcionam dentro da janela.") {
            SwitchCell("Ativado", sch.enabled, { v -> update { it.copy(schedule = it.schedule.copy(enabled = v)) } })
            // Seletor de horário em vez de −/+ de 30 min; só salva ao confirmar.
            Cell(
                "Início", value = ScheduleWindow.format(sch.startMinute), valueColor = Bloqueio.colors.accent,
                onClick = { editingTime = TimeField.START },
            )
            Cell(
                "Fim", value = ScheduleWindow.format(sch.endMinute), valueColor = Bloqueio.colors.accent,
                showDivider = false, onClick = { editingTime = TimeField.END },
            )
        }

        // Foco
        if (config.focus.isActive(now)) {
            GroupedSection(header = "Foco") {
                Cell("Encerrar foco agora", titleColor = Bloqueio.colors.danger, showDivider = false, onClick = {
                    runGuarded(context) { RulesRepository.endFocus() }
                })
            }
        }

        // VPN
        VpnSection(config, ::update)

        // Restrições
        val r = config.restrictions
        GroupedSection(header = "Restrições do sistema", footer = "Valem enquanto a proteção do sistema estiver ativa.") {
            SwitchCell(
                "Bloquear reset de fábrica", r.blockFactoryReset,
                { v -> update { it.copy(restrictions = it.restrictions.copy(blockFactoryReset = v)) } },
                subtitle = "Ninguém apaga o celular pelas Configurações.",
            )
            SwitchCell(
                "Bloquear modo seguro", r.blockSafeBoot,
                { v -> update { it.copy(restrictions = it.restrictions.copy(blockSafeBoot = v)) } },
                subtitle = "O modo seguro desligaria o Bloqueio ao reiniciar.",
            )
            SwitchCell(
                "Bloquear configuração de VPN", r.blockVpnConfig,
                { v -> update { it.copy(restrictions = it.restrictions.copy(blockVpnConfig = v)) } },
                subtitle = "Impede adicionar ou trocar de VPN.",
            )
            SwitchCell(
                "Bloquear data e hora", r.blockDateTime,
                { v -> update { it.copy(restrictions = it.restrictions.copy(blockDateTime = v)) } },
                subtitle = "Mudar o relógio burlaria o horário de descanso e as cotas.",
                showDivider = false,
            )
        }

        // Senha e proteção
        GroupedSection(header = "Segurança") {
            Cell("Trocar senha mestra", chevron = true, onClick = { changingPw = true })
            Cell(
                "Remover proteção do dispositivo",
                subtitle = if (policy.isDeviceOwner) "Libera a desinstalação do Bloqueio" else "A proteção do sistema não está ativa",
                titleColor = Bloqueio.colors.danger,
                showDivider = false,
                onClick = if (policy.isDeviceOwner) ({ confirmRemove = true }) else null,
            )
        }
    }

    editingTime?.let { field ->
        val current = if (field == TimeField.START) config.schedule.startMinute else config.schedule.endMinute
        TimePickerAlert(
            title = if (field == TimeField.START) "Início do descanso" else "Fim do descanso",
            initialMinute = current,
            onDismiss = { editingTime = null },
            onConfirm = { minute ->
                editingTime = null
                update {
                    it.copy(
                        schedule = if (field == TimeField.START) {
                            it.schedule.copy(startMinute = minute)
                        } else {
                            it.schedule.copy(endMinute = minute)
                        },
                    )
                }
            },
        )
    }

    if (changingPw) {
        var cur by remember { mutableStateOf("") }
        var np by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        IosAlert("Trocar senha", msg, onDismiss = { changingPw = false }, content = {
            AlertField(cur, { cur = it }, "Senha atual", secure = true)
            AlertField(np, { np = it }, "Nova senha", secure = true)
        }) {
            TextButtonIos(if (busy) "Salvando…" else "Salvar") {
                if (busy) return@TextButtonIos
                if (np.length < PasswordVault.MIN_LENGTH) {
                    msg = "Mínimo de ${PasswordVault.MIN_LENGTH} caracteres."
                    return@TextButtonIos
                }
                busy = true
                scope.launch {
                    val res = withContext(Dispatchers.Default) { vault.change(cur, np) }
                    busy = false
                    when (res) {
                        PasswordVault.Result.Ok -> {
                            changingPw = false
                            UiMessages.info("Senha mestra alterada.")
                        }
                        PasswordVault.Result.Wrong -> msg = "Senha atual incorreta."
                        is PasswordVault.Result.LockedOut -> msg = "Aguarde ${res.seconds} s."
                    }
                }
            }
            TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { changingPw = false }
        }
    }

    if (confirmRemove) {
        IosAlert(
            "Remover proteção?",
            "A trava da VPN, os bloqueios e as restrições são liberados e o Bloqueio pode ser desinstalado. Para ativar de novo, será preciso configurar pelo PC.",
            onDismiss = { confirmRemove = false },
        ) {
            TextButtonIos("Remover", color = Bloqueio.colors.danger) {
                policy.removeProtection()
                confirmRemove = false
                UiMessages.info("Proteção do sistema removida. Agora o Bloqueio pode ser desinstalado.")
            }
            TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { confirmRemove = false }
        }
    }
}

@Composable
private fun VpnSection(config: AppConfig, update: ((AppConfig) -> AppConfig) -> Unit) {
    val vpn = config.vpn
    GroupedSection(
        header = "VPN Tailscale",
        footer = "O botão de desconectar do Tailscale fica desabilitado (política ForceEnabled). " +
            "Se o Wi‑Fi/dados tiver internet mas nada passar pela VPN ${vpn.failThreshold}× seguidas, " +
            "o servidor caiu: o Tailscale é desligado e a volta é testada a cada ${vpn.retryMinutes} min.",
    ) {
        SwitchCell("Travar VPN no Tailscale", vpn.enabled, { v -> update { it.copy(vpn = it.vpn.copy(enabled = v)) } })
        StepperCell("Testar retorno a cada", "${vpn.retryMinutes} min", {
            update { it.copy(vpn = it.vpn.copy(retryMinutes = (it.vpn.retryMinutes - 1).coerceAtLeast(1))) }
        }, {
            update { it.copy(vpn = it.vpn.copy(retryMinutes = (it.vpn.retryMinutes + 1).coerceAtMost(60))) }
        }, showDivider = false)
    }
}

private enum class TimeField { START, END }

/** Chip "Desbloqueado · 12:31 restantes" com a ação Bloquear ao lado. */
@Composable
private fun UnlockedChip(timeLeft: String, onLock: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.cell))
            .background(Bloqueio.colors.cell)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Bloqueio.colors.success))
        Spacer(Modifier.width(10.dp))
        Text(
            "Desbloqueado · $timeLeft restantes", style = BloqueioType.callout, color = Bloqueio.colors.label,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Bloquear", style = BloqueioType.headline, color = Bloqueio.colors.accent,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onLock)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlert(title: String, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    IosAlert(title = title, onDismiss = onDismiss, content = {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = Bloqueio.colors.cellSecondary,
                selectorColor = Bloqueio.colors.accentFill,
                timeSelectorSelectedContainerColor = Bloqueio.colors.accentFill.copy(alpha = 0.18f),
                timeSelectorUnselectedContainerColor = Bloqueio.colors.cellSecondary,
                timeSelectorSelectedContentColor = Bloqueio.colors.label,
                timeSelectorUnselectedContentColor = Bloqueio.colors.label,
            ),
        )
    }) {
        TextButtonIos("Salvar") { onConfirm(state.hour * 60 + state.minute) }
        TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel, onClick = onDismiss)
    }
}
