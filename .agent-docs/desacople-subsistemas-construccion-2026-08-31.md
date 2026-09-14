# Construcción: desacople total de subsistemas

Fecha: 2026-08-31

**Política vigente (transporte):** [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md) — core y subsistemas solo por **REST** o **WebSocket**; máximo desacople.

El WAR de staging puede excluir `observability`, `oltgateway`, `netdiag`, `traffic` y `servicehealth` en cualquier combinación. El core y los hermanos ya no se importan entre sí; la evidencia cruza por puertos / HTTP.

## Core

- `/onu` vive en `oltgateway`. El core expone `OnuOperationsPort`; el fallback es `LegacyOnuOperations` (SmartOLT).
- Tráfico STOMP: `SubscriptionTrafficWebSocket` en `traffic`, cleanup vía `WebSocketSessionCleanup`.
- Adaptadores NetDiag de directorio/RADIUS/ONT están en `netdiag/adapter`.
- `RouterOsUptimeParser` se movió a `routeros`.

## netdiag ↔ oltgateway

Puertos en `netdiag/port` (`NetDiagOltDescriptorPort`, `NetDiagOltAlarmParserPort`, `NetDiagOltCliPort`, `NetDiagOltInventoryPort`). Adaptadores en `oltgateway/adapter`. Sin oltgateway, alarmas e inventario se saltan.

## servicehealth

Puertos en `servicehealth/port`. Adaptadores:

- `HealthOnuAdapter` (`servicehealth.adapter`, HTTP `OltGatewayClient`)
- `HealthTrafficAdapter` (traffic)
- `HealthNetDiagAdapter` (netdiag)

Óptica/estado: pull HTTP. Prod `GET /optical-samples`; staging `POST /onus/optical` (`optical-pull-mode=live-sns`). Health importa solo la superficie HTTP del Gateway (`client`/`api`/`dto`/`exception`). Sin Gateway, el 360 degrada evidencia; no falla el arranque.

## Mecanismo

- `@EnableJpaRepositories` + `SubsystemScanFilter`
- `@SubsystemEntityScan` (Hibernate no crea tablas de un módulo apagado)
- `scripts/verify-war.sh` acepta `--with` vacío

## Tests

`SubsystemDependencyRulesTest` incluye la regla de `servicehealth`. Excepción: imports `*.port.*`.
