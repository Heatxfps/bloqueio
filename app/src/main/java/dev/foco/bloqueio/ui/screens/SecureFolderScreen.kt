package dev.foco.bloqueio.ui.screens

import dev.foco.bloqueio.ui.components.UiMessages
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.securefolder.CleanEnvironment
import dev.foco.bloqueio.ui.components.AppIcon
import dev.foco.bloqueio.ui.components.AppPickerDialog
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius

/** Pasta segura: única lista que o usuário edita livremente (docs/funcionalidades/04-pasta-segura.md). */
@Composable
fun SecureFolderScreen() {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val catalog = remember { AppCatalog(context) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    // Relido ao voltar para o app (a permissão pode ter sido concedida pelo PC).
    val canClean = rememberSetupStatus().cleanEnvironment
    var pendingOpen by remember { mutableStateOf<String?>(null) }
    var lastRemoved by remember { mutableStateOf<String?>(null) }

    fun open(pkg: String) {
        if (!CleanEnvironment.launch(context, pkg)) {
            UiMessages.error("Este app não está instalado. Instale-o de novo ou tire-o da pasta.")
        }
    }

    LargeTitleScaffold(
        "Pasta segura",
        trailing = {
            Text(
                if (editing) "OK" else "Editar",
                style = BloqueioType.body, color = Bloqueio.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { editing = !editing }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
    ) {
        Text(
            if (canClean) {
                "Apps abertos daqui rodam com as opções do desenvolvedor e a depuração desligadas, como num celular de fábrica. Tudo volta ao normal quando você sai."
            } else {
                "Ambiente limpo indisponível: os apps abrem com as opções do desenvolvedor ligadas. Ative pelo PC (docs/INSTALACAO.md, passo 4)."
            },
            style = BloqueioType.footnote,
            color = if (canClean) Bloqueio.colors.secondaryLabel else Bloqueio.colors.warningText,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        val items = config.secureFolder.map { it as String? } + listOf(null) // null = botão adicionar
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(Bloqueio.colors.cell)
                .padding(vertical = 16.dp),
        ) {
            items.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    row.forEach { pkg ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (pkg == null) {
                                AddTile { picking = true }
                            } else {
                                FolderApp(
                                    label = catalog.label(pkg),
                                    pkg = pkg,
                                    editing = editing,
                                    // Sem ambiente limpo, avisa antes de abrir (HIG › Feedback).
                                    onOpen = { if (canClean) open(pkg) else pendingOpen = pkg },
                                    onRemove = {
                                        RulesRepository.removeFromSecureFolder(pkg)
                                        lastRemoved = pkg
                                    },
                                )
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Desfazer fica na tela até a próxima ação (sem prazo), em vez de um Toast.
        lastRemoved?.let { pkg ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.cell))
                    .background(Bloqueio.colors.cell)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${catalog.label(pkg)} saiu da pasta.", style = BloqueioType.callout,
                    color = Bloqueio.colors.label, modifier = Modifier.weight(1f),
                )
                Text(
                    "Desfazer", style = BloqueioType.headline, color = Bloqueio.colors.accent,
                    modifier = Modifier
                        .clickable(role = Role.Button) {
                            RulesRepository.addToSecureFolder(pkg)
                            lastRemoved = null
                        }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    pendingOpen?.let { pkg ->
        IosAlert(
            title = "Ambiente limpo indisponível",
            message = "${catalog.label(pkg)} vai abrir com as opções do desenvolvedor e a depuração ligadas. " +
                "Para ativar o ambiente limpo, configure pelo PC uma vez.",
            onDismiss = { pendingOpen = null },
        ) {
            TextButtonIos("Abrir mesmo assim") {
                pendingOpen = null
                open(pkg)
            }
            TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel) { pendingOpen = null }
        }
    }

    if (picking) {
        AppPickerDialog(
            title = "Adicionar à pasta segura",
            exclude = config.secureFolder.toSet(),
            onPick = {
                RulesRepository.addToSecureFolder(it)
                lastRemoved = null
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun FolderApp(label: String, pkg: String, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(contentAlignment = Alignment.TopStart) {
        Column(
            Modifier
                .clickable(enabled = !editing, role = Role.Button, onClickLabel = "Abrir no ambiente limpo", onClick = onOpen)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(pkg, size = 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                label, style = BloqueioType.caption, color = Bloqueio.colors.label,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        if (editing) {
            // Alvo de 48 dp; o desenho continua sendo o selo de 22 dp no canto do ícone.
            Box(
                Modifier
                    .offset((-16).dp, (-16).dp)
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = "Remover $label da pasta" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Bloqueio.colors.grayFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Bloqueio.colors.cellSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, "Adicionar", tint = Bloqueio.colors.accent)
        }
        Spacer(Modifier.height(6.dp))
        Text("Adicionar", style = BloqueioType.caption, color = Bloqueio.colors.accent)
    }
}
