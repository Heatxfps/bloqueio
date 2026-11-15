package dev.foco.bloqueio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius

/**
 * Seletor de app (folha estilo iOS). Também aceita um nome de pacote digitado,
 * para criar regra de app que ainda não está instalado.
 */
@Composable
fun AppPickerDialog(title: String, exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppCatalog(context).launchableApps(force = true) }
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter {
        it.packageName !in exclude &&
            (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
    }
    val looksLikePackage = Regex("^[a-zA-Z][\\w]*(\\.[\\w]+)+$").matches(query.trim())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(Radius.card))
                .background(Bloqueio.colors.background),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = BloqueioType.headline, color = Bloqueio.colors.label, modifier = Modifier.weight(1f))
                Text(
                    "Fechar", style = BloqueioType.body, color = Bloqueio.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bloqueio.colors.cellSecondary),
            ) {
                TextFieldCell(query, { query = it }, "Buscar ou digitar pacote (com.exemplo.app)")
            }
            LazyColumn(
                Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(Radius.cell))
                    .background(Bloqueio.colors.cell),
            ) {
                if (looksLikePackage && apps.none { it.packageName == query.trim() }) {
                    item {
                        Cell(
                            title = "Adicionar \"${query.trim()}\"",
                            subtitle = "Regra vale mesmo sem o app instalado",
                            titleColor = Bloqueio.colors.accent,
                            onClick = { onPick(query.trim()) },
                        )
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    Cell(
                        title = app.label,
                        subtitle = app.packageName,
                        leading = { AppIcon(app.packageName) },
                        onClick = { onPick(app.packageName) },
                    )
                }
            }
        }
    }
}

