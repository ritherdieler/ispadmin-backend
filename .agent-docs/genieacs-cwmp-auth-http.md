# GenieACS: autenticación HTTP CWMP (`cwmp.auth`)

## Estado actual (prod, 2026-08-25)

| Parámetro Mongo | Valor | Efecto |
|-----------------|-------|--------|
| `cwmp.auth` | **`true`** | GenieACS **no exige** usuario ni contraseña en la capa HTTP del Inform (sin Digest ni Basic). |

El ACS acepta sesiones TR-069 entrantes **sin** validar `Username` / `Password` del `ManagementServer` en HTTP.

Las credenciales **siguen existiendo** en la ONU y en GenieACS (`ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD` en `/opt/gigafiber/genieacs/.env`); se empujan con las provisions `inform` y `gigafiber-bootstrap`. Solo dejó de usarse la comprobación HTTP al recibir el Inform.

## Por qué (razón operativa)

Firmware **VSOL** (gSOAP 2.7 / Realtek) y **ZTE F6600R** no completan el flujo **Digest HTTP** que GenieACS envía en el primer `401`:

1. CPE envía Inform sin credencial HTTP.
2. GenieACS responde `401` con `WWW-Authenticate: Digest … qop="auth,auth-int"`.
3. El gSOAP del CPE **no reintenta** correctamente con el hash Digest.
4. GenieACS registra `Authentication failure` y cierra la sesión sin procesar el Inform.

Con `cwmp.auth = AUTH("gigafiber-acs", "<password>")` (config anterior), **ningún** CPE VSOL/ZTE volvió a informar tras el reboot del VPS del **2026-08-22** (~09:52 Lima), aunque las credenciales estuvieran bien en la GUI del CPE.

PoC lab VSOL V2804AX15T: mismo comportamiento documentado en [genieacs-vsol-v2804-parametros.md](./genieacs-vsol-v2804-parametros.md).

### Qué **no** cambió

| Flujo | Auth |
|-------|------|
| **Connection Request** (ACS → ONU, `:7547/tr069`) | Sigue **Digest** con `ConnectionRequestUsername` / `ConnectionRequestPassword` del árbol del dispositivo. |
| **Credenciales en la ONU** | Siguen configuradas (`gigafiber-acs` + password de `.env`). |
| **Provision `inform`** | Sigue declarando user/pass ACS y CR al CPE (`{value: 1}`). |

## Compensación de seguridad actual

| Capa | Medida |
|------|--------|
| **nginx** (`acs.gigafiberperu.cloud`) | `allow 38.224.231.4; deny all;` en `:80` y `:443` (CWMP). Solo tráfico NAT desde MK2. |
| **GenieACS CWMP** | Puerto `:7547` publicado solo en `127.0.0.1`; no expuesto directo a Internet. |
| **UI admin** | `:8443` con allowlist `38.224.231.2`, `38.224.231.4`. |
| **NBI** | `:7557` solo localhost (túnel SSH). |

Ver [genieacs-despliegue-gigafiber.md](./genieacs-despliegue-gigafiber.md).

## Riesgos que quedan

1. **Confianza en IP de origen**: quien pueda originar HTTP CWMP desde `38.224.231.4` (MK comprometido, NAT abierto) puede abrir sesión **sin** demostrar password HTTP.
2. **Inform en HTTP plano** (VSOL no usa HTTPS): el XML TR-069 no va cifrado MK2↔VPS; Digest tampoco cifra, pero autenticaba. Hoy la protección es red + allowlist.
3. **`cwmp.auth = true` es global**: aplica a todos los modelos que pasen el filtro nginx, no solo VSOL/ZTE.

## Historial

| Fecha | Evento |
|-------|--------|
| 2026-08-20 | Pilot con `AUTH("gigafiber-acs", …)` en Mongo. |
| 2026-08-22 ~09:52 | Reboot VPS; Informs VSOL/ZTE empiezan a fallar con `Authentication failure` (coincide con caída temporal de `wg-olt`). |
| 2026-08-25 | Cambio a `cwmp.auth = true`; VSOL y ZTE vuelven a informar. |

## Fuente en repo / VPS

| Archivo | Qué hace |
|---------|----------|
| `scripts/genieacs/configure-genieacs-pilot.sh` | Escribe `cwmp.auth = true` en Mongo y reinicia GenieACS. |
| `scripts/genieacs/start-genieacs-local.sh` | Mismo valor en entorno local. |
| `/opt/gigafiber/genieacs/configure-genieacs-pilot.sh` | Copia desplegada en VPS. |

## Volver a exigir Digest HTTP (solo si el firmware lo soporta)

```javascript
// Mongo genieacs.config — NO aplicar a VSOL/ZTE actuales
db.config.updateOne(
  { _id: "cwmp.auth" },
  { $set: { value: 'AUTH("gigafiber-acs", "<ACS_CPE_PASSWORD>")' } },
  { upsert: true }
);
```

Reiniciar GenieACS. Verificar en `genieacs-cwmp-access.log` que aparecen líneas `Inform` y no `Authentication failure`.

## Relacionado

- Credenciales CR / cache vacío en árbol: [genieacs-cr-credentials.md](./genieacs-cr-credentials.md)
- VSOL parámetros y limitaciones gSOAP: [genieacs-vsol-v2804-parametros.md](./genieacs-vsol-v2804-parametros.md)
