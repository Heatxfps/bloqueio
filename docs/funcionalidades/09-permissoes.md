# 09 · Modelo de permissões

| Ação | Usuário | Admin |
|---|---|---|
| Adicionar app à lista de bloqueio | ✅ | ✅ |
| Adicionar app à proteção contra desinstalação | ✅ | ✅ |
| Adicionar app a redes/jogos/essenciais | ✅ | ✅ |
| Iniciar foco / aumentar nível | ✅ | ✅ |
| Remover/editar qualquer regra | ❌ | ✅ |
| Encerrar foco antes do fim | ❌ | ✅ |
| Alterar cotas, horário, VPN, restrições | ❌ | ✅ |
| Adicionar/remover apps da pasta segura | ✅ | ✅ |
| Remover proteção do dispositivo | ❌ | ✅ |

Na UI, itens que só o admin edita mostram o cadeado 🔒. A verificação real acontece no `RulesRepository` (não só na UI).
