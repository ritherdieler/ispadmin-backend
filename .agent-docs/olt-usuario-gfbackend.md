# Usuario SSH `gfbackend` en la OLT lab

Cuenta exclusiva del backend en el **VPS nuevo** KVM4 `2.24.66.53`. Creada el 2026-09-22 en la OLT Huawei MA5608T `10.11.104.2`.

El Tomcat que ya corre en el VPS actual `212.85.13.47` sigue con `oltadmin`. No cambiar `OLT_GATEWAY_USERNAME` ni `OLT_GATEWAY_PASSWORD` de `/opt/gigafiber/.env` en ese host.

| Campo | Valor |
| --- | --- |
| Usuario | `gfbackend` |
| Nivel | Administrator |
| Perfil | `root` |
| Reenter | 4 |
| Append | `ispadmin-backend` |
| Destino | KVM4 `/opt/gigafiber/.env` → `OLT_GATEWAY_USERNAME` / `OLT_GATEWAY_PASSWORD` |
| Login SSH | Verificado el 2026-09-22 (`enable` entra en `MA5608T#`) |
| Save | `save` terminó con “The data of 3 slot's control board is saved completely” |

La contraseña no va en git. Copia local, gitignored: `scripts/kvm4-olt-gateway.env`. El 2026-09-22 SSH a `2.24.66.53:22` no respondió desde la Mac, así que ese `.env` del KVM4 todavía no se tocó. El prestaging de la Mac sigue en `oltadmin`.
