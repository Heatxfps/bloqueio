package dev.foco.bloqueio.ui.screens

import dev.foco.bloqueio.ui.components.UiMessages
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import dev.foco.bloqueio.security.AdminRequiredException
import dev.foco.bloqueio.security.AdminSession
import kotlinx.coroutines.delay

/** Relógio que recompõe a cada [periodMs] (tempo restante, horário, sessão admin). */
@Composable
fun rememberNow(periodMs: Long = 1_000): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs)
        }
    }

@Composable
fun rememberAdminUnlocked(): Boolean {
    val until = AdminSession.unlockedUntil.collectAsState().value
    val now = rememberNow().value
    return until > now
}

/** Executa uma ação de regra mostrando aviso amigável se exigir admin. */
fun runGuarded(context: android.content.Context, action: () -> Unit) {
    try {
        action()
    } catch (_: AdminRequiredException) {
        UiMessages.error("Só o admin pode fazer isso. Desbloqueie na aba Admin.")
    } catch (e: IllegalArgumentException) {
        UiMessages.error(e.message ?: "Valor inválido.")
    }
}

/** Relógio da cota: 12:34 (min:seg) ou 1:02:03 acima de uma hora. Igual ao da notificação. */
fun formatClock(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun formatMinutes(sec: Long): String {
    val m = (sec + 59) / 60
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
