package dev.foco.bloqueio.enforce

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detecta telas de CONSUMO (Reels, Shorts, Para você…) dentro das redes sociais.
 * Telas de criação, mensagens e perfil não batem em nenhuma regra e ficam liberadas.
 *
 * Os IDs mudam com atualizações dos apps: manter esta tabela é a principal manutenção
 * do bloqueio parcial (docs/LIMITACOES.md). Há reserva por rótulo da aba selecionada.
 */
object DistractionDetector {

    private data class Spec(
        val viewIds: List<String> = emptyList(),
        /** Rótulos (pt/en) da aba de navegação que, quando SELECIONADA, indica consumo. */
        val selectedTabs: List<String> = emptyList(),
        val label: String,
    )

    private val specs = mapOf(
        "com.instagram.android" to Spec(
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_video_container",
            ),
            selectedTabs = listOf("Reels", "Explorar", "Explore", "Pesquisar e explorar", "Search and explore"),
            label = "Reels e Explorar",
        ),
        "com.google.android.youtube" to Spec(
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_player",
            ),
            selectedTabs = listOf("Shorts"),
            label = "Shorts",
        ),
        "com.facebook.katana" to Spec(
            selectedTabs = listOf("Reels", "Vídeo", "Video", "Watch"),
            label = "Reels e Vídeos",
        ),
        "com.zhiliaoapp.musically" to tikTok("com.zhiliaoapp.musically"),
        "com.ss.android.ugc.trill" to tikTok("com.ss.android.ugc.trill"),
        "com.kwai.video" to Spec(
            selectedTabs = listOf("Início", "Home", "Descobrir", "Discover"),
            label = "feed de vídeos",
        ),
        "com.instagram.barcelona" to Spec(selectedTabs = listOf("Feed", "Início", "Home"), label = "feed"),
        "com.twitter.android" to Spec(selectedTabs = listOf("Vídeo", "Video"), label = "vídeos"),
    )

    private fun tikTok(pkg: String) = Spec(
        viewIds = listOf("$pkg:id/viewpager_feed"),
        selectedTabs = listOf("Início", "Home", "Para você", "For You", "Seguindo", "Following", "Amigos", "Friends"),
        label = "feed",
    )

    fun supports(pkg: String) = pkg in specs

    /** Retorna o nome da área de consumo detectada, ou null se a tela atual é liberada. */
    fun detect(pkg: String, root: AccessibilityNodeInfo?): String? {
        val spec = specs[pkg] ?: return null
        root ?: return null
        for (id in spec.viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.any { it.isVisibleToUser }) return spec.label
        }
        if (spec.selectedTabs.isNotEmpty() && hasSelectedTab(root, spec.selectedTabs, depth = 0)) {
            return spec.label
        }
        return null
    }

    private fun hasSelectedTab(node: AccessibilityNodeInfo, labels: List<String>, depth: Int): Boolean {
        if (depth > 40) return false
        if (node.isSelected) {
            val text = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
            if (labels.any { text.startsWith(it, ignoreCase = true) }) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasSelectedTab(child, labels, depth + 1)) return true
        }
        return false
    }
}
