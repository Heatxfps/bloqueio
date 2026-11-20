# 02 · Cotas de distração

- Duas cotas diárias independentes: **Redes sociais** (padrão 20 min) e **Jogos** (padrão 20 min). Zeram à meia-noite.
- Cada cota pode ser **ligada ou desligada** em **Admin › Cotas diárias**. Desligada, a categoria não conta tempo nem é bloqueada por cota, e a Home e a notificação mostram "Sem limite". Foco e descanso continuam valendo.
- **Contagem**: o `GuardService` soma de segundo em segundo o que a pessoa está **vendo**: a janela de app em foco, com a tela ligada. Não depende de toque, então um vídeo rodando parado continua contando.
  - **Pausa** com a gaveta de notificações aberta, na tela de bloqueio ou com a tela apagada.
  - **Jogos** e redes sem detector descontam o app inteiro.
  - **Instagram, YouTube e Facebook** (e TikTok/Kwai) só descontam na **área de consumo**: o mesmo detector da tabela abaixo. O resto do app não conta.
  - Cada mudança vai para o logcat com a tag `Quota` (`contando: Instagram · Reels/SOCIAL [aba selecionada "Reels"]`, `pausado (...)`).

| App | Conta | Não conta (conferido no aparelho em 16/11/2026) |
|---|---|---|
| Instagram | Aba Reels; reel aberto da Pesquisa (player `clips_viewer_view_pager`) | Página inicial (mesmo com vídeo rodando), Direct, Pesquisa, Perfil |
| YouTube | Aba Shorts; Short aberto da prateleira da Início (`reel_recycler`) | Início (com vídeo em autoplay), vídeo longo, miniplayer, Inscrições, Você |
| Facebook | Aba Reels; reel aberto do feed (rótulo "Detalhes do reel") | Página inicial, foto aberta, foto 360°, Amigos, Marketplace, Notificações, Perfil |
- **Jogos esgotados** → bloqueio total dos jogos.
- **Redes esgotadas** → bloqueio **parcial**: só as telas de consumo são bloqueadas. Direct/mensagens, publicar, câmera, perfil e notificações continuam liberados para trabalho.

| App | Bloqueado (consumo) | Para onde vai | Liberado |
|---|---|---|---|
| Instagram | Aba Reels e o player de Reels em tela cheia | Página inicial | Feed, Direct (inclusive Reels enviados), Pesquisar/Explorar, câmera, perfil |
| YouTube | Aba Shorts e o player de Shorts | Início | Início, busca, inscrições, vídeos longos, upload |
| Facebook | Abas Reels / Vídeo e o player de Reels aberto pelo feed | Página inicial / volta ao feed | Feed, publicar, páginas, mensagens |
| TikTok | Feed (Para você / Seguindo / Amigos) | Perfil | Perfil (criar), caixa de entrada |
| Kwai | Feed de vídeos | Perfil | Perfil, criar |

### Como o bloqueio parcial decide (sem bloquear o app todo)
- Só conta o **player em tela cheia** (≥ 90% da largura e 70% da altura; no Facebook, que esconde os IDs, por rótulos que só existem no player, ex.: "Detalhes do reel") ou a **aba de consumo selecionada na barra de navegação** do app (faixa superior/inferior da tela). Um Reels no meio do Direct ou a prateleira de Shorts da Início não disparam nada.
- Para sair, o Bloqueio **toca numa aba permitida** do próprio app (tabela acima). "Voltar" só é usado quando o player foi aberto de dentro de outra tela, e aí volta para essa tela.
- Cada disparo vai para o logcat com a tag `Distraction` (`adb logcat -s Distraction`), com o motivo exato. É o lugar para ajustar quando um app mudar.

Categorias padrão: apps conhecidos + qualquer app que o Android marca como jogo (`ApplicationInfo.CATEGORY_GAME`). O usuário pode adicionar apps às categorias; só o admin remove.

A cota restante aparece na **Now Bar** (ver [DESIGN.md](../DESIGN.md)).
