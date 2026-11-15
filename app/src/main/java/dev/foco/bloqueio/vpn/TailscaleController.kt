package dev.foco.bloqueio.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Controle do Tailscale pelos intents públicos do app (os mesmos usados por Tasker/Automate).
 */
class TailscaleController(private val context: Context) {

    fun connect(pkg: String) = send(pkg, "com.tailscale.ipn.CONNECT_VPN")

    fun disconnect(pkg: String) = send(pkg, "com.tailscale.ipn.DISCONNECT_VPN")

    fun isInstalled(pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun isVpnActive(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private fun send(pkg: String, action: String) {
        val intent = Intent(action).setClassName(pkg, "com.tailscale.ipn.IPNReceiver")
        runCatching { context.sendBroadcast(intent) }
    }
}
