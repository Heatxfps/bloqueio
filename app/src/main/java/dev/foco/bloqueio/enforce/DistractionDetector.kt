package dev.foco.bloqueio.enforce

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bloqueio PARCIAL: só as áreas de consumo (Reels, Shorts, feed "Para você"), nunca o app.
 *
 * Detecta duas coisas, e nada mais:
 *  1. **Visualizador em tela cheia** (ex.: o player de Reels/Shorts) — o nó precisa cobrir
 *     quase a tela toda. Um Reels dentro do Direct ou a prateleira de Shorts na Início não contam.
 *  2. **Aba de consumo selecionada** na barra de navegação do próprio app (faixa superior ou
 *     inferior da tela). Texto no meio da tela nunca conta.
 *
 * Para sair, o serviço toca numa **aba permitida** ([escapeTo]) em vez de "voltar", que em
 * vários apps fecharia o app inteiro. "Voltar" só é usado quando o visualizador foi aberto de
 * dentro de outra tela (ex.: um Reels aberto do perfil), e aí volta para essa tela.
 *
 * Os IDs e rótulos mudam com atualizações dos apps (docs/LIMITACOES.md); todo disparo vai para
 * o logcat com a tag "Distraction" para facilitar a manutenção.
 */
object DistractionDetector {

    data class Detection(val area: String, val viaTab: Boolean, val reason: String)

    private data class Spec(
        val label: String,
        /** IDs de visualizadores em tela cheia (sem o prefixo do pacote). */
        val viewerIds: List<String> = emptyList(),
        /** Rótulos (pt/en) das abas de consumo na barra de navegação. */
        val consumptionTabs: List<String> = emptyList(),
        /** Abas permitidas para onde levar a pessoa, em ordem de preferência. */
        val escapeTabs: List<String> = emptyList(),
    )

    private val home = listOf("Página inicial", "Início", "Home", "Feed")
    private val profile = listOf("Perfil", "Profile", "Eu", "Me", "Você", "You")

    private val specs = mapOf(
        // Explorar/Pesquisar fica liberado: é onde se busca perfis para trabalhar.
        "com.instagram.android" to Spec(
            label = "Reels",
            viewerIds = listOf("clips_viewer_view_pager", "clips_viewer_container"),
            consumptionTabs = listOf("Reels"),
            escapeTabs = home + profile,
        ),
        // A Início do YouTube (busca, inscrições, vídeos longos) fica liberada; só Shorts sai.
        "com.google.android.youtube" to Spec(
            label = "Shorts",
            viewerIds = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player"),
            consumptionTabs = listOf("Shorts"),
            escapeTabs = listOf("Início", "Home", "Inscrições", "Subscriptions") + profile,
        ),
        "com.facebook.katana" to Spec(
            label = "Reels",
            consumptionTabs = listOf("Reels", "Vídeo", "Video", "Watch"),
            escapeTabs = home + profile,
        ),
        // No TikTok o feed é o consumo; Perfil e Caixa de entrada continuam (criar fica no Perfil).
        "com.zhiliaoapp.musically" to tikTok(),
        "com.ss.android.ugc.trill" to tikTok(),
        "com.kwai.video" to Spec(
            label = "feed de vídeos",
            consumptionTabs = listOf("Início", "Home", "Descobrir", "Discover"),
            escapeTabs = profile,
        ),
    )

    private fun tikTok() = Spec(
        label = "feed",
        consumptionTabs = listOf("Início", "Home", "Para você", "For You", "Amigos", "Friends"),
        escapeTabs = profile + listOf("Caixa de entrada", "Inbox"),
    )

    fun supports(pkg: String) = pkg in specs

    fun detect(pkg: String, root: AccessibilityNodeInfo?): Detection? {
        val spec = specs[pkg] ?: return null
        root ?: return null
        val screen = Rect().also(root::getBoundsInScreen)
        if (screen.height() <= 0) return null

        // Aba primeiro: dá para sair tocando numa aba permitida, sem "voltar".
        val tab = findNavItem(root, screen, spec.consumptionTabs, selectedOnly = true)
        if (tab != null) {
            return Detection(spec.label, viaTab = true, reason = "aba selecionada \"${labelOf(tab)}\"").also(::log)
        }
        for (id in spec.viewerIds) {
            val full = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull { n ->
                val r = Rect().also(n::getBoundsInScreen)
                n.isVisibleToUser && r.width() >= screen.width() * 0.9f && r.height() >= screen.height() * 0.7f
            }
            if (full != null) return Detection(spec.label, viaTab = false, reason = "visualizador $id").also(::log)
        }
        return null
    }

    /** Toca na primeira aba permitida encontrada. Retorna false se não achou nenhuma. */
    fun escapeTo(pkg: String, root: AccessibilityNodeInfo?): Boolean {
        val spec = specs[pkg] ?: return false
        root ?: return false
        val screen = Rect().also(root::getBoundsInScreen)
        for (label in spec.escapeTabs) {
            val node = findNavItem(root, screen, listOf(label), selectedOnly = false) ?: continue
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                Log.i(TAG, "$pkg: levado para a aba \"$label\"")
                return true
            }
        }
        return false
    }

    /** Item da barra de navegação: na faixa de 22% do topo ou de baixo, com rótulo exato. */
    private fun findNavItem(
        root: AccessibilityNodeInfo,
        screen: Rect,
        labels: List<String>,
        selectedOnly: Boolean,
    ): AccessibilityNodeInfo? {
        val band = screen.height() * 0.22f
        fun inNavBand(n: AccessibilityNodeInfo): Boolean {
            val r = Rect().also(n::getBoundsInScreen)
            return r.height() in 1..(screen.height() / 6) &&
                (r.bottom <= screen.top + band || r.top >= screen.bottom - band)
        }
        fun visit(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > 45) return null
            if ((!selectedOnly || n.isSelected) && n.isVisibleToUser && matches(labelOf(n), labels) && inNavBand(n)) {
                return n
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                visit(c, depth + 1)?.let { return it }
            }
            return null
        }
        return visit(root, 0)
    }

    private fun labelOf(n: AccessibilityNodeInfo): String =
        (n.contentDescription ?: n.text)?.toString()?.trim().orEmpty()

    /** Igual ao rótulo, ou o rótulo seguido de detalhe ("Reels, guia 2 de 5"). */
    private fun matches(text: String, labels: List<String>): Boolean = labels.any { l ->
        text.equals(l, ignoreCase = true) ||
            text.startsWith("$l,", ignoreCase = true) ||
            text.startsWith("$l ", ignoreCase = true) && text.length <= l.length + 20
    }

    private fun log(d: Detection) = Log.i(TAG, "consumo detectado: ${d.area} (${d.reason})")

    private const val TAG = "Distraction"
}
