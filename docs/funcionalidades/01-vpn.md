# 01 · VPN travada no Tailscale + failover

## Objetivo
Ninguém consegue trocar ou desligar a VPN, e o único app permitido é o Tailscale. Se o servidor (exit node) cair, o Tailscale é desconectado automaticamente para o celular não ficar sem internet. **Nada para configurar** no app.

## Como funciona
1. **Trava** (Device Owner):
   - Política gerenciada do Tailscale `ForceEnabled = true` via `setApplicationRestrictions` → o botão **Desconectar** do Tailscale fica desabilitado.
   - `setAlwaysOnVpnPackage(admin, "com.tailscale.ipn", lockdown = true)`.
   - Restrição `DISALLOW_CONFIG_VPN` (não dá para adicionar/trocar VPN em Configurações).
2. **Detecção de queda sem configuração** (`HostMonitor`, a cada 30 s no `GuardService`):
   - O Android valida a rede física (Wi‑Fi/dados) por fora da VPN (`NET_CAPABILITY_VALIDATED`).
   - Se a rede física **está validada** mas `https://connectivitycheck.gstatic.com/generate_204` **não responde pela VPN**, quem caiu foi o servidor.
   - Sem rede física validada (sem sinal) → estado `Sem internet`, nada é desconectado.
   - 3 falhas seguidas → **failover**: `ForceEnabled = false`, tira o always-on/lockdown e envia `com.tailscale.ipn.DISCONNECT_VPN`.
3. **Retorno**: a cada `retryMinutes` (padrão 5) envia `CONNECT_VPN`, espera 20 s e testa a internet pela VPN. Funcionou → restaura `ForceEnabled` + always-on; senão desconecta de novo.
4. **Auto-cura**: se a VPN estiver desligada fora do failover, o monitor manda `CONNECT_VPN`.

## Estados (mostrados na Home e na Now Bar)
`Protegida` (verde) · `Sem internet` · `Servidor fora — sem VPN` (laranja) · `Testando retorno` (azul) · `Sem Device Owner` (cinza)

## Ajustes (somente admin)
Liga/desliga a trava e o intervalo de teste de retorno.
