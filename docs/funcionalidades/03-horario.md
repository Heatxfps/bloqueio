# 03 · Horário de descanso e Hub

- Janela configurável pelo admin (padrão **20:00 → 04:00**, atravessa a meia-noite).
- Dentro da janela o celular mostra o **Hub**: a única tela inicial possível até o fim do descanso.

## O Hub

| Parte | O que mostra |
|---|---|
| Relógio | Hora grande e data |
| Contagem regressiva | Anel que se completa ao longo da noite + "Libera às 04:00 · faltam 6 h 12 min" |
| Apps liberados | Grade com os apps **essenciais** instalados (Telefone, Mensagens, Relógio, Câmera, Mapas, Configurações e os que você adicionar em Regras → Essenciais) |
| Pasta segura | Os apps da pasta (bancos) também funcionam no descanso e abrem no ambiente limpo |
| Emergência | "Pausar com o Admin": senha mestra → descanso pausado por 15, 30 ou 60 min |

- **Notificações e controles rápidos funcionam**: é só deslizar de cima para baixo como sempre.
- Botão Início e "voltar" sempre levam ao Hub. Tentar abrir um app não essencial (pelos recentes ou por uma notificação) também leva ao Hub, e o app continua pausado pelo sistema.
- Às **04:00** (ou ao pausar) o Hub devolve o launcher normal e se fecha sozinho.
- Chamadas de emergência sempre funcionam.

## Como funciona (técnico)

- Não usa o "modo quiosque" (lock task) do Android: nele o painel de **controles rápidos fica sempre desativado**.
- Em vez disso, o Bloqueio (Device Owner) liga o alias `HubHome` (categoria HOME) e o fixa como launcher preferido com `addPersistentPreferredActivity`; no fim, `clearPackagePersistentPreferredActivities` devolve o launcher do usuário.
- Os apps que não são essenciais nem da pasta segura são pausados (`setPackagesSuspended`) durante a janela; o serviço de acessibilidade leva ao Hub se algum for aberto.
- A pausa do Admin fica em `Schedule.pausedUntil`; enquanto estiver no futuro, o descanso não vale.
- `DISALLOW_CONFIG_DATE_TIME` impede burlar mudando o relógio.
