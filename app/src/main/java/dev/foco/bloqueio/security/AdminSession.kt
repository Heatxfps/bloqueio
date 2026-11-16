package dev.foco.bloqueio.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdminRequiredException : IllegalStateException("Ação permitida somente ao admin")

/**
 * Desbloqueio temporário do admin. Vive só em memória: matar o processo = bloqueado.
 */
object AdminSession {
    private val _unlockedUntil = MutableStateFlow(0L)

    /** Quando o desbloqueio atual começou (para a barra de progresso da Now Bar). */
    @Volatile var unlockedAt = 0L
        private set
    val unlockedUntil: StateFlow<Long> = _unlockedUntil.asStateFlow()

    fun isUnlocked(now: Long = System.currentTimeMillis()) = _unlockedUntil.value > now

    fun unlock(minutes: Int) {
        unlockedAt = System.currentTimeMillis()
        _unlockedUntil.value = unlockedAt + minutes * 60_000L
    }

    fun lock() {
        _unlockedUntil.value = 0L
    }

    fun require() {
        if (!isUnlocked()) throw AdminRequiredException()
    }
}
