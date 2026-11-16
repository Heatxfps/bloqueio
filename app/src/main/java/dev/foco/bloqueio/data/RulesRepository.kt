package dev.foco.bloqueio.data

import android.content.Context
import dev.foco.bloqueio.security.AdminSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Fonte única das regras e configurações.
 *
 * Invariante de segurança (docs/funcionalidades/09-permissoes.md):
 * o usuário só ADICIONA; editar/remover exige [AdminSession]. Exceção: pasta segura.
 */
object RulesRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private lateinit var file: File
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "rules.json")
        _config.value = runCatching { json.decodeFromString<AppConfig>(file.readText()) }
            .getOrElse { Defaults.initialConfig(System.currentTimeMillis()).also(::persist) }
    }

    // ---------- Usuário (append-only) ----------

    fun addRule(packageName: String, type: RuleType) = mutate { c ->
        if (c.rules.any { it.packageName == packageName && it.type == type }) c
        else c.copy(
            rules = c.rules + Rule(
                packageName.trim(), type, System.currentTimeMillis(), AdminSession.isUnlocked(),
            ),
        )
    }

    /**
     * Iniciar um foco é livre. Depois de começar, ele não muda até o fim: trocar o nível ou a
     * duração (para mais ou para menos) exige o admin (docs/funcionalidades/05-foco.md).
     */
    fun startFocus(level: FocusLevel, minutes: Int) = mutate { c ->
        require(minutes in 1..(24 * 60)) { "Escolha entre 1 minuto e 24 horas." }
        val now = System.currentTimeMillis()
        if (c.focus.isActive(now)) AdminSession.require()
        c.copy(focus = Focus(level, now + minutes * 60_000L, startedAt = now))
    }

    // ---------- Pasta segura (livre para o usuário) ----------

    fun addToSecureFolder(pkg: String) = mutate { c ->
        if (pkg in c.secureFolder) c else c.copy(secureFolder = c.secureFolder + pkg)
    }

    fun removeFromSecureFolder(pkg: String) = mutate { c -> c.copy(secureFolder = c.secureFolder - pkg) }

    // ---------- Somente admin ----------

    fun removeRule(rule: Rule) = mutate { c ->
        AdminSession.require()
        c.copy(rules = c.rules - rule)
    }

    fun endFocus() = mutate { c ->
        AdminSession.require()
        c.copy(focus = Focus())
    }

    fun adminUpdate(transform: (AppConfig) -> AppConfig) = mutate { c ->
        AdminSession.require()
        transform(c)
    }

    // ---------- Interno (sistema) ----------

    /** Expira o foco quando o tempo acaba. Não exige admin: não afrouxa nada antes do prazo. */
    fun expireFocusIfNeeded(now: Long) {
        val f = _config.value.focus
        if (f.level != FocusLevel.OFF && f.until <= now) mutate { it.copy(focus = Focus()) }
    }

    @Synchronized
    private fun mutate(block: (AppConfig) -> AppConfig) {
        val next = block(_config.value)
        if (next != _config.value) {
            _config.value = next
            persist(next)
        }
    }

    private fun persist(c: AppConfig) {
        val tmp = File(file.parentFile, "rules.json.tmp")
        tmp.writeText(json.encodeToString(AppConfig.serializer(), c))
        tmp.renameTo(file)
    }
}
