# Zona 2 TP-Link — bind TR-069 al WAN 10.20 (2026-09-19)

Zona 2 = CPE con WAN `10.20/22` ya levantada y CR todavía en internet. No es retag VLAN 100→1000.

## Layout vivo (GPV)

Product class `IGD` o `XC220-G3v` (mismo firmware TP-Link). Piloto: `5C628B-XC220%2DG3v-5C628B97BE48` / OLT `TPLG8B97BE48` `1/13/2` VLANs 100,1000.

| WAN | VLAN | IP | `X_TP_ServiceType` |
|-----|------|----|--------------------|
| `WCD.5` internet | `X_TP_VID=100` | `192.168.30.133` | `Internet` |
| `WCD.6` gestión | `X_TP_VID=1000` | `10.20.0.80` | `TR069` |

No hay `X_CT-COM_ServiceList`. El WAN de gestión **ya** es TR069+VLAN 1000. El Inform sale por la ruta por defecto (internet).

`ensure-mgmt` devuelve `TPLG` + cola hex 8 del MAC ACS. El runner acepta ese par (`hex_tail` 8). No confunde VSOL/HWTC (cola 8 distinta).

## Qué se hizo

TDD: `gf-tr069-bind-mgmt.js` + tests Node + `--bind-mgmt` en `retag-tr069-vlan1000.sh`. El provision no escribe la WAN de internet. Ruta host ACS (`212.85.13.47`) por el WAN `10.20`, reusando un slot `Layer3Forwarding` de gestión.

Piloto 2× (internet `192.168.30.133` ping OK antes y después). Enqueue NBI **202**. CR sigue en `192.168.30.133`. Inform 19:40Z sí hubo.

Tras el primer intento, `Forwarding.1` (interface internet, gw `192.168.30.1`) quedó con Dest=`212.85.13.47`, Status `Disabled` (máscara vacía). `Forwarding.2` (interface `10.20`) Dest vacío, Enable false. El segundo intento no persistió SPV en `.2`.

## Bloqueo

No lote. El CR no se mueve con ServiceType/VLAN (ya correctos). La ruta host no quedó Enabled en el WAN 10.20. HTTP 202: el CR a `:7547` por internet no cierra sesión en 45 s.

Siguiente: SPV mínimo de `Forwarding.2` (Dest ACS, máscara `/32`, Enable, Type Network) sin wildcards (el `default.js` de XC220 ya pega el tope 50 ms). No añadir más AddObject. Limpiar Dest en `Forwarding.1` si sigue el IP del ACS.
