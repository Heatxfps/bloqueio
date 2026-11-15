# 07 · Regras por pacote

Tipos de regra (`RuleType`): `BLOCK` (sempre bloqueado), `PROTECT_UNINSTALL`, `SOCIAL`, `GAME`, `ESSENTIAL`.

- Regras são salvas pelo **nome do pacote** (ex.: `com.zhiliaoapp.musically`), não pelo app instalado. Por isso podem ser criadas para apps que nem estão no celular (campo "adicionar por pacote").
- `PackageReceiver` escuta `PACKAGE_ADDED`: quando um app com regra é (re)instalado, a suspensão/proteção é aplicada na hora.
- Desinstalar um app bloqueado não apaga a regra.
