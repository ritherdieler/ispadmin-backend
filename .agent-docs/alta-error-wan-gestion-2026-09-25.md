# Error visible en la WAN de gestión (2026-09-25)

El alta de `ZTEGDC47BFFD` se detuvo en la WAN de gestión porque el gateway de producción escribió DHCP en VLAN 1000 y el perfil 20, y la relectura inmediata no vio esa configuración (`OMCI_READBACK_MISMATCH`). La respuesta al alta fue un 500 sin texto, así que el registro mostró el sobre genérico del servidor.

El código local ya relee hasta tres veces, con dos segundos entre lecturas, sin volver a escribir. Ese reintento no está en el gateway de producción que atendió esta alta (`tomcat9027`, ruta `/ispadmin`). Hace falta desplegar ese WAR para que el reintento y el texto nuevo lleguen a la OLT de laboratorio.

Si la tercera lectura sigue sin coincidir, el fallo nombra lo que la OLT todavía muestra: tipo, VLAN, prioridad, perfil y si hay dirección. Gateway y ACS devuelven ese texto en `message`. El registro del alta usa ese texto como motivo y, cuando empieza por un código (`OMCI_…`, `ACS_…`, `MIKROTIK_…`), también como código del paso. Un fallo sin causa identificable sigue siendo `STAGE_EXECUTION_FAILED`.

`server.error.include-message=always` hace que cualquier otro error HTTP del WAR incluya el mensaje de la excepción.
