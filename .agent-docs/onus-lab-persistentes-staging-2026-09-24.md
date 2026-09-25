# ONUs de lab persistentes en staging

Fecha: 2026-09-24

## Filtro

El gateway filtra inventario y escrituras cuando la petición llega con la **clave de staging**. `X-Gigafiber-Env: stg` solo comprueba que coincida con esa clave. No hay un header aparte.

Con esa clave, listas, detalle por SN y escrituras (autorizar, eliminar, reboot) aplican solo a seriales del registro `olt_lab_onu` en el schema del gateway (`prod_oltgateway`, porque el Core de staging habla con el gateway de prod).

## Registro

Tabla `olt_lab_onu` (`sn`, `created_at`). El alta y la baja no dependen de que la ONU esté autorizada. Autorizar o eliminar en la OLT no borra la fila.

Seed inicial: `0031C0B6`, `12345B4641531C0B6`, `ZTEGDC47BFFD`. Un serial coincide por igualdad o por sufijo.

API del gateway, solo con caller staging: `GET|POST /api/olt-gateway/onu/lab`, `DELETE /api/olt-gateway/onu/lab/{sn}`. Esas rutas no pasan por el write guard de laboratorio, para poder marcar un serial nuevo.

El Core las reexpone en `/onu/lab` solo si `gigafiber.environment.tag=stg` y el cliente del gateway está activo. Prod no publica esas rutas. El cliente HTTP sigue mandando la clave de staging y `X-Gigafiber-Env`.

## Deploy

2026-09-24: staging (`tomcat-staging` `/ispadmin-staging`) y prod (`tomcat9027` `/ispadmin`, release `1.0.3+81719d8`) en KVM4. El registro arranca en cualquier proceso que tenga `oltgateway.datasource.url`. El filtro que ve el backoffice de staging sigue en el gateway de prod, que es a quien llama el Core de staging. No existe `olt.gateway.enabled`.

## Backoffice

En Vite `--mode staging`, menú **ONUs de lab** (`/onus/lab`): alta por SN y quitar el tag. En configuradas, no configuradas y detalle aparece el badge `lab` si el serial está en el registro.
