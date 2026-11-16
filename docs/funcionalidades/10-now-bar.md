# 10 · Notificação: anel das cotas e Now Bar do foco

A notificação contínua do Bloqueio tem duas formas. Código: `notify/NowBarNotifier.kt`
(desenho) e `service/GuardService.kt` (quando atualizar).

## Foco ativo → Now Bar

É o **único** momento em que o app ocupa a Now Bar. A notificação pede promoção a Live Update
(`android.requestPromotedOngoing` + `ProgressStyle`, Android 16+):

- **Título:** Foco total / Foco parcial / Celular bloqueado.
- **Texto:** "Até 15:30 · redes e jogos bloqueados".
- **Chip e cabeçalho:** cronômetro regressivo do sistema até o fim.
- **Barra:** roxa, enche do início ao fim, com o símbolo do modo andando na ponta.

## Resto do tempo → anel na gaveta

Uma notificação comum, sem promoção, com layout próprio (`DecoratedCustomViewStyle`). Ela usa o
**mesmo anel duplo da tela Início**: o externo é redes (rosa), o interno é jogos (índigo), e o
progresso é a fração que **sobra**.

Recolhida (`res/layout/notif_quota.xml`):
```
 ◎  ● Redes 12:34    ● Jogos 08:10
    VPN protegida · descanso às 20:00
```

Expandida (`res/layout/notif_quota_big.xml`): anel de 84 dp, cada cota numa linha em 18 sp e a
linha de contexto embaixo.

- Tempos em **min:seg**, com números de largura fixa (`tnum`). Cota zerada aparece como "esgotada".
- A bolinha colorida é a legenda do anel. O nome sempre vem ao lado, porque a cor nunca está sozinha.
- O anel tem descrição para o TalkBack ("Redes: 12:34. Jogos: 08:10.").
- Com rede social ou jogo aberto, a notificação atualiza **a cada segundo**. O ícone pequeno vira
  balão ou controle, e o serviço desconta o tempo ao vivo entre os ciclos de 10 s.

### Linha de contexto (a primeira que valer)

1. Descanso pausado até 20:45
2. Hora de descansar · libera às 04:00
3. Servidor fora do ar · VPN desligada até ele voltar
4. Admin liberado até 15:12
5. Descanso às 20:00 · em 12 min (nos 15 min antes)
6. Instagram está usando a cota de redes
7. VPN protegida · descanso às 20:00

## Quando atualiza

- A cada 10 s, no ciclo do serviço. A cada 1 s, com um app de cota aberto (fora de foco e de descanso).
- Na hora em que: o app em primeiro plano muda, a configuração muda (foco, pausa, cotas), o admin
  destrava, o estado da VPN muda.

## Requisito do Samsung (só para o foco)

A Now Bar só aceita apps de fora da Samsung com **Opções do desenvolvedor › Notif. ao vivo para
todos apps** (*Live notifications for all apps*) ligado. Por baixo, é a chave
`enable_notification_nowbar_test` em `Settings.Secure`.

O Bloqueio liga essa chave **sozinho** (graças ao `WRITE_SECURE_SETTINGS` dado na instalação) e
confere a cada 10 s: se o ambiente limpo da pasta segura, uma atualização do sistema ou alguém a
desligar, ele religa. Só roda em aparelhos Samsung.

Sem isso, o foco aparece normalmente na gaveta, com cronômetro e barra.
