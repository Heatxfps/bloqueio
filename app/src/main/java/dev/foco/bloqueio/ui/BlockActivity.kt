package dev.foco.bloqueio.ui

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
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.ui.components.IconTile
import dev.foco.bloqueio.ui.components.PrimaryButton
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioTheme
import dev.foco.bloqueio.ui.theme.BloqueioType

/** Tela de bloqueio: calma, nunca agressiva (docs/DESIGN.md → Tom de voz). */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val label = pkg?.let { AppCatalog(this).label(it) }
        setContent {
            BloqueioTheme {
                BackHandler { goHome() }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Bloqueio.colors.background)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconTile(Icons.Rounded.HourglassBottom, Bloqueio.colors.focus, size = 84.dp)
                    Spacer(Modifier.height(24.dp))
                    Text("Hora de focar", style = BloqueioType.largeTitle, color = Bloqueio.colors.label)
                    Spacer(Modifier.height(8.dp))
                    if (label != null) {
                        Text(label, style = BloqueioType.headline, color = Bloqueio.colors.secondaryLabel)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        message, style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    PrimaryButton("Voltar ao início") { goHome() }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PACKAGE = "package"
    }
}
