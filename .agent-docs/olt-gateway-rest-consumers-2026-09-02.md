# Health y NetDiag solo REST hacia Gateway (2026-09-02)

Tras el split de schema, el transporte Health→Gateway ya era HTTP loopback. Este ciclo cierra el puente de **tipos y beans**:

- Health y NetDiag **no importan** `com.dscorp.wispadmin.oltgateway.*`.
- Gateway **no implementa** puertos de NetDiag (se borró `oltgateway/adapter`).
- Clientes propios: `HealthOltGatewayHttpClient`, `NetDiagOltGatewayHttpClient` (`X-Olt-Gateway-Key`, misma base URL que `OltGatewayClient.resolveBaseUrl`).
- REST nuevo/enriquecido: `GET /api/olt-gateway/descriptor`, `POST /admin/alarms/poll` incluye `alarms[]` parseadas (`HuaweiOltAlarmParser` se queda en Gateway).
- `OltGatewayClient` permanece para authorize/delete de la capa C del propio Gateway.

Sockets/SSE siguen reservados. Sin secretos nuevos (`olt.gateway.api-key` / `internal-base-url`).
