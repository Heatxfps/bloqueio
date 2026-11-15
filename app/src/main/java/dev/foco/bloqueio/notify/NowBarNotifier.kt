package dev.foco.bloqueio.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import dev.foco.bloqueio.R
import dev.foco.bloqueio.data.FocusLevel
import dev.foco.bloqueio.enforce.EnforceState
import dev.foco.bloqueio.enforce.ScheduleWindow
import dev.foco.bloqueio.ui.MainActivity
import dev.foco.bloqueio.vpn.VpnState
import java.time.Instant
import java.time.ZoneId

/**
 * Notificação contínua das cotas. No Android 16 (One UI 8) vira Live Update e é
 * promovida para a Now Bar: uma única barra com redes (rosa) + jogos (índigo).
 * Especificação visual em docs/DESIGN.md.
 */
object NowBarNotifier {
    const val ID = 1
    private const val CHANNEL = "guard"

    private const val PINK = 0xFFFF2D55.toInt()
    private const val INDIGO = 0xFF5856D6.toInt()

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Cotas e proteção", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cota de redes sociais e jogos na Now Bar"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(context: Context, s: EnforceState, vpn: VpnState) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(ID, build(context, s, vpn))
        }
    }

    fun build(context: Context, s: EnforceState, vpn: VpnState): Notification {
        val socialTotal = s.config.quotas.socialMinutes.coerceAtLeast(1)
        val gamesTotal = s.config.quotas.gamesMinutes.coerceAtLeast(1)
        val socialLeft = ceilMinutes(s.socialLeftSec)
        val gamesLeft = ceilMinutes(s.gamesLeftSec)

        val (title, chip) = when {
            s.inSchedule ->
                "Modo noite até ${ScheduleWindow.format(s.config.schedule.endMinute)}" to "Noite"
            s.focusLevel != FocusLevel.OFF -> {
                val until = Instant.ofEpochMilli(s.config.focus.until).atZone(ZoneId.systemDefault()).toLocalTime()
                val name = if (s.focusLevel == FocusLevel.TOTAL) "Foco total" else "Foco parcial"
                "$name · até %02d:%02d".format(until.hour, until.minute) to "Foco"
            }
            else -> "Redes $socialLeft min · Jogos $gamesLeft min" to "${minOf(socialLeft, gamesLeft)}m"
        }
        val text = "VPN: ${vpn.label}"

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= 36) {
            val socialUsed = (socialTotal - socialLeft).coerceIn(0, socialTotal)
            val gamesUsed = (gamesTotal - gamesLeft).coerceIn(0, gamesTotal)
            val segments = listOf(
                socialUsed to PINK,
                socialLeft.coerceAtMost(socialTotal) to faded(PINK),
                gamesUsed to INDIGO,
                gamesLeft.coerceAtMost(gamesTotal) to faded(INDIGO),
            ).filter { it.first > 0 }
                .map { (len, color) -> Notification.ProgressStyle.Segment(len).setColor(color) }
            val style = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgressSegments(segments)
                .setProgressPoints(listOf(Notification.ProgressStyle.Point(socialTotal).setColor(Color.WHITE)))
                .setProgress(socialTotal + gamesTotal)
            b.setStyle(style)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(chip)
        } else {
            val used = (socialTotal - socialLeft) + (gamesTotal - gamesLeft)
            b.setProgress(socialTotal + gamesTotal, used.coerceAtLeast(0), false)
        }
        return b.build()
    }

    private fun ceilMinutes(sec: Long) = ((sec + 59) / 60).toInt()

    private fun faded(color: Int) = Color.argb(77, Color.red(color), Color.green(color), Color.blue(color))
}
