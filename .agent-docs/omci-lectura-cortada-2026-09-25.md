# Lectura de gestión cortada (2026-09-25)

## Qué pasaba

`display ont info` se daba por terminado en cuanto una línea acababa en `>`. `<Gem Index 1>` y `<Gem Index 2>` salen antes del perfil TR-069, así que a veces el perfil no entraba en la lectura aunque ya estuviera en la ONU. Si el host de gestión tampoco entraba, el paso se detenía en esa lectura.

## Qué quedó

- El prompt solo cierra el comando si es la última línea (`MA5608T(config-if-gpon-0/1)#`) y el texto lleva 500 ms sin crecer.
- Si el informe no terminó y falta la línea del perfil, esa lectura no cuenta y se repite. Si el informe ya terminó y la línea no está, cuenta como perfil vacío.
- Si el host DHCP de la VLAN 1000 ya está y el perfil sigue vacío, se vuelve a enviar el perfil antes de la siguiente lectura.

Pruebas: `:oltgateway:test` de `HuaweiCliPromptDetectorTest` y `OmciManagementV2Test`, en verde. Desplegado en producción (`1.0.3+b21fe95`). Diez altas de `ZTEGDC47BFFD` con 45 s de espera confirmaron la gestión las diez veces, con dirección `10.20.0.110`, en 1 o 2 s.
