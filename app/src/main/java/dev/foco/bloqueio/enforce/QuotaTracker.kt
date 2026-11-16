package dev.foco.bloqueio.enforce

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class DailyUsage(val date: String, val socialSec: Long = 0, val gamesSec: Long = 0)

/** Uso diário por categoria. Zera sozinho quando a data muda. */
object QuotaTracker {
    private lateinit var dir: File
    private var lastWrite = 0L
    private val _usage = MutableStateFlow(DailyUsage(LocalDate.now().toString()))
    val usage: StateFlow<DailyUsage> = _usage.asStateFlow()

    fun init(context: Context) {
        dir = context.filesDir
        _usage.value = load(LocalDate.now().toString())
    }

    @Synchronized
    fun add(category: Category, seconds: Long) {
        val today = current()
        val next = when (category) {
            Category.SOCIAL -> today.copy(socialSec = today.socialSec + seconds)
            Category.GAME -> today.copy(gamesSec = today.gamesSec + seconds)
            Category.NONE -> return
        }
        _usage.value = next
        // Conta de segundo em segundo, mas só grava no disco a cada 10 s (no máximo 10 s perdidos
        // se o processo morrer).
        val now = System.currentTimeMillis()
        if (now - lastWrite >= 10_000) {
            lastWrite = now
            File(dir, "usage-${next.date}.json").writeText(Json.encodeToString(DailyUsage.serializer(), next))
        }
    }

    /** Uso de hoje (troca de dia aplicada). */
    @Synchronized
    fun current(): DailyUsage {
        val today = LocalDate.now().toString()
        if (_usage.value.date != today) {
            _usage.value = load(today)
            dir.listFiles { f -> f.name.startsWith("usage-") && f.name < "usage-$today" }
                ?.forEach { it.delete() }
        }
        return _usage.value
    }

    private fun load(date: String): DailyUsage = runCatching {
        Json.decodeFromString(DailyUsage.serializer(), File(dir, "usage-$date.json").readText())
    }.getOrElse { DailyUsage(date) }
}
