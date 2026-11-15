# Instalação e configuração

O Bloqueio só consegue travar VPN, impedir desinstalação e suspender apps se for **Device Owner** (dono do dispositivo). No Android isso só pode ser definido por ADB, uma única vez, e exige que **nenhuma conta** (Google, Samsung…) esteja no aparelho naquele momento.

## Pré-requisitos

- Android 10+ (Android 16 / One UI 8 para a Now Bar)
- PC com [Platform Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
- Depuração USB ativa (você já tem o modo desenvolvedor ligado)
- Tailscale instalado e logado

## Passo a passo

### 1. Remova temporariamente as contas

`Configurações → Contas e backup → Gerenciar contas` → remova **todas** (Google, Samsung, WhatsApp Business etc.). Elas voltam no passo 5. Seus dados ficam no celular; só o vínculo é removido.

> No Samsung, se aparecer "already some accounts on the device", ainda existe alguma conta (às vezes do Samsung Pass ou de apps como Microsoft).

### 2. Instale o APK

Baixe o APK da aba **Actions** ou **Releases** do repositório e:

```bash
adb install -r bloqueio.apk
```

### 3. Torne o Bloqueio Device Owner

```bash
adb shell dpm set-device-owner dev.foco.bloqueio/.admin.BloqueioAdminReceiver
```

Resposta esperada: `Success: Device owner set to package ...`

### 4. Conceda a permissão de configurações seguras

Necessária para o ambiente limpo da pasta segura e para religar o serviço de acessibilidade sozinho:

```bash
adb shell pm grant dev.foco.bloqueio android.permission.WRITE_SECURE_SETTINGS
```

### 5. Adicione as contas de volta

Agora pode logar Google/Samsung normalmente.

### 6. Primeira abertura do app

1. Crie a **senha mestra** e anote o **código de recuperação** (aparece uma única vez).
2. Ative o **serviço de acessibilidade** "Bloqueio" (o app abre a tela certa).
3. Permita **notificações** (Now Bar).

## Desinstalar (somente admin)

`Admin → Proteção → Remover proteção do dispositivo`. Isso remove o Device Owner e libera a desinstalação normal. Não existe outro caminho sem reset de fábrica (que fica bloqueado enquanto a proteção estiver ativa).

## Solução de problemas

| Problema | Causa / solução |
|---|---|
| `Not allowed to set the device owner because there are already some accounts` | Ainda há conta no aparelho (passo 1) |
| `Not allowed ... already several users` | Pasta Segura Samsung / Dual Messenger criam usuários extras. Desative-os temporariamente |
| Now Bar não mostra a cota | Requer One UI 8 (Android 16). Em versões anteriores a cota aparece como notificação fixa |
| Tailscale não desconecta sozinho | Abra o Tailscale uma vez após instalar o Bloqueio; versões antigas do Tailscale não aceitam intents |
