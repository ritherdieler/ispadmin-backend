# Alta v2: Internet PPPoE en espera y Wi-Fi

Fecha: 2026-09-25. Staging KVM4, suscripción 57, ONU de laboratorio `ZTEGDC47BFFD`.

El alta se quedaba en Internet PPPoE porque la WAN ya estaba creada y el PPPoE conectado, pero GenieACS no había leído el estado de la conexión. El worker solo miraba la caché y seguía esperando.

Al leer ese estado, Internet pasó. Wi-Fi falló después por dos causas: la ONU no devuelve la clave (solo se puede escribir) y el perfil de staging no cargaba la clave con la que se guarda el estado previo del Wi-Fi, aunque esa clave ya estaba en el servidor.

La corrida reanudada terminó: PPPoE conectado, SSID `traviesa` y `traviesa - 5G`, operación en estado correcto.
