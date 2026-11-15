package dev.foco.bloqueio.service

import dev.foco.bloqueio.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado compartilhado entre os serviços e a UI (mesmo processo). */
object GuardState {
    /** Pacote em primeiro plano, informado pelo serviço de acessibilidade. */
    val foreground = MutableStateFlow<String?>(null)
    val vpn = MutableStateFlow(VpnState.CONNECTING)
    val accessibilityConnected = MutableStateFlow(false)
}
