# Bloqueio

App Android de **autocontrole digital** com proteção real: trava a VPN no Tailscale (com failover automático), cotas de distração com bloqueio **parcial** (Reels/Shorts), toque de recolher noturno, modos de foco, pasta segura com "ambiente limpo" e um painel **Admin** protegido por senha mestra.

> Design no estilo Apple (iOS) e cotas exibidas na **Now Bar** da Samsung (Live Updates do Android 16).

## Funcionalidades

| # | Funcionalidade | Como funciona | Doc |
|---|---|---|---|
| 1 | VPN travada no Tailscale + failover | Device Owner: política `ForceEnabled` do Tailscale (botão desconectar desabilitado) + *Always-on VPN* + `DISALLOW_CONFIG_VPN`. Sem configuração: se a rede tem internet mas nada passa pela VPN, o Tailscale é desligado e religado quando o servidor volta | [VPN](docs/funcionalidades/01-vpn.md) |
| 2 | Cotas de distração (20 min redes / 20 min jogos) | Contagem em tempo real pelo serviço de acessibilidade. Esgotou: jogos bloqueados; redes sociais em bloqueio **parcial** (Reels, Shorts, Explorar…) | [Cotas](docs/funcionalidades/02-cotas.md) |
| 3 | Toque de recolher (ex.: 20:00–04:00) | Só apps essenciais funcionam; o resto é suspenso | [Horário](docs/funcionalidades/03-horario.md) |
| 4 | Pasta segura / ambiente limpo | Apps abertos pela pasta rodam com Opções do desenvolvedor, depuração USB e Wi‑Fi desligadas; ao sair, tudo volta | [Pasta segura](docs/funcionalidades/04-pasta-segura.md) |
| 5 | Foco total e parcial | Total: redes + jogos bloqueados. Parcial: jogos bloqueados, redes só com as áreas de **criação** liberadas | [Foco](docs/funcionalidades/05-foco.md) |
| 6 | Impossível desinstalar | Device Owner + `setUninstallBlocked` + bloqueio de reset de fábrica/modo seguro | [Proteção](docs/funcionalidades/06-protecao.md) |
| 7 | Bloquear qualquer app, mesmo desinstalado | Regras salvas por nome de pacote; reaplicadas quando o app é reinstalado | [Regras](docs/funcionalidades/07-regras.md) |
| 8 | Admin com senha mestra | PBKDF2, desbloqueio temporário, troca de senha e redefinição por código de recuperação | [Admin](docs/funcionalidades/08-admin.md) |
| 9 | Usuário só adiciona, admin edita | Regras são *append-only* para o usuário; exceção: pasta segura | [Permissões](docs/funcionalidades/09-permissoes.md) |

## Documentação

- [Instalação e configuração (Device Owner via ADB)](docs/INSTALACAO.md) — **leia antes de instalar**
- [Arquitetura](docs/ARQUITETURA.md)
- [Design System (estilo Apple + Now Bar)](docs/DESIGN.md)
- [Padrões de código e contribuição](docs/PADROES.md)
- [Limitações conhecidas](docs/LIMITACOES.md)

## Compilar

Não é preciso ter Android Studio: todo push compila o APK no GitHub Actions (aba **Actions → Build APK → Artifacts**). Tags `v*` geram uma Release com o APK anexado.

Localmente (JDK 17 + Android SDK 36):

```bash
gradle :app:assembleDebug
```

## Stack

Kotlin 2.2 · Jetpack Compose (Material 3 com tema próprio estilo iOS) · kotlinx.serialization · minSdk 29 (Android 10) · targetSdk 36 (Android 16)
