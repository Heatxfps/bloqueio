# 02 · Cotas de distração

- Duas cotas diárias independentes: **Redes sociais** (padrão 20 min) e **Jogos** (padrão 20 min). Zeram à meia-noite.
- Contagem: a cada 10 s o `GuardService` soma o tempo se a tela estiver ligada e o app em primeiro plano (informado pelo serviço de acessibilidade) for da categoria.
- **Jogos esgotados** → bloqueio total dos jogos.
- **Redes esgotadas** → bloqueio **parcial**: só as telas de consumo são bloqueadas. Direct/mensagens, publicar, câmera, perfil e notificações continuam liberados para trabalho.

| App | Bloqueado (consumo) | Liberado |
|---|---|---|
| Instagram | Reels, Explorar | Direct, câmera/criar, perfil, notificações |
| YouTube | Shorts | Busca, inscrições, upload |
| Facebook | Reels, Watch/Vídeos | Publicar, páginas, mensagens |
| TikTok | Para você / Seguindo | Câmera (criar), inbox, perfil |
| Kwai | Feed de vídeos | Câmera (criar) |

Categorias padrão: apps conhecidos + qualquer app que o Android marca como jogo (`ApplicationInfo.CATEGORY_GAME`). O usuário pode adicionar apps às categorias; só o admin remove.

A cota restante aparece na **Now Bar** (ver [DESIGN.md](../DESIGN.md)).
