package dev.foco.bloqueio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

/**
 * Texto em que só os dígitos que mudam rolam (como o `.numericText()` do iOS): ao diminuir,
 * o novo dígito desce de cima; ao aumentar, sobe de baixo. Para o TalkBack é um texto só.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    description: String = text,
) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { index, ch ->
            // A chave por posição (contando do fim) mantém os dígitos alinhados quando o tamanho muda.
            androidx.compose.runtime.key(text.length - index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val down = targetState < initialState
                        val spec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 420f)
                        (slideInVertically(spec) { h -> if (down) -h else h } + fadeIn()) togetherWith
                            (slideOutVertically(spec) { h -> if (down) h else -h } + fadeOut()) using
                            SizeTransform(clip = true)
                    },
                    label = "digito",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}
