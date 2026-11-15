package dev.foco.bloqueio.vpn

import android.util.Log
import dev.foco.bloqueio.admin.PolicyManager
import dev.foco.bloqueio.data.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class VpnState(val label: String) {
    DISABLED("Desativada"),
    NO_OWNER("Sem Device Owner"),
    NOT_INSTALLED("Tailscale não instalado"),
    CONNECTING("Conectando"),
    PROTECTED("Protegida"),
    OFFLINE("Sem internet"),
    FAILOVER("Servidor fora — sem VPN"),
    PROBING("Testando retorno"),
}

/**
 * Trava o Tailscale ligado (ForceEnabled + Always-on) e faz failover quando o servidor cai.
 * Não precisa de configuração: "servidor caiu" = rede física validada pelo Android, mas
 * nenhuma internet passando pela VPN (inclusive DNS). Ver docs/funcionalidades/01-vpn.md.
 *
 * Ligar/desligar é sempre feito pelo Always-on (Device Owner), nunca pelos intents do
 * Tailscale: tirar o Always-on revoga a permissão de VPN, e a janela para concedê-la de
 * novo é bloqueada por DISALLOW_CONFIG_VPN. Repor o Always-on concede a permissão.
 */
class HostMonitor(
    private val policy: PolicyManager,
    private val tailscale: TailscaleController,
) {
    private val _state = MutableStateFlow(VpnState.CONNECTING)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private var fails = 0
    private var lastProbeAt = 0L

    suspend fun tick(cfg: VpnConfig) {
        val pkg = cfg.tailscalePackage
        when {
            !cfg.enabled -> {
                policy.setTailscaleForceEnabled(false, pkg)
                policy.setAlwaysOnVpn(null, false)
                set(VpnState.DISABLED)
                return
            }
            !tailscale.isInstalled(pkg) -> return set(VpnState.NOT_INSTALLED)
            !policy.isDeviceOwner -> return set(VpnState.NO_OWNER)
        }

        if (_state.value == VpnState.FAILOVER) {
            tryRecover(cfg)
            return
        }

        lockOn(pkg)
        val vpnUp = tailscale.isVpnActive()

        if (!tailscale.isUnderlyingNetworkValidated()) {
            // Sem sinal/Wi‑Fi: não é culpa do servidor, não desconecta.
            fails = 0
            set(VpnState.OFFLINE)
            return
        }

        if (vpnUp && internetProbe()) {
            fails = 0
            set(VpnState.PROTECTED)
        } else {
            fails++
            Log.w(TAG, "Sem internet pela VPN (vpnUp=$vpnUp) falha $fails/${cfg.failThreshold}")
            if (fails >= cfg.failThreshold) enterFailover(pkg) else set(VpnState.CONNECTING)
        }
    }

    private fun enterFailover(pkg: String) {
        Log.w(TAG, "Failover: servidor fora, desligando o Tailscale")
        policy.setTailscaleForceEnabled(false, pkg)
        policy.setAlwaysOnVpn(null, false) // derruba a VPN
        tailscale.disconnect(pkg)
        fails = 0
        lastProbeAt = System.currentTimeMillis()
        set(VpnState.FAILOVER)
    }

    private suspend fun tryRecover(cfg: VpnConfig) {
        val pkg = cfg.tailscalePackage
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < cfg.retryMinutes * 60_000L) return
        lastProbeAt = now
        if (!tailscale.isUnderlyingNetworkValidated()) return
        set(VpnState.PROBING)
        // Sem lockdown durante o teste: se o servidor ainda estiver fora, o resto não trava.
        policy.setAlwaysOnVpn(pkg, lockdown = false)
        delay(20_000)
        if (tailscale.isVpnActive() && internetProbe()) {
            Log.i(TAG, "Servidor voltou: travando a VPN de novo")
            lockOn(pkg)
            fails = 0
            set(VpnState.PROTECTED)
        } else {
            Log.i(TAG, "Servidor ainda fora")
            policy.setAlwaysOnVpn(null, false)
            set(VpnState.FAILOVER)
        }
    }

    /** ForceEnabled (botão desconectar desabilitado) + Always-on com lockdown. */
    private fun lockOn(pkg: String) {
        policy.setTailscaleForceEnabled(true, pkg)
        policy.setAlwaysOnVpn(pkg, lockdown = true)
    }

    private fun set(s: VpnState) {
        if (_state.value != s) Log.i(TAG, "Estado: ${_state.value} -> $s")
        _state.value = s
    }

    /** Duas tentativas, dois destinos: um soluço da rede não conta como falha. */
    private suspend fun internetProbe(): Boolean {
        repeat(2) { attempt ->
            for (url in PROBE_URLS) if (probe(url)) return true
            if (attempt == 0) delay(3_000)
        }
        return false
    }

    private suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6_000
            c.readTimeout = 6_000
            c.instanceFollowRedirects = false
            c.useCaches = false
            try {
                c.responseCode in 200..399
            } finally {
                c.disconnect()
            }
        }.onFailure { Log.d(TAG, "Probe $url falhou: ${it.javaClass.simpleName} ${it.message}") }
            .getOrDefault(false)
    }

    private companion object {
        const val TAG = "HostMonitor"
        val PROBE_URLS = listOf(
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace",
        )
    }
}
