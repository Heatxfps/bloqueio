# Instalação passo a passo

<p align="center"><img src="../.github/assets/passos.svg" alt="Instalação em 4 passos" width="100%"></p>

O Bloqueio só consegue travar a VPN, impedir a desinstalação e suspender apps se for o **Device Owner** (dono do dispositivo). O Android só deixa definir isso **pelo computador** e **sem nenhuma conta** no celular naquele momento. Tirar as contas não apaga nada: fotos, apps e conversas continuam no aparelho, e as contas voltam no fim.

**Tempo:** uns 15 minutos. **Você vai precisar de:**

- Um celular com **Android 10 ou mais novo**. A Now Bar do foco precisa de Samsung com One UI 8 ou mais novo.
- Um computador (Windows, macOS ou Linux) e um **cabo USB** que transfira dados.
- *(Opcional)* O [Tailscale](https://tailscale.com/download/android) instalado, se você quiser a VPN travada.

> [!WARNING]
> Depois de instalado, o Bloqueio **só sai com a senha do admin**. Guarde a senha **e** o código de recuperação, por exemplo num gerenciador de senhas ou no papel. Sem os dois, a única saída é restaurar o aparelho pelo modo de recuperação, o que **apaga tudo**.

---

## 1. Prepare o computador

Instale o **Platform Tools**, que traz o comando `adb`:

| Sistema | Como instalar |
|---|---|
| Windows | No PowerShell: `winget install Google.PlatformTools`. Ou baixe o [zip oficial](https://developer.android.com/tools/releases/platform-tools) e extraia. |
| macOS | `brew install android-platform-tools` |
| Linux | `sudo apt install adb` (Debian/Ubuntu) ou o [zip oficial](https://developer.android.com/tools/releases/platform-tools) |

Baixe também o **[bloqueio.apk](https://github.com/Heatxfps/bloqueio/releases/latest/download/bloqueio.apk)** para uma pasta fácil de achar, como Downloads.

## 2. Ative a depuração USB no celular

1. **Configurações → Sobre o telefone → Informações do software**: toque **7 vezes** em **Número de compilação**. Em outras marcas, o item fica direto em *Sobre o telefone*.
2. Volte em **Configurações → Opções do desenvolvedor** e ligue **Depuração USB**.
3. Conecte o cabo. No celular, aparece **"Permitir depuração USB?"**: marque *Sempre permitir* e toque em **Permitir**.

Para conferir, abra um terminal na pasta do APK e rode:

```bash
adb devices
```

Deve aparecer um código seguido de `device`. Se aparecer `unauthorized`, desbloqueie o celular e aceite o aviso.

## 3. Tire as contas (temporariamente)

**Configurações → Contas e backup → Gerenciar contas**. Remova **todas**: Google, Samsung, WhatsApp Business, Microsoft, Outlook…

No Samsung, desative também, se você usa:
- **Pasta Segura** (Configurações → Segurança e privacidade → Pasta Segura).
- **Dual Messenger** (Configurações → Recursos avançados).

Os dois criam "usuários" extras, e o Android não aceita Device Owner com mais de um usuário.

## 4. Rode os comandos

Com o celular conectado e desbloqueado, rode no terminal, **um de cada vez**:

```bash
adb install bloqueio.apk
```

```bash
adb shell dpm set-device-owner dev.foco.bloqueio/.admin.BloqueioAdminReceiver
```

A resposta esperada é `Success: Device owner set to package dev.foco.bloqueio`.

```bash
adb shell pm grant dev.foco.bloqueio android.permission.WRITE_SECURE_SETTINGS
```

```bash
adb shell appops set dev.foco.bloqueio GET_USAGE_STATS allow
```

| Comando | Para que serve |
|---|---|
| `install` | Instala o app. Pelo adb o Android não aplica as "configurações restritas", que impediriam ligar a acessibilidade. |
| `set-device-owner` | Dá ao Bloqueio o poder de travar a VPN, bloquear a desinstalação e suspender apps. |
| `WRITE_SECURE_SETTINGS` | Deixa o app religar a própria acessibilidade e montar o ambiente limpo da pasta segura. |
| `GET_USAGE_STATS` | Deixa o app saber quando você sai de um app de banco aberto pela pasta segura. |

## 5. Coloque as contas de volta

Pode entrar de novo no Google, no Samsung e nos outros normalmente. Se tinha desativado a Pasta Segura ou o Dual Messenger, já pode reativar.

## 6. Primeira abertura

Abra o **Bloqueio** e siga as telas:

1. **Crie a senha do admin.** Use 8 caracteres ou mais, com letras e números.
2. **Anote o código de recuperação.** Ele aparece **uma única vez**, e é ele que redefine a senha se você esquecer.
3. **Ligue a acessibilidade "Bloqueio".** O app abre a tela certa. Ela serve para saber qual app está aberto e se a tela é de Reels ou Shorts, e nada sai do celular.
4. **Permita as notificações**, para o anel das cotas e o cronômetro do foco.

Pronto. Na aba **Regras** você adiciona apps às categorias, e no **Admin** ajusta as cotas, o horário de descanso e a VPN.

### Now Bar (Samsung)

A Now Bar **não liga sozinha** para apps de fora da Samsung. Como a depuração USB já deixou as Opções do desenvolvedor visíveis, aproveite e ative:

**Configurações → Opções do desenvolvedor → Notif. ao vivo para todos apps** (em inglês, *Live notifications for all apps*).

Sem isso, o cronômetro do foco aparece só na gaveta de notificações.

### Tailscale (opcional)

Se você usa o Tailscale, entre nele antes ou depois de instalar o Bloqueio, tanto faz. Ele fica travado ligado, sem o botão de desconectar. Se você não usa, desligue em **Admin → VPN → Travar VPN no Tailscale**.

---

## Atualizar

Baixe o `bloqueio.apk` novo e rode `adb install -r bloqueio.apk`, ou abra o arquivo no próprio celular. Todas as versões são assinadas com a mesma chave, então a atualização instala por cima e mantém regras, senha e cotas.

## Desinstalar

**Admin → Remover proteção do dispositivo** (pede a senha). Isso tira o Device Owner, e depois o app desinstala normalmente.

## Solução de problemas

| Mensagem ou sintoma | O que fazer |
|---|---|
| `adb: command not found` / `'adb' não é reconhecido` | O Platform Tools não está no PATH. Abra o terminal dentro da pasta `platform-tools` ou reinstale pelo winget/brew. |
| `no devices/emulators found` | O cabo é só de carga, ou a depuração USB está desligada (passo 2). |
| `unauthorized` | Desbloqueie o celular e aceite "Permitir depuração USB". |
| `already some accounts on the device` | Ainda há alguma conta (passo 3). Confira também o Samsung Pass, o Microsoft e os apps de e-mail. |
| `already several users on the device` | A Pasta Segura, o Dual Messenger ou o "Modo crianças" estão ativos. Desative e tente de novo. |
| `Not allowed to set the device owner because the device is already provisioned` | Alguns aparelhos exigem as contas fora **e** nenhum usuário extra. Se mesmo assim falhar, o caminho é restaurar o aparelho e rodar o passo 4 antes de entrar em qualquer conta. |
| Banco reclama de "risco de segurança" | Abra o banco pela aba **Pasta segura** do Bloqueio, que monta o ambiente limpo. Alguns bancos recusam mesmo assim. Veja [LIMITACOES.md](LIMITACOES.md). |
| A acessibilidade aparece desligada depois de atualizar | O Bloqueio religa sozinho em até 30 s. Se não religar, desligue e ligue de novo em Configurações → Acessibilidade → Apps instalados → Bloqueio. |
| O cronômetro não aparece na Now Bar | Ative **Opções do desenvolvedor → Notif. ao vivo para todos apps** (seção *Now Bar* acima). |
