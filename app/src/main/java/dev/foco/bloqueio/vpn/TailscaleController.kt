package dev.foco.bloqueio.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Controle do Tailscale pelos intents públicos do app (os mesmos usados por Tasker/Automate).
 */
class TailscaleController(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun connect(pkg: String) = send(pkg, "com.tailscale.ipn.CONNECT_VPN")

    fun disconnect(pkg: String) = send(pkg, "com.tailscale.ipn.DISCONNECT_VPN")

    fun isInstalled(pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun isVpnActive(): Boolean = networks().any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }

    /**
     * A rede física (Wi‑Fi/dados) tem internet? O Android valida isso por fora da VPN,
     * então diferencia "sem sinal" de "servidor do Tailscale caiu".
     */
    fun isUnderlyingNetworkValidated(): Boolean = networks().any {
        !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Suppress("DEPRECATION")
    private fun networks() = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }

    private fun send(pkg: String, action: String) {
        val intent = Intent(action).setClassName(pkg, "com.tailscale.ipn.IPNReceiver")
        runCatching { context.sendBroadcast(intent) }
    }
}
