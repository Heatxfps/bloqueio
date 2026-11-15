package dev.foco.bloqueio.securefolder

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import dev.foco.bloqueio.data.RuleType
import dev.foco.bloqueio.data.RulesRepository

/**
 * "Ambiente limpo" da pasta segura (docs/funcionalidades/04-pasta-segura.md).
 *
 * Enquanto um app da pasta (ex.: banco) está aberto, o celular parece de fábrica:
 *  - opções do desenvolvedor, depuração USB e depuração por Wi‑Fi desligadas;
 *  - **o serviço de acessibilidade do Bloqueio desligado** — bancos tratam app com
 *    acessibilidade instalado fora da Play Store como risco ("golpe da mão fantasma").
 *
 * Sem acessibilidade, a saída do app é detectada pelo **acesso ao uso** do Android
 * ([foregroundPackage]). Sem esse acesso, a acessibilidade não é desligada (seria impossível
 * religar ao sair). Apps de distração (redes, jogos, bloqueados) nunca desligam a
 * acessibilidade, para a pasta não virar uma brecha.
 *
 * Requer WRITE_SECURE_SETTINGS e, para a acessibilidade, GET_USAGE_STATS (ambos via ADB).
 */
object CleanEnvironment {
    private const val TAG = "CleanEnv"
    private val KEYS = listOf(
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        Settings.Global.ADB_ENABLED,
        "adb_wifi_enabled",
    )
    private const val PREFS = "clean_env"
    private const val KEY_ACTIVE = "active"
    private const val KEY_A11Y_OFF = "a11y_off"
    private const val KEY_PKG = "pkg"

    fun canWriteSecureSettings(context: Context) =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isActive(context: Context) = prefs(context).getBoolean(KEY_ACTIVE, false)

    /** O ambiente limpo desligou a acessibilidade do Bloqueio (a auto-cura deve esperar). */
    fun accessibilityPaused(context: Context) = prefs(context).getBoolean(KEY_A11Y_OFF, false)

    /** Entra no ambiente limpo e abre o app. Retorna false se o app não pôde ser aberto. */
    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        enter(context, pkg)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    @Synchronized
    fun enter(context: Context, pkg: String) {
        if (!canWriteSecureSettings(context) || isActive(context)) return
        val cr = context.contentResolver
        val edit = prefs(context).edit()
        KEYS.forEach { key -> edit.putInt(key, Settings.Global.getInt(cr, key, 0)) }
        val pauseA11y = hasUsageAccess(context) && !isDistraction(pkg)
        edit.putBoolean(KEY_ACTIVE, true).putBoolean(KEY_A11Y_OFF, pauseA11y).putString(KEY_PKG, pkg).commit()
        KEYS.forEach { key -> runCatching { Settings.Global.putInt(cr, key, 0) } }
        if (pauseA11y) setOwnAccessibility(context, enabled = false)
        Log.i(TAG, "entrou ($pkg) acessibilidade desligada=$pauseA11y")
    }

    @Synchronized
    fun exit(context: Context) {
        if (!isActive(context)) return
        val cr = context.contentResolver
        val p = prefs(context)
        // Liga a flag de desenvolvedor antes do ADB (o sistema exige essa ordem).
        KEYS.forEach { key -> runCatching { Settings.Global.putInt(cr, key, p.getInt(key, 0)) } }
        if (p.getBoolean(KEY_A11Y_OFF, false)) setOwnAccessibility(context, enabled = true)
        p.edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_A11Y_OFF, false).apply()
        Log.i(TAG, "saiu: configurações restauradas")
    }

    /** Apps da pasta + o próprio Bloqueio e partes do sistema que não contam como "sair". */
    fun isInsideFolder(context: Context, pkg: String): Boolean =
        pkg in RulesRepository.config.value.secureFolder ||
            pkg == "com.android.systemui" ||
            pkg.startsWith("com.google.android.inputmethod") ||
            pkg == "com.samsung.android.honeyboard"

    /** App em primeiro plano segundo o acesso ao uso (eventos dos últimos 30 s). */
    fun foregroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 30_000, end)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }

    private fun isDistraction(pkg: String): Boolean {
        val c = RulesRepository.config.value
        return pkg in c.packagesOf(RuleType.SOCIAL) || pkg in c.packagesOf(RuleType.GAME) ||
            pkg in c.packagesOf(RuleType.BLOCK)
    }

    private fun setOwnAccessibility(context: Context, enabled: Boolean) {
        val me = "${context.packageName}/dev.foco.bloqueio.service.GuardAccessibilityService"
        val cr = context.contentResolver
        val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
        val next = if (enabled) current + me else current
        runCatching {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
            if (enabled) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
