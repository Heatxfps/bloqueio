package dev.foco.bloqueio.enforce

import dev.foco.bloqueio.data.AppConfig
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.data.RuleType

enum class Category { SOCIAL, GAME, NONE }

enum class BlockReason(val message: String) {
    RULE("Este app está na sua lista de bloqueio."),
    SCHEDULE("Fora do horário. Só os apps essenciais funcionam agora."),
    FOCUS("Você está em modo foco."),
    GAME_QUOTA("Sua cota de jogos de hoje acabou."),
}

sealed interface Verdict {
    data object Allow : Verdict
    /** Rede social liberada só nas telas de criação; consumo (Reels/Shorts) bloqueado. */
    data class Partial(val why: String) : Verdict
    data class Block(val reason: BlockReason) : Verdict
}

data class EnforceState(
    val config: AppConfig,
    val usedSocialSec: Long,
    val usedGamesSec: Long,
    val now: Long,
    val minuteOfDay: Int,
) {
    val socialLeftSec get() = (config.quotas.socialMinutes * 60L - usedSocialSec).coerceAtLeast(0)
    val gamesLeftSec get() = (config.quotas.gamesMinutes * 60L - usedGamesSec).coerceAtLeast(0)
    val inSchedule get() = config.schedule.enabled &&
        ScheduleWindow.contains(minuteOfDay, config.schedule.startMinute, config.schedule.endMinute)
    val focusLevel get() = if (config.focus.isActive(now)) config.focus.level else FocusLevel.OFF
}

/** Lógica pura de decisão (sem Context). Ordem documentada em docs/ARQUITETURA.md. */
object Enforcer {

    fun decide(pkg: String, category: Category, alwaysAllowed: Boolean, s: EnforceState): Verdict {
        if (alwaysAllowed) return Verdict.Allow
        val c = s.config
        if (pkg in c.packagesOf(RuleType.BLOCK)) return Verdict.Block(BlockReason.RULE)
        if (s.inSchedule && pkg !in c.packagesOf(RuleType.ESSENTIAL)) {
            return Verdict.Block(BlockReason.SCHEDULE)
        }
        val focus = s.focusLevel
        return when (category) {
            Category.GAME -> when {
                focus != FocusLevel.OFF -> Verdict.Block(BlockReason.FOCUS)
                s.gamesLeftSec <= 0 -> Verdict.Block(BlockReason.GAME_QUOTA)
                else -> Verdict.Allow
            }
            Category.SOCIAL -> when {
                focus == FocusLevel.TOTAL -> Verdict.Block(BlockReason.FOCUS)
                focus == FocusLevel.PARTIAL -> Verdict.Partial("Foco parcial: só criar está liberado.")
                s.socialLeftSec <= 0 -> Verdict.Partial("Cota de redes sociais esgotada.")
                else -> Verdict.Allow
            }
            Category.NONE -> Verdict.Allow
        }
    }
}

object ScheduleWindow {
    /** Janela [start, end) em minutos; atravessa a meia-noite quando start > end. */
    fun contains(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    fun format(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}
