package dev.foco.bloqueio.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import dev.foco.bloqueio.R
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.enforce.Category
import dev.foco.bloqueio.enforce.EnforceState
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.security.AdminSession
import dev.foco.bloqueio.ui.MainActivity
import dev.foco.bloqueio.vpn.VpnState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Notificação viva do app. No Android 16+ vira Live Update e é promovida para a Now Bar
 * (One UI 8+). Ela muda conforme o momento, do mais urgente para o mais calmo:
 *
 * 1. Foco ativo: cronômetro e barra até o fim.
 * 2. Descanso (ou pausa de emergência): cronômetro até a liberação (ou até o bloqueio voltar).
 * 3. Servidor fora: aviso de que a VPN foi desligada.
 * 4. Admin liberado: cronômetro até travar de novo.
 * 5. Descanso em até 15 min: contagem até lá.
 * 6. O principal: redes e jogos num cartão só, em min:seg.
 *
 * Especificação em docs/funcionalidades/10-now-bar.md.
 */
object NowBarNotifier {
    const val ID = 1
    private const val CHANNEL = "guard"
    private const val CHANNEL_BLOCKS = "blocks"
    private const val ID_BLOCK = 2

    private const val PINK = 0xFFFF375F.toInt()
    private const val INDIGO = 0xFF5E5CE6.toInt()
    private const val PURPLE = 0xFFBF5AF2.toInt()
    private const val ORANGE = 0xFFFF9F0A.toInt()
    private const val RED = 0xFFFF453A.toInt()

    private const val PRE_REST_MIN = 15

    /** App de cota aberto agora (rede social ou jogo). */
    data class InUse(val label: String, val category: Category)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Cotas e proteção", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cotas, foco e descanso na Now Bar"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val blocks = NotificationChannel(CHANNEL_BLOCKS, "Bloqueios", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Registro do que foi bloqueado (ex.: Reels, Shorts)"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, blocks))
    }

    /**
     * Registro do bloqueio parcial. Fica na gaveta (sem prazo), ao contrário do Toast
     * que só confirma na hora (HIG › Accessibility › Cognitive).
     */
    fun notifyPartialBlock(context: Context, area: String, why: String) {
        val n = Notification.Builder(context, CHANNEL_BLOCKS)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("Bloqueado: $area")
            .setContentText(why)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_BLOCK, n) }
    }

    fun update(context: Context, s: EnforceState, vpn: VpnState, inUse: InUse? = null) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(ID, build(context, s, vpn, inUse))
        }
    }

    fun build(context: Context, s: EnforceState, vpn: VpnState, inUse: InUse? = null): Notification =
        render(context, pick(s, vpn, inUse))

    // ------------------------------------------------------------------ cartões

    /**
     * Um estado da Now Bar. Gramática comum a todos (docs/funcionalidades/10-now-bar.md):
     * título = o que está valendo; texto = uma linha de contexto; chip = o valor vivo
     * (cronômetro quando [countdownTo] existe); barra = tempo, com o símbolo do cartão andando.
     */
    private data class Card(
        val icon: Int,
        val title: String,
        val text: String,
        val color: Int,
        val chip: String? = null,
        val countdownTo: Long? = null,
        /** Barra simples: [progress] de [max], preenchida com [color]. */
        val max: Int = 0,
        val progress: Int = 0,
        /** Barra em trechos (cor própria por trecho); substitui max/progress. */
        val segments: List<Pair<Int, Int>>? = null,
        /** Marcas na barra (posição, cor), nas mesmas unidades dos trechos. */
        val points: List<Pair<Int, Int>> = emptyList(),
        /** Símbolo que anda na ponta do progresso. */
        val tracker: Int? = null,
        /** Símbolo no fim da barra (para onde o tempo está indo). */
        val endIcon: Int? = null,
    )

    private fun pick(s: EnforceState, vpn: VpnState, inUse: InUse?): Card {
        val now = s.now
        val cfg = s.config
        val sched = cfg.schedule

        // 1. Foco ativo: cronômetro + barra que avança até o fim, com o símbolo do modo andando.
        if (s.focusLevel != FocusLevel.OFF) {
            val f = cfg.focus
            val start = if (f.startedAt in 1 until f.until) f.startedAt else now
            val (title, icon, extra) = when (s.focusLevel) {
                FocusLevel.LOCK -> Triple("Celular bloqueado", R.drawable.ic_stat_lock, "só os essenciais")
                FocusLevel.TOTAL -> Triple("Foco total", R.drawable.ic_stat_focus, "redes e jogos bloqueados")
                else -> Triple("Foco parcial", R.drawable.ic_stat_focus, "nas redes, só criar")
            }
            return Card(
                icon, title, "Até ${hhmm(f.until)} · $extra", PURPLE,
                countdownTo = f.until,
                max = seconds(f.until - start), progress = seconds(now - start),
                tracker = icon,
            )
        }

        // 2. Descanso, ou a pausa de emergência dentro dele.
        val inWindow = sched.enabled && ScheduleWindow.contains(s.minuteOfDay, sched.startMinute, sched.endMinute)
        if (inWindow) {
            if (sched.pausedUntil > now) {
                val from = sched.pausedAt.takeIf { it in 1 until sched.pausedUntil } ?: now
                return Card(
                    R.drawable.ic_stat_moon, "Descanso pausado",
                    "O bloqueio volta às ${hhmm(sched.pausedUntil)}", ORANGE,
                    countdownTo = sched.pausedUntil,
                    max = seconds(sched.pausedUntil - from), progress = seconds(now - from),
                    tracker = R.drawable.ic_stat_lock_open, endIcon = R.drawable.ic_stat_moon,
                )
            }
            val end = nextAt(sched.endMinute, now)
            val length = windowMinutes(sched.startMinute, sched.endMinute) * 60_000L
            return Card(
                R.drawable.ic_stat_moon, "Hora de descansar",
                "Só os essenciais até as ${ScheduleWindow.format(sched.endMinute)}", INDIGO,
                countdownTo = end,
                max = seconds(length), progress = seconds(length - (end - now)),
                tracker = R.drawable.ic_stat_moon,
            )
        }

        // 3. Servidor caiu
        if (vpn == VpnState.FAILOVER) {
            return Card(
                R.drawable.ic_stat_warning, "Servidor fora do ar",
                "A VPN foi desligada para a internet não cair. Volta sozinha.", RED, chip = "VPN",
            )
        }

        // 4. Admin liberado
        val adminUntil = AdminSession.unlockedUntil.value
        if (adminUntil > now) {
            val from = AdminSession.unlockedAt.takeIf { it in 1 until adminUntil } ?: now
            return Card(
                R.drawable.ic_stat_lock_open, "Admin liberado",
                "Regras editáveis até as ${hhmm(adminUntil)}", ORANGE,
                countdownTo = adminUntil,
                max = seconds(adminUntil - from), progress = seconds(adminUntil - now),
            )
        }

        // 5. Descanso em até 15 min: a barra enche até o cadeado no fim.
        if (sched.enabled) {
            val start = nextAt(sched.startMinute, now)
            if (start - now < PRE_REST_MIN * 60_000L) {
                return Card(
                    R.drawable.ic_stat_moon, "Descanso às ${ScheduleWindow.format(sched.startMinute)}",
                    "Termine o que está fazendo. Depois, só os essenciais.", INDIGO,
                    countdownTo = start,
                    max = PRE_REST_MIN * 60, progress = PRE_REST_MIN * 60 - seconds(start - now),
                    tracker = R.drawable.ic_stat_moon, endIcon = R.drawable.ic_stat_lock,
                )
            }
        }

        // 6. O principal: as duas cotas num cartão só.
        return quotaCard(s, vpn, inUse)
    }

    /**
     * Redes e jogos juntos, em min:seg. Só uma cota desce por vez (a do app aberto): ela ganha o
     * cronômetro no chip e a cor do ícone; parada, o chip mostra a que está mais perto de acabar.
     * A barra tem os dois trechos lado a lado: cor cheia = o que sobra, cor apagada = o que já foi.
     */
    private fun quotaCard(s: EnforceState, vpn: VpnState, inUse: InUse?): Card {
        val cfg = s.config
        val socialTotal = cfg.quotas.socialMinutes * 60
        val gamesTotal = cfg.quotas.gamesMinutes * 60
        val socialLeft = s.socialLeftSec.toInt().coerceIn(0, socialTotal)
        val gamesLeft = s.gamesLeftSec.toInt().coerceIn(0, gamesTotal)

        fun part(name: String, left: Int, total: Int) = when {
            total <= 0 -> null
            left <= 0 -> "$name esgotada"
            else -> "$name ${mmss(left.toLong())}"
        }
        val title = listOfNotNull(part("Redes", socialLeft, socialTotal), part("Jogos", gamesLeft, gamesTotal))
            .joinToString("  ·  ").ifEmpty { "Sem cotas" }

        val active = inUse?.takeIf {
            (it.category == Category.SOCIAL && socialLeft > 0) || (it.category == Category.GAME && gamesLeft > 0)
        }
        val social = active?.category == Category.SOCIAL
        val rest = if (cfg.schedule.enabled) " · descanso às ${ScheduleWindow.format(cfg.schedule.startMinute)}" else ""
        val text = when {
            active != null -> "${active.label} está usando a cota de ${if (social) "redes" else "jogos"}"
            vpn == VpnState.PROTECTED -> "Nada descontando · VPN protegida$rest"
            else -> "Nada descontando · VPN ${vpn.label.lowercase()}$rest"
        }

        val segments = buildList {
            if (socialTotal > 0) {
                add(socialLeft to PINK)
                add(socialTotal - socialLeft to faded(PINK))
            }
            if (gamesTotal > 0) {
                add(gamesLeft to INDIGO)
                add(gamesTotal - gamesLeft to faded(INDIGO))
            }
        }.filter { it.first > 0 }
        val divider = if (socialTotal > 0 && gamesTotal > 0) listOf(socialTotal to Color.WHITE) else emptyList()

        if (active != null) {
            val left = if (social) socialLeft else gamesLeft
            return Card(
                if (social) R.drawable.ic_stat_social else R.drawable.ic_stat_game, title, text,
                if (social) PINK else INDIGO,
                countdownTo = s.now + left * 1000L,
                segments = segments, points = divider,
            )
        }
        // Parado: o chip mostra a cota mais perto de acabar (entre as que ainda têm tempo).
        val scarce = listOf(socialLeft to PINK, gamesLeft to INDIGO).filter { it.first > 0 }.minByOrNull { it.first }
        return Card(
            R.drawable.ic_stat_guard, title, text, scarce?.second ?: PINK,
            chip = scarce?.let { mmss(it.first.toLong()) } ?: "0:00",
            segments = segments, points = divider,
        )
    }

    // ------------------------------------------------------------------ desenho

    private fun render(context: Context, c: Card): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(c.icon)
            .setColor(c.color)
            .setContentTitle(c.title)
            .setContentText(c.text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (c.countdownTo != null) {
            // Cronômetro regressivo: o sistema anima os segundos sozinho.
            b.setWhen(c.countdownTo).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }

        val hasBar = c.segments != null || c.max > 0
        if (Build.VERSION.SDK_INT >= 36) {
            if (hasBar) {
                val style = Notification.ProgressStyle()
                if (c.segments != null) {
                    style.setStyledByProgress(false)
                        .setProgressSegments(c.segments.map { (len, color) -> Notification.ProgressStyle.Segment(len).setColor(color) })
                        .setProgress(c.segments.sumOf { it.first })
                } else {
                    style.setStyledByProgress(true)
                        .setProgressSegments(listOf(Notification.ProgressStyle.Segment(c.max).setColor(c.color)))
                        .setProgress(c.progress.coerceIn(0, c.max))
                }
                if (c.points.isNotEmpty()) {
                    style.setProgressPoints(c.points.map { (at, color) -> Notification.ProgressStyle.Point(at).setColor(color) })
                }
                c.tracker?.let { style.setProgressTrackerIcon(tinted(context, it, c.color)) }
                c.endIcon?.let { style.setProgressEndIcon(tinted(context, it, c.color)) }
                b.setStyle(style)
            } else {
                b.setStyle(Notification.BigTextStyle().bigText(c.text))
            }
            // Pedido de promoção para Live Update / Now Bar (Notification.EXTRA_REQUEST_PROMOTED_ONGOING).
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            // Sem texto curto, o chip mostra o cronômetro.
            if (c.chip != null) b.setShortCriticalText(c.chip)
        } else if (hasBar) {
            val max = c.segments?.sumOf { it.first } ?: c.max
            b.setProgress(max, if (c.segments != null) max else c.progress.coerceIn(0, max), false)
        }
        return b.build()
    }

    private fun tinted(context: Context, res: Int, color: Int): Icon =
        Icon.createWithResource(context, res).setTint(color)

    private fun faded(color: Int) = Color.argb(77, Color.red(color), Color.green(color), Color.blue(color))

    // ------------------------------------------------------------------ tempo

    private fun seconds(ms: Long) = (ms / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun mmss(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

    private fun hhmm(epoch: Long): String {
        val t = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /** Próxima vez (depois de [now]) em que o relógio marca [minuteOfDay]. */
    private fun nextAt(minuteOfDay: Int, now: Long): Long {
        val zone = ZoneId.systemDefault()
        var t = LocalDate.now(zone).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant().toEpochMilli()
        while (t <= now) t += 86_400_000L
        return t
    }

    private fun windowMinutes(start: Int, end: Int) = if (end > start) end - start else 1440 - start + end
}
