# Subsistemas: desacople máximo y transporte

**Regla permanente (obligatoria para agentes y código nuevo).**

El **core** (`wispadmin` / WAR `ispadmin` o `ispadmin-staging`) y todos los **subsistemas** (OLT Gateway, ACS TR-069, NetDiag, traffic, service-health, observability, CRM WhatsApp, etc.) deben mantenerse **lo más desacoplados posible**.

## Regla de exposición pública

Todos los **clientes externos** hablan **solo con el core**. El core es la **fachada pública** y el **orquestador** de todos los subsistemas.

Clientes externos incluye, sin limitarse a:

- backoffice web
- app Android
- scripts operativos de UI o soporte
- integraciones cliente que consumen funcionalidades de negocio de IspAdmin

Un subsistema no debe exponer su API o su WebSocket directamente a esos clientes por conveniencia de implementación.

## Frontera de integración

Se distinguen dos fronteras:

| Frontera | Regla |
|----------|-------|
| **Cliente externo → core** | Única entrada pública permitida |
| **Core ↔ subsistema** | Integración interna permitida por transporte controlado |

Entre core ↔ subsistema y entre subsistemas se permiten:

| Transporte | Uso típico |
|------------|------------|
| **REST / HTTP JSON** | Lectura, comandos, inventarios, pull de telemetría (p. ej. core → OLT Gateway con `X-Olt-Gateway-Key`) |
| **WebSocket** (incl. STOMP si el módulo ya lo usa) | Streaming en vivo (p. ej. tráfico Mbps) |
| **Redis Streams** (interno) | Avisos entre WARs hacia el snapshot 360 (`gigafiber.events`). No es API pública. Clientes externos no hablan con Redis. |

Redis no sustituye HTTP para preguntas (series, inventario, comandos). Solo transporta hechos (`traffic.latest`, `onu.optical`, `cpe.provisioning`, `cpe.inform`, anomalías). Sin Redis el GET 360 hace fallback al lector HTTP actual. El **ACS WAR no publica Redis**: Gateway orquesta TR-069 y hace XADD de `cpe.provisioning` / `cpe.inform`.

No hay otras fronteras permitidas entre procesos o WAR desplegados por separado.

Si un cliente necesita datos, streaming o acciones de un subsistema, el acceso debe pasar por el core, que decide cómo orquestarlo hacia adentro.

## Prohibido

- Inyectar facades, repositorios o entidades JPA de otro subsistema desde el core (o entre hermanos).
- **JDBC cruzado en runtime:** un WAR no lee ni escribe el schema de otro. Prohibido SQL `ispadmin.tr069_model_profile` desde ACS, `stg_acs.*` desde Core, `stg_oltgateway.*` desde Core, `acs.profiles.catalog` / `` `$catalog`.tabla ``, o un datasource extra apuntando al MySQL de un hermano. Cada WAR usa su JDBC y tablas **sin** calificar schema ajeno. Copiar filas entre schemas es solo script one-shot en `scripts/sql/` (nunca al arrancar). Lo refuerza `CrossSchemaJdbcForbiddenTest`.
- Compartir schema JDBC del dueño del subsistema (p. ej. core leyendo `stg_oltgateway` / `prod_oltgateway` directamente).
- Importar paquetes de dominio de otro subsistema salvo **contratos propios** del consumidor (DTOs/clientes HTTP en el paquete del consumidor, p. ej. `servicehealth.client.*`, `wispadmin.service.onu.gateway.*`).
- “Reacoplar” por comodidad (classpath compartido, `@Autowired` al bean del otro WAR, SQL cruzado).
- Conectar backoffice, mobile o clientes operativos directamente a `traffic`, `oltgateway`, `acs`, `netdiag`, `service-health`, `observability` u otro subsistema sin una excepción arquitectónica explícita.

## Obligatorio

- Cada subsistema con WAR/proceso propio: **API propia**, schema propio cuando aplique, API key o auth de frontera.
- El core consume vía URL interna (`olt.gateway.internal-base-url`, etc.), no vía beans del paquete `oltgateway`.
- Si falta el subsistema: degradar evidencia/feature; no tumbar el arranque del core.
- Documentar endpoints nuevos en el catálogo del módulo (p. ej. [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)).
- Si existiera una excepción donde un cliente hable con un subsistema, debe quedar justificada y aprobada explícitamente en documentación de arquitectura; no se asume como patrón por defecto.

## Vocabulario

| Término | Significado |
|---------|-------------|
| **Core** | WAR principal IspAdmin (suscripciones, alta FIBER, 360, login). No llamarlo “CRM”. |
| **CRM** | Feature/módulo WhatsApp omnicanal (y docs `crm-omnicanal-*`), no el WAR core. |
| **OLT Gateway** | Un solo proceso dueño de SSH/SNMP hacia `10.11.104.2`. Prod, staging y prestaging le hablan por HTTP. Stopgap 2026-09-21: el dueño sigue embebido en el Core prod. Staging y prestaging apagan sync y SNMP. No hay `olt.gateway.enabled`. |
| **ACS WAR** | WAR TR-069 (`acs`). Solo Gateway lo llama. Posee deviceId/cache WiFi/WAN. Core no tiene dominio ACS. |

## Referencias

- Construcción / excludes: [desacople-subsistemas-construccion-2026-08-31.md](./desacople-subsistemas-construccion-2026-08-31.md), [subsistemas-war-toggles.md](./subsistemas-war-toggles.md)
- Gateway HTTP: [olt-gateway-compartido-fase0-2026-09-21.md](./olt-gateway-compartido-fase0-2026-09-21.md), [olt-gateway-3layer.md](./olt-gateway-3layer.md), [gateway-desacople-fases-2026-09-02.md](./gateway-desacople-fases-2026-09-02.md), [oltgateway-war-staging-2026-09-03.md](./oltgateway-war-staging-2026-09-03.md) (histórico), [olt-gateway-rest-consumers-2026-09-02.md](./olt-gateway-rest-consumers-2026-09-02.md)
- WiFi-on-Inform (`cpe.inform`, ACS last-state, Gateway XADD, Core series): [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md)
- Tests de frontera: `SubsystemDependencyRulesTest`, `WarSubsystemPackagingTest`
