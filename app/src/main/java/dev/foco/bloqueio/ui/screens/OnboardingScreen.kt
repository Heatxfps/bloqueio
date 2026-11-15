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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.ui.components.GroupedSection
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.LargeTitleScaffold
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.components.TextFieldCell
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Primeiro uso, em 3 passos: senha mestra → código de recuperação (uma vez) → permissões.
 * As permissões vêm no onboarding porque o app não funciona sem elas (HIG › Onboarding).
 */
@Composable
fun OnboardingScreen(vault: PasswordVault, onDone: () -> Unit) {
    // rememberSaveable: girar a tela no meio do onboarding não pode perder o código.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var recovery by rememberSaveable { mutableStateOf("") }
    when (step) {
        0 -> CreatePassword(vault) { recovery = it; step = 1 }
        1 -> ShowRecoveryCode(recovery) { step = 2 }
        else -> Permissions(onDone)
    }
}

@Composable
private fun CreatePassword(vault: PasswordVault, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val tooShort = pw.isNotEmpty() && pw.length < PasswordVault.MIN_LENGTH
    val mismatch = confirm.isNotEmpty() && pw != confirm
    val valid = pw.length >= PasswordVault.MIN_LENGTH && pw == confirm

    fun create() {
        if (!valid || busy) return
        busy = true
        // PBKDF2 com 120 mil iterações: fora da thread principal para não travar a tela.
        scope.launch {
            val code = withContext(Dispatchers.Default) { vault.setup(pw) }
            onCreated(code)
        }
    }

    LargeTitleScaffold("Bem-vindo") {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Rounded.Lock, Bloqueio.colors.accent, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Crie a senha mestra do Admin. Só com ela dá para remover regras, mudar cotas, horário, VPN ou desinstalar o app.",
                style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel, textAlign = TextAlign.Center,
            )
        }
        GroupedSection(
            header = "Senha mestra · passo 1 de 3",
            // Validação enquanto digita (HIG › Entering data), em vez de só desabilitar o botão.
            footer = when {
                tooShort -> "Faltam ${PasswordVault.MIN_LENGTH - pw.length} caracteres."
                mismatch -> "As senhas não são iguais."
                else -> "Mínimo de ${PasswordVault.MIN_LENGTH} caracteres."
            },
        ) {
            TextFieldCell(pw, { pw = it }, "Senha", secure = true, imeAction = ImeAction.Next)
            HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Bloqueio.colors.separator)
            TextFieldCell(
                confirm, { confirm = it }, "Confirmar senha", secure = true,
                imeAction = ImeAction.Done, onImeAction = ::create,
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "Criando…" else "Continuar", enabled = valid && !busy, onClick = ::create)
    }
}

@Composable
private fun ShowRecoveryCode(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
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
        TextButtonIos(if (copied) "Copiado" else "Copiar código") {
            clipboard.setText(AnnotatedString(code))
            copied = true
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Já anotei", onClick = onDone)
    }
}

@Composable
private fun Permissions(onDone: () -> Unit) {
    val status = rememberSetupStatus()
    LargeTitleScaffold("Permissões") {
        Text(
            "Para contar as cotas e bloquear Reels e Shorts, o Bloqueio precisa saber qual app está aberto e mostrar a cota na Now Bar.",
            style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
        )
        SetupSection(status, header = "Passo 3 de 3")
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (status.essentialsDone) "Começar a usar" else "Continuar sem ativar", onClick = onDone)
    }
}
