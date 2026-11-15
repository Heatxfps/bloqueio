package dev.foco.bloqueio.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.service.GuardService
import dev.foco.bloqueio.ui.components.MessageBanner
import dev.foco.bloqueio.ui.screens.AdminScreen
import dev.foco.bloqueio.ui.screens.HomeScreen
import dev.foco.bloqueio.ui.screens.OnboardingScreen
import dev.foco.bloqueio.ui.screens.RulesScreen
import dev.foco.bloqueio.ui.screens.SecureFolderScreen
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioTheme
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.LocalBottomBarInset
import dev.foco.bloqueio.ui.theme.LocalGlassEnabled
import dev.foco.bloqueio.ui.theme.pressScale
import kotlin.math.abs

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

private val BarHeight = 64.dp

/**
 * Conteúdo rola por baixo de uma barra de abas flutuante de vidro (camada funcional,
 * HIG › Liquid Glass). O conteúdo é a fonte do desfoque; a barra é o único vidro da tela.
 */
@Composable
private fun Tabs(vault: PasswordVault) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // Voltar numa aba que não é a Início leva para a Início antes de sair do app.
    BackHandler(enabled = selected != 0) { selected = 0 }
    val hazeState = rememberHazeState(blurEnabled = LocalGlassEnabled.current)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().hazeSource(hazeState)) {
            // Aviso fixo (sucesso/erro) logo abaixo da barra de status, acima da aba atual.
            Box(Modifier.statusBarsPadding()) { MessageBanner() }
            // As telas reservam espaço no fim para o último item não ficar sob a barra.
            CompositionLocalProvider(LocalBottomBarInset provides BarHeight + 24.dp + navBottom) {
                Crossfade(selected, animationSpec = tween(150), label = "tab", modifier = Modifier.weight(1f)) { tab ->
                    when (tab) {
                        0 -> HomeScreen()
                        1 -> RulesScreen()
                        2 -> SecureFolderScreen()
                        else -> AdminScreen(vault)
                    }
                }
            }
        }
        GlassTabBar(
            selected = selected,
            onSelect = { selected = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(BarHeight),
        )
    }
}

@Composable
private fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, hazeState: HazeState, modifier: Modifier) {
    val c = Bloqueio.colors
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(50)
    val surface = if (LocalGlassEnabled.current) {
        Modifier.hazeEffect(
            hazeState,
            HazeStyle(
                backgroundColor = c.background,
                tints = listOf(HazeTint(c.cell.copy(alpha = if (dark) 0.55f else 0.62f))),
                blurRadius = 24.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(c.cell),
            ),
        )
    } else {
        // Sem vidro (Android 11 ou alto contraste): superfície opaca.
        Modifier.background(c.cell)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(shape)
            .then(surface)
            .border(
                0.75.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.75f), Color.White.copy(alpha = 0.04f)),
                ),
                shape,
            ),
    ) {
        val tabWidth = maxWidth / tabs.size
        val target = tabWidth * selected
        // Pílula "líquida": desliza com mola e estica enquanto se move.
        val offset by animateDpAsState(target, spring(dampingRatio = 0.72f, stiffness = 380f), label = "pill")
        val stretch = (abs((offset - target).value) / tabWidth.value).coerceIn(0f, 1f)
        Box(
            Modifier
                .offset(x = offset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(5.dp)
                .graphicsLayer { scaleX = 1f + stretch * 0.3f }
                .clip(shape)
                .background(c.accent.copy(alpha = if (dark) 0.22f else 0.13f)),
        )
        // Brilho especular no topo da cápsula.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.32f), Color.Transparent),
                    ),
                ),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            tabs.forEachIndexed { i, tab ->
                val isSel = i == selected
                val color = if (isSel) c.accent else c.gray
                val source = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(source, pressed = 0.9f)
                        .selectable(
                            selected = isSel,
                            interactionSource = source,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(i) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Ícone decorativo: o rótulo abaixo já é lido pelo TalkBack.
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(
                        tab.label,
                        style = BloqueioType.caption.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
