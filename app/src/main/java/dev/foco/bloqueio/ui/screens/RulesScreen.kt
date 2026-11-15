package dev.foco.bloqueio.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RuleType
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.ui.components.AppIcon
import dev.foco.bloqueio.ui.components.AppPickerDialog
import dev.foco.bloqueio.ui.components.Cell
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.theme.Bloqueio

/** Regras: qualquer um adiciona; só o admin remove (docs/funcionalidades/09-permissoes.md). */
@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val admin = rememberAdminUnlocked()
    val catalog = remember { AppCatalog(context) }
    var picking by remember { mutableStateOf<RuleType?>(null) }

    LargeTitleScaffold("Regras") {
        RuleType.entries.forEach { type ->
            val rules = config.rules.filter { it.type == type }
            GroupedSection(
                header = type.title,
                locked = !admin,
                footer = footerFor(type),
            ) {
                rules.forEach { rule ->
                    val installed = catalog.isInstalled(rule.packageName)
                    Cell(
                        title = catalog.label(rule.packageName),
                        subtitle = if (installed) rule.packageName else "${rule.packageName} · não instalado",
                        leading = { AppIcon(rule.packageName) },
                        trailing = {
                            if (admin) TextButtonRemove { runGuarded(context) { RulesRepository.removeRule(rule) } }
                        },
                    )
                }
                Cell(
                    title = "Adicionar app…",
                    titleColor = Bloqueio.colors.accent,
                    showDivider = false,
                    onClick = { picking = type },
                )
            }
        }
    }

    picking?.let { type ->
        AppPickerDialog(
            title = type.title,
            exclude = config.packagesOf(type),
            onPick = { pkg ->
                RulesRepository.addRule(pkg, type)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TextButtonRemove(onClick: () -> Unit) {
    Box(Modifier.width(96.dp)) {
        TextButtonIos("Remover", color = Bloqueio.colors.danger, onClick = onClick)
    }
}

private fun footerFor(type: RuleType) = when (type) {
    RuleType.BLOCK -> "Bloqueados o tempo todo. A regra continua mesmo se o app for desinstalado e reinstalado."
    RuleType.PROTECT_UNINSTALL -> "Esses apps não podem ser desinstalados (requer Device Owner)."
    RuleType.SOCIAL -> "Contam na cota de redes sociais. Ao esgotar, só Reels, Shorts e feeds são bloqueados."
    RuleType.GAME -> "Contam na cota de jogos. Apps que o Android marca como jogo entram automaticamente."
    RuleType.ESSENTIAL -> "Continuam funcionando durante o horário de descanso."
}
