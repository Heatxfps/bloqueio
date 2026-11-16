# 10 · Now Bar dinâmica

A notificação contínua do Bloqueio muda conforme o momento. No Android 16+ ela pede promoção
a **Live Update** (`android.requestPromotedOngoing` + `ProgressStyle`). No One UI 8+ isso a
leva à **Now Bar** da tela de bloqueio e ao chip da barra de status.

Código: `notify/NowBarNotifier.kt` (escolha e desenho do cartão) e `service/GuardService.kt`
(quando atualizar).

## Gramática comum

Todos os cartões seguem a mesma estrutura, como uma Live Activity
(HIG › Live Activities: "Focus on important information that people need to see at a glance").

| Parte | Papel |
|---|---|
| **Título** | O que está valendo agora, em poucas palavras |
| **Texto** | Uma linha de contexto (até quando, o que está bloqueado) |
| **Chip** | O valor vivo: cronômetro quando há prazo; senão, um texto curto |
| **Barra** | Tempo. O símbolo do cartão anda na ponta, e o destino fica no fim |
| **Cor** | Rosa = redes · Índigo = jogos e noite · Roxo = foco · Laranja = exceção temporária · Vermelho = falha |

## Cartões, do mais urgente para o mais calmo

### 1 · Foco ativo
- Título: **Foco total** / **Foco parcial** / **Celular bloqueado**.
- Texto: "Até 15:30 · redes e jogos bloqueados".
- Chip: cronômetro regressivo até o fim.
- Barra roxa, que enche do início ao fim do foco. O símbolo do modo (alvo ou cadeado) anda na ponta.

### 2 · Descanso / pausa de emergência
- **Hora de descansar**: "Só os essenciais até as 04:00", com cronômetro até a liberação e barra índigo da noite.
- **Descanso pausado**: "O bloqueio volta às 20:45", com cronômetro até o bloqueio voltar. A barra
  laranja vai do momento da pausa (`Schedule.pausedAt`) até o fim, com um cadeado aberto andando
  em direção à lua.

### 3 · Servidor fora do ar
- Em vermelho: "A VPN foi desligada para a internet não cair. Volta sozinha." O chip mostra "VPN".

### 4 · Admin liberado
- "Regras editáveis até as 15:12", com cronômetro até travar. A barra laranja esvazia.

### 5 · Descanso em 15 min
- Título: **Descanso às 20:00**. Texto: "Termine o que está fazendo. Depois, só os essenciais."
- Chip: cronômetro até o início.
- Barra índigo de 15 min: a lua anda até o cadeado no fim.

### 6 · Cotas (o principal)
As duas cotas ficam num cartão só, em **min:seg**:

```
Redes 12:34  ·  Jogos 08:10
Nada descontando · VPN protegida · descanso às 20:00
[■■■■■■■■□□□|■■■■■□□□□□□]   chip: 8:10
  rosa        índigo
```

- Barra: dois trechos lado a lado, cada um do tamanho da sua cota. A cor cheia é o que sobra,
  e a apagada é o que já foi usado. Uma marca branca separa os dois.
- **Parado** (nenhum app de cota aberto): o chip mostra, em min:seg, a cota mais perto de acabar.
- **Com rede social ou jogo aberto**: só essa cota desce. O chip vira cronômetro e o ícone muda
  para balão (redes) ou controle (jogos). O texto diz "Instagram está usando a cota de redes".
  O título atualiza a cada segundo, e entre os ciclos de 10 s o serviço desconta o tempo ao vivo.
- Cota zerada: "Redes esgotada".

## Quando a barra é atualizada

- A cada 10 s, no ciclo do serviço.
- A cada 1 s, enquanto uma rede social ou um jogo está aberto (fora de foco e de descanso).
- Na hora em que: o app em primeiro plano muda, a configuração muda (foco iniciado, pausa,
  cotas), o admin destrava, o estado da VPN muda.
- Os cronômetros são do próprio sistema (`setUsesChronometer` + `setChronometerCountDown`).

## Requisito do Samsung

O Android promove a notificação sozinho (`PROMOTED_ONGOING` no `dumpsys notification`). Mas o
One UI só a mostra na Now Bar se as **Live notifications** estiverem liberadas para apps de
terceiros. Conforme a versão, isso fica em:

- Configurações › Tela de bloqueio e AOD › Now Bar (lista de apps), ou
- Opções do desenvolvedor › **Live notifications for all apps** (Notificações ao vivo para todos os apps).

Sem isso, os cartões continuam funcionando na gaveta de notificações, mas não aparecem na Now Bar.
