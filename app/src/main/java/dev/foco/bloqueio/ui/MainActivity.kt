package dev.foco.bloqueio.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.service.GuardService
import dev.foco.bloqueio.ui.screens.AdminScreen
import dev.foco.bloqueio.ui.screens.HomeScreen
import dev.foco.bloqueio.ui.screens.OnboardingScreen
import dev.foco.bloqueio.ui.screens.RulesScreen
import dev.foco.bloqueio.ui.screens.SecureFolderScreen
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioTheme
import dev.foco.bloqueio.ui.theme.BloqueioType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GuardService.start(this)
        setContent { BloqueioTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val vault = remember { PasswordVault(context) }
    var ready by rememberSaveable { mutableStateOf(vault.isSetUp()) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Bloqueio.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        if (!ready) {
            Box(Modifier.statusBarsPadding()) { OnboardingScreen(vault) { ready = true } }
        } else {
            Tabs(vault)
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Início", Icons.Rounded.Home),
    Tab("Regras", Icons.Rounded.Shield),
    Tab("Pasta segura", Icons.Rounded.FolderSpecial),
    Tab("Admin", Icons.Rounded.Lock),
)

@Composable
private fun Tabs(vault: PasswordVault) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            when (selected) {
                0 -> HomeScreen()
                1 -> RulesScreen()
                2 -> SecureFolderScreen()
                else -> AdminScreen(vault)
            }
        }
        // Tab bar estilo iOS
        Column(Modifier.background(Bloqueio.colors.cell).navigationBarsPadding()) {
            HorizontalDivider(thickness = 0.5.dp, color = Bloqueio.colors.separator)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp).selectableGroup()) {
                tabs.forEachIndexed { i, tab ->
                    val color = if (i == selected) Bloqueio.colors.accent else Bloqueio.colors.gray
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .selectable(selected = i == selected, role = Role.Tab, onClick = { selected = i })
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Ícone decorativo: o rótulo abaixo já é lido pelo TalkBack.
                        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                        Text(tab.label, style = BloqueioType.caption.copy(letterSpacing = 0.sp), color = color)
                    }
                }
            }
        }
    }
}
