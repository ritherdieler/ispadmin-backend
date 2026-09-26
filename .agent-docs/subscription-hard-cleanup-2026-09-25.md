# Limpieza de suscripción (admin)

`POST /subscription/{id}/hard-cleanup` solo responde a `ADMIN`. Cualquier otro perfil recibe 403. El botón de limpiar suscripción del backoffice llama este endpoint.

El e2e de Android, al terminar y con el id de la suscripción creada, hace login ADMIN y ese POST. Al inicio no limpia: si la ONU ya está registrada, avisa y se detiene.

Antes de borrar, Core guarda un snapshot y el estado de cada paso en `subscription_hard_cleanup` (Flyway `V59`). Un segundo POST salta los pasos ya `ok`.

Orden: MikroTik (cola, lista `deudores`, secreto y sesión PPPoE), ACS (`POST /api/acs/v1/subscription/purge`), OLT (el mismo `DELETE /api/olt-gateway/onus/by-sn/{sn}` que el botón Eliminar de ONUs configurados: baja de la OLT si existe, suelta la reserva y limpia el diario; después `POST /api/olt-gateway/subscription/purge` borra el inventario), foto de fachada, tráfico (`POST /api/traffic/v1/subscription/purge`). Un corte de red, timeout o HTTP 5xx se reintenta hasta 3 veces en la misma petición. Un 4xx de negocio no se reintenta. Si el recurso ya no existe, el paso queda limpio.

La fila y el resto del schema Core se borran solo cuando esos pasos están `ok`. El borrado recorre las tablas con `subscription_id`, las hijas por clave foránea y `subscription_ids_json`. Después comprueba que no quede ninguna fila. Si queda, la respuesta es `PARTIAL` con `code=TRACE_REMAINING` y `detail` igual al nombre de la tabla. El journal se borra al final de un `COMPLETE`.

Cada paso fallido trae `code`, `message` en español y `detail` (HTTP o causa corta, sin claves).

Códigos habituales: `MIKROTIK_UNAVAILABLE`, `ACS_UNAVAILABLE`, `ACS_REJECTED`, `ACS_CLIENT_DISABLED`, `OLT_UNAVAILABLE`, `OLT_REJECTED`, `OLT_CLIENT_DISABLED`, `FIREBASE_URL`, `FIREBASE_UNAVAILABLE`, `TRAFFIC_UNAVAILABLE`, `TRAFFIC_REJECTED`, `TRAFFIC_CLIENT_DISABLED`, `TRACE_REMAINING`, `SUBSCRIPTION_NOT_FOUND`.
