package dev.foco.bloqueio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.BlockReason
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioTheme
import dev.foco.bloqueio.ui.theme.BloqueioType
import java.time.Instant
import java.time.ZoneId

/**
 * Tela de bloqueio: calma, nunca agressiva (docs/DESIGN.md → Tom de voz).
 * Título, ícone e "quando volta" mudam conforme o motivo do bloqueio.
 */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reason = runCatching { BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }
            .getOrDefault(BlockReason.RULE)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val label = pkg?.let { AppCatalog(this).label(it) }
        val returnsAt = returnsAt(reason)
        setContent {
            BloqueioTheme {
                BackHandler { goHome() }
                val (icon, tint) = when (reason) {
                    BlockReason.SCHEDULE -> Icons.Rounded.Bedtime to Bloqueio.colors.games
                    BlockReason.RULE -> Icons.Rounded.Block to Bloqueio.colors.grayFill
                    else -> Icons.Rounded.HourglassBottom to Bloqueio.colors.focusFill
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Bloqueio.colors.background)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val turn = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        if (icon == Icons.Rounded.HourglassBottom) {
                            turn.animateTo(180f, tween(durationMillis = 900, delayMillis = 250, easing = FastOutSlowInEasing))
                        }
                    }
                    Box(Modifier.graphicsLayer { rotationZ = turn.value }) {
                        IconTile(icon, tint, size = 84.dp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        reason.title, style = BloqueioType.largeTitle, color = Bloqueio.colors.label,
                        textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (label != null) {
                        Text(label, style = BloqueioType.headline, color = Bloqueio.colors.secondaryLabel)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        reason.message, style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                    if (returnsAt != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(returnsAt, style = BloqueioType.headline, color = Bloqueio.colors.label)
                    }
                    Spacer(Modifier.height(40.dp))
                    PrimaryButton("Voltar ao início") { goHome() }
                }
            }
        }
    }

    /** Quando o app volta a funcionar, em linguagem simples. */
    private fun returnsAt(reason: BlockReason): String? {
        val config = RulesRepository.config.value
        return when (reason) {
            BlockReason.SCHEDULE -> "Volta às ${ScheduleWindow.format(config.schedule.endMinute)}"
            BlockReason.FOCUS -> {
                val t = Instant.ofEpochMilli(config.focus.until).atZone(ZoneId.systemDefault()).toLocalTime()
                "Foco até %02d:%02d".format(t.hour, t.minute)
            }
            BlockReason.GAME_QUOTA -> "Volta amanhã à meia-noite"
            BlockReason.RULE -> null
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_PACKAGE = "package"
    }
}
