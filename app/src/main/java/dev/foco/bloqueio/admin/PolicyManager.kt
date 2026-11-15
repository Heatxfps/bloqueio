package dev.foco.bloqueio.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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

    /**
     * Define o Always-on VPN. Definir um pacote também CONCEDE a permissão de VPN a ele e
     * o sistema inicia a VPN sozinho — sem a janela de consentimento, que fica bloqueada por
     * DISALLOW_CONFIG_VPN. Passar null derruba a VPN e revoga essa permissão.
     */
    fun setAlwaysOnVpn(vpnPackage: String?, lockdown: Boolean) {
        if (!isDeviceOwner) return
        val currentPkg = guard { dpm.getAlwaysOnVpnPackage(admin) }
        val currentLockdown = guard { dpm.isAlwaysOnVpnLockdownEnabled(admin) } ?: false
        if (currentPkg == vpnPackage && (vpnPackage == null || currentLockdown == lockdown)) return
        guard { dpm.setAlwaysOnVpnPackage(admin, vpnPackage, vpnPackage != null && lockdown) }
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

    val isHubActive: Boolean get() = prefs.getBoolean(KEY_HUB, false)

    /**
     * Hub do descanso como tela inicial obrigatória: liga o alias HOME e, como Device Owner,
     * fixa-o como launcher preferido. Desligar devolve o launcher do usuário.
     * (Não usa lock task: nele o painel de controles rápidos fica desativado.)
     */
    fun setHubAsHome(enabled: Boolean) {
        if (enabled == isHubActive) return
        val alias = ComponentName(context, HUB_ALIAS)
        context.packageManager.setComponentEnabledSetting(
            alias,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (isDeviceOwner) {
            if (enabled) {
                val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                guard { dpm.addPersistentPreferredActivity(admin, filter, alias) }
            } else {
                guard { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
            }
        }
        prefs.edit().putBoolean(KEY_HUB, enabled).apply()
    }

    /** Remove o Device Owner (somente pelo Admin). Libera tudo antes. */
    fun removeProtection() {
        if (!isDeviceOwner) return
        setHubAsHome(false)
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
        const val KEY_HUB = "hub_active"
        const val HUB_ALIAS = "dev.foco.bloqueio.ui.HubHome"
    }
}
