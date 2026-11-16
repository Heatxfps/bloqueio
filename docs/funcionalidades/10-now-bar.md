# 10 · Now Bar dinâmica

A notificação contínua do Bloqueio muda conforme o momento. No Android 16+ ela pede promoção
a **Live Update** (`android.requestPromotedOngoing` + `ProgressStyle`). No One UI 8+ isso a
leva à **Now Bar** da tela de bloqueio e ao chip da barra de status.

Código: `notify/NowBarNotifier.kt` (escolha do cartão) e `service/GuardService.kt` (quando atualizar).

## Cartões, do mais urgente para o mais calmo

| # | Quando | Título | Chip | Barra |
|---|---|---|---|---|
| 1 | Foco ativo | Foco parcial / Foco total / Celular bloqueado | cronômetro até o fim | tempo decorrido do foco (roxo) |
| 2 | Horário de descanso | Hora de descansar | cronômetro até a liberação | noite decorrida (índigo) |
| 2b | Pausa de emergência no descanso | Descanso pausado | cronômetro até o bloqueio voltar | — |
| 3 | Servidor da VPN caiu (failover) | Servidor fora do ar | "VPN" | — |
| 4 | Admin destravado | Admin liberado | cronômetro até travar | tempo restante (laranja) |
| 5 | Rede social ou jogo aberto | Nome do app | cronômetro da cota descendo | cota restante (rosa/índigo) |
| 5b | Idem, mas a cota acabou | Cota de redes esgotada | "0m" | vazia |
| 6 | Faltam menos de 15 min para o descanso | Descanso em breve | cronômetro até começar | 15 min |
| 7 | Nada acontecendo | Redes sociais → Jogos → Proteção ativa (alterna a cada 20 s) | "12m" / "OK" | cota restante |

Cada cartão tem o próprio ícone (balão, controle, lua, cadeado, alerta), e é ele que aparece no chip.

## Quando a barra é atualizada

- A cada 10 s, no ciclo do serviço (é isso que faz o rodízio do cartão 7 andar).
- Na hora em que: o app em primeiro plano muda, a configuração muda (foco iniciado,
  pausa, cotas), o admin destrava, o estado da VPN muda.
- Os cronômetros são do próprio sistema (`setUsesChronometer` + `setChronometerCountDown`):
  os segundos andam sem o app precisar notificar a cada segundo.

## Requisito do Samsung

O Android promove a notificação sozinho (`PROMOTED_ONGOING` no `dumpsys notification`). Mas o
One UI só a mostra na Now Bar se as **Live notifications** estiverem liberadas para apps de
terceiros. Conforme a versão, isso fica em:

- Configurações › Tela de bloqueio e AOD › Now Bar (lista de apps), ou
- Opções do desenvolvedor › **Live notifications for all apps** (Notificações ao vivo para todos os apps).

Sem isso, os cartões continuam funcionando na gaveta de notificações, mas não aparecem na Now Bar.
