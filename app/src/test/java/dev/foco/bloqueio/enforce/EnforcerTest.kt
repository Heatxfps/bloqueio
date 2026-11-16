package dev.foco.bloqueio.enforce

import dev.foco.bloqueio.data.AppConfig
import dev.foco.bloqueio.data.Focus
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.data.Rule
import dev.foco.bloqueio.data.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcerTest {
    private val now = 1_000_000L
    private val base = AppConfig(
        rules = listOf(
            Rule("ig", RuleType.SOCIAL, 0),
            Rule("dialer", RuleType.ESSENTIAL, 0),
            Rule("bad", RuleType.BLOCK, 0),
        ),
    )

    private fun state(
        config: AppConfig = base,
        social: Long = 0,
        games: Long = 0,
        minute: Int = 12 * 60,
    ) = EnforceState(config, social, games, now, minute)

    @Test fun `app comum liberado`() =
        assertEquals(Verdict.Allow, Enforcer.decide("x", Category.NONE, false, state()))

    @Test fun `regra BLOCK sempre bloqueia`() =
        assertEquals(Verdict.Block(BlockReason.RULE), Enforcer.decide("bad", Category.NONE, false, state()))

    @Test fun `sempre liberado vence tudo`() =
        assertEquals(Verdict.Allow, Enforcer.decide("bad", Category.NONE, true, state(minute = 22 * 60)))

    @Test fun `horario bloqueia nao essenciais`() {
        assertEquals(Verdict.Block(BlockReason.SCHEDULE), Enforcer.decide("x", Category.NONE, false, state(minute = 22 * 60)))
        assertEquals(Verdict.Allow, Enforcer.decide("dialer", Category.NONE, false, state(minute = 22 * 60)))
    }

    @Test fun `pasta segura funciona no descanso`() {
        val c = base.copy(secureFolder = listOf("banco"))
        assertEquals(Verdict.Allow, Enforcer.decide("banco", Category.NONE, false, state(c, minute = 22 * 60)))
    }

    @Test fun `cota de jogos esgotada bloqueia jogo`() =
        assertEquals(
            Verdict.Block(BlockReason.GAME_QUOTA),
            Enforcer.decide("g", Category.GAME, false, state(games = 20 * 60)),
        )

    @Test fun `cota de redes esgotada vira parcial`() =
        assertTrue(Enforcer.decide("ig", Category.SOCIAL, false, state(social = 20 * 60)) is Verdict.Partial)

    @Test fun `foco parcial bloqueia jogos e deixa redes parcial`() {
        val c = base.copy(focus = Focus(FocusLevel.PARTIAL, now + 60_000))
        assertEquals(Verdict.Block(BlockReason.FOCUS), Enforcer.decide("g", Category.GAME, false, state(c)))
        assertTrue(Enforcer.decide("ig", Category.SOCIAL, false, state(c)) is Verdict.Partial)
    }

    @Test fun `foco total bloqueia redes`() {
        val c = base.copy(focus = Focus(FocusLevel.TOTAL, now + 60_000))
        assertEquals(Verdict.Block(BlockReason.FOCUS), Enforcer.decide("ig", Category.SOCIAL, false, state(c)))
    }

    @Test fun `foco bloquear libera so essenciais e pasta segura`() {
        val c = base.copy(focus = Focus(FocusLevel.LOCK, now + 60_000), secureFolder = listOf("banco"))
        assertEquals(Verdict.Block(BlockReason.FOCUS), Enforcer.decide("x", Category.NONE, false, state(c)))
        assertEquals(Verdict.Allow, Enforcer.decide("dialer", Category.NONE, false, state(c)))
        assertEquals(Verdict.Allow, Enforcer.decide("banco", Category.NONE, false, state(c)))
        assertEquals(Verdict.Block(BlockReason.FOCUS), Enforcer.decide("ig", Category.SOCIAL, false, state(c)))
    }

    @Test fun `foco expirado nao vale`() {
        val c = base.copy(focus = Focus(FocusLevel.TOTAL, now - 1))
        assertEquals(Verdict.Allow, Enforcer.decide("ig", Category.SOCIAL, false, state(c)))
    }

    @Test fun `janela atravessa meia-noite`() {
        assertTrue(ScheduleWindow.contains(23 * 60, 20 * 60, 4 * 60))
        assertTrue(ScheduleWindow.contains(3 * 60, 20 * 60, 4 * 60))
        assertFalse(ScheduleWindow.contains(4 * 60, 20 * 60, 4 * 60))
        assertFalse(ScheduleWindow.contains(12 * 60, 20 * 60, 4 * 60))
    }
}
