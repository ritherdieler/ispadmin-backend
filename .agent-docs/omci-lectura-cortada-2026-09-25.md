# Lectura de gestión cortada (2026-09-25)

## Qué pasaba

`display ont info` se daba por terminado en cuanto una línea acababa en `>`. `<Gem Index 1>` y `<Gem Index 2>` salen antes del perfil TR-069, así que a veces el perfil no entraba en la lectura aunque ya estuviera en la ONU. Si el host de gestión tampoco entraba, el paso se detenía en esa lectura.

## Qué quedó

- El prompt solo cierra el comando si es la línea entera (`MA5608T(config-if-gpon-0/1)#`).
- Si falta la línea del perfil, o el host 0 no está en la lectura de IP y tampoco es el aviso de “sin IP”, esa lectura no cuenta y se repite.
- Si el host DHCP de la VLAN 1000 ya está y el perfil sigue vacío, se vuelve a enviar el perfil antes de la siguiente lectura.

Pruebas: `:oltgateway:test` de `HuaweiCliPromptDetectorTest` y `OmciManagementV2Test`, en verde. El WAR de producción no se redesplegó.
