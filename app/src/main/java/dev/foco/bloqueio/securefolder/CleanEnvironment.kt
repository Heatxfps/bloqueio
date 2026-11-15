package dev.foco.bloqueio.securefolder

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * "Ambiente limpo" da pasta segura: desliga opções do desenvolvedor e depuração
 * enquanto um app da pasta está aberto e restaura depois. Requer WRITE_SECURE_SETTINGS.
 */
object CleanEnvironment {
    private val KEYS = listOf(
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        Settings.Global.ADB_ENABLED,
        "adb_wifi_enabled",
    )
    private const val PREFS = "clean_env"
    private const val KEY_ACTIVE = "active"

    fun canWriteSecureSettings(context: Context) =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    fun isActive(context: Context) = prefs(context).getBoolean(KEY_ACTIVE, false)

    /** Entra no ambiente limpo e abre o app. Retorna false se o app não pôde ser aberto. */
    fun launch(context: Context, pkg: String): Boolean {
        enter(context)
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false.also { exit(context) }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    @Synchronized
    fun enter(context: Context) {
        if (!canWriteSecureSettings(context) || isActive(context)) return
        val cr = context.contentResolver
        val edit = prefs(context).edit()
        KEYS.forEach { key -> edit.putInt(key, Settings.Global.getInt(cr, key, 0)) }
        edit.putBoolean(KEY_ACTIVE, true).commit()
        KEYS.forEach { key -> runCatching { Settings.Global.putInt(cr, key, 0) } }
    }

    @Synchronized
    fun exit(context: Context) {
        if (!isActive(context)) return
        val cr = context.contentResolver
        val p = prefs(context)
        // Liga a flag de desenvolvedor antes do ADB (o sistema exige essa ordem).
        KEYS.forEach { key -> runCatching { Settings.Global.putInt(cr, key, p.getInt(key, 0)) } }
        p.edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
