package dev.foco.bloqueio.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.inputmethod.InputMethodManager
import dev.foco.bloqueio.enforce.Category

data class InstalledApp(val packageName: String, val label: String, val isGame: Boolean)

/** Lista de apps instalados e classificação em categorias. */
class AppCatalog(private val context: Context) {
    private val pm = context.packageManager

    @Volatile private var cache: List<InstalledApp> = emptyList()
    @Volatile private var cacheAt = 0L

    fun launchableApps(force: Boolean = false): List<InstalledApp> {
        val now = System.currentTimeMillis()
        if (!force && cache.isNotEmpty() && now - cacheAt < 5 * 60_000) return cache
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        cache = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), it.isGameApp()) }
            .sortedBy { it.label.lowercase() }
        cacheAt = now
        return cache
    }

    fun invalidate() {
        cacheAt = 0
    }

    fun label(pkg: String): String = runCatching {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(pkg)

    fun icon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    fun isInstalled(pkg: String) = runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    fun category(pkg: String, config: AppConfig): Category = when {
        pkg in config.packagesOf(RuleType.GAME) -> Category.GAME
        pkg in config.packagesOf(RuleType.SOCIAL) -> Category.SOCIAL
        isGame(pkg) -> Category.GAME
        else -> Category.NONE
    }

    private val gameCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private fun isGame(pkg: String): Boolean = gameCache.getOrPut(pkg) {
        runCatching { pm.getApplicationInfo(pkg, 0).isGameApp() }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun ApplicationInfo.isGameApp() =
        category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

    /** Pacotes que nunca são bloqueados: o próprio app, SystemUI, launcher, teclados, discador de emergência. */
    fun alwaysAllowed(): Set<String> {
        val set = mutableSetOf(
            context.packageName, "com.android.systemui", "android",
            "com.android.phone", "com.android.server.telecom", "com.samsung.android.incallui",
            "com.android.emergency", "com.google.android.permissioncontroller",
            "com.android.permissioncontroller", "com.android.packageinstaller",
        )
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { set += it.activityInfo.packageName }
        context.getSystemService(InputMethodManager::class.java)?.inputMethodList
            ?.forEach { set += it.packageName }
        return set
    }
}
