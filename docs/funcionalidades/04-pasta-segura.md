# 04 · Pasta segura (ambiente limpo)

## Objetivo
Abrir apps sensíveis (bancos, gov.br, jogos com anti-cheat) como se o celular estivesse com configurações de fábrica, mesmo com o modo desenvolvedor ligado no dia a dia.

## Como funciona
1. O usuário adiciona/remove apps livremente na **Pasta segura** (única lista que o usuário pode editar).
2. Ao tocar no app pela pasta, `CleanEnvironment.enter()`:
   - salva e zera `development_settings_enabled`, `adb_enabled`, `adb_wifi_enabled` (requer `WRITE_SECURE_SETTINGS`);
   - **desliga o serviço de acessibilidade do Bloqueio**. Bancos brasileiros tratam app com acessibilidade ativa instalado fora da Play Store como risco ("golpe da mão fantasma") e mostram "seu celular corre risco";
   - abre o app.
3. Sem acessibilidade, a saída é detectada pelo **acesso ao uso** (`GET_USAGE_STATS`, via ADB): quando outro app fica 4 s em primeiro plano, `exit()` restaura tudo e religa a acessibilidade. As sub-opções do desenvolvedor são preservadas (a flag é gravada direto, sem o botão das Configurações, que as reseta).
4. **Sem brecha:** redes sociais, jogos e apps bloqueados colocados na pasta **não** desligam a acessibilidade. Sem acesso ao uso, a acessibilidade também não é desligada (não haveria como religar).
5. Enquanto a depuração está desligada, o celular some do `adb` (esperado).

## Limites
É um estado **global** temporário, não um perfil isolado. Bancos que verificam outras coisas (root, bootloader desbloqueado, Device Owner, VPN ativa) não são enganados por ele. Ver [LIMITACOES.md](../LIMITACOES.md).
