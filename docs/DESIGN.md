# Design System — "Bloqueio"

Inspirado nas Human Interface Guidelines da Apple (iOS): calmo, muito espaço em branco, hierarquia por tipografia e não por bordas, listas agrupadas com cantos arredondados e cores de sistema. Toda tela nova **deve** usar apenas os tokens abaixo (implementados em `ui/theme`).

## Princípios

1. **Clareza** — uma ação principal por tela. Texto curto, direto, em português.
2. **Deferência** — o conteúdo manda; cromado mínimo, sem sombras pesadas, sem gradientes.
3. **Profundidade** — camadas por cor de fundo (fundo agrupado → célula), não por elevação.
4. **Calma** — bloquear não é punir. A tela de bloqueio é gentil, nunca vermelha agressiva.

## Cores (`BloqueioColors`)

Regra: **cor de texto ≠ cor de preenchimento.** Os tons vivos do iOS (verde, laranja) não passam 4,5:1 como texto, então cada papel tem seu token. Todo texto passa **4,5:1** nos dois temas (contrastes calculados em WCAG).

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `background` | `#F2F2F7` | `#000000` | Fundo das telas |
| `cell` | `#FFFFFF` | `#1C1C1E` | Células e cards |
| `cellSecondary` | `#F2F2F7` | `#2C2C2E` | Campos, trilhos de controle |
| `label` | `#000000` | `#FFFFFF` | Texto principal |
| `secondaryLabel` | `#6C6C70` (4,69:1) | `#EBEBF5` @60% (5,94:1) | Texto de apoio, cabeçalhos, placeholders |
| `separator` | `#3C3C43` @29% | `#545458` @60% | Divisórias (0,5 dp) |
| `accent` | `#0066D6` (5,42:1) | `#0A84FF` (4,66:1) | **Somente** o que é clicável: links, botões de texto |
| `accentFill` | `#0066D6` | `#0071E3` (4,70:1 c/ branco) | Fundo do botão principal |
| `social` | `#FF2D55` | `#FF375F` | Cota de redes (anéis, barra) |
| `games` | `#5856D6` | `#5E5CE6` | Cota de jogos |
| `success` / `successText` | `#34C759` / `#1A7F37` (5,08:1) | `#30D158` | Preenchimento (switch, ícone de estado) / texto "Ativo", "Protegida" |
| `warning` / `warningText` | `#FF9500` / `#A35200` (5,00:1) | `#FF9F0A` | Fundo de aviso / texto de aviso |
| `danger` | `#C42B1C` (5,66:1) | `#FF453A` | Ações destrutivas (só admin) |
| `focus` / `focusFill` | `#8E3FC2` | `#BF5AF2` / `#A050D0` (4,61:1 c/ branco) | Modos de foco |
| `gray` / `grayFill` | `#636366` | `#8E8E93` / `#636366` | Aba inativa, ícone desativado / botão neutro |

**Alto contraste:** com o "Texto de alto contraste" do Android ligado, `secondaryLabel` vira `#3A3A3C` / `#D1D1D6` e as divisórias ficam `#8E8E93`.

## Tipografia (`BloqueioType`)

Fonte do sistema (no Samsung, *SamsungOne*; o equivalente visual do SF Pro). Escala do iOS:

| Estilo | Tamanho / peso | Uso |
|---|---|---|
| `largeTitle` | 34 sp / Bold | Título no topo de cada tela |
| `title2` | 22 sp / Bold | Números grandes (minutos restantes) |
| `headline` | 17 sp / SemiBold | Título de célula em destaque |
| `body` | 17 sp / Regular | Texto de célula |
| `callout` | 16 sp / Regular | Descrições |
| `footnote` | 13 sp / Regular | Rodapé de seção, ajuda |
| `caption` | 12 sp / Regular | Cabeçalho de seção (MAIÚSCULAS, `secondaryLabel`) |

## Espaçamento e forma

- Grade de **4 dp**. Margem lateral da tela: **16 dp**. Entre seções: **32 dp**.
- Célula: altura mínima **52 dp**, padding horizontal **16 dp**.
- Cantos: célula agrupada **12 dp**, card grande **20 dp**, botão **14 dp**, chip/pílula **50%**.
- Separadores começam alinhados ao texto (inset de 16 dp, ou 60 dp quando há ícone).

## Componentes (`ui/components`)

| Componente | Descrição |
|---|---|
| `LargeTitleScaffold` | Título grande à esquerda, conteúdo rolável, fundo agrupado |
| `GroupedSection` | Cabeçalho em caption + bloco branco arredondado + rodapé em footnote |
| `Cell` | Ícone opcional em quadrado arredondado colorido (29 dp, raio 7) + título + valor/chevron/toggle |
| `IosSwitch` | Toggle verde estilo iOS (51×31 dp), `Role.Switch` para o TalkBack |
| `SwitchCell` | Linha com switch: a **linha inteira** é o alvo de toque e o título é o rótulo lido |
| `MessageBanner` | Aviso de sucesso/erro abaixo da barra de status; fica até tocar em OK (substitui Toast) |
| `PrimaryButton` | Preenchido `accent`, 50 dp de altura, raio 14, texto SemiBold |
| `QuotaRing` | Anel duplo (rosa redes / índigo jogos) estilo Atividade do Apple Watch |
| `SegmentedControl` | Controle segmentado; `selectableGroup` + `Role.Tab`, opções com 44 dp |
| `LockBadge` | Cadeado pequeno indicando "só admin edita" |

## Acessibilidade (obrigatório em toda tela)

- **Alvos de toque** ≥ 48 dp (`heightIn(min = 48.dp)` ou `minimumInteractiveComponentSize()`).
- **Alturas mínimas, nunca fixas**, em qualquer coisa com texto: a fonte do sistema pode ir a 200%.
- **Semântica**: controles próprios declaram `role` e estado (`toggleable`, `selectable`); ícone ao lado de texto usa `contentDescription = null`; botão só de ícone tem descrição.
- **Feedback sem prazo**: nada de Toast dentro do app — use `MessageBanner` ou texto na própria seção.
- **Ações que o usuário não desfaz sozinho** (iniciar foco) pedem confirmação; remoções comuns oferecem Desfazer.
- **Trabalho pesado** (PBKDF2, listar apps) fora da thread principal, com progresso visível.

## Movimento e vidro (linguagem da Apple)

Regra de ouro: **a ousadia fica num lugar só.** Vidro só na camada funcional, cor no conteúdo, controles neutros, e elementos que se **transformam** em vez de sumir e aparecer.

| Elemento | Implementação | Detalhes |
|---|---|---|
| Barra de abas | `liquidGlass` (biblioteca `io.github.kyant0:backdrop`) sobre o conteúdo gravado com `layerBackdrop` | **Refração** nas bordas (`lens`, Android 13+), desfoque leve, `vibrancy`, brilho especular, sombra suave |
| Seleção da aba | Bolha de vidro própria (`bubble = true`) | Mais refração com aberração cromática; desliza com mola e estica em movimento |
| Aura das cotas | Campo radial rosa/índigo **no conteúdo**, atrás dos anéis | Mais forte quanto mais cota sobra; "respira" em 6 s; o vidro da barra a reflete ao rolar |
| Começar → cronômetro | `SharedTransitionLayout` + `sharedBounds` | A cápsula cresce e vira o cartão do cronômetro |
| Números | `RollingText` | Só os dígitos que mudam rolam (ao diminuir, descem de cima) |
| Símbolos | Ícone da aba e do modo **pulam** ao escolher; **cadeado fecha** no Bloquear; **✓ se desenha** nos avisos de sucesso | Cada animação diz o que aconteceu (HIG › SF Symbols › Animations) |
| Toque | `pressScale` com a mola única do app (0,7 / 600) | Afunda e volta elástico |
| Alertas | `FLAG_BLUR_BEHIND` + cartão a 90% | Escala 1,06 → 1 |

**Cor com disciplina:** seleção (modos, pílulas) em neutro — texto sobre fundo invertido. A cor do foco aparece só no **Começar** e no **cronômetro**. Ícones da Início só ganham cor quando indicam estado (VPN protegida, descanso ativo, foco ativo). Sem sombras coloridas.

**Tipografia:** Inter (OFL, `res/font/inter_variable.ttf`, licença em `docs/licencas/`) no lugar da SF, que só pode ser usada em aparelhos Apple. Duas famílias pelo eixo óptico: `InterText` (opsz 14) e `InterDisplay` (opsz 32). Espaçamento negativo crescente com o tamanho (−0,022 em no título grande), números de largura fixa (`tnum`) em contagens. Cabeçalhos de seção em letra normal semibold (sem caixa alta).

**Acessibilidade do movimento:** *Remover animações* deixa tudo instantâneo e desliga a paralaxe; *Texto de alto contraste* ou Android 12- desligam o vidro (superfícies opacas). Nada depende só de cor ou movimento.

## Ícones

Material Symbols **Rounded**, peso 400, dentro do quadrado colorido da célula (estilo Ajustes do iOS):
VPN `shield` azul · Cotas `hourglass` rosa · Horário `bedtime` índigo · Foco `do_not_disturb` roxo · Pasta segura `folder_special` verde · Admin `lock` cinza.

## Now Bar (Samsung) / Live Update (Android 16)

As duas cotas ficam numa **única barra** segmentada:

```
 [■■■■■■□□□□ | ■■□□□□□□□□]   Redes 8 min · Jogos 16 min        ⏳ 8m
   rosa (redes)  índigo (jogos)
```

- `Notification.ProgressStyle` com 4 segmentos: usado-redes (rosa), restante-redes (rosa 30%), usado-jogos (índigo), restante-jogos (índigo 30%). Um *point* marca a divisão entre as categorias.
- `shortCriticalText` = menor tempo restante (aparece no chip da Now Bar/status bar).
- `setRequestPromotedOngoing(true)` + permissão `POST_PROMOTED_NOTIFICATIONS` → o One UI 8 promove para a Now Bar e para a tela de bloqueio.
- Abaixo do Android 16: notificação contínua comum com o mesmo texto.
- Durante foco/recolher, a barra troca para o estado do modo (ex.: "Foco total até 18:00").

## Tom de voz

- Segunda pessoa, gentil: "Hora de focar. Reels volta amanhã." — nunca "ACESSO NEGADO".
- Ações destrutivas sempre pedem confirmação e só existem no Admin.
