# E2E OLT solo lectura — 2026-07-31

Prueba end-to-end del backend contra la MA5608T (`10.11.104.2`) **sin escritura** (`olt.gateway.writes.enabled=false`). No se ejecutaron comandos de config que afecten ONUs.

## Resultado

| Check | Resultado |
|-------|-----------|
| Health | `UP`, `oltReachable=true`, ~202 ms |
| NetDiag health | `UP` |
| `olt-gateway-validate-read.sh --quick` | **PASS=10 FAIL=0** |
| Inventory sync (auto) | inserted=2, updated=36, unchanged=732, 35.4 s |
| Signal poll (auto) | slots=2, ports=32, **onusUpdated=711**, 111 s |
| ONUs configured API | **totalElements=770**, con Rx/Tx |
| Targets NetDiag OLT/PON | upserted **33** (1 OLT + 32 PON) al arranque |
| Captura `display alarm active all` | 5 bloques END, dying-gasp / LOSi / config recovery |
| Optical sample `1/7/1` | rx=-20.6, oltRx=-24.56, temp=37 °C |

Artefactos: `.e2e-artifacts/olt-readonly-e2e-report.json`, `olt-readonly-e2e.log`, `olt-alarms-syslog-capture.txt`.

## Cómo repetir

```bash
./run-dev.sh   # writes off por default en dev
bash scripts/olt-gateway-validate-read.sh --quick
expect scripts/olt-capture-alarms-syslog.expect
```

Nota: mientras corre signal poll, `GET /health` puede tardar (compite por el bus CLI). Esperar `Signal poll done` o usar endpoints BD (`/onus/configured`).

## Referencias

- [e2e-olt-mk1-netdiag.md](./e2e-olt-mk1-netdiag.md)
- [olt-ma5608t-alarms-syslog.md](./olt-ma5608t-alarms-syslog.md)
- [netdiag-olt-targets.md](./netdiag-olt-targets.md)
