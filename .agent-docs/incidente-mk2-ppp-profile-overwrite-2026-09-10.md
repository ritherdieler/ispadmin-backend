# Incidente MK2 2026-09-10: `find where name=$name` sobrescribió los 10 perfiles PPP

**Severidad:** alta (config), impacto de servicio: **ninguno**.
**Duración de la config incorrecta:** ~4 minutos (18:27 → 18:31 -05).
**Estado:** resuelto, causa raíz corregida en el script.

## Qué pasó

Al aplicar `scripts/mikrotik-mk2-pppoe-vlan100.rsc` (fase 4 de la migración PPPoE) en
MK2 `38.224.231.4`, el bucle que crea el catálogo de perfiles usaba:

```
:foreach name,rate in=$planProfiles do={
  :if ([:len [/ppp profile find where name=$name]] = 0) do={ ... } else={
    /ppp profile set [find where name=$name] ...
  }
}
```

RouterOS evalúa el `where` en el contexto de **cada ítem**, así que `$name` no se resuelve
como la variable local del `:foreach` sino como la **propiedad `name` del propio perfil**.
La comparación `name=name` es siempre verdadera, `find` devolvió los 10 perfiles y el
`set` los pisó a todos con los valores de la última iteración.

Resultado: `default`, `default-encryption` y los 8 perfiles del PPPoE legado
(`PLAN 50 SOLES`, `PLAN 70`, `PLAN 100`, `CORTE DE SERVICIO`, `PLAN 150 SOLES`,
`PLAN 70 NEW`, `PLAN 100 NW`, `PLAN 150 SOLES NEW`) quedaron con
`local-address=10.64.0.1`, `remote-address=PPPOE-DINAMICO`, `rate-limit=600M/600M`,
`dns-server=8.8.8.8,8.8.4.4`, `only-one=yes`, `change-tcp-mss=yes`.

El síntoma que lo delató fue otro: el `pppoe-server add default-profile=GF-200-200` abortó
el import con `input does not match any value of default-profile`, porque los perfiles
`GF-*` del plan nunca llegaron a crearse (el `else` se los comió).

## Por qué no hubo corte

RouterOS **no reaplica el perfil a una sesión PPPoE viva**; solo lo lee al autenticar. Las
51 sesiones del servidor legado `PPOE CLIENTES` (bridge `LAN-VLAN1`) siguieron con su
`local-address` 192.168.26.1 y su rate-limit original durante toda la ventana. Se
restauró antes de que ninguna reconectara. Verificado: `[:len [/ppp active find]]` = 51
antes, durante y después.

Si alguna sesión hubiera reconectado en esa ventana habría tomado IP del pool
`PPPOE-DINAMICO` (10.64.x) en vez de `PPOE CLIENTES` (192.168.26.x), perdiendo su simple
queue por IP y su ruta.

## Restauración

Valores originales recuperados del export completo `antes_app8_susalud_mkt2.rsc`
(2026-08-05) que vive en el propio router (`/file print`). Los builtin `default` y
`default-encryption` no aparecen en el export por ser DEFAULT, se devolvieron a
`!local-address !remote-address !dns-server !rate-limit only-one=default change-tcp-mss=yes`.

Script de restauración: `scripts/mikrotik-mk2-ppp-profile-restore-20260910.rsc`
(idempotente, todos los `find` con literales).

## Causa raíz y corrección

En `scripts/mikrotik-mk2-pppoe-vlan100.rsc` la variable del bucle se renombró a
`$profileName` / `$profileRate`. Con un nombre que no coincide con ninguna propiedad del
ítem, `find where name=$profileName` sí compara contra el valor de la variable.

## Reglas para futuros `.rsc`

1. **Nunca** usar una variable con el mismo nombre que la propiedad dentro de
   `find where <prop>=$<prop>`. Prefijar siempre (`$profileName`, `$queueName`, `$listName`).
2. Preferir literales en `find where` cuando el conjunto es fijo.
3. Antes de un `set` masivo, comprobar el tamaño del match:
   `:if ([:len [/ppp profile find where name=$profileName]] > 1) do={:error "match ambiguo"}`.
4. Los literales de array (`{"a"="1";"b"="2"}`) deben ir en **una sola línea**: `/import`
   interpreta las llaves multilínea como bloque de código y el `:foreach` no itera.
5. Para limpiar un valor en `set` se usa `!propiedad`, no `propiedad=""`
   (`""` da `ambiguous value of pool` en `local-address`).
6. Verificar el estado completo del recurso tocado después de importar, no solo las filas
   que el script pretendía crear.

## Estado final del router

| Recurso | Valor |
|---|---|
| Perfiles legados | Restaurados al export 2026-08-05, `change-tcp-mss=default` |
| `default` / `default-encryption` | Restaurados a builtin |
| Perfiles nuevos | `GF-200-200`, `GF-300-300`, `GF-400-400`, `GF-500-500`, `GF-600-600`, `GF-CORTE`, `GF-STG-200-200` |
| Gateway | `10.64.0.1/18` en `vlan100-olt` |
| Pools | `PPPOE-DINAMICO` 10.64.0.2-10.64.47.254, `PPPOE-STG` 10.64.60.2-10.64.60.254 |
| Servidores PPPoE | `GIGAFIBER-PPPOE` y `GIGAFIBER-PPPOE-STG` en `vlan100-olt`, legado intacto en `LAN-VLAN1` |
| Firewall | NAT masquerade 10.64.0.0/18 + 2 drops de aislamiento (posiciones 18 y 19 de `forward`) |
| Sesiones activas | 51, sin interrupción |

Backups previos disponibles en el router: `BCKP.backup` y
`CCR2116 NUEVO-20260910-1702.backup`, ambos de las 17:0x, anteriores al cambio.
