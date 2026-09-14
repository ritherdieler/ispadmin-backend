# Deploy staging — óptica SSH lab — 2026-08-31

## Backend

| Campo | Valor |
|-------|--------|
| Comando | `./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic` |
| Release local | `1.0.3+49c036a` (working tree con el feature; staging no registra OBS) |
| Destino | `ispadmin-staging.war` → `/ispadmin-staging` |
| Smoke | `GET /ispadmin-staging/` → HTTP 200 |
| Overlay horneado | `olt.gateway.sync.signal-enabled=false`, `olt.gateway.sync.lab-optical-ssh-enabled=true` |

`netdiag` y `traffic` se mantuvieron para no apagar el 360 anterior.

## Primer poll

`22:59:58` America/Lima — `LabOpticalSshScheduler`: `collected=1 unmapped=0` (conjunto lab actual: #2329).

CLI bus SSH listo en el arranque del WAR staging. Poll SNMP full-OLT **no** encendido.

## Cómo usar

Backoffice en modo staging → `/subscriptions/2329/service-health` → **Actualizar óptica** (headers de confirmación). El scheduler vuelve a recorrer todas las `subscription_acs.lab=1` cada 15 min.

Detalle: [lab-optical-ssh-staging-2026-08-31.md](./lab-optical-ssh-staging-2026-08-31.md).
