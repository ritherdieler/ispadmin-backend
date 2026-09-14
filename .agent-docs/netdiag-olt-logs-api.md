# NetDiag — API logs OLT

Construcción: **2026-07-31**.

## Endpoint

`GET /api/netdiag/olt/logs`  
Header: `X-Netdiag-Key`  
Condición: `net.diag.enabled=true`

### Query params

| Param | Tipo | Default | Notas |
|-------|------|---------|-------|
| `board` | int | — | Slot GPON |
| `port` | int | — | Puerto 0–15 |
| `unparsedOnly` | boolean | false | Solo `is_unparsed=true` |
| `dateFrom` | ISO Instant | — | `receivedAt >=` |
| `dateTo` | ISO Instant | — | `receivedAt <=` |
| `page` | int | 0 | 0-based |
| `size` | int | 50 | clamp 1–200 |

### Response (`OltLogPageDto`)

```json
{
  "items": [
    {
      "id": 7,
      "receivedAt": "2026-07-31T17:00:00Z",
      "sourceIp": "10.11.104.2",
      "reasonCode": "OLT_ALARM_UNPARSED",
      "board": 1,
      "port": 8,
      "onuIndex": null,
      "targetId": null,
      "severity": null,
      "incidentId": null,
      "channel": "cli_alarm_active",
      "alarmIdHex": null,
      "alarmName": null,
      "component": null,
      "isClear": false,
      "isUnparsed": true,
      "rawMessage": "..."
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

## UI

Backoffice: `/noc/olt-logs` (ver `ispadmin-backoffice/.agent-docs/netdiag-noc-fase1.md`).
