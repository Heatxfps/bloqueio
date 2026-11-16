package dev.foco.bloqueio.ui

import dev.foco.bloqueio.data.Focus
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.securefolder.CleanEnvironment
import androidx.compose.foundation.lazy.grid.GridItemSpan
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RuleType
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.security.AdminSession
import dev.foco.bloqueio.security.PasswordVault
import dev.foco.bloqueio.service.GuardService
import dev.foco.bloqueio.ui.components.AlertField
import dev.foco.bloqueio.ui.components.AppIcon
import dev.foco.bloqueio.ui.components.IosAlert
import dev.foco.bloqueio.ui.components.SegmentedControl
import dev.foco.bloqueio.ui.components.TextButtonIos
import dev.foco.bloqueio.ui.screens.rememberNow
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioTheme
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.DarkColors
import dev.foco.bloqueio.ui.theme.LocalColors
import dev.foco.bloqueio.ui.theme.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hub do horário de descanso (docs/funcionalidades/03-horario.md).
 *
 * Durante o descanso é a tela inicial obrigatória do celular: relógio, contagem regressiva,
 * apps essenciais e saída de emergência pelo Admin. A barra de notificações e os controles
 * rápidos continuam funcionando (não é lock task). Quando o descanso acaba ou é pausado,
 * devolve o launcher normal e se fecha.
 */
class HubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloqueioTheme {
                // Sempre escuro: é noite, e a tela precisa ser calma.
                CompositionLocalProvider(LocalColors provides DarkColors) {
                    BackHandler { /* o Hub é a tela inicial: voltar não sai dele */ }
                    Hub(onFinished = ::leave)
                }
            }
        }
    }

    /** Fim do descanso (ou pausa): devolve o launcher normal e fecha o Hub. */
    private fun leave() {
        PolicyManager(this).setHubAsHome(false)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun Hub(onFinished: () -> Unit) {
    val context = LocalContext.current
    val config by RulesRepository.config.collectAsState()
    val now by rememberNow(1_000)
    val state = remember(config, now) { GuardService.state() }
    var admin by remember { mutableStateOf(false) }

    LaunchedEffect(state.phoneLocked) {
        if (!state.phoneLocked) {
            delay(1_200)
            onFinished()
        }
    }

    val catalog = remember { AppCatalog(context) }
    val apps by produceState(initialValue = emptyList<Pair<String, String>>(), config.rules) {
        value = withContext(Dispatchers.Default) {
            val launchable = catalog.launchableApps(force = true).associateBy { it.packageName }
            config.packagesOf(RuleType.ESSENTIAL).mapNotNull { pkg -> launchable[pkg]?.let { pkg to it.label } }
                .sortedBy { it.second.lowercase() }
        }
    }
    // Pasta segura (bancos): liberada no descanso e aberta no ambiente limpo.
    val folder by produceState(initialValue = emptyList<Pair<String, String>>(), config.secureFolder) {
        value = withContext(Dispatchers.Default) {
            config.secureFolder.filter { catalog.isInstalled(it) }.map { it to catalog.label(it) }
        }
    }

    val t = LocalDateTime.now()
    val minuteNow = t.hour * 60 + t.minute
    val start = config.schedule.startMinute
    val end = config.schedule.endMinute
    val total = ((end - start + 1440) % 1440).coerceAtLeast(1)
    // Foco "Bloquear" fora do descanso: a contagem é a do foco.
    val byFocus = !state.inSchedule && state.focusLocks
    val focus = config.focus
    val remaining = if (byFocus) ((focus.until - now) / 60_000L).toInt() + 1 else (end - minuteNow + 1440) % 1440
    val elapsedFraction = if (byFocus) {
        val totalMs = (focus.until - focus.startedAt).coerceAtLeast(1)
        (1f - (focus.until - now).toFloat() / totalMs).coerceIn(0f, 1f)
    } else {
        (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    }
    val endLabel = if (byFocus) {
        val e = java.time.Instant.ofEpochMilli(focus.until).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        "%02d:%02d".format(e.hour, e.minute)
    } else {
        ScheduleWindow.format(end)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bloqueio.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            "%02d:%02d".format(t.hour, t.minute),
            style = BloqueioType.largeTitle.copy(fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
            color = Bloqueio.colors.label,
        )
        Text(
            t.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))),
            style = BloqueioType.callout, color = Bloqueio.colors.secondaryLabel,
        )

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Bloqueio.colors.cell)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountdownRing(elapsedFraction)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    when {
                        byFocus -> "Celular bloqueado para foco"
                        state.inSchedule -> "Hora de descansar"
                        else -> "Tudo certo!"
                    },
                    style = BloqueioType.headline, color = Bloqueio.colors.label,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (state.phoneLocked) "Libera às $endLabel" else "Liberando o celular…",
                    style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel,
                )
                Spacer(Modifier.height(4.dp))
                Text(remainingText(remaining), style = BloqueioType.title2, color = Bloqueio.colors.label)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("APPS LIBERADOS", style = BloqueioType.caption, color = Bloqueio.colors.secondaryLabel)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(apps, key = { it.first }) { (pkg, label) -> HubApp(pkg, label) { launchApp(context, pkg) } }
            if (folder.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "PASTA SEGURA · AMBIENTE LIMPO",
                        style = BloqueioType.caption, color = Bloqueio.colors.secondaryLabel,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(folder, key = { "f:" + it.first }) { (pkg, label) ->
                    HubApp(pkg, label) { CleanEnvironment.launch(context, pkg) }
                }
            }
        }

        Text(
            "Deslize de cima para baixo para ver notificações e controles.",
            style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        TextButtonIos("Emergência: pausar com o Admin", color = Bloqueio.colors.accent) { admin = true }
        Spacer(Modifier.height(8.dp))
    }

    if (admin) PauseAlert(onDismiss = { admin = false })
}

private fun remainingText(minutes: Int): String = when {
    minutes <= 0 -> "terminando"
    minutes < 60 -> "faltam $minutes min"
    else -> "faltam ${minutes / 60} h ${minutes % 60} min"
}

/** Anel que vai se completando ao longo da noite (lua = cor de jogos/índigo). */
@Composable
private fun CountdownRing(fraction: Float) {
    val animated by animateFloatAsState(fraction, label = "noite")
    val color = Bloqueio.colors.games
    Canvas(Modifier.size(64.dp)) {
        val stroke = size.minDimension * 0.14f
        val d = size.minDimension - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
        drawArc(color, -90f, 360f * animated, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun launchApp(context: android.content.Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun HubApp(pkg: String, label: String, onOpen: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressScale(source, pressed = 0.92f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(pkg, size = 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = BloqueioType.caption, color = Bloqueio.colors.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Saída de emergência: senha mestra → pausa o descanso por um tempo. */
@Composable
private fun PauseAlert(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { PasswordVault(context) }
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var idx by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val minutes = listOf(15, 30, 60)

    IosAlert(
        title = "Pausar o descanso",
        message = msg ?: "Digite a senha mestra. O celular volta ao normal pelo tempo escolhido.",
        onDismiss = onDismiss,
        content = {
            AlertField(pw, { pw = it; msg = null }, "Senha mestra", secure = true)
            Box(Modifier.padding(top = 4.dp)) {
                SegmentedControl(minutes.map { "$it min" }, idx, { idx = it })
            }
        },
    ) {
        TextButtonIos(if (busy) "Verificando…" else "Pausar") {
            if (busy || pw.isEmpty()) return@TextButtonIos
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.Default) { vault.verify(pw) }
                busy = false
                when (r) {
                    PasswordVault.Result.Ok -> {
                        AdminSession.unlock(1)
                        val until = System.currentTimeMillis() + minutes[idx] * 60_000L
                        // Emergência: pausa o descanso e encerra um foco "Bloquear" em andamento.
                        RulesRepository.adminUpdate {
                            val f = if (it.focus.level == FocusLevel.LOCK) Focus() else it.focus
                            it.copy(schedule = it.schedule.copy(pausedUntil = until), focus = f)
                        }
                        AdminSession.lock()
                        onDismiss()
                    }
                    PasswordVault.Result.Wrong -> msg = "Senha incorreta."
                    is PasswordVault.Result.LockedOut -> msg = "Muitas tentativas. Tente em ${r.seconds} s."
                }
            }
        }
        TextButtonIos("Cancelar", color = Bloqueio.colors.secondaryLabel, onClick = onDismiss)
    }
}
