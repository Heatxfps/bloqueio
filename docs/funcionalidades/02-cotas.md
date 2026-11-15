# 02 · Cotas de distração

- Duas cotas diárias independentes: **Redes sociais** (padrão 20 min) e **Jogos** (padrão 20 min). Zeram à meia-noite.
- Contagem: a cada 10 s o `GuardService` soma o tempo se a tela estiver ligada e o app em primeiro plano (informado pelo serviço de acessibilidade) for da categoria.
- **Jogos esgotados** → bloqueio total dos jogos.
- **Redes esgotadas** → bloqueio **parcial**: só as telas de consumo são bloqueadas. Direct/mensagens, publicar, câmera, perfil e notificações continuam liberados para trabalho.

| App | Bloqueado (consumo) | Para onde vai | Liberado |
|---|---|---|---|
| Instagram | Aba Reels e o player de Reels em tela cheia | Página inicial | Feed, Direct (inclusive Reels enviados), Pesquisar/Explorar, câmera, perfil |
| YouTube | Aba Shorts e o player de Shorts | Início | Início, busca, inscrições, vídeos longos, upload |
| Facebook | Abas Reels / Vídeo | Página inicial | Feed, publicar, páginas, mensagens |
| TikTok | Feed (Para você / Seguindo / Amigos) | Perfil | Perfil (criar), caixa de entrada |
| Kwai | Feed de vídeos | Perfil | Perfil, criar |

### Como o bloqueio parcial decide (sem bloquear o app todo)
- Só conta o **player em tela cheia** (≥ 90% da largura e 70% da altura) ou a **aba de consumo selecionada na barra de navegação** do app (faixa superior/inferior da tela). Um Reels no meio do Direct ou a prateleira de Shorts da Início não disparam nada.
- Para sair, o Bloqueio **toca numa aba permitida** do próprio app (tabela acima). "Voltar" só é usado quando o player foi aberto de dentro de outra tela, e aí volta para essa tela.
- Cada disparo vai para o logcat com a tag `Distraction` (`adb logcat -s Distraction`), com o motivo exato. É o lugar para ajustar quando um app mudar.

Categorias padrão: apps conhecidos + qualquer app que o Android marca como jogo (`ApplicationInfo.CATEGORY_GAME`). O usuário pode adicionar apps às categorias; só o admin remove.

A cota restante aparece na **Now Bar** (ver [DESIGN.md](../DESIGN.md)).
