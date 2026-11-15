package dev.foco.bloqueio.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import dev.foco.bloqueio.data.AppConfig
import dev.foco.bloqueio.data.RuleType

/**
 * Único ponto de acesso ao [DevicePolicyManager]. Tudo é no-op seguro sem Device Owner.
 */
class PolicyManager(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = BloqueioAdminReceiver.component(context)
    private val prefs = context.getSharedPreferences("policy", Context.MODE_PRIVATE)

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /** Restrições + proteção contra desinstalação. Chamado sempre que a config muda. */
    fun applyBaseline(config: AppConfig) {
        if (!isDeviceOwner) return
        guard { dpm.setUninstallBlocked(admin, context.packageName, true) }
        val protected = config.packagesOf(RuleType.PROTECT_UNINSTALL)
        protected.forEach { pkg -> guard { dpm.setUninstallBlocked(admin, pkg, true) } }
        // Libera os que saíram da lista (removidos pelo admin).
        val previous = prefs.getStringSet(KEY_PROTECTED, emptySet()).orEmpty()
        (previous - protected).forEach { pkg -> guard { dpm.setUninstallBlocked(admin, pkg, false) } }
        prefs.edit().putStringSet(KEY_PROTECTED, protected).apply()

        val r = config.restrictions
        restriction(UserManager.DISALLOW_FACTORY_RESET, r.blockFactoryReset)
        restriction(UserManager.DISALLOW_SAFE_BOOT, r.blockSafeBoot)
        restriction(UserManager.DISALLOW_CONFIG_VPN, r.blockVpnConfig && config.vpn.enabled)
        restriction(UserManager.DISALLOW_CONFIG_DATE_TIME, r.blockDateTime)
    }

    /** Liga/desliga o Always-on VPN com lockdown no Tailscale. */
    fun setVpnLock(enabled: Boolean, vpnPackage: String) {
        if (!isDeviceOwner) return
        val current = guard { dpm.getAlwaysOnVpnPackage(admin) }
        val target = if (enabled) vpnPackage else null
        if (current == target) return
        guard { dpm.setAlwaysOnVpnPackage(admin, target, enabled) }
    }

    /**
     * Política gerenciada do Tailscale (MDM): `ForceEnabled` esconde/desabilita o botão de
     * desconectar no app do Tailscale.
     */
    fun setTailscaleForceEnabled(enabled: Boolean, vpnPackage: String) {
        if (!isDeviceOwner) return
        val current = guard { dpm.getApplicationRestrictions(admin, vpnPackage) } ?: Bundle()
        if (current.getBoolean(KEY_FORCE_ENABLED, false) == enabled && current.containsKey(KEY_FORCE_ENABLED)) return
        val next = Bundle(current).apply { putBoolean(KEY_FORCE_ENABLED, enabled) }
        guard { dpm.setApplicationRestrictions(admin, vpnPackage, next) }
    }

    /**
     * Sincroniza a suspensão: suspende [desired] e libera o que o app suspendeu antes e não
     * está mais na lista. Nunca mexe em apps suspensos por outra origem.
     */
    fun syncSuspended(desired: Set<String>) {
        if (!isDeviceOwner) return
        val previous = prefs.getStringSet(KEY_SUSPENDED, emptySet()).orEmpty()
        val toSuspend = desired - previous
        val toRelease = previous - desired
        val failed = mutableSetOf<String>()
        if (toSuspend.isNotEmpty()) {
            guard { dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true) }
                ?.let { failed += it }
        }
        if (toRelease.isNotEmpty()) {
            guard { dpm.setPackagesSuspended(admin, toRelease.toTypedArray(), false) }
        }
        prefs.edit().putStringSet(KEY_SUSPENDED, desired - failed).apply()
    }

    /** Remove o Device Owner (somente pelo Admin). Libera tudo antes. */
    fun removeProtection() {
        if (!isDeviceOwner) return
        syncSuspended(emptySet())
        guard { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        guard { dpm.setApplicationRestrictions(admin, "com.tailscale.ipn", Bundle()) }
        listOf(
            UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_CONFIG_DATE_TIME,
        ).forEach { restriction(it, false) }
        prefs.getStringSet(KEY_PROTECTED, emptySet()).orEmpty()
            .forEach { pkg -> guard { dpm.setUninstallBlocked(admin, pkg, false) } }
        guard { dpm.setUninstallBlocked(admin, context.packageName, false) }
        @Suppress("DEPRECATION")
        guard { dpm.clearDeviceOwnerApp(context.packageName) }
    }

    private fun restriction(key: String, on: Boolean) = guard {
        if (on) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key)
    }

    private inline fun <T> guard(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Log.w("PolicyManager", "Falha em chamada de Device Owner", e)
            null
        }

    private companion object {
        const val KEY_SUSPENDED = "suspended"
        const val KEY_PROTECTED = "protected"
        const val KEY_FORCE_ENABLED = "ForceEnabled"
    }
}
