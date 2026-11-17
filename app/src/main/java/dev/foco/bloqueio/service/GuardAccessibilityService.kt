package dev.foco.bloqueio.service

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        instance = this
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
        enforce(pkg, now) { rootInActiveWindow }
    }

    /**
     * Rechecagem periódica (chamada pelo contador a cada segundo, na thread principal). Eventos não
     * bastam: ao fechar a gaveta, o app que estava por baixo volta ao foco sem mandar
     * WINDOW_STATE_CHANGED, e a rolagem dele era ignorada por não bater com o "foreground".
     * Aqui o app em foco é lido direto das janelas.
     */
    fun recheck() {
        if (systemPanelOnTop()) return
        val root = focusedAppRoot() ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg in ignorable) return
        GuardState.foreground.value = pkg
        enforce(pkg, System.currentTimeMillis()) { root }
    }

    private fun enforce(pkg: String, now: Long, rootOf: () -> AccessibilityNodeInfo?) {
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
                val target = if (verdict.reason == BlockReason.SCHEDULE || GuardService.state().focusLocks) {
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
                val root = rootOf()
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

    companion object {
        /** Instância conectada (mesmo processo), para o contador perguntar o que está na tela. */
        @Volatile var instance: GuardAccessibilityService? = null
            private set
    }

    /**
     * Gaveta de notificações, painel rápido ou tela de bloqueio por cima: a janela do sistema
     * está com o foco (no Samsung, a "NotificationShade").
     */
    fun systemPanelOnTop(): Boolean = runCatching {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused }
    }.getOrDefault(false)

    /** Pacote da janela de app em foco (a que a pessoa está vendo e usando). */
    fun focusedAppPackage(): String? = focusedAppRoot()?.packageName?.toString()

    private fun focusedAppRoot(): AccessibilityNodeInfo? = runCatching {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val w = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive }
        w?.root
    }.getOrNull()

    /**
     * Área de consumo (Reels, Shorts...) que está na tela agora em [pkg], ou null se a pessoa
     * está em outra parte do app (feed, busca, mensagens, perfil...). Base da contagem da cota.
     */
    fun consumptionOnScreen(pkg: String): DistractionDetector.Detection? {
        val root = focusedAppRoot() ?: return null
        if (root.packageName?.toString() != pkg) return null
        return DistractionDetector.detect(pkg, root, log = false)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        GuardState.accessibilityConnected.value = false
        super.onDestroy()
    }
}
