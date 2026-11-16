package dev.foco.bloqueio.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
 * 1. Foco ativo: cronômetro regressivo até o fim.
 * 2. Descanso: cronômetro até a liberação (ou até o fim da pausa de emergência).
 * 3. Servidor fora: aviso de que a VPN foi desligada.
 * 4. Admin liberado: cronômetro até travar de novo.
 * 5. Usando rede social ou jogo: a cota descendo ao vivo (ou "esgotada").
 * 6. Descanso começando em até 15 min: contagem até lá.
 * 7. Sem nada acontecendo: alterna redes → jogos → proteção a cada 20 s.
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
    private const val GREEN = 0xFF30D158.toInt()

    /** Quanto tempo cada cartão fica antes de alternar, quando nada está acontecendo. */
    private const val ROTATE_MS = 20_000L
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

    /** Um estado da Now Bar. [countdownTo] liga o cronômetro regressivo (o chip mostra o tempo). */
    private data class Card(
        val icon: Int,
        val title: String,
        val text: String,
        val color: Int,
        val chip: String? = null,
        val countdownTo: Long? = null,
        val max: Int = 0,
        val progress: Int = 0,
        /** Marca (ex.: divisão entre redes e jogos), em unidades de [max]. */
        val point: Int? = null,
    )

    private fun pick(s: EnforceState, vpn: VpnState, inUse: InUse?): Card {
        val now = s.now
        val cfg = s.config
        val sched = cfg.schedule

        // 1. Foco
        if (s.focusLevel != FocusLevel.OFF) {
            val f = cfg.focus
            val start = if (f.startedAt in 1 until f.until) f.startedAt else now
            val (title, icon) = when (s.focusLevel) {
                FocusLevel.LOCK -> "Celular bloqueado" to R.drawable.ic_stat_lock
                FocusLevel.TOTAL -> "Foco total" to R.drawable.ic_stat_focus
                else -> "Foco parcial" to R.drawable.ic_stat_focus
            }
            val extra = when (s.focusLevel) {
                FocusLevel.LOCK -> "Só os essenciais funcionam"
                FocusLevel.TOTAL -> "Redes e jogos bloqueados"
                else -> "Nas redes, só criar"
            }
            return Card(
                icon, title, "$extra · termina às ${hhmm(f.until)}", PURPLE,
                countdownTo = f.until,
                max = seconds(f.until - start), progress = seconds(now - start),
            )
        }

        // 2. Descanso (ou pausa de emergência dentro dele)
        val inWindow = sched.enabled && ScheduleWindow.contains(s.minuteOfDay, sched.startMinute, sched.endMinute)
        if (inWindow) {
            val end = nextAt(sched.endMinute, now)
            if (sched.pausedUntil > now) {
                return Card(
                    R.drawable.ic_stat_moon, "Descanso pausado",
                    "O bloqueio volta às ${hhmm(sched.pausedUntil)}", ORANGE,
                    countdownTo = sched.pausedUntil,
                )
            }
            val length = windowMinutes(sched.startMinute, sched.endMinute) * 60_000L
            return Card(
                R.drawable.ic_stat_moon, "Hora de descansar",
                "Só os essenciais até as ${ScheduleWindow.format(sched.endMinute)}", INDIGO,
                countdownTo = end,
                max = seconds(length), progress = seconds(length - (end - now)),
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

        // 5. Usando um app de cota agora
        if (inUse != null) {
            val social = inUse.category == Category.SOCIAL
            val left = if (social) s.socialLeftSec else s.gamesLeftSec
            val total = (if (social) cfg.quotas.socialMinutes else cfg.quotas.gamesMinutes) * 60
            val kind = if (social) "redes sociais" else "jogos"
            val icon = if (social) R.drawable.ic_stat_social else R.drawable.ic_stat_game
            val color = if (social) PINK else INDIGO
            return if (left > 0) {
                Card(
                    icon, inUse.label, "Cota de $kind descendo · restam ${minutes(left)} min", color,
                    countdownTo = now + left * 1000,
                    max = total.coerceAtLeast(1), progress = left.toInt(),
                )
            } else {
                Card(
                    icon, "Cota de $kind esgotada",
                    if (social) "Reels, Shorts e feeds bloqueados até amanhã" else "Jogos bloqueados até amanhã",
                    color, chip = "0m", max = 1, progress = 0,
                )
            }
        }

        // 6. Descanso começando
        if (sched.enabled) {
            val start = nextAt(sched.startMinute, now)
            val minsToStart = ((start - now) / 60_000).toInt()
            if (minsToStart < PRE_REST_MIN) {
                return Card(
                    R.drawable.ic_stat_moon, "Descanso em breve",
                    "Às ${ScheduleWindow.format(sched.startMinute)} só os essenciais funcionam", INDIGO,
                    countdownTo = start,
                    max = PRE_REST_MIN * 60, progress = seconds(start - now),
                )
            }
        }

        // 7. Rodízio calmo: redes → jogos → proteção
        val socialTotal = cfg.quotas.socialMinutes * 60
        val gamesTotal = cfg.quotas.gamesMinutes * 60
        val rotation = buildList {
            if (socialTotal > 0) add(
                Card(
                    R.drawable.ic_stat_social, "Redes sociais",
                    quotaText(s.socialLeftSec, cfg.quotas.socialMinutes), PINK,
                    chip = "${minutes(s.socialLeftSec)}m",
                    max = socialTotal, progress = s.socialLeftSec.toInt(),
                ),
            )
            if (gamesTotal > 0) add(
                Card(
                    R.drawable.ic_stat_game, "Jogos",
                    quotaText(s.gamesLeftSec, cfg.quotas.gamesMinutes), INDIGO,
                    chip = "${minutes(s.gamesLeftSec)}m",
                    max = gamesTotal, progress = s.gamesLeftSec.toInt(),
                ),
            )
            add(protectionCard(s, vpn))
        }
        return rotation[((now / ROTATE_MS) % rotation.size).toInt()]
    }

    private fun protectionCard(s: EnforceState, vpn: VpnState): Card {
        val sched = s.config.schedule
        val rest = if (sched.enabled) " · descanso às ${ScheduleWindow.format(sched.startMinute)}" else ""
        val ok = vpn == VpnState.PROTECTED || vpn == VpnState.DISABLED
        return Card(
            R.drawable.ic_stat_guard,
            if (ok) "Proteção ativa" else "VPN: ${vpn.label}",
            (if (vpn == VpnState.PROTECTED) "VPN protegida" else "VPN ${vpn.label.lowercase()}") + rest,
            if (ok) GREEN else ORANGE,
            chip = if (ok) "OK" else "VPN",
        )
    }

    private fun quotaText(leftSec: Long, totalMin: Int) =
        if (leftSec <= 0) "Esgotada por hoje" else "Restam ${minutes(leftSec)} de $totalMin min hoje"

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
            // Cronômetro regressivo: o sistema anima os segundos sozinho, sem atualizar a cada segundo.
            b.setWhen(c.countdownTo).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            if (c.max > 0) {
                val style = Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(c.max).setColor(c.color)))
                    .setProgress(c.progress.coerceIn(0, c.max))
                c.point?.let { style.setProgressPoints(listOf(Notification.ProgressStyle.Point(it).setColor(Color.WHITE))) }
                b.setStyle(style)
            } else {
                b.setStyle(Notification.BigTextStyle().bigText(c.text))
            }
            // Pedido de promoção para Live Update / Now Bar (Notification.EXTRA_REQUEST_PROMOTED_ONGOING).
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            // Sem texto curto, o chip mostra o cronômetro.
            if (c.chip != null) b.setShortCriticalText(c.chip)
        } else if (c.max > 0) {
            b.setProgress(c.max, c.progress.coerceIn(0, c.max), false)
        }
        return b.build()
    }

    // ------------------------------------------------------------------ tempo

    private fun seconds(ms: Long) = (ms / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun minutes(sec: Long) = ((sec + 59) / 60).toInt()

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
