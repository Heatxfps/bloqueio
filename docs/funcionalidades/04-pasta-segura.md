# 04 · Pasta segura (ambiente limpo)

## Objetivo
Abrir apps sensíveis (bancos, gov.br, jogos com anti-cheat) como se o celular estivesse com configurações de fábrica, mesmo com o modo desenvolvedor ligado no dia a dia.

## Como funciona
1. O usuário adiciona/remove apps livremente na **Pasta segura** (única lista que o usuário pode editar).
2. Ao tocar no app pela pasta, `CleanEnvironment.enter()`:
   - salva os valores atuais de `development_settings_enabled`, `adb_enabled`, `adb_wifi_enabled`;
   - grava `0` em todos (requer `WRITE_SECURE_SETTINGS`, concedida via ADB);
   - abre o app.
3. Quando o primeiro plano passa para um app fora da pasta (ignorando teclado/SystemUI), `CleanEnvironment.exit()` restaura os valores salvos. As sub-opções do desenvolvedor são preservadas porque o app grava a flag diretamente (não usa o botão das Configurações, que as reseta).

## Limites
É um estado **global** temporário, não um perfil isolado. Ver [LIMITACOES.md](../LIMITACOES.md).
