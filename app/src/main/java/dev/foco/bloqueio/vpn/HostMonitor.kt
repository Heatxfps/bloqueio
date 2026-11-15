package dev.foco.bloqueio.vpn

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
 * nenhuma internet passando pela VPN. Ver docs/funcionalidades/01-vpn.md.
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
                lock(false, pkg)
                _state.value = VpnState.DISABLED
                return
            }
            !tailscale.isInstalled(pkg) -> {
                _state.value = VpnState.NOT_INSTALLED
                return
            }
            !policy.isDeviceOwner -> {
                _state.value = VpnState.NO_OWNER
                return
            }
        }

        if (_state.value == VpnState.FAILOVER) {
            tryRecover(cfg)
            return
        }

        lock(true, pkg)
        val vpnUp = tailscale.isVpnActive()
        if (!vpnUp) tailscale.connect(pkg) // auto-cura

        if (!tailscale.isUnderlyingNetworkValidated()) {
            // Sem sinal/Wi‑Fi: não é culpa do servidor, não desconecta.
            fails = 0
            _state.value = VpnState.OFFLINE
            return
        }

        if (vpnUp && internetProbe()) {
            fails = 0
            _state.value = VpnState.PROTECTED
        } else {
            fails++
            if (fails >= cfg.failThreshold) enterFailover(pkg) else _state.value = VpnState.CONNECTING
        }
    }

    private suspend fun enterFailover(pkg: String) {
        lock(false, pkg)
        delay(1_000)
        tailscale.disconnect(pkg)
        fails = 0
        lastProbeAt = System.currentTimeMillis()
        _state.value = VpnState.FAILOVER
    }

    private suspend fun tryRecover(cfg: VpnConfig) {
        val pkg = cfg.tailscalePackage
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < cfg.retryMinutes * 60_000L) return
        lastProbeAt = now
        if (!tailscale.isUnderlyingNetworkValidated()) return
        _state.value = VpnState.PROBING
        tailscale.connect(pkg)
        delay(20_000)
        if (tailscale.isVpnActive() && internetProbe()) {
            lock(true, pkg)
            fails = 0
            _state.value = VpnState.PROTECTED
        } else {
            tailscale.disconnect(pkg)
            _state.value = VpnState.FAILOVER
        }
    }

    /** ForceEnabled (botão desconectar desabilitado) + Always-on com lockdown. */
    private fun lock(on: Boolean, pkg: String) {
        policy.setTailscaleForceEnabled(on, pkg)
        policy.setVpnLock(on, pkg)
    }

    private suspend fun internetProbe(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            c.instanceFollowRedirects = false
            try {
                c.responseCode == 204
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }
}
