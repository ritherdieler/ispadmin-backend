# Investigación TR-069 PENDING — staging #2384 (2026-09-06)

## Síntoma

E2e Espresso staging (`E2E_WIFI_SSID=stagingmimi`, ONU `ZTEGDC47BFFD`) falló a 300 s esperando `tr069_provision_status_COMPLETE`.

Core: OLT/MikroTik **COMPLETE**, TR-069 **PENDING**.

## Mensaje canónico

```
IP/SSID/WAN ConnectionStatus no se confirmaron en el ACS dentro del tiempo de espera.
| ip observado=192.168.30.25 esperado=192.168.250.16
| ConnectionStatus=Connected
| ssid24 observado=mimiwifi esperado=stagingmimi
| ssid5 observado=mimiwifi - 5G esperado=stagingmimi - 5G
| lastInform=2026-09-07T03:30:12.077Z
```

## Causa raíz

El ACS **sí encoló** tareas GenieACS (`refreshObject` + `setParameterValues` + varios `getParameterValues` ~03:32Z) tras `POST …/cpe/provision` (22:34 -05).

El CPE **no ejecutó** esas tareas: `_lastInform` quedó congelado en `2026-09-07T03:30:12.077Z` y en GenieACS siguen los valores del alta **local** previa (`mimiwifi`, WAN cliente `192.168.30.25`).

Connection Request URL en GenieACS: `http://192.168.255.236:58000/…`

Desde el VPS:

- `ping 192.168.255.236` → 100% loss  
- `ping 192.168.30.25` → 100% loss  
- HTTP al CR → `No route to host`

Sin sesión TR-069 post-cola, la verificación lee datos stale y caduca en `PENDING` (no `FAILED`).

## Contexto agravante

Alta local anterior con `SKIP_POST_CLEANUP=1` dejó WiFi `mimiwifi` e IP `.30.25` en el CPE/GenieACS. El cleanup OLT del e2e staging re-autorizó en `.250.16`, pero el plano ACS (`.255`) no quedó alcanzable para CR.

## Evidencia

| Pieza | Valor |
|-------|--------|
| Sub | `#2384` / DNI `98751844` |
| Device GenieACS | `5872C9-F6600R-ZTEGDC47BFFD` |
| Tareas colgadas | 14 (`status` null) |
| Faults GenieACS | 0 |
| `lastBoot` | `2026-09-05T21:14:03Z` |

## Remedio operativo sugerido

1. Recuperar L3 ACS hacia la ONU (mgmt `.255.x` vía `wg-olt` / VLAN ACS) hasta que CR responda.  
2. Vaciar tareas GenieACS stale del device y re-disparar provision / wifi-refresh.  
3. En lab, evitar solapar alta local sin cleanup ACS con e2e staging sobre el mismo SN.

## Verificación OLT (2026-09-06 ~23:35) — ONU **no** autorizada

Inconsistencia Core vs OLT real:

| Fuente | Estado |
|--------|--------|
| Core `#2384` | `olt_provision_status=COMPLETE`, IP `.250.16` |
| Activation store Gateway | `oltStatus=COMPLETE`, `uniqueExternalId=gigafiber-ma5608t_1_6_115` |
| `GET …/onus/by-sn/ZTEGDC47BFFD` | `onu_not_found` |
| `unconfigured_onus` | **sí** aparece board 1 / port 6 |
| `olt_mgr_onu` id 7983 | `deleted_at=2026-09-06 22:46:11`, board=-1 |

Timeline audit:

1. `22:30:57` — `delete_onu` (precleanup e2e) sobre `…_1_6_115` / onu 7982  
2. `22:32:33` — `POST …/onu/activate` (alta) → HTTP 200; **no** hay audit `authorize_onu` para 7983  
3. `22:46:11` — inventory sync `sync_missing_on_olt` soft-delete 7983: el SN **no estaba** en el inventario leído de la OLT  

Conclusión: el alta marcó OLT COMPLETE en Core/activation-store, pero la ONT **no quedó** (o no permaneció) autorizada en la MA5608T; hoy está en autofind otra vez.
