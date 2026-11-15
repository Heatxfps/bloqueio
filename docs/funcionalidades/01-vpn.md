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
   - Cada verificação faz 2 tentativas em 2 destinos (gstatic e Cloudflare), para um soluço da rede não contar.
   - 4 verificações seguidas falhando (~2 min) → **failover**: `ForceEnabled = false` e o always-on é removido, o que derruba a VPN.
3. **Retorno**: a cada `retryMinutes` (padrão 5) o always-on é reposto **sem lockdown**, espera 20 s e testa a internet pela VPN. Funcionou → lockdown + `ForceEnabled` de volta; senão remove o always-on de novo.
4. **Auto-cura**: fora do failover o always-on com lockdown é reaplicado a cada verificação, e o sistema religa o Tailscale sozinho.

> **Por que não usar os intents `CONNECT_VPN`/`DISCONNECT_VPN` do Tailscale?** Remover o always-on revoga a permissão de VPN do Tailscale, e a janela do Android para concedê-la de novo é bloqueada pela restrição `DISALLOW_CONFIG_VPN` (o Tailscale mostra erro ao tentar ligar). Repor o always-on pelo Device Owner concede a permissão sem janela.

Os eventos ficam no logcat com a tag `HostMonitor` (`adb logcat -s HostMonitor`).

## Estados (mostrados na Home e na Now Bar)
`Protegida` (verde) · `Sem internet` · `Servidor fora — sem VPN` (laranja) · `Testando retorno` (azul) · `Sem Device Owner` (cinza)

## Ajustes (somente admin)
Liga/desliga a trava e o intervalo de teste de retorno.
