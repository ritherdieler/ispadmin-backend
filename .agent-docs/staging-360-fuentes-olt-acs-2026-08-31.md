# Staging 360 · fuentes OLT/ACS/tráfico (2026-08-31)

## Qué se desplegó

```bash
./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic
```

`scripts/subsystems.sh` (con `--write-dir`) ahora hornea, además del overlay ACS de `servicehealth`:

| Módulo | Flags adicionales |
|--------|-------------------|
| `oltgateway` | `service.health.optical-enabled=true`, `olt.gateway.enabled=true`, inventory on, **signal SNMP off**, `optical-pull-mode=live-sns` (con servicehealth), **trap SNMP off** |
| `netdiag` | `net.diag.enabled=true`, UDP trap/syslog **off** (evita choque de puertos con prod) |

## Fixture #2329

| Fuente | Resultado | Motivo |
|--------|-----------|--------|
| **ACS / Wi‑Fi** | FRESH (2 asociados, RSSI, last_inform) | Tag `lab` + `subscription_acs.lab=1`; collector y `WIFI_REFRESH` OK |
| **OLT** | Identidad OK; óptica live por SN | Inventario **`VSOL0031C0B6`** (board 1 / port 6 / onu 10). Poll SNMP full-OLT **off** en staging. Health `POST /onus/optical` con SNs lab — [lab-optical-ssh-staging-2026-08-31.md](./lab-optical-ssh-staging-2026-08-31.md) |
| **TRAFFIC** | OK tras cableado MK (poll ~60 s) | `host_device_id=8`, cola `[stg]` en CCR 2, plan `lab-basico` — [lab-trafico-mikrotik-2329-2026-08-31.md](./lab-trafico-mikrotik-2329-2026-08-31.md) |
| **NETDIAG** | Target `STAGING-CCR-ACTIVE` (CCR activo) | Provisionado por `scripts/sql/netdiag-target-staging-active-ccr.sql`; esperar probe `SUCCESS` (~60 s) |

## Cómo revalidar ACS

1. Backoffice: `vite --mode staging --port 3010` → `/subscriptions/2329/service-health`
2. Botón **Actualizar Wi‑Fi del equipo** (headers `X-Confirm-Action` + `Idempotency-Key`)
3. Botón **Actualizar óptica** (mismo confirm; SSH puntual, no poll SNMP)
4. API: `GET /subscription/2329/service-health` debe mostrar `states.acs=FRESH` y muestra óptica si el SSH respondió

## Cierre

Para apagar collectors en staging: `./scripts/deploy.sh --env staging` (sin `--with`).
Para solo ACS lab: `./scripts/deploy.sh --env staging --with servicehealth`.
