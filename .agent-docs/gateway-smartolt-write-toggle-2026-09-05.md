# Toggle SmartOLT / SSH de escrituras en Gateway (2026-09-05)

El interruptor `olt.provider.authorize|delete|reboot|move` (`SMARTOLT` \| `GATEWAY`) vive en el WAR Gateway. Core, con `olt.gateway.client-enabled=true`, habla solo con Gateway (compat HTTP). Default: `GATEWAY` (SSH).

## Flujo

```mermaid
flowchart LR
  subgraph clients [Clientes]
    App[Android API]
  end
  subgraph coreWar [Core WAR]
    Core[RealOltService]
  end
  subgraph gwWar [Gateway WAR]
    Router[OnuWriteRouter]
    Ssh[SSH CLI]
    Cloud[SmartOLT cloud]
  end
  App -->|HTTP JWT| Core
  Core -->|HTTP X-Olt-Gateway-Key| Router
  Router -->|olt.provider GATEWAY| Ssh
  Router -->|olt.provider SMARTOLT| Cloud
```

Lecturas (`unconfigured_onus`, `by-sn`) siguen SSH/SNMP/autofind local. Tras authorize/delete/move por cloud se persiste `olt_mgr_onu`.

## Keys

Ver [vps-secrets-management.md](./vps-secrets-management.md): `OLT_SERVICE_*`, `OLT_PROVIDER_*`. Overlay: `application-oltgateway.properties`.

## Verificación

```bash
./mvnw -Dtest=OnuWriteRouterTest,OnuWriteProviderPropertiesDefaultsTest,RealOltServiceTest,SatelliteBoundaryTest,RestSmartOltWriteClientTest,OltManagerFacadeTest test
```
