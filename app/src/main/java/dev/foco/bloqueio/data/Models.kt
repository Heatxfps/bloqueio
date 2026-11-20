package dev.foco.bloqueio.data

import kotlinx.serialization.Serializable

@Serializable
enum class RuleType(val title: String) {
    BLOCK("Sempre bloqueados"),
    PROTECT_UNINSTALL("Protegidos contra desinstalação"),
    SOCIAL("Redes sociais (cota)"),
    GAME("Jogos (cota)"),
    ESSENTIAL("Essenciais (liberados no horário)"),
}

/** Regra por nome de pacote: continua valendo mesmo se o app for desinstalado. */
@Serializable
data class Rule(
    val packageName: String,
    val type: RuleType,
    val createdAt: Long,
    val addedByAdmin: Boolean = false,
)

@Serializable
/** Do mais leve ao mais rígido. LOCK = celular travado nos essenciais (como o descanso). */
enum class FocusLevel { OFF, PARTIAL, TOTAL, LOCK }

@Serializable
data class Focus(val level: FocusLevel = FocusLevel.OFF, val until: Long = 0L, val startedAt: Long = 0L) {
    fun isActive(now: Long) = level != FocusLevel.OFF && until > now
}

@Serializable
data class Schedule(
    val enabled: Boolean = true,
    /** Minutos desde 00:00. Padrão 20:00 → 04:00. */
    val startMinute: Int = 20 * 60,
    val endMinute: Int = 4 * 60,
    /** Pausa feita pelo Admin no Hub (epoch ms). Enquanto no futuro, o descanso não vale. */
    val pausedUntil: Long = 0L,
    /** Quando a pausa começou (para a barra da Now Bar). */
    val pausedAt: Long = 0L,
)

@Serializable
data class Quotas(
    val socialMinutes: Int = 20,
    val gamesMinutes: Int = 20,
    /** Desligada: a categoria não conta nem bloqueia por cota (foco e descanso continuam valendo). */
    val socialEnabled: Boolean = true,
    val gamesEnabled: Boolean = true,
)

@Serializable
data class VpnConfig(
    val enabled: Boolean = true,
    val tailscalePackage: String = "com.tailscale.ipn",
    /** Falhas seguidas (sem internet pela VPN com a rede física ok) antes de desconectar. */
    val failThreshold: Int = 4,
    val retryMinutes: Int = 5,
)

@Serializable
data class Restrictions(
    val blockFactoryReset: Boolean = true,
    val blockSafeBoot: Boolean = true,
    val blockVpnConfig: Boolean = true,
    val blockDateTime: Boolean = true,
)

@Serializable
data class AppConfig(
    val rules: List<Rule> = emptyList(),
    val secureFolder: List<String> = emptyList(),
    val focus: Focus = Focus(),
    val schedule: Schedule = Schedule(),
    val quotas: Quotas = Quotas(),
    val vpn: VpnConfig = VpnConfig(),
    val restrictions: Restrictions = Restrictions(),
) {
    fun packagesOf(type: RuleType): Set<String> =
        rules.asSequence().filter { it.type == type }.map { it.packageName }.toSet()
}

object Defaults {
    val SOCIAL = listOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill",
        "com.google.android.youtube",
        "com.kwai.video",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
    )

    val ESSENTIAL = listOf(
        "com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer",
        "com.samsung.android.messaging", "com.google.android.apps.messaging",
        "com.samsung.android.app.contacts", "com.google.android.contacts",
        "com.sec.android.app.clockpackage", "com.google.android.deskclock",
        "com.sec.android.app.camera", "com.google.android.GoogleCamera",
        "com.google.android.apps.maps", "com.waze",
        "com.android.settings",
        "com.tailscale.ipn",
    )

    fun initialConfig(now: Long) = AppConfig(
        rules = SOCIAL.map { Rule(it, RuleType.SOCIAL, now, addedByAdmin = true) } +
            ESSENTIAL.map { Rule(it, RuleType.ESSENTIAL, now, addedByAdmin = true) },
    )
}
