# MK2 www-ssl (REST 443) — 2026-08-01

## Problema

Desde el VPS, `MK2:443` fallaba: `/ip service` tenía **www-ssl disabled** y sin certificado (`certificate=none`).

## Fix

1. `scripts/mk2-enable-www-ssl.py` — CA `netdiag-ca`, cert servidor `netdiag-rest-mk2`, `www-ssl` en **443** con `address=212.85.13.47/32,192.168.0.0/16`.
2. `scripts/mk2-export-truststore.sh` — importa CA MK2 en `routeros-mk-truststore.jks` (alias `mk2-netdiag-ca`).
3. Redeploy WAR Tomcat para cargar JKS.

Ejecutar en VPS (credenciales MK2 desde `network_device.id=8`):

```bash
python3 /opt/gigafiber/scripts/mk2-enable-www-ssl.py
```

## Validación

- VPS: `curl --cacert mk2-netdiag-ca.crt -u user:pass https://38.224.231.4/rest/system/identity` → JSON identity.
- TCP `38.224.231.4:443` OK desde VPS y Tomcat tras deploy.

NetDiag sigue con `net.diag.mikrotik.fallback-classic=true` (8728) si REST falla; con www-ssl activo el poll MK2 puede usar REST directo.
