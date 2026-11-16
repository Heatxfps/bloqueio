package dev.foco.bloqueio.service

import dev.foco.bloqueio.enforce.DistractionDetector
import android.util.Log
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.Category
import dev.foco.bloqueio.enforce.EnforceState
import dev.foco.bloqueio.enforce.Enforcer
import dev.foco.bloqueio.enforce.QuotaTracker
import dev.foco.bloqueio.enforce.Verdict
import dev.foco.bloqueio.notify.NowBarNotifier
import dev.foco.bloqueio.ui.HubActivity
import dev.foco.bloqueio.securefolder.CleanEnvironment
import dev.foco.bloqueio.security.AdminSession
import dev.foco.bloqueio.vpn.HostMonitor
import dev.foco.bloqueio.vpn.TailscaleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Serviço em primeiro plano: conta cotas, aplica suspensões, monitora a VPN,
 * atualiza a Now Bar e se auto-cura. Tick de 10 s.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var policy: PolicyManager
    private lateinit var catalog: AppCatalog
    private lateinit var monitor: HostMonitor

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            catalog.invalidate()
            // App (re)instalado: reaplica regras na hora (docs/funcionalidades/07-regras.md).
            scope.launch { applyPolicies(force = true) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        policy = PolicyManager(this)
        catalog = AppCatalog(this)
        monitor = HostMonitor(policy, TailscaleController(this))
        startForeground(
            NowBarNotifier.ID,
            NowBarNotifier.build(this, currentState(), monitor.state.value, inUse()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            RulesRepository.config.collect {
                policy.applyBaseline(it)
                // Mudou regra/horário/pausa: reaplica já, sem esperar o próximo ciclo de 30 s.
                runCatching { applyPolicies() }
                runCatching { syncHub() }
                // Foco começou/terminou, pausa, cotas mudadas: a Now Bar troca na hora.
                runCatching { refreshNowBar() }
            }
        }
        // Abriu/fechou rede social ou jogo, ou o admin destravou: troca o cartão na hora.
        scope.launch { GuardState.foreground.collect { runCatching { refreshNowBar() } } }
        scope.launch { AdminSession.unlockedUntil.collect { runCatching { refreshNowBar() } } }
        scope.launch {
            monitor.state.collect {
                GuardState.vpn.value = it
                runCatching { refreshNowBar() }
            }
        }
        scope.launch { loop() }
        scope.launch { counter() }
        scope.launch { watchCleanEnvironment() }
        scope.launch {
            while (isActive) {
                runCatching { monitor.tick(RulesRepository.config.value.vpn) }
                delay(30_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        var last = System.currentTimeMillis()
        var lastSuspendSync = 0L
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val elapsed = ((now - last) / 1000).coerceIn(0, 30)
            last = now
            runCatching {
                RulesRepository.expireFocusIfNeeded(now)
                if (now - lastSuspendSync > 30_000) {
                    applyPolicies()
                    lastSuspendSync = now
                }
                syncHub()
                healAccessibility()
                restoreCleanEnvIfOrphan()
                refreshNowBar()
            }
            delay(10_000)
        }
    }

    private fun refreshNowBar() {
        NowBarNotifier.update(this, currentState(), monitor.state.value, inUse())
    }

    /**
     * Conta as cotas de segundo em segundo. Desconta só o que a pessoa está VENDO: o app da janela
     * em foco, com a tela ligada. Gaveta de notificações aberta, tela de bloqueio ou tela apagada
     * = pausa. Não depende de toque nem de eventos: um vídeo rodando parado continua contando.
     */
    private suspend fun counter() {
        var last = System.currentTimeMillis()
        var lastLogged: String? = ""
        while (scope.isActive) {
            delay(1_000)
            val now = System.currentTimeMillis()
            val elapsed = ((now - last) / 1000).coerceIn(0, 5)
            last += elapsed * 1000
            runCatching {
                val use = inUse()
                if (use != null && elapsed > 0) QuotaTracker.add(use.category, elapsed)
                val tag = use?.let { "${it.label}/${it.category}" + (it.why?.let { w -> " [$w]" } ?: "") }
                if (tag != lastLogged) {
                    Log.i("Quota", if (tag == null) "pausado (${pauseReason()})" else "contando: $tag")
                    lastLogged = tag
                }
                // Com app de cota na tela, a notificação acompanha cada segundo.
                if (use != null) refreshNowBar()
            }
        }
    }

    private fun pauseReason(): String {
        if (!getSystemService(PowerManager::class.java).isInteractive) return "tela apagada"
        if (GuardAccessibilityService.instance?.systemPanelOnTop() == true) return "gaveta ou tela de bloqueio"
        val pkg = visiblePackage()
        return if (pkg != null && DistractionDetector.supports(pkg)) "${catalog.label(pkg)} fora de Reels/Shorts"
        else "app sem cota: $pkg"
    }

    /** Pacote do app que a pessoa está vendo (janela em foco), ou null se um painel do sistema cobre. */
    private fun visiblePackage(): String? {
        val a11y = GuardAccessibilityService.instance ?: return GuardState.foreground.value
        if (a11y.systemPanelOnTop()) return null
        return a11y.focusedAppPackage() ?: GuardState.foreground.value
    }

    /** Rede social ou jogo aberto agora, com a tela ligada (é quando a cota desce). */
    private fun inUse(): NowBarNotifier.InUse? {
        if (!getSystemService(PowerManager::class.java).isInteractive) return null
        val pkg = visiblePackage() ?: return null
        val cat = catalog.category(pkg, RulesRepository.config.value)
        if (cat == Category.NONE) return null
        // Redes com detector (Instagram, Facebook, YouTube, TikTok...): a cota só desce na área
        // de consumo (Reels, Shorts). Feed, busca, mensagens e perfil não descontam.
        // Jogos e redes sem detector descontam o app inteiro.
        if (cat == Category.SOCIAL && DistractionDetector.supports(pkg)) {
            val a11y = GuardAccessibilityService.instance ?: return null
            val area = a11y.consumptionOnScreen(pkg) ?: return null
            return NowBarNotifier.InUse("${catalog.label(pkg)} · ${area.area}", cat, area.reason)
        }
        return NowBarNotifier.InUse(catalog.label(pkg), cat)
    }

    /** Suspende (Device Owner) tudo que está em Block; o resto fica com a acessibilidade. */
    /**
     * Com a acessibilidade desligada pelo ambiente limpo, quem percebe a saída do banco é o
     * acesso ao uso. Sai só depois de 4 s fora da pasta (o banco pode abrir telas do sistema).
     */
    private suspend fun watchCleanEnvironment() {
        var outsideSince = 0L
        while (scope.isActive) {
            if (CleanEnvironment.isActive(this) && CleanEnvironment.accessibilityPaused(this)) {
                val fg = CleanEnvironment.foregroundPackage(this)
                val now = System.currentTimeMillis()
                if (fg != null && !CleanEnvironment.isInsideFolder(this, fg)) {
                    if (outsideSince == 0L) outsideSince = now
                    if (now - outsideSince >= 4_000) {
                        CleanEnvironment.exit(this)
                        outsideSince = 0L
                    }
                } else {
                    outsideSince = 0L
                }
            }
            delay(2_000)
        }
    }

    /** Liga o Hub como tela inicial no começo do descanso e devolve o launcher no fim. */
    private fun syncHub() {
        val want = currentState().phoneLocked && policy.isDeviceOwner
        val wasActive = policy.isHubActive
        policy.setHubAsHome(want) // idempotente; conserta o estado real (ex.: depois de atualizar o app)
        if (want && !wasActive) {
            runCatching {
                startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun applyPolicies(force: Boolean = false) {
        if (!policy.isDeviceOwner) return
        val state = currentState()
        val allowed = catalog.alwaysAllowed() + state.config.vpn.tailscalePackage
        val desired = catalog.launchableApps(force)
            .map { it.packageName }
            .filter { pkg ->
                val v = Enforcer.decide(pkg, catalog.category(pkg, state.config), pkg in allowed, state)
                v is Verdict.Block
            }
            .toSet()
        policy.syncSuspended(desired)
    }

    /** Religa o serviço de acessibilidade se alguém o desativou (requer WRITE_SECURE_SETTINGS). */
    private var a11yStuckSince = 0L

    private fun healAccessibility() {
        if (!CleanEnvironment.canWriteSecureSettings(this)) return
        // O ambiente limpo desligou a acessibilidade de propósito (banco aberto): não religar.
        if (CleanEnvironment.accessibilityPaused(this)) return
        val me = "$packageName/${GuardAccessibilityService::class.java.name}"
        val cr = contentResolver
        val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val listed = enabled.split(':').any { it.equals(me, ignoreCase = true) }
        // Depois de atualizar o app, o Android às vezes fica preso "ligando" o serviço: está na
        // lista mas nunca conecta. Passados 30 s assim, tira e põe de novo para destravar.
        if (listed && !GuardState.accessibilityConnected.value) {
            if (a11yStuckSince == 0L) a11yStuckSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - a11yStuckSince > 30_000) {
                val others = enabled.split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
                runCatching {
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me).joinToString(":"))
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                }
                a11yStuckSince = 0L
            }
            return
        }
        a11yStuckSince = 0L
        if (!listed) {
            val next = if (enabled.isBlank()) me else "$enabled:$me"
            runCatching {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        }
    }

    /** Se o processo morreu com o ambiente limpo ativo, restaura quando sair da pasta. */
    private fun restoreCleanEnvIfOrphan() {
        if (!CleanEnvironment.isActive(this)) return
        if (CleanEnvironment.accessibilityPaused(this)) return // vigiado por watchCleanEnvironment()
        val fg = GuardState.foreground.value ?: return
        if (fg !in RulesRepository.config.value.secureFolder && fg !in catalog.alwaysAllowed() - packageName) {
            CleanEnvironment.exit(this)
        }
    }

    private fun currentState(): EnforceState = state()

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GuardService::class.java))
            }
        }

        fun state(): EnforceState {
            val usage = QuotaTracker.current()
            val t = LocalTime.now()
            return EnforceState(
                RulesRepository.config.value, usage.socialSec, usage.gamesSec,
                System.currentTimeMillis(), t.hour * 60 + t.minute,
            )
        }
    }
}

