# Limitações conhecidas (sem root)

Transparência sobre o que o Android permite e o que não permite.

| Tema | Limitação | Mitigação |
|---|---|---|
| Device Owner | Só pode ser definido por ADB com o aparelho sem contas | Passo a passo em [INSTALACAO.md](INSTALACAO.md) |
| Pasta segura | O Android não permite "falsificar" o estado do sistema **por app**. O ambiente limpo desliga as Opções do desenvolvedor/depuração **no aparelho todo** enquanto o app da pasta está aberto | Restaura sozinho ao sair do app; apps que detectam root/bootloader desbloqueado não são enganados |
| Pasta segura | Não é um perfil isolado (dados separados). Perfil de trabalho e Device Owner não convivem facilmente | Para isolar dados use a Pasta Segura nativa da Samsung |
| Bloqueio parcial | Depende dos IDs de tela de cada app (Instagram, YouTube…). Uma atualização do app pode quebrar a detecção | IDs centralizados em `DistractionDetector` + detecção por texto como reserva |
| Now Bar | Promoção para a Now Bar exige Android 16 / One UI 8 e a decisão final é do sistema | Notificação contínua comum como reserva |
| Tailscale | Controle por intents (`CONNECT_VPN`/`DISCONNECT_VPN`) depende da versão do Tailscale | Monitor tenta de novo e avisa na notificação |
| Failover | Com o Tailscale desligado não dá para saber se o servidor voltou; o retorno é testado religando o Tailscale periodicamente | Intervalo configurável pelo admin |
| ForceEnabled | Política gerenciada lida pelo próprio app do Tailscale; versões antigas podem ignorá-la | Always-on religa o Tailscale e `DISALLOW_CONFIG_VPN` impede trocar de VPN |
| Lockdown | O "bloquear conexões sem VPN" do Android quebra o DNS com o Tailscale | Não é usado; o failover depende de a internet funcionar sem a VPN |
| Horário | Alterar data/hora burlaria o recolher | Restrição `DISALLOW_CONFIG_DATE_TIME` ativa por padrão |
| Senha esquecida | Sem senha **e** sem código de recuperação não há como abrir o Admin | Guarde o código; última saída é reset de fábrica via recovery (bloqueado no menu de Configurações) |
