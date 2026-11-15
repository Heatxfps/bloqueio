# 06 · Proteção contra desinstalação

- Device Owner não pode ser desinstalado pela interface; além disso `setUninstallBlocked(self)`.
- Restrições ativas por padrão (admin pode alterar): `DISALLOW_FACTORY_RESET`, `DISALLOW_SAFE_BOOT`, `DISALLOW_CONFIG_VPN`, `DISALLOW_CONFIG_DATE_TIME`.
- O serviço de acessibilidade é religado sozinho se for desativado (via `WRITE_SECURE_SETTINGS`).
- Único caminho de remoção: `Admin → Proteção → Remover proteção do dispositivo` (exige senha mestra), que chama `clearDeviceOwnerApp`.
