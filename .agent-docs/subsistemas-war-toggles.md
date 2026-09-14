# Subsistemas conmutables del WAR único

Staging no debe cargar beans de módulos que la prueba no usa. El mecanismo es runtime (`gigafiber.subsystems.<key>.enabled`) + `SubsystemScanFilter`. El bytecode de acs/oltgateway/traffic **sí** viaja en el WAR único; se apaga con la propiedad.

## Claves

| Key | Paquete | Default prod (`matchIfMissing`) |
|-----|---------|----------------------------------|
| `observability` | `com.dscorp.wispadmin.observability` | enabled |
| `oltgateway` | `com.dscorp.wispadmin.oltgateway` | enabled |
| `netdiag` | `com.dscorp.wispadmin.netdiag` | enabled |
| `traffic` | `com.dscorp.wispadmin.traffic` | enabled |
| `servicehealth` | `com.dscorp.wispadmin.servicehealth` | enabled |
| `acs` | `com.dscorp.wispadmin.acs` | enabled |

Propiedad: `gigafiber.subsystems.<key>.enabled`. El core `com.dscorp.wispadmin.wispadmin` nunca se excluye. RouterOS tampoco (colas MikroTik).

## Runtime

`SubsystemScanFilter` mapea el prefijo de paquete a la clave y excluye la clase si la propiedad es `false`. Cada satélite tiene su DataSource (`acs.datasource.*`, `oltgateway.datasource.*`, `traffic.datasource.*`). Los clientes HTTP (`*.internal-base-url`) apuntan al mismo context-path del WAR.

El empaquetado Maven `subsystem.excludes` / `scripts/subsystems.sh` ya no decide el contenido del WAR. Los toggles `--with` de deploy se ignoran (un solo artefacto).


```bash
./scripts/deploy.sh --env staging
./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic
```

Verificación: `scripts/verify-war.sh` (`jar tf` falla si un paquete excluido aparece o si uno pedido falta). No exige bytecode `traffic`/`oltgateway`/`acs` en el WAR core aunque estén en `--with`.

## `shared/` no referencia subsistemas opcionales

`shared` viaja **siempre** en el Core WAR. Si un `@Component` de `shared` nombra un tipo de `netdiag`, `observability`, etc., Spring resuelve el genérico al crear el bean y el Core cae con `ClassNotFoundException` cuando staging excluye ese paquete. `ObjectProvider` no lo evita.

La retención de telemetría es el ejemplo: `TelemetryRetentionPort` y el coordinador viven en `shared`; los adapters (`NetDiagTelemetryRetentionAdapter`, `ObservabilityTelemetryRetentionAdapter`) viven **dentro** del paquete del subsistema. Si el módulo no va en el WAR, el puerto simplemente no se registra y el Core arranca. Lo cubren `SubsystemDependencyRulesTest.sharedDoesNotReferenceOptionalSubsystemTypes` y `WarSubsystemPackagingTest.telemetryRetentionAdaptersLiveInTheirSubsystemPackage`.

## Grafo permitido

Cualquier combinación de `--with` es válida. Nadie importa el bytecode de un hermano; la evidencia cruzada pasa por HTTP JSON o por puertos del consumidor.

```
wispadmin (core) ──► routeros, HTTP traffic, HTTP oltgateway
oltgateway ──► HTTP acs (TR-069); SSH/SNMP OLT; schema stg_oltgateway; XADD cpe.provisioning
acs        ──► GenieACS NBI; schema stg_acs (sin Redis)
netdiag    ──► wispadmin, HTTP oltgateway
traffic    ──► routeros, HTTP al core (`/internal/traffic/targets`)
servicehealth ──► wispadmin, HTTP traffic, HTTP oltgateway (óptica + CPE)
observability ──► wispadmin
```

`routeros` no es conmutable (colas MikroTik).

Regla automática: `SubsystemDependencyRulesTest` recorre `src/main/kotlin` y falla si `oltgateway` importa `wispadmin`/`netdiag`/`servicehealth`, o si el core importa `oltgateway`.

Degradación sin un hermano:

| Consumidor | Sin WAR / URL vacía |
|------------|----------------|
| core `/onu` inventario | fachada HTTP 503; writes SmartOLT siguen en `OnuSmartOltController` / `LegacyOnuOperations` |
| netdiag alarmas/inventario | skip con razón explícita |
| traffic STOMP | `TrafficWebSocketEventListener` local; sin clases `wispadmin.websocket` |
| servicehealth | evidencia vacía; pull óptico no ingesta |

## Login facial

El peso del WAR lo marcan `src/main/resources/models` (~114 MB), no observability (~1.9 MB). Excluir modelos es otra clave (`facerecognition`) y no está activa en staging.
