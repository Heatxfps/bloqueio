package dev.foco.bloqueio.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.TextFieldCell
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius

/** Primeiro uso: cria a senha mestra e mostra o código de recuperação UMA vez. */
@Composable
fun OnboardingScreen(vault: PasswordVault, onDone: () -> Unit) {
    var recovery by remember { mutableStateOf<String?>(null) }
    if (recovery == null) {
        CreatePassword(vault) { recovery = it }
    } else {
        ShowRecoveryCode(recovery!!, onDone)
    }
}

@Composable
private fun CreatePassword(vault: PasswordVault, onCreated: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pw.length >= PasswordVault.MIN_LENGTH && pw == confirm

    LargeTitleScaffold("Bem-vindo") {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Rounded.Lock, Bloqueio.colors.accent, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Crie a senha mestra do Admin. Só com ela dá para remover regras, mudar cotas, horário, VPN ou desinstalar o app.",
                style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel, textAlign = TextAlign.Center,
            )
        }
        GroupedSection(header = "Senha mestra", footer = "Mínimo de ${PasswordVault.MIN_LENGTH} caracteres.") {
            TextFieldCell(pw, { pw = it }, "Senha", secure = true)
            HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Bloqueio.colors.separator)
            TextFieldCell(confirm, { confirm = it }, "Confirmar senha", secure = true)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continuar", enabled = valid) { onCreated(vault.setup(pw)) }
    }
}

@Composable
private fun ShowRecoveryCode(code: String, onDone: () -> Unit) {
    LargeTitleScaffold("Código de recuperação") {
        Text(
            "Se esquecer a senha, este código é a ÚNICA forma de redefini-la. Anote em papel ou num gerenciador de senhas. Ele não será mostrado de novo.",
            style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
        )
        Spacer(Modifier.height(24.dp))
        SelectionContainer {
            Text(
                code,
                style = BloqueioType.title2.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
                color = Bloqueio.colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(Bloqueio.colors.cell)
                    .padding(vertical = 28.dp, horizontal = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Já anotei", onClick = onDone)
    }
}
