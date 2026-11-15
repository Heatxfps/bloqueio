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
    val unlockedUntil: StateFlow<Long> = _unlockedUntil.asStateFlow()

    fun isUnlocked(now: Long = System.currentTimeMillis()) = _unlockedUntil.value > now

    fun unlock(minutes: Int) {
        _unlockedUntil.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun lock() {
        _unlockedUntil.value = 0L
    }

    fun require() {
        if (!isUnlocked()) throw AdminRequiredException()
    }
}
