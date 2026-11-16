## Baixar

**[bloqueio.apk](https://github.com/Heatxfps/bloqueio/releases/latest/download/bloqueio.apk)**: Android 10 ou mais novo.

> ⚠️ Instalar o APK **não** basta. O Bloqueio só consegue travar a VPN, impedir a desinstalação e suspender apps depois de virar **Device Owner** pelo computador. Para isso, siga o **[passo a passo de instalação](https://github.com/Heatxfps/bloqueio/blob/main/docs/INSTALACAO.md)** (uns 15 minutos).

## O que mudou na 1.0.1

- **Now Bar sem configuração manual no Samsung.** O Bloqueio liga sozinho a opção *Notif. ao vivo para todos apps* (Opções do desenvolvedor), que libera a Now Bar para apps de fora da Samsung, e religa se ela for desligada.

Quem já tem a 1.0.0 só precisa instalar por cima: `adb install -r bloqueio.apk`, ou abrir o arquivo no celular.

## O que o app faz

- **Cotas diárias** de redes sociais e de jogos, contadas segundo a segundo. Nas redes, só **Reels e Shorts** descontam e, quando a cota acaba, só eles são bloqueados.
- **Modos de foco**: Parcial, Total e Bloquear, com cronômetro na **Now Bar** (One UI 8+).
- **Horário de descanso**, com um Hub que só deixa abertos os apps essenciais.
- **Pasta segura** com ambiente limpo, para os apps de banco.
- **VPN travada no Tailscale**, com failover automático quando o servidor cai (opcional).
- **Proteção contra desinstalação** e um **Admin** com senha mestra e código de recuperação.

O APK é assinado sempre com a mesma chave, então as próximas versões instalam por cima desta.
