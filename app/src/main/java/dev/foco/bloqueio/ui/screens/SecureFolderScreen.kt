package dev.foco.bloqueio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val canClean = remember { CleanEnvironment.canWriteSecureSettings(context) }

    LargeTitleScaffold(
        "Pasta segura",
        trailing = {
            Text(
                if (editing) "OK" else "Editar",
                style = BloqueioType.body, color = Bloqueio.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { editing = !editing }.padding(6.dp),
            )
        },
    ) {
        Text(
            if (canClean) {
                "Apps abertos daqui rodam com as opções do desenvolvedor e a depuração desligadas, como num celular de fábrica. Tudo volta ao normal quando você sai."
            } else {
                "Para o ambiente limpo funcionar, conceda WRITE_SECURE_SETTINGS via ADB (docs/INSTALACAO.md, passo 4)."
            },
            style = BloqueioType.footnote,
            color = if (canClean) Bloqueio.colors.secondaryLabel else Bloqueio.colors.warning,
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
                                    onOpen = {
                                        if (!CleanEnvironment.launch(context, pkg)) {
                                            Toast.makeText(context, "App não instalado", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRemove = { RulesRepository.removeFromSecureFolder(pkg) },
                                )
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            title = "Adicionar à pasta segura",
            exclude = config.secureFolder.toSet(),
            onPick = {
                RulesRepository.addToSecureFolder(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun FolderApp(label: String, pkg: String, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.clickable(onClick = if (editing) onRemove else onOpen).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(pkg, size = 56.dp)
            if (editing) {
                Box(
                    Modifier
                        .offset((-6).dp, (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Bloqueio.colors.gray),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Remove, "Remover", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = BloqueioType.caption, color = Bloqueio.colors.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
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
