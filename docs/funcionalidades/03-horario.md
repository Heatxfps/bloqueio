# 03 · Toque de recolher

- Janela configurável pelo admin (padrão **20:00 → 04:00**, atravessa a meia-noite).
- Dentro da janela só funcionam os **essenciais**: Telefone, Mensagens, Relógio, Câmera, Mapas, Configurações, Tailscale, o próprio Bloqueio e os apps que o usuário adicionar como essenciais.
- Com Device Owner os demais apps abertos são interceptados e, se possível, suspensos (`setPackagesSuspended`); voltam ao fim da janela. Sem Device Owner, a acessibilidade bloqueia na abertura.
- Chamadas de emergência sempre funcionam.
- `DISALLOW_CONFIG_DATE_TIME` impede burlar mudando o relógio.
