## Baixar

**[bloqueio.apk](https://github.com/Heatxfps/bloqueio/releases/latest/download/bloqueio.apk)**: Android 10 ou mais novo.

> ⚠️ Instalar o APK **não** basta. O Bloqueio só consegue travar a VPN, impedir a desinstalação e suspender apps depois de virar **Device Owner** pelo computador. Para isso, siga o **[passo a passo de instalação](https://github.com/Heatxfps/bloqueio/blob/main/docs/INSTALACAO.md)** (uns 15 minutos).

## O que mudou na 1.0.3

- **Ligar e desligar cada cota.** Em **Admin › Cotas diárias**, redes sociais e jogos têm um interruptor próprio. Com a cota desligada, a categoria não conta tempo nem é bloqueada por cota, e a Home e a notificação mostram "Sem limite". Foco e descanso continuam valendo.

### Na 1.0.2

- **Cota de redes contando no ritmo certo.** Ao acordar o celular, o contador "recuperava" o tempo de tela apagada e cobrava na cota se você abrisse o Reels logo em seguida. Agora só conta o tempo em que o Reels ou o Shorts estão de fato na tela.
- **Reels bloqueado também depois da gaveta.** Com a cota esgotada, abrir e fechar a gaveta de notificações deixava rolar o Reels à vontade. Agora o app confere a tela a cada segundo e tira você de lá.
- *(1.0.1)* No Samsung, o app liga sozinho *Notif. ao vivo para todos apps*, que libera a Now Bar.

Quem já tem uma versão anterior só precisa instalar por cima: `adb install -r bloqueio.apk`, ou abrir o arquivo no celular.

## O que o app faz

- **Cotas diárias** de redes sociais e de jogos, contadas segundo a segundo. Nas redes, só **Reels e Shorts** descontam e, quando a cota acaba, só eles são bloqueados.
- **Modos de foco**: Parcial, Total e Bloquear, com cronômetro na **Now Bar** (One UI 8+).
- **Horário de descanso**, com um Hub que só deixa abertos os apps essenciais.
- **Pasta segura** com ambiente limpo, para os apps de banco.
- **VPN travada no Tailscale**, com failover automático quando o servidor cai (opcional).
- **Proteção contra desinstalação** e um **Admin** com senha mestra e código de recuperação.

O APK é assinado sempre com a mesma chave, então as próximas versões instalam por cima desta.
