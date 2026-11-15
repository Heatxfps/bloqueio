# Padrões do projeto

## Código

- **Kotlin** idiomático, `ktlint` style (4 espaços, sem wildcard imports).
- Pacote base `dev.foco.bloqueio`. Organização **por funcionalidade** (ver [ARQUITETURA.md](ARQUITETURA.md)).
- Lógica de decisão **pura** (sem `Context`) em `enforce/` para ser testável em JVM.
- Estado da UI exposto como `StateFlow`; Composables não acessam `DevicePolicyManager` diretamente — sempre via `PolicyManager`.
- Toda chamada de Device Owner passa por `PolicyManager` e é *no-op* seguro quando o app não é Device Owner.
- Textos da UI em pt-BR, no tom definido em [DESIGN.md](DESIGN.md#tom-de-voz). (Extrair para `strings.xml` e internacionalizar é um item futuro.)

## Regras de permissão (invariante de segurança)

- O usuário comum **só adiciona** regras (`RulesRepository.addRule`).
- Editar/remover regra, alterar cota, horário, VPN e proteção exige `AdminSession.isUnlocked()`; os métodos do repositório verificam isso e lançam `AdminRequiredException`.
- Exceção única: a lista da **pasta segura** é livre para o usuário.

## Design

- Usar somente tokens de `ui/theme` e componentes de `ui/components` (ver [DESIGN.md](DESIGN.md)).
- Telas novas começam com `LargeTitleScaffold`.

## Git

- Branch principal `main`; trabalho em `feat/…`, `fix/…`, `docs/…`.
- Commits no padrão **Conventional Commits** em português: `feat(vpn): failover quando o host cai`.
- Toda funcionalidade nova tem sua página em `docs/funcionalidades/`.

## Assinatura do APK (importante)

O app é Device Owner e **não pode ser desinstalado** — portanto toda atualização precisa ser assinada com a **mesma chave**, senão o Android recusa instalar por cima.

- A chave fica fora do git em `keystore/bloqueio-release.p12` (senha em `keystore/senha.txt`). **Faça backup dela.**
- No GitHub ela está nos secrets `BLOQUEIO_KEYSTORE_B64` e `BLOQUEIO_KEYSTORE_PASSWORD`, usados pelo workflow.
- Build local assinado: defina `BLOQUEIO_KEYSTORE_PASSWORD` com o conteúdo de `senha.txt` antes do `gradle`.

## Versionamento

SemVer. `versionName` no `app/build.gradle.kts`; tag `vX.Y.Z` dispara a Release com APK.
