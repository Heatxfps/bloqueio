# 01 · VPN travada no Tailscale + failover

## Objetivo
Ninguém consegue trocar ou desligar a VPN, e o único app permitido é o Tailscale. Se o servidor host (exit node) cair, o Tailscale é desconectado automaticamente para o celular não ficar sem internet.

## Como funciona
1. **Trava** (Device Owner):
   - `setAlwaysOnVpnPackage(admin, "com.tailscale.ipn", lockdown = true)`
   - Restrição `DISALLOW_CONFIG_VPN` (não dá para adicionar/trocar VPN em Configurações)
2. **Monitor de saúde** (`HostMonitor`, a cada 30 s no `GuardService`):
   - Testa uma conexão TCP com `hostIp:hostPort` (ex.: `100.x.y.z:22`) **e** `https://connectivitycheck.gstatic.com/generate_204`.
   - 3 falhas seguidas → **failover**: tira o always-on/lockdown e envia `com.tailscale.ipn.DISCONNECT_VPN`.
3. **Retorno**: a cada `retryMinutes` (padrão 5) envia `CONNECT_VPN`, espera 20 s e testa. Se o host respondeu, restaura a trava; senão desconecta de novo. Se houver `publicHealthUrl` configurada, ela é usada sem precisar religar o Tailscale.
4. **Auto-cura**: se a VPN estiver desligada fora do failover, o monitor manda `CONNECT_VPN`.

## Estados (mostrados na Home)
`Protegida` (verde) · `Host fora — sem VPN` (laranja) · `Testando retorno` (azul) · `Sem Device Owner` (cinza)

## Configuração (somente admin)
IP do host, porta, intervalo de retorno, URL pública opcional.
