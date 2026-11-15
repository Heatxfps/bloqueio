# Design System — "Bloqueio"

Inspirado nas Human Interface Guidelines da Apple (iOS): calmo, muito espaço em branco, hierarquia por tipografia e não por bordas, listas agrupadas com cantos arredondados e cores de sistema. Toda tela nova **deve** usar apenas os tokens abaixo (implementados em `ui/theme`).

## Princípios

1. **Clareza** — uma ação principal por tela. Texto curto, direto, em português.
2. **Deferência** — o conteúdo manda; cromado mínimo, sem sombras pesadas, sem gradientes.
3. **Profundidade** — camadas por cor de fundo (fundo agrupado → célula), não por elevação.
4. **Calma** — bloquear não é punir. A tela de bloqueio é gentil, nunca vermelha agressiva.

## Cores (`BloqueioColors`)

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `background` (grouped) | `#F2F2F7` | `#000000` | Fundo das telas |
| `cell` | `#FFFFFF` | `#1C1C1E` | Células e cards |
| `cellSecondary` | `#F2F2F7` | `#2C2C2E` | Campos, chips |
| `label` | `#000000` | `#FFFFFF` | Texto principal |
| `secondaryLabel` | `#3C3C43` @60% | `#EBEBF5` @60% | Texto de apoio |
| `separator` | `#3C3C43` @29% | `#545458` @60% | Divisórias (0,5 dp) |
| `accent` (systemBlue) | `#007AFF` | `#0A84FF` | Ações, links, toggles |
| `social` (systemPink) | `#FF2D55` | `#FF375F` | Cota de redes sociais |
| `games` (systemIndigo) | `#5856D6` | `#5E5CE6` | Cota de jogos |
| `success` (systemGreen) | `#34C759` | `#30D158` | VPN ok, liberado |
| `warning` (systemOrange) | `#FF9500` | `#FF9F0A` | Cota < 5 min |
| `danger` (systemRed) | `#FF3B30` | `#FF453A` | Ações destrutivas (só admin) |
| `focus` (systemPurple) | `#AF52DE` | `#BF5AF2` | Modos de foco |

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
| `IosSwitch` | Toggle verde estilo iOS (51×31 dp) |
| `PrimaryButton` | Preenchido `accent`, 50 dp de altura, raio 14, texto SemiBold |
| `QuotaRing` | Anel duplo (rosa redes / índigo jogos) estilo Atividade do Apple Watch |
| `SegmentedControl` | Controle segmentado (Desligado / Parcial / Total) |
| `LockBadge` | Cadeado pequeno indicando "só admin edita" |

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
