# Arquitetura

## Visão geral

```
┌────────────────────────────── UI (Compose) ──────────────────────────────┐
│ HomeScreen · RulesScreen · SecureFolderScreen · AdminScreen · Onboarding │
│ BlockActivity (tela de bloqueio)                                         │
└───────────────▲──────────────────────────────────────────┬───────────────┘
                │ StateFlow                                │ ações
┌───────────────┴──────────────── Domínio ─────────────────▼───────────────┐
│ RulesRepository (JSON, append-only p/ usuário)   AdminSession (senha)    │
│ Enforcer (decide: liberar / parcial / bloquear)  QuotaTracker            │
└───────▲──────────────────────▲──────────────────────────▲────────────────┘
        │                      │                          │
┌───────┴────────┐  ┌──────────┴───────────┐  ┌───────────┴──────────────┐
│ GuardAccessi-  │  │ GuardService (FGS)   │  │ PolicyManager (Device     │
│ bilityService  │  │ tick 10 s: cotas,    │  │ Owner): VPN always-on,    │
│ app em 1º plano│  │ horário, VPN health, │  │ suspender apps, bloquear  │
│ Reels/Shorts   │  │ Now Bar, auto-cura   │  │ desinstalação, restrições │
└────────────────┘  └──────────────────────┘  └──────────────────────────┘
```

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `admin` | `BloqueioAdminReceiver` (DeviceAdminReceiver) e `PolicyManager` (wrapper do `DevicePolicyManager`) |
| `data` | Modelos (`Rule`, `AppConfig`) e `RulesRepository` (persistência JSON em `filesDir`) |
| `security` | `PasswordVault` (PBKDF2 + código de recuperação) e `AdminSession` (desbloqueio temporário) |
| `enforce` | `Enforcer` (regra de decisão pura e testável), `QuotaTracker`, `DistractionDetector` (Reels/Shorts), `ScheduleWindow` |
| `vpn` | `TailscaleController` (intents do Tailscale) e `HostMonitor` (failover) |
| `securefolder` | `CleanEnvironment` (liga/desliga opções do desenvolvedor) |
| `service` | `GuardService` (foreground), `GuardAccessibilityService`, `PackageReceiver`, `BootReceiver` |
| `notify` | `NowBarNotifier` (Live Update / ProgressStyle) |
| `ui` | Compose: `theme` (design system), `components`, `screens` |

## Fluxo de decisão

`Enforcer.decide(pkg, estado)` retorna um `Verdict`:

1. Pacote do próprio app, launcher, SystemUI, telefone → `Allow`
2. Regra `BLOCK` explícita → `Block`
3. Dentro do horário de recolher e não-essencial → `Block`
4. Jogo: foco total/parcial **ou** cota de jogos esgotada → `Block`
5. Rede social: foco total → `Block`; foco parcial **ou** cota esgotada → `Partial` (o `DistractionDetector` age só nas telas de consumo)
6. Senão → `Allow`

Com Device Owner, `Block` usa `setPackagesSuspended` (o sistema mostra o app acinzentado). Sem Device Owner, o serviço de acessibilidade volta para a Home e abre a `BlockActivity`.

## Persistência

- `files/rules.json` — regras e configuração (`AppConfig`)
- `files/usage-YYYY-MM-DD.json` — uso do dia por categoria (zera à meia-noite)
- `SharedPreferences("vault")` — hash da senha, sal, hash do código de recuperação

## Por que Device Owner

Só o Device Owner consegue, sem root: travar o Always-on VPN, impedir desinstalação de si mesmo e de outros apps, suspender apps, bloquear reset de fábrica e modo seguro. Ver [LIMITACOES.md](LIMITACOES.md).
