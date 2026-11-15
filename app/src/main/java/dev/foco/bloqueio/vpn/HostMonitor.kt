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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

enum class VpnState(val label: String) {
    DISABLED("Desativada"),
    NO_OWNER("Sem Device Owner"),
    NOT_INSTALLED("Tailscale não instalado"),
    CONNECTING("Conectando"),
    PROTECTED("Protegida"),
    FAILOVER("Host fora — sem VPN"),
    PROBING("Testando retorno"),
}

/**
 * Trava a VPN no Tailscale e faz failover quando o host cai.
 * Máquina de estados descrita em docs/funcionalidades/01-vpn.md.
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
                policy.setVpnLock(false, pkg)
                _state.value = VpnState.DISABLED
                return
            }
            !tailscale.isInstalled(pkg) -> {
                _state.value = VpnState.NOT_INSTALLED
                return
            }
        }

        if (_state.value == VpnState.FAILOVER) {
            tryRecover(cfg)
            return
        }

        policy.setVpnLock(true, pkg)
        val vpnUp = tailscale.isVpnActive()
        if (!vpnUp) tailscale.connect(pkg) // auto-cura

        if (cfg.hostIp.isBlank()) {
            _state.value = when {
                !policy.isDeviceOwner -> VpnState.NO_OWNER
                vpnUp -> VpnState.PROTECTED
                else -> VpnState.CONNECTING
            }
            return
        }

        val healthy = vpnUp && (tcpProbe(cfg.hostIp, cfg.hostPort) || internetProbe())
        if (healthy) {
            fails = 0
            _state.value = if (policy.isDeviceOwner) VpnState.PROTECTED else VpnState.NO_OWNER
        } else {
            fails++
            if (fails >= cfg.failThreshold) enterFailover(pkg) else _state.value = VpnState.CONNECTING
        }
    }

    private suspend fun enterFailover(pkg: String) {
        policy.setVpnLock(false, pkg)
        delay(1_000)
        tailscale.disconnect(pkg)
        fails = 0
        lastProbeAt = System.currentTimeMillis()
        _state.value = VpnState.FAILOVER
    }

    private suspend fun tryRecover(cfg: VpnConfig) {
        val pkg = cfg.tailscalePackage
        if (cfg.publicHealthUrl.isNotBlank()) {
            if (httpProbe(cfg.publicHealthUrl)) restore(pkg)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < cfg.retryMinutes * 60_000L) return
        lastProbeAt = now
        _state.value = VpnState.PROBING
        tailscale.connect(pkg)
        delay(20_000)
        if (tailscale.isVpnActive() && tcpProbe(cfg.hostIp, cfg.hostPort)) {
            restore(pkg)
        } else {
            tailscale.disconnect(pkg)
            _state.value = VpnState.FAILOVER
        }
    }

    private fun restore(pkg: String) {
        tailscale.connect(pkg)
        policy.setVpnLock(true, pkg)
        fails = 0
        _state.value = VpnState.CONNECTING
    }

    private suspend fun tcpProbe(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 3_000) } }.isSuccess
    }

    private suspend fun internetProbe() = httpProbe("https://connectivitycheck.gstatic.com/generate_204")

    private suspend fun httpProbe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 4_000
            c.readTimeout = 4_000
            c.instanceFollowRedirects = false
            try {
                c.responseCode in 200..399
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }
}
