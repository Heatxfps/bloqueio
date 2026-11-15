package dev.foco.bloqueio.service

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
        monitor = HostMonitor(policy, TailscaleController(this)) { CleanEnvironment.vpnPaused(this) }
        startForeground(
            NowBarNotifier.ID,
            NowBarNotifier.build(this, currentState(), monitor.state.value),
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
            }
        }
        scope.launch { monitor.state.collect { GuardState.vpn.value = it } }
        scope.launch { loop() }
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
                countUsage(elapsed)
                if (now - lastSuspendSync > 30_000) {
                    applyPolicies()
                    lastSuspendSync = now
                }
                syncHub()
                healAccessibility()
                restoreCleanEnvIfOrphan()
                NowBarNotifier.update(this, currentState(), monitor.state.value)
            }
            delay(10_000)
        }
    }

    private fun countUsage(seconds: Long) {
        if (seconds <= 0) return
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        val pkg = GuardState.foreground.value ?: return
        if (!screenOn) return
        val cat = catalog.category(pkg, RulesRepository.config.value)
        if (cat != Category.NONE) QuotaTracker.add(cat, seconds)
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
        val want = currentState().inSchedule && policy.isDeviceOwner
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
    private fun healAccessibility() {
        if (!CleanEnvironment.canWriteSecureSettings(this)) return
        // O ambiente limpo desligou a acessibilidade de propósito (banco aberto): não religar.
        if (CleanEnvironment.accessibilityPaused(this)) return
        val me = "$packageName/${GuardAccessibilityService::class.java.name}"
        val cr = contentResolver
        val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (enabled.split(':').none { it.equals(me, ignoreCase = true) }) {
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

