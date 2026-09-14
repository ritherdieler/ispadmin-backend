# Protección gestión MK2 (allowlist)

**Fecha:** 2026-07-21  
**Equipo:** MK2 `38.224.231.4`  
**Lista:** `api_whitelist`

## Política

Solo pueden conectar a API / SSH / Winbox:

| Origen | Address |
|--------|---------|
| VPS ispAdmin | `212.85.13.47` |
| Red interna | `192.168.0.0/16` |

| Servicio | Puerto | Estado |
|----------|--------|--------|
| API | `8728` | Allowlist + drop resto |
| SSH | `22` | Allowlist + drop resto |
| Winbox | `8291` | Allowlist + drop resto |
| api-ssl | `8729` | **disabled** |

## Aplicado

```text
/ip firewall address-list add list=api_whitelist address=212.85.13.47 comment="VPS ispAdmin"
/ip firewall address-list add list=api_whitelist address=192.168.0.0/16 comment="Red interna"

/ip service set api address=212.85.13.47/32,192.168.0.0/16
/ip service set ssh address=212.85.13.47/32,192.168.0.0/16
/ip service set winbox address=212.85.13.47/32,192.168.0.0/16
/ip service disable api-ssl

/ip firewall filter add chain=input action=accept protocol=tcp dst-port=8728 src-address-list=api_whitelist comment="API allowlist"
/ip firewall filter add chain=input action=drop protocol=tcp dst-port=8728 comment="API drop rest"
/ip firewall filter add chain=input action=accept protocol=tcp dst-port=22 src-address-list=api_whitelist comment="SSH allowlist"
/ip firewall filter add chain=input action=drop protocol=tcp dst-port=22 comment="SSH drop rest"
/ip firewall filter add chain=input action=accept protocol=tcp dst-port=8291 src-address-list=api_whitelist comment="Winbox allowlist"
/ip firewall filter add chain=input action=drop protocol=tcp dst-port=8291 comment="Winbox drop rest"
```

## Teléfono en datos

```text
/ip firewall address-list add list=api_whitelist address=<TU_IP_PUBLICA> timeout=4h comment="telefono temporal"
```

También hay que sumar esa IP al `address=` de ssh/winbox/api, o quitar el filtro del servicio y dejar solo firewall.

## Verificación

- Bots de internet no deben generar nuevos `login failure` vía api/ssh.
- VPS y hosts `192.168.x.x` (ej. `192.168.88.99`) siguen entrando.
