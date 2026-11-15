package dev.foco.bloqueio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RuleType
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.ui.components.AppIcon
import dev.foco.bloqueio.ui.components.AppPickerDialog
import dev.foco.bloqueio.ui.components.Cell
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.components.UiMessages
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius

/**
 * Regras: qualquer um adiciona; só o admin remove (docs/funcionalidades/09-permissoes.md).
 * Visão geral por categoria + lista de cada uma (divulgação progressiva, HIG › Layout).
 */
@Composable
fun RulesScreen() {
    var open by rememberSaveable { mutableStateOf<RuleType?>(null) }
    val current = open
    if (current == null) {
        RulesOverview(onOpen = { open = it })
    } else {
        BackHandler { open = null }
        RuleTypeScreen(current, onBack = { open = null })
    }
}

private data class TypeStyle(val icon: ImageVector, val color: @Composable () -> Color, val short: String)

private val styles = mapOf(
    RuleType.BLOCK to TypeStyle(Icons.Rounded.Block, { Bloqueio.colors.grayFill }, "Sempre bloqueados"),
    RuleType.SOCIAL to TypeStyle(Icons.Rounded.Group, { Bloqueio.colors.social }, "Redes sociais"),
    RuleType.GAME to TypeStyle(Icons.Rounded.SportsEsports, { Bloqueio.colors.games }, "Jogos"),
    RuleType.PROTECT_UNINSTALL to TypeStyle(Icons.Rounded.Delete, { Bloqueio.colors.success }, "Não desinstaláveis"),
    RuleType.ESSENTIAL to TypeStyle(Icons.Rounded.Bedtime, { Bloqueio.colors.gray }, "Essenciais no descanso"),
)

@Composable
private fun RulesOverview(onOpen: (RuleType) -> Unit) {
    val config by RulesRepository.config.collectAsState()
    val admin = rememberAdminUnlocked()

    LargeTitleScaffold("Regras") {
        if (!admin) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.cell))
                    .background(Bloqueio.colors.warning.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Bloqueio.colors.warningText)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Você pode adicionar. Remover ou editar só com o Admin.",
                    style = BloqueioType.footnote, color = Bloqueio.colors.label,
                )
            }
        }
        listOf(
            "Bloqueios" to listOf(RuleType.BLOCK),
            "Com cota" to listOf(RuleType.SOCIAL, RuleType.GAME),
            "Proteção" to listOf(RuleType.PROTECT_UNINSTALL, RuleType.ESSENTIAL),
        ).forEach { (header, types) ->
            GroupedSection(header = header) {
                types.forEachIndexed { i, type ->
                    val st = styles.getValue(type)
                    val count = config.rules.count { it.type == type }
                    Cell(
                        title = st.short,
                        leading = { IconTile(st.icon, st.color()) },
                        value = if (type == RuleType.GAME) "Automático + $count" else "$count",
                        chevron = true,
                        showDivider = i < types.lastIndex,
                        onClick = { onOpen(type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleTypeScreen(type: RuleType, onBack: () -> Unit) {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val admin = rememberAdminUnlocked()
    val catalog = remember { AppCatalog(context) }
    var picking by remember { mutableStateOf(false) }
    val rules = config.rules.filter { it.type == type }

    Column(Modifier.fillMaxSize().background(Bloqueio.colors.background)) {
        // Voltar visível (além do gesto/botão do sistema).
        Row(
            Modifier
                .padding(start = 4.dp, top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Voltar para Regras", onClick = onBack)
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = Bloqueio.colors.accent)
            Text("Regras", style = BloqueioType.body, color = Bloqueio.colors.accent)
        }
        Box(Modifier.weight(1f)) {
            LargeTitleScaffold(styles.getValue(type).short) {
                GroupedSection(locked = !admin, footer = footerFor(type)) {
                    if (rules.isEmpty()) {
                        Cell("Nenhum app ainda.", subtitle = "Toque em Adicionar app para criar a primeira regra.")
                    }
                    rules.forEach { rule ->
                        val installed = catalog.isInstalled(rule.packageName)
                        val label = catalog.label(rule.packageName)
                        Cell(
                            title = label,
                            subtitle = if (installed) rule.packageName else "${rule.packageName} · não instalado",
                            leading = { AppIcon(rule.packageName) },
                            trailing = {
                                if (admin) {
                                    Box(Modifier.width(104.dp)) {
                                        TextButtonIos("Remover", color = Bloqueio.colors.danger) {
                                            runGuarded(context) {
                                                RulesRepository.removeRule(rule)
                                                UiMessages.info("$label saiu de “${styles.getValue(type).short}”.")
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    Cell(
                        title = "Adicionar app…",
                        titleColor = Bloqueio.colors.accent,
                        showDivider = false,
                        onClick = { picking = true },
                    )
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            title = styles.getValue(type).short,
            exclude = config.packagesOf(type),
            onPick = { pkg ->
                RulesRepository.addRule(pkg, type)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun footerFor(type: RuleType) = when (type) {
    RuleType.BLOCK -> "Bloqueados o tempo todo. A regra continua mesmo se o app for desinstalado e reinstalado."
    RuleType.PROTECT_UNINSTALL -> "Esses apps não podem ser desinstalados enquanto a proteção do sistema estiver ativa."
    RuleType.SOCIAL -> "Contam na cota de redes sociais. Ao esgotar, só Reels, Shorts e feeds são bloqueados."
    RuleType.GAME -> "Contam na cota de jogos. Apps que o Android marca como jogo entram automaticamente."
    RuleType.ESSENTIAL -> "Continuam funcionando durante o horário de descanso."
}
