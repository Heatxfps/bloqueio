package dev.foco.bloqueio.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
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
 * A notificação contínua do app tem duas formas (docs/funcionalidades/10-now-bar.md):
 *
 * - **Foco ativo**: Live Update promovido para a Now Bar, com cronômetro e barra até o fim.
 *   É o único momento em que o app ocupa a Now Bar.
 * - **Resto do tempo**: notificação comum na gaveta com o anel duplo das cotas (o mesmo da tela
 *   Início: externo = redes, interno = jogos) e os dois tempos em min:seg. Uma linha de contexto
 *   diz o que mais está valendo (descanso, pausa, admin, servidor fora, app descontando).
 */
object NowBarNotifier {
    const val ID = 1
    private const val CHANNEL = "guard"
    private const val CHANNEL_BLOCKS = "blocks"
    private const val ID_BLOCK = 2

    private const val PINK = 0xFFFF375F.toInt()
    private const val INDIGO = 0xFF5E5CE6.toInt()
    private const val PURPLE = 0xFFBF5AF2.toInt()

    private const val PRE_REST_MIN = 15

    /** App de cota aberto agora (rede social ou jogo). */
    data class InUse(val label: String, val category: Category, val why: String? = null)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Cotas e foco", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Anel das cotas na gaveta; cronômetro do foco na Now Bar"
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
        if (s.focusLevel != FocusLevel.OFF) focus(context, s) else quotas(context, s, vpn, inUse)

    // ------------------------------------------------------------------ foco (Now Bar)

    private fun focus(context: Context, s: EnforceState): Notification {
        val f = s.config.focus
        val now = s.now
        val start = if (f.startedAt in 1 until f.until) f.startedAt else now
        val (title, icon, extra) = when (s.focusLevel) {
            FocusLevel.LOCK -> Triple("Celular bloqueado", R.drawable.ic_stat_lock, "só os essenciais")
            FocusLevel.TOTAL -> Triple("Foco total", R.drawable.ic_stat_focus, "redes e jogos bloqueados")
            else -> Triple("Foco parcial", R.drawable.ic_stat_focus, "nas redes, só criar")
        }
        val b = base(context, icon)
            .setColor(PURPLE)
            .setContentTitle(title)
            .setContentText("Até ${hhmm(f.until)} · $extra")
            .setCategory(Notification.CATEGORY_PROGRESS)
            // Cronômetro regressivo: o sistema anima os segundos; o chip da Now Bar mostra o tempo.
            .setWhen(f.until).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        val max = seconds(f.until - start)
        if (Build.VERSION.SDK_INT >= 36 && max > 0) {
            b.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(max).setColor(PURPLE)))
                    .setProgress(seconds(now - start).coerceIn(0, max))
                    .setProgressTrackerIcon(Icon.createWithResource(context, icon).setTint(PURPLE)),
            )
            // Pedido de promoção para Live Update / Now Bar (Notification.EXTRA_REQUEST_PROMOTED_ONGOING).
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        } else if (max > 0) {
            b.setProgress(max, seconds(now - start).coerceIn(0, max), false)
        }
        return b.build()
    }

    // ------------------------------------------------------------------ cotas (gaveta)

    private fun quotas(context: Context, s: EnforceState, vpn: VpnState, inUse: InUse?): Notification {
        val cfg = s.config
        val socialTotal = cfg.quotas.socialMinutes * 60
        val gamesTotal = cfg.quotas.gamesMinutes * 60
        val socialLeft = s.socialLeftSec.toInt().coerceIn(0, socialTotal.coerceAtLeast(0))
        val gamesLeft = s.gamesLeftSec.toInt().coerceIn(0, gamesTotal.coerceAtLeast(0))
        val socialFrac = if (!s.socialQuotaOn) 1f else if (socialTotal > 0) socialLeft / socialTotal.toFloat() else 0f
        val gamesFrac = if (!s.gamesQuotaOn) 1f else if (gamesTotal > 0) gamesLeft / gamesTotal.toFloat() else 0f

        fun value(left: Int, total: Int, on: Boolean) = when {
            !on -> "sem limite"
            total <= 0 -> "sem cota"
            left <= 0 -> "esgotada"
            else -> mmss(left.toLong())
        }
        val socialValue = value(socialLeft, socialTotal, s.socialQuotaOn)
        val gamesValue = value(gamesLeft, gamesTotal, s.gamesQuotaOn)
        val active = inUse?.takeIf {
            (it.category == Category.SOCIAL && socialLeft > 0) || (it.category == Category.GAME && gamesLeft > 0)
        }
        val context1 = contextLine(s, vpn, active)

        // Bolinha colorida = legenda do anel (a cor nunca vem sozinha: o nome está ao lado).
        fun line(color: Int, name: String, v: String) = SpannableStringBuilder().apply {
            append("●", ForegroundColorSpan(color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" $name $v")
        }
        val compactTimes = SpannableStringBuilder()
            .append(line(PINK, "Redes", socialValue)).append("    ").append(line(INDIGO, "Jogos", gamesValue))
        val description = "Redes: $socialValue. Jogos: $gamesValue."

        val small = RemoteViews(context.packageName, R.layout.notif_quota).apply {
            setImageViewBitmap(R.id.ring, ring(context, 40, socialFrac, gamesFrac))
            setContentDescription(R.id.ring, description)
            setTextViewText(R.id.times, compactTimes)
            setTextViewText(R.id.context, context1)
        }
        val big = RemoteViews(context.packageName, R.layout.notif_quota_big).apply {
            setImageViewBitmap(R.id.ring, ring(context, 84, socialFrac, gamesFrac))
            setContentDescription(R.id.ring, description)
            setTextViewText(R.id.social, line(PINK, "Redes", socialValue))
            setTextViewText(R.id.games, line(INDIGO, "Jogos", gamesValue))
            setTextViewText(R.id.context, context1)
        }

        val icon = when (active?.category) {
            Category.SOCIAL -> R.drawable.ic_stat_social
            Category.GAME -> R.drawable.ic_stat_game
            else -> R.drawable.ic_stat_guard
        }
        return base(context, icon)
            .setColor(if (active?.category == Category.GAME) INDIGO else PINK)
            // Título/texto continuam para leitores de tela, tela de bloqueio e Android sem custom view.
            .setContentTitle("Redes $socialValue · Jogos $gamesValue")
            .setContentText(context1)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .build()
    }

    /** O que mais está valendo agora, numa linha. */
    private fun contextLine(s: EnforceState, vpn: VpnState, active: InUse?): String {
        val now = s.now
        val sched = s.config.schedule
        val inWindow = sched.enabled && ScheduleWindow.contains(s.minuteOfDay, sched.startMinute, sched.endMinute)
        val adminUntil = AdminSession.unlockedUntil.value
        val toRest = if (sched.enabled) nextAt(sched.startMinute, now) - now else Long.MAX_VALUE
        return when {
            inWindow && sched.pausedUntil > now -> "Descanso pausado até ${hhmm(sched.pausedUntil)}"
            inWindow -> "Hora de descansar · libera às ${ScheduleWindow.format(sched.endMinute)}"
            vpn == VpnState.FAILOVER -> "Servidor fora do ar · VPN desligada até ele voltar"
            adminUntil > now -> "Admin liberado até ${hhmm(adminUntil)}"
            toRest < PRE_REST_MIN * 60_000L ->
                "Descanso às ${ScheduleWindow.format(sched.startMinute)} · em ${((toRest + 59_999) / 60_000)} min"
            active != null -> "${active.label} está usando a cota de ${if (active.category == Category.SOCIAL) "redes" else "jogos"}"
            else -> {
                val v = if (vpn == VpnState.PROTECTED) "VPN protegida" else "VPN ${vpn.label.lowercase()}"
                if (sched.enabled) "$v · descanso às ${ScheduleWindow.format(sched.startMinute)}" else v
            }
        }
    }

    /**
     * Anel duplo das cotas como imagem: mesma geometria do QuotaRing da tela Início
     * (traço de 12%, anel interno recuado 1,15 traço, trilho a 20%, ponta arredondada, começa no topo).
     */
    private fun ring(context: Context, sizeDp: Int, social: Float, games: Float): Bitmap {
        // Até 3x: nítido sem pesar no Binder (a imagem viaja a cada atualização).
        val px = (sizeDp * minOf(context.resources.displayMetrics.density, 3f)).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = px * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        fun draw(inset: Float, color: Int, fraction: Float) {
            val half = stroke / 2 + inset
            val r = RectF(half, half, px - half, px - half)
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = Color.argb(51, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawArc(r, 0f, 360f, false, paint)
            if (fraction > 0.002f) {
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = color
                canvas.drawArc(r, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
            }
        }
        draw(0f, PINK, social)
        draw(stroke * 1.15f, INDIGO, games)
        return bmp
    }

    // ------------------------------------------------------------------ comum

    private fun base(context: Context, icon: Int): Notification.Builder {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

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
}
