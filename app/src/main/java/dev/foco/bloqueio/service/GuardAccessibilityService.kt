package dev.foco.bloqueio.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import dev.foco.bloqueio.data.AppCatalog
import dev.foco.bloqueio.data.RulesRepository
import dev.foco.bloqueio.enforce.DistractionDetector
import dev.foco.bloqueio.enforce.Enforcer
import dev.foco.bloqueio.enforce.Verdict
import dev.foco.bloqueio.notify.NowBarNotifier
import dev.foco.bloqueio.securefolder.CleanEnvironment
import dev.foco.bloqueio.ui.BlockActivity
import dev.foco.bloqueio.ui.HubActivity
import dev.foco.bloqueio.enforce.BlockReason

/**
 * Olhos do app: sabe qual app está em primeiro plano (cotas), bloqueia na abertura
 * (reserva quando não há Device Owner) e fecha telas de consumo no bloqueio parcial.
 */
class GuardAccessibilityService : AccessibilityService() {
    private lateinit var catalog: AppCatalog
    private var allowed: Set<String> = emptySet()
    private var ignorable: Set<String> = emptySet()
    private var allowedAt = 0L
    private var lastActionAt = 0L
    private var lastScanAt = 0L
    private var lastBackAt = 0L
    private var lastBackPkg: String? = null

    override fun onServiceConnected() {
        catalog = AppCatalog(this)
        refreshSets()
        GuardState.accessibilityConnected.value = true
        GuardService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignorable) return
        val now = System.currentTimeMillis()
        if (now - allowedAt > 5 * 60_000) refreshSets()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            GuardState.foreground.value = pkg
            val config = RulesRepository.config.value
            if (CleanEnvironment.isActive(this) && pkg !in config.secureFolder) {
                CleanEnvironment.exit(this)
            }
        } else if (pkg != GuardState.foreground.value) {
            return
        }

        val config = RulesRepository.config.value
        val verdict = Enforcer.decide(
            pkg, catalog.category(pkg, config), pkg in allowed || pkg == config.vpn.tailscalePackage,
            GuardService.state(),
        )
        when (verdict) {
            is Verdict.Allow -> Unit
            is Verdict.Block -> if (now - lastActionAt > 1_500) {
                lastActionAt = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                // No descanso, o lugar certo é o Hub (relógio, apps liberados), não a tela de bloqueio.
                val target = if (verdict.reason == BlockReason.SCHEDULE) {
                    Intent(this, HubActivity::class.java)
                } else {
                    Intent(this, BlockActivity::class.java)
                        .putExtra(BlockActivity.EXTRA_REASON, verdict.reason.name)
                        .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                }
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            is Verdict.Partial -> {
                if (!DistractionDetector.supports(pkg) || now - lastScanAt < 350) return
                lastScanAt = now
                val root = rootInActiveWindow
                val found = DistractionDetector.detect(pkg, root) ?: return
                if (now - lastActionAt > 800) {
                    lastActionAt = now
                    // Aba de consumo → leva para uma aba permitida (o app continua aberto).
                    // Visualizador aberto de outra tela → "voltar" retorna a essa tela.
                    // Se um "voltar" recente não tirou a pessoa do player, tenta a aba permitida.
                    val backFailed = pkg == lastBackPkg && now - lastBackAt < 3_000
                    val moved = (found.viaTab || backFailed) && DistractionDetector.escapeTo(pkg, root)
                    if (!moved) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        lastBackAt = now
                        lastBackPkg = pkg
                    }
                    Toast.makeText(this, "${found.area} bloqueado. ${verdict.why}", Toast.LENGTH_SHORT).show()
                    NowBarNotifier.notifyPartialBlock(this, found.area, verdict.why)
                }
            }
        }
    }

    private fun refreshSets() {
        allowed = catalog.alwaysAllowed()
        ignorable = buildSet {
            add("com.android.systemui")
            getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { add(it.packageName) }
        }
        allowedAt = System.currentTimeMillis()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        GuardState.accessibilityConnected.value = false
        super.onDestroy()
    }
}
