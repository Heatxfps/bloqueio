package dev.foco.bloqueio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius

/** Campo de texto estilo célula do iOS (sem sublinhado Material). */
@Composable
fun TextFieldCell(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    label: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(label, style = BloqueioType.body, color = Bloqueio.colors.label, modifier = Modifier.width(110.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = BloqueioType.body, color = Bloqueio.colors.secondaryLabel.copy(alpha = 0.5f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = BloqueioType.body.copy(color = Bloqueio.colors.label),
                cursorBrush = SolidColor(Bloqueio.colors.accent),
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secure) KeyboardType.Password else keyboardType,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Linha com − valor + (stepper do iOS). */
@Composable
fun StepperCell(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, showDivider: Boolean = true) {
    Cell(
        title = title,
        value = value,
        showDivider = showDivider,
        trailing = {
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Bloqueio.colors.cellSecondary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−", onMinus)
                Box(Modifier.size(1.dp, 18.dp).background(Bloqueio.colors.separator))
                StepButton("+", onPlus)
            }
        },
    )
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp, 32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = BloqueioType.title2, color = Bloqueio.colors.label)
    }
}

/** Alerta estilo iOS: título, mensagem, conteúdo opcional e botões empilhados. */
@Composable
fun IosAlert(
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(Bloqueio.colors.cell)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = BloqueioType.headline, color = Bloqueio.colors.label, textAlign = TextAlign.Center)
            if (message != null) {
                Text(
                    message, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
            content()
            Spacer(Modifier.height(4.dp))
            buttons()
        }
    }
}

/** Campo dentro de alertas (fundo secundário arredondado). */
@Composable
fun AlertField(value: String, onValueChange: (String) -> Unit, placeholder: String, secure: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bloqueio.colors.cellSecondary),
    ) {
        TextFieldCell(value, onValueChange, placeholder, secure = secure)
    }
}

@Composable
fun TextButtonIos(text: String, color: androidx.compose.ui.graphics.Color = Bloqueio.colors.accent, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BloqueioType.body, color = color)
    }
}
