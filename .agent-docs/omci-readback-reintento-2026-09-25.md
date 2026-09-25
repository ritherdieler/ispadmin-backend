# Relectura de la gestión OMCI (2026-09-25)

En el alta de `ZTEGDC47BFFD` la OLT aplicó DHCP en VLAN 1000 y el perfil 20, pero el gateway leyó la ONU en el mismo instante y la dio por no configurada. El alta quedó fallida aunque la gestión sí quedó (`10.20.0.110`).

`OmciManagementV2.ensure` ahora, solo después de escribir, relee hasta 3 veces en la misma sesión CLI. Espera 2 segundos entre lecturas. No vuelve a enviar `ont ipconfig` ni `ont tr069-server-config`. Si la tercera lectura sigue sin coincidir, el fallo es el mismo de antes.

Prueba: `:oltgateway:test --tests '*OmciManagementV2Test'`.
