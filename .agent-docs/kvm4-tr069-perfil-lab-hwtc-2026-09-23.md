# Perfil TR-069 de laboratorio hacia el ACS de KVM4

Fecha: 2026-09-23  
ONU: `HWTC9F4BE990`, GPON `0/1/6`, ONT-ID 116, descripción `LAB-V2-HWTC9F4BE990`.  
ACS de destino: GenieACS de KVM4 `2.24.66.53`.  
Fuera de este cambio: el perfil 2 y el DNS `acs.gigafiberperu.cloud`.

## Qué quedó cableado

| Pieza | Valor |
| --- | --- |
| Perfil OLT | `20`, nombre `GF_KVM4_LAB` |
| URL | `http://acs.gigafiberperu.tech/` |
| Usuario del perfil | `gigafiber-acs` |
| ONU ligada | Solo `0/1/6` ONT 116 |
| Perfil 2 | Sigue en `http://acs.gigafiberperu.cloud/`, usuario `gigafiberacs`, sin ONUs ligadas |
| Entrada nginx KVM4 | `/etc/nginx/sites-available/acs-kvm4-lab.conf` |
| Quién puede hablarle | Solo `38.224.231.4` (MK2) en el puerto 80. El resto recibe 403 |
| Destino interno | `127.0.0.1:7547` |
| IP de gestión leída | `10.20.0.96/22`, VLAN 1000, line profile 30 |
| Variable de altas v2 | `PROVISIONING_V2_TR069_PROFILE_ID=20` en `/opt/gigafiber/.env.staging-v2` |

El proceso `tomcat-staging` ya arrancó con ese id. `NET_DIAG_ENABLED=false` en ese proceso: el WAR que corre todavía registra NetDiag como `ApplicationRunner`, así que no hay que volver a encenderlo hasta desplegar el arreglo con `syncScheduled()`.

## Inform confirmado

Tras asociar el perfil 20 y reiniciar solo esta ONU, GenieACS de KVM4 registró:

| Campo | Valor |
| --- | --- |
| Device | `68229F-V2804AX15T-1234568229F4BE990` |
| Serial CWMP | `1234568229F4BE990` |
| Modelo | `V2804AX15T` |
| Alta e Inform | `2026-09-23T17:16:31Z` |

El serial GPON que muestra la OLT es `485754439F4BE990` (`HWTC-9F4BE990`). `ACS_CONTACT` busca el serial completo de la alta, `HWTC9F4BE990`, y la compuerta de provisions admite `F6600R` y `VSOLVA74`. Esta ONU no pasa esa etapa sin ampliar esa correspondencia.

La OLT también reporta `Match state: mismatch` en el line profile 30. No se modificó ese perfil.

## Cómo repetir una prueba

1. No cambiar la URL del perfil 2 ni el DNS `acs.gigafiberperu.cloud`.
2. Una alta v2 en KVM4 staging toma el perfil 20 desde `PROVISIONING_V2_TR069_PROFILE_ID`.
3. El Inform tiene que aparecer en el NBI local de KVM4, `127.0.0.1:7557`, no en el GenieACS de producción.
4. No recrear el contenedor `tomcat-staging` con `docker compose up --force-recreate`: el WAR no está en un volumen. Si se recrea, hay que volver a copiar `/opt/gigafiber/ispadmin-staging.war` a `webapps/ispadmin-staging.war` y esperar `GET /ispadmin-staging/actuator/health` = `UP`.

## UI de GenieACS

La UI pública es `https://acs.gigafiberperu.tech/` y también `https://acs.gigafiberperu.tech:8443/`. Nginx en 443 y 8443 no filtra por IP y hace proxy a `127.0.0.1:3000`. El registro A `acs.gigafiberperu.tech` apunta a `2.24.66.53`. `acs.gigafiberperu.cloud` sigue en el VPS legado. El CWMP del puerto 80 sigue limitado a MK2.

Acceso de la UI, rol `admin`:

| Campo | Valor |
| --- | --- |
| URL | `https://acs.gigafiberperu.tech:8443/` |
| Usuario | `gfkvm4` |
| Contraseña | `iaBEQvOt8I4GTFklVd6YmLSA` |

El usuario `admin` anterior sigue existiendo. Si se vuelve a correr `deploy-genieacs-ui-nginx.sh`, el vhost recupera la lista de IPs de MK2.

## Retoma 2026-09-23 12:51

El preset `inform` de KVM4 escribía `http://acs.gigafiberperu.cloud/` y la ONU volvía a producción. Ese script ahora deja `http://acs.gigafiberperu.tech/`. El CWMP de nginx acepta ese nombre y la IP. El intervalo de laboratorio de 30 s incluye el serial CWMP `1234568229F4BE990`.

Tras desligar y volver a aplicar el perfil 20, y reiniciar la ONU, GenieACS de KVM4 la registró de nuevo con URL `http://2.24.66.53/`, Inform periódico activo e intervalo 30. El siguiente Inform se mantuvo en KVM4.

Esa corrida se borró después para un e2e desde cero. La ONU no tiene suscripción, reserva v2 ni fila en el gateway. No está en el GenieACS de KVM4 ni en el de producción. Quedó en autofind: slot 1, puerto 6, serial `485754439F4BE990 (HWTC-9F4BE990)`. El perfil 20 y la URL `http://acs.gigafiberperu.tech/` del preset `inform` de KVM4 siguen activos.

## Pendiente para PPPoE y Wi-Fi

- Homologar el serial CWMP y el modelo `V2804AX15T` antes de `ACS_CONTACT`.
- Publicar los provisions v2 solo en el GenieACS de KVM4 y volver a apagar `GENIEACS_V2_PUBLISH_ENABLED`.
- Desplegar el arreglo de arranque de NetDiag antes de dejar `NET_DIAG_ENABLED=true`.
