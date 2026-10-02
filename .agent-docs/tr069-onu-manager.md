# Gestor TR-069 por ONU

## Recorrido y permisos

El backoffice consulta Core en `/subscription/{id}/tr069`. Core comprueba la identidad ONU–suscripción y llama a Gateway; Gateway selecciona el ACS por `X-Gigafiber-Env` y transmite la consulta al ACS; ACS lee la caché de GenieACS. Los endpoints `summary`, `tree`, `faults/current` y `faults/history` son GET. `summary` admite técnicos y administradores; los otros tres requieren ADMIN. El historial acepta `page` desde cero y `size` entre 1 y 100.

El árbol muestra valores ya presentes en la caché y su `_timestamp`; nunca solicita una lectura CWMP. Las rutas de claves, contraseñas y tokens se censuran antes de salir de ACS. La WAN se identifica por la IP de `ConnectionRequestURL` o por etiquetas de servicio TR069, y se muestra en solo lectura. La edición WAN no forma parte de esta entrega.

## Acciones

`PUT /subscription/{id}/cpe-config` admite únicamente `wifi: { ssid24, ssid5, passphrase }`. ACS aplica la misma clave en ambas bandas y lee de nuevo los dos SSID y las dos hojas `KeyPassphrase`. Solo una coincidencia completa produce `CONFIRMED`; rechazo produce `FAILED`; aceptación sin lectura concluyente produce `UNVERIFIED`. La clave se compara en memoria y no vuelve en respuestas ni se persiste. El reinicio mantiene seguimiento mediante `/subscription/{id}/service-health/actions/{actionId}`.

Cuando GenieACS devuelve un ID de tarea, este se conserva en la acción de Core. Core enlaza cada elemento del historial con una acción de la misma suscripción y ONU si coincide el ID de tarea.

## Historial de faults

La migración `V7__cpe_fault_history.sql` crea el archivo en el esquema propio de ACS. El recolector consulta cada minuto y archiva solo dispositivos vinculados a `cpe_record`. También se captura antes de las purgas y eliminaciones hechas por la plataforma. Un fault se resuelve únicamente tras una consulta satisfactoria en la que ya no aparece; una falla del NBI deja intacto el estado anterior. El archivo comienza al desplegar esta versión y puede omitir un fault externo que aparezca y desaparezca entre sondeos.

Solo se conservan identificadores y datos diagnósticos depurados, nunca el documento crudo ni valores de parámetros. Los faults actuales de las ONU `lab` F6600R y V2804AX15T eran cero durante la validación del 2026-10-02; deduplicación, resolución y errores del NBI se verifican con pruebas.

## Despliegue staging (2026-10-02)

Se desplegó `ispadmin-staging.war` en KVM4 desde `f810f5f` (`1.0.4+f810f5f`) con `FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging`. El preflight de módulos y la suite completa de backend pasaron; el WAR contiene Core, ACS, Gateway y Traffic. `GET /ispadmin-staging/` respondió HTTP 200. Las cuatro rutas TR-069 devolvieron HTTP 401 sin sesión, confirmando que quedan protegidas por autenticación. No se hicieron escrituras en ONU.

El backoffice staging sigue siendo local por política: Vite corre en `http://127.0.0.1:3010/` con `--mode staging`, apuntando a KVM4 en `api.gigafiberperu.tech`. Producción no se desplegó: el runbook de corte exige crear antes los esquemas `prod_acs`, `prod_oltgateway` y `prod_traffic`, y completar el preflight de producción con autorización explícita.
