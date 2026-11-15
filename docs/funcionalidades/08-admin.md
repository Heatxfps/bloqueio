# 08 · Admin e senha mestra

- **Criação**: no primeiro uso. Mínimo 6 caracteres. Armazenada como PBKDF2-HMAC-SHA256 (120 000 iterações, sal de 16 bytes).
- **Código de recuperação**: `XXXXX-XXXXX-XXXXX-XXXXX`, mostrado **uma vez**; também guardado como hash.
- **Desbloqueio temporário**: 5, 15 ou 30 min. Fica bloqueado ao fim do tempo ou ao tocar em "Bloquear agora". A sessão vive só em memória (reiniciar o app = bloqueado).
- **Trocar senha**: exige a senha atual.
- **Esqueci a senha**: informar o código de recuperação → criar nova senha → um **novo** código é gerado.
- **Força bruta**: após 5 erros, espera exponencial (30 s, 60 s, 120 s…).
