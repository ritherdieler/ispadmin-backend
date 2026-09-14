# Restauracion de los perfiles PPP legados de MK2 (PPPoE wireless "PPOE CLIENTES").
#
# Causa: mikrotik-mk2-pppoe-vlan100.rsc usaba [/ppp profile find where name=$name]
# dentro de un :foreach. RouterOS resuelve $name contra la propiedad "name" de cada
# item, asi que el where siempre daba verdadero y el /ppp profile set aplico los
# valores del ultimo plan (600M/600M, local 10.64.0.1, pool PPPOE-DINAMICO) sobre
# los 10 perfiles del router, incluidos los 8 del PPPoE legado y los 2 builtin.
#
# Valores originales tomados del export antes_app8_susalud_mkt2.rsc (2026-08-05).
# Las 51 sesiones activas no se cayeron: RouterOS no reaplica el perfil a una
# sesion viva, solo al reconectar. Restaurado antes de que ninguna reconectara.
#
# Todos los find usan literales, nunca variables, para no repetir el fallo.

/ppp profile set [find where name="PLAN 50 SOLES"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=200M/200M change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 70"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=300M/300M change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 100"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=400M/400M change-tcp-mss=default comment=""

/ppp profile set [find where name="CORTE DE SERVICIO"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=1k/1k change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 150 SOLES"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=600M/600M change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 70 NEW"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=400M/400M change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 100 NW"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=400M/400M change-tcp-mss=default comment=""

/ppp profile set [find where name="PLAN 150 SOLES NEW"] local-address=192.168.26.1 \
  remote-address="PPOE CLIENTES" dns-server=8.8.8.8,8.8.4.4 only-one=yes \
  rate-limit=600M/600M change-tcp-mss=default comment=""

/ppp profile set [find where name="default"] !local-address !remote-address \
  !dns-server !rate-limit only-one=default change-tcp-mss=yes comment=""

/ppp profile set [find where name="default-encryption"] !local-address \
  !remote-address !dns-server !rate-limit only-one=default \
  change-tcp-mss=yes comment=""

:put "Perfiles PPP legados restaurados. Verificar con: /ppp profile print detail"
