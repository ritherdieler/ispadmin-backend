# jul/26/2026 09:39:18 by RouterOS 6.48.6
# software id = 11CD-SMGX
#
# model = CCR1036-8G-2S+
# serial number = HDB081Z9ZES
/interface bridge
add name=LAN
/interface ethernet
set [ find default-name=ether1 ] comment="ENTRADA RESPALDO JR"
set [ find default-name=ether3 ] comment="ADMINISTRACION OLT"
set [ find default-name=ether4 ] advertise=\
    10M-half,10M-full,100M-half,100M-full,1000M-half,1000M-full
set [ find default-name=ether5 ] comment="SWITCH CCR 4SFP 8 PUERTOS RJ45"
set [ find default-name=ether7 ] comment="AIRFIBER 9 OCTUBRE MASTER"
set [ find default-name=ether8 ] comment="CCR ADMIN BRIDGE" loop-protect=on
set [ find default-name=sfp-sfpplus1 ] advertise=\
    10M-half,10M-full,100M-half,100M-full,1000M-half,1000M-full,10000M-full \
    comment="ENTRADA WAN CORP. T.TONY"
set [ find default-name=sfp-sfpplus2 ] advertise=\
    10M-half,10M-full,100M-half,100M-full,1000M-half,1000M-full \
    auto-negotiation=no comment="SALIDA LAN 10 GB OLT PORT"
/interface eoip
add disabled=yes mac-address=02:31:5F:0B:91:67 name=eoip-tunnel1 \
    remote-address=0.0.0.0 tunnel-id=0
/interface gre
add local-address=38.224.231.2 name=gre-ispadmin-vps remote-address=\
    212.85.13.47
/interface vlan
add interface=sfp-sfpplus1 name="SERVICIO CORP.TARAZONA" vlan-id=450
add interface=LAN name=vlan1 vlan-id=1
/interface wireless security-profiles
set [ find default=yes ] supplicant-identity=MikroTik
/ip pool
add name="PPOE CLIENTES" ranges=192.168.26.2-192.168.26.254
add name="PPOE GAMERS" ranges=192.168.55.2-192.168.55.254
/ppp profile
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name=\
    "PLAN 50 SOLES" only-one=yes rate-limit=100M/100M remote-address=\
    "PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name="PLAN 70" \
    only-one=yes rate-limit=200M/200M remote-address="PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name="PLAN 100" \
    only-one=yes rate-limit=400M/400M remote-address="PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name=\
    "CORTE DE SERVICIO" only-one=yes rate-limit=1k/1k remote-address=\
    "PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name=\
    "PLAN 150 SOLES" only-one=yes rate-limit=600M/600M remote-address=\
    "PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name="PLAN 70 NEW" \
    only-one=yes rate-limit=200M/200M remote-address="PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name="PLAN 100 NW" \
    only-one=yes rate-limit=400M/400M remote-address="PPOE CLIENTES"
add dns-server=8.8.8.8,8.8.4.4 local-address=192.168.26.1 name=\
    "PLAN 150 SOLES NEW" only-one=yes rate-limit=600M/600M remote-address=\
    "PPOE CLIENTES"
/queue simple
add max-limit=1k/1k name="AVILA CU\EF\BF\BDADO MAGALY CRUZ/50" target=\
    192.168.22.28/32
add max-limit=20M/20M name="TONY ROSALES ANTENA CLARO 50" target=\
    192.168.22.6/32
add max-limit=1k/1k name="6-34-122 JHONATAN MEZA 50" target=192.168.25.130/32
add max-limit=1k/1k name="MIRIAM SAMUDIO CASA/50" target=192.168.33.21/32
add max-limit=1k/1k name="LIVIA MONTES/50" target=192.168.33.35/32
add max-limit=1k/1k name="SONIA DELGADO MERCADO 50" target=192.168.22.79/32
add max-limit=10M/15M name="0 HASHIMOTO " target=192.168.22.59/32
add max-limit=1k/1k name="0-6-154 JADE MILAGRO 70" target=192.168.25.164/32
add max-limit=20M/20M name="0 WANLY WIRELES" target=192.168.20.239/32
add max-limit=1k/1k name="MIRIAN HERRERA TRRE 50" target=192.168.20.77/32
add max-limit=1k/1k name="BETSABET EVER 50" target=192.168.20.44/32
add max-limit=1k/1k name="DAVID CLEVER 50" target=192.168.20.45/32
add max-limit=10M/10M name="convenio ALAN CHACRA SAMUDIO 50" target=\
    192.168.20.47/32
add max-limit=1k/1k name="Choro (cuarto la villa)" target=192.168.88.67/32
add max-limit=200M/200M name="0 WIFI CUARTO" target=192.168.22.200/32
add max-limit=1k/1k name="EDGAR ALMENARA CASA BLANCA " target=\
    192.168.49.199/32
add max-limit=15M/15M name="convenio 0 SOTO LA ENCENADA/50" target=\
    192.168.49.17/32
add max-limit=1k/1k name="PERCY LA ENSENADA/70" target=192.168.49.193/32
add max-limit=1k/1k name="LUIS ABELARDO BUSTAMANTE 50" target=\
    192.168.22.71/32
add max-limit=1k/1k name="MAICOL ANALY PAREDES SHUAN 50" target=\
    192.168.22.80/32
add max-limit=1k/1k name="CAMILa vega" target=192.168.25.168/32
add max-limit=1k/1k name="6-46-158 ARTURO MINIMARKET PLAZA" target=\
    192.168.88.56/32
add max-limit=1k/1k name="Yever Larianco 50" target=192.168.95.254/32
add max-limit=200M/200M name="Karla CACERES ATRAS BASE  CONN 50" target=\
    192.168.93.59/32
add max-limit=1k/1k name="BanDame CONN 50" target=192.168.93.60/32
add max-limit=1k/1k name="JuanRomero 50" target=192.168.25.30/32
add max-limit=1k/1k name="ROSI TAPIA FERRETERIA" target=192.168.25.87/32
add max-limit=1k/1k name="RodrigoCaurino CONN 50" target=192.168.93.66/32
add max-limit=3M/10M name="LucianoTienda notocar" target=192.168.93.67/32
add max-limit=1k/1k name="Marta S-G 50" target=192.168.22.171/32
add max-limit=1k/1k name="0 FelixCarrillo CONN 50" target=192.168.93.98/32
add max-limit=15M/15M name="LUIS PALACIOS COMEDOR" target=192.168.93.105/32
add max-limit=9M/9M name="0 CHUPETIN9Octubre" target=192.168.93.213/32
add max-limit=1k/1k name="PamelaCastro CONN 50" target=192.168.93.113/32
add max-limit=15M/15M name="LUIS PALACIOS ALMACEN" target=192.168.93.182/32
add max-limit=1k/1k name="Ilmer Soto plan 50 nuevo casa blanca" target=\
    192.168.22.215/32
add max-limit=1k/1k name="RosaLaEnsenada CONN 50" target=192.168.93.190/32
add max-limit=20M/20M name="KARINA PrimaPamela CONN 50" target=\
    192.168.93.199/32
add max-limit=1k/1k name="0 RbCabinas CONN" target=192.168.93.51/32
add max-limit=1k/1k name="0 Yolanda alvarado" target=192.168.93.38/32
add max-limit=1k/1k name="Andres Caurino tiwinza 50" target=192.168.93.24/32
add max-limit=1k/1k name="Yusa CONN 50" target=192.168.22.85/32
add max-limit=20M/20M name="cindyElMilagro CONN 50" target=192.168.95.251/32
add max-limit=1k/1k name="NIEVES ALIAGA 50" target=192.168.95.222/32
add max-limit=1k/1k name="Daniel ALVA SantaConstansa 50" target=\
    192.168.93.160/32
add max-limit=5M/10M name="0 EdithSantaConstansa serro antena" target=\
    192.168.93.170/32
add max-limit=1k/1k name="Delia de la cruz StaConstansa" target=\
    192.168.93.176/32
add max-limit=1k/1k name="FernandezObregon gringas STACons 50" target=\
    192.168.93.179/32
add max-limit=1k/1k name="0 YolviCasaBlanca 50" target=192.168.93.201/32
add max-limit=1k/1k name="TioAnabel CONN 50" target=192.168.93.205/32
add max-limit=1k/1k name="MARIBEL PABLO 50" target=192.168.25.65/32
add max-limit=1k/1k name="JESUS CHAVEZ ATRAS GRIFO 50" target=\
    192.168.25.66/32
add max-limit=1k/1k name="CARLOS JUARES FIBRA 70" target=192.168.25.90/32
add max-limit=1k/1k name="5-25-100 OLENKASA CRISTEL 70" target=\
    192.168.25.108/32
add max-limit=1k/1k name="6-39-134 JEySON MORA 50" target=192.168.25.142/32
add max-limit=1k/1k name="GERARDO ACEBEDO MA\D1U(reg anterior molina)" \
    target=192.168.25.162/32
add max-limit=1k/1k name="15-16-165 RONY TOMATE" target=192.168.25.176/32
add max-limit=1k/1k name="FUNDO EL PARAISO" target=192.168.88.62/32
add max-limit=30M/30M name="Sergio Casa San Jeronimo" target=\
    192.168.93.239/32
add max-limit=1k/1k name="6-54-188 Sandro MOISES Valverde" target=\
    192.168.25.200/32
add max-limit=1k/1k name="6-53-180 Jesus Shanchez plan 70" target=\
    192.168.25.192/32
add max-limit=100M/100M name="5-41-185 MAYA FELIPE VECIONO" target=\
    192.168.25.197/32
add max-limit=1k/1k name="15-18-189 Gabi Blas Gargate" target=\
    192.168.25.201/32
add comment="BASE GIGAFIBER PERU KORI" max-limit=1G/1G name=\
    "5-46-468  BASE GIGAFIBER" target=192.168.25.202/32
add max-limit=1k/1k name="FLOR CASA NARANJA PRIMERO " target=192.168.9.33/32
add max-limit=1k/1k name="LUCIANO JUARES" target=192.168.22.110/32
add max-limit=1k/1k name="15-20-193 JESUS-QUIROS frente diana mautino" \
    target=192.168.25.206/32
add max-limit=1G/500M name="SERGIO CASA LA VILLA" target=192.168.88.99/32
add max-limit=1G/500M name="sergio wifi6" target=192.168.123.2/32
add max-limit=1k/1k name="LIZETH OBREGON 9" target=192.168.22.116/32
add max-limit=1k/1k name="MARIA RIVAS (FILADELFIA)" target=192.168.22.32/32
add max-limit=15M/15M name="0 BRAYAN SOTO" target=192.168.88.164/32
add max-limit=1k/1k name="DARIO ADOLFO ALMACEN" target=192.168.88.155/32
add comment="25/07/2022  ALFONDO NIETO" max-limit=1k/1k name=\
    "PABLO MILAN FAM YONEL" target=192.168.22.13/32
add comment="CHICAS BODEGA COTADO DE ANGEL CHIFA 21/10/2022" max-limit=1k/1k \
    name="ROXANA SALAS POR CASTILLO" target=192.168.88.176/32
add comment="4-4-379 MIGUEL INQUILINO ACHORADA ULTIMO PISO 22/10/2022" \
    max-limit=1k/1k name="MIGUEL ANGEL GONZALES " target=192.168.25.223/32
add comment="8-13-394 TIENDA YOVANA WALI TIENDA" max-limit=1k/1k name=\
    "WALI ENSENADA TIENDA YOVANA" target=192.168.25.238/32
add comment="5-70-409 ANALY COSTADO CASA JARDINES" max-limit=1k/1k name=\
    "ANALY SHUAN MAICOL" target=192.168.25.251/32
add comment="5-72-417 MYLAN PABLO MEDINA 12/12/2022" max-limit=1k/1k name=\
    "PABLO MEDINA" target=192.168.26.13/32
add max-limit=1k/1k name="MARCOANTONIO ENCENADA" target=192.168.88.20/32
add max-limit=500M/500M name="NILTON ROUTER" target=192.168.88.19/32
add max-limit=1k/1k name=queue4 target=192.168.144.44/32
add max-limit=400M/400M name="PEUEVA DE CAMBIO DE IP FIORELLA DECENA" target=\
    192.168.88.11/32
add max-limit=50M/50M name="MAMA DE JONATAN" target=192.168.26.184/32
add comment=FIBER max-limit=1k/1k name=\
    "id:638, usuario:HECTOR JORGE MAYTA HUTADO, lugar:la villa, nap:ML-0-1" \
    target=192.168.26.227/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:666, usuario:Jose Antonio Blas Malvaceda, lugar:la ensenada" target=\
    192.168.93.231/32
add comment=FIBER max-limit=100M/100M name=\
    "id:835, usuario:KEVIN DIEGO MONZON ORBEGOSO, lugar:la villa, nap:ML-0-1" \
    target=192.168.26.195/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:890, usuario:YOVANA MAUTINO, lugar:la villa" target=192.168.22.98/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:1065, usuario:ADERLIN ADELINA JULCA, lugar:tiwinza" target=\
    192.168.95.221/32
add comment=FIBER max-limit=20M/20M name="id:1076, usuario:MIJAEL EDGAR GARCIA\
    \_ROJAS, lugar:casa blanca, nap:ML-0-1" target=192.168.26.131/32
add comment=FIBER max-limit=20M/20M name=\
    "id:1091, usuario:EVELIN GARCIA, lugar:luvio, nap:ML-0-1" target=\
    192.192.26.217/32
add comment=WIRELESS max-limit=200M/200M name=\
    "id:840, usuario:Patricia Nora Trujillo Vela, lugar:la villa" target=\
    192.168.22.163/32
add comment=FIBER max-limit=1k/1k name=\
    "id:855, usuario:BEKER VARGAS HURTADO, lugar:la villa, nap:ML-0-1" \
    target=192.78.26.78/32
add comment=FIBER max-limit=1k/1k name=\
    "id:894, usuario:MARGARITA null, lugar:la villa, nap:ML-0-1" target=\
    192.168.26.20/32
add comment=FIBER max-limit=100M/100M name=\
    "id:1623, usuario:MARCO LOARTE RIMAC,lugar:santa anita, nap:SA-9-3" \
    target=192.168.22.145/32
add comment=WIRELESS max-limit=100M/100M name=\
    "id:1083, usuario:OSCAR COTRINA CAURINO, lugar:la villa" target=\
    192.168.26.178/32
add comment=FIBER max-limit=1k/1k name=\
    "id:1165, usuario:Simeon Quispe Galarza, lugar:la villa, nap:ML-0-1" \
    target=192.168.200.19/32
add comment=FIBER max-limit=80M/80M name="id:1366, usuario:Mar\C3\ADa Esmerita\
    \_Castillo Calle, lugar:la villa, nap:LV -5-7" target=192.168.200.251/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:752, usuario:MAGALY JANAMPA OBREGON, lugar:9 de octubre" target=\
    192.168.22.21/32
add comment=WIRELESS max-limit=200M/200M name=\
    "id:785, usuario:MIGUEL YULI\C3\91O RAMIREZ SOLORZANO, lugar:la villa" \
    target=192.168.22.188/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:617, usuario:Ricardo Jara Vega, lugar:las piedras" target=\
    192.168.88.61/32
add max-limit=11M/11M name=\
    "id:1612, usuario:ROXANA ROSALES CRUZ, lugar:la villa" target=\
    192.168.22.239/32
add comment=WIRELESS max-limit=40M/40M name=\
    "id:1614, usuario:Mauricio  Vargas, lugar:san geronimo" target=\
    192.168.22.246/32
add max-limit=200M/200M name=\
    "id:1628, usuario:Jenny Rosales Cruz, lugar:la villa, nap:LV -1-1" \
    target=192.168.22.174/32
add max-limit=100M/100M name="id:1633, usuario:Arminda Del Carmen Angulo Perdo\
    mo, lugar:la villa, nap:LV -2-2" target=192.168.22.186/32
add max-limit=1k/1k name=\
    "id:1646, usuario:AZUCENA SUSY GANTU VILLANUEVA , lugar:la villa" target=\
    192.168.22.197/32
add comment=WIRELES max-limit=10M/20M name=\
    "ROSA FAMILIA DE LIZETH(en prueva de servicio desde24/04/25)" target=\
    192.168.26.80/32
add comment=FIBER max-limit=1k/1k name="id:614, usuario:HEYNER OLIVEROS RICO, \
    lugar:santa anita, nap:ML-0-1, plan:duo_basico, tipo:FIBER" target=\
    192.168.26.15/32
add comment=WIRELESS max-limit=1k/1k name="id:617, usuario:Ricardo Jara Vega, \
    lugar:las piedras, plan:empresa_150, tipo:WIRELESS" target=\
    192.168.88.61/32
add comment=WIRELESS max-limit=200M/200M name="id:619, usuario:Celene Midory S\
    eminario Velasquez, lugar:casa blanca, plan:basico_wireless 50, tipo:WIREL\
    ESS" target=192.168.22.100/32
add comment=FIBER max-limit=1k/1k name="id:624, usuario:SOLEDAD CRISTINA BARDA\
    LES GONZALES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.24/32
add comment=WIRELESS max-limit=1k/1k name="id:627, usuario:NILTON GERMAN CACER\
    ES PEREZ, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.223/32
add comment=FIBER max-limit=1k/1k name="id:632, usuario:Vilma Maribel Larianco\
    \_Ramos, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.221/32
add comment=FIBER max-limit=1k/1k name="id:635, usuario:Didiana Barranzuela Sa\
    ntos, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.47/32
add comment=FIBER max-limit=1k/1k name="id:643, usuario:ANYUSBEL IBARRA PETIT,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.240/32
add comment=WIRELESS max-limit=1k/1k name="id:645, usuario:GERALDIN BARALT, lu\
    gar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.97/32
add comment=WIRELESS max-limit=1k/1k name="id:652, usuario:Angel Gabriel Canel\
    on, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.214/32
add comment=FIBER max-limit=1k/1k name="id:656, usuario:Norma Gladis Laurente \
    Toribio, lugar:san jeronimo, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.28/32
add comment=FIBER max-limit=1k/1k name="id:662, usuario:ANTONIO ALVAREZ MENDEZ\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.144/32
add comment=FIBER max-limit=1k/1k name="id:664, usuario:JESUSA BERTILA HUERTA \
    CASTILLO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.220/32
add comment=FIBER max-limit=1k/1k name="id:670, usuario:MARIA ELENA OJEDA JIME\
    NEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.93/32
add comment=FIBER max-limit=1k/1k name="id:677, usuario:DARIO LI\C3\91AN MIRAN\
    DA, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.25.23/32
add comment=FIBER max-limit=1k/1k name="id:679, usuario:FREDY YVAN CACERES PER\
    EZ botica, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.111/32
add comment=FIBER max-limit=1k/1k name="id:689, usuario:NILSON ANIBAL SANTILLA\
    N RAMIREZ, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.22.94/32
add comment=WIRELESS max-limit=1k/1k name="id:693, usuario:EDGAR ALVARADO CRUZ\
    , lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.115/32
add comment=WIRELESS max-limit=1k/1k name="id:696, usuario:VERONICA LUZ SANCHE\
    Z CHAVEZ, lugar:las colinas, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.43/32
add comment=FIBER max-limit=1k/1k name="id:699, usuario:PEDRO SOLANO, lugar:la\
    \_ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.49.21/32
add comment=WIRELESS max-limit=1k/1k name="id:701, usuario:Janeth Yovana Pablo\
    \_Trujillo, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.40/32
add comment=FIBER max-limit=1k/1k name="id:713, usuario:Jazmin Mari\C3\B1o Sob\
    rado, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.108/32
add comment=FIBER max-limit=200M/200M name="id:714, usuario:MOISES RIMAC LEON,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.146/32
add comment=FIBER max-limit=1k/1k name="id:715, usuario:Miriam Vega Velasquez,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.116/32
add comment=WIRELESS max-limit=1k/1k name="id:718, usuario:Edwin Carrasco Bran\
    dan, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.25.56/32
add comment=WIRELESS max-limit=1k/1k name="id:720, usuario:JAVIER ROSALES DIAZ\
    , lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.66/32
add comment=FIBER max-limit=1k/1k name="id:722, usuario:Cynthia Yesenia Rivera\
    \_Flores, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.151/32
add comment=FIBER max-limit=1k/1k name="id:725, usuario:Cyntia Aranda Palomare\
    s, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.115/32
add comment=FIBER max-limit=1k/1k name="id:730, usuario:Roberto Broncano Diest\
    ra, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.183/32
add comment=WIRELESS max-limit=1k/1k name="id:736, usuario:DIANA MARGARITA ROJ\
    AS CASTILLO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.23/32
add comment=WIRELESS max-limit=1k/1k name="id:737, usuario:Jhasmin Toribio Mal\
    lqui, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.239/32
add comment=WIRELESS max-limit=1k/1k name="id:740, usuario:VENCISLAO MEZIAS RE\
    QUENA LEON, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.20.40/32
add comment=FIBER max-limit=1k/1k name="id:747, usuario:MIRIAM ROSMERY VARGAS \
    COTRINA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.15/32
add comment=FIBER max-limit=1k/1k name="id:748, usuario:OSCAR SAUCEDO VASQUEZ,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.233/32
add comment=WIRELESS max-limit=10M/20M name="id:752, usuario:magaly janampa ob\
    regon, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.21/32
add comment=FIBER max-limit=1k/1k name="id:755, usuario:RAQUEL GISELA BARRIENT\
    OS CCORI, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.193/32
add comment=WIRELESS max-limit=1k/1k name="id:757, usuario:EMERSON STALIN SANC\
    HEZ ARQUI\C3\91IGO, lugar:santa constansa, plan:basico_wireless 50, tipo:W\
    IRELESS" target=192.168.95.50/32
add comment=WIRELESS max-limit=1k/1k name="id:758, usuario:JOSTIN HIROSHI RAMO\
    S SOTO, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.152/32
add comment=WIRELESS max-limit=1k/1k name="id:762, usuario:JAVIER CLEVER OSORI\
    O, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.111/32
add comment=WIRELESS max-limit=1k/1k name="id:768, usuario:JANPIERRE ELIAZAR S\
    ANTOS CASTILLO, lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" \
    target=192.168.9.16/32
add comment=FIBER max-limit=200M/200M name="id:771, usuario:JEANCARLOS WALDIR \
    MONTES BAZAN, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.93.77/32
add comment=FIBER max-limit=1k/1k name="id:774, usuario:JOSE MIGUEL MOISES PIN\
    EDO GARCIA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.189/32
add comment=FIBER max-limit=1k/1k name="id:776, usuario:DONEIN BRAYAN ALVAREZ \
    LOYOLA, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.26.141/32
add comment=FIBER max-limit=1k/1k name="id:778, usuario:KEVIN CRISTYAN MEZA RO\
    DRIGUEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.20.23/32
add comment=FIBER max-limit=1k/1k name="id:780, usuario:Edwin Yuler Espinoza R\
    obles, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.37/32
add comment=FIBER max-limit=1k/1k name="id:784, usuario:NICOLAS MENDOZA CARLOS\
    , lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.82/32
add comment=FIBER max-limit=1k/1k name="id:786, usuario:Grifo La Bella Villa, \
    lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.91/32
add comment=FIBER max-limit=1k/1k name="id:788, usuario:Miguel Angel Zu\C3\B1i\
    ga Due\C3\B1as, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.172/32
add comment=FIBER max-limit=1k/1k name="id:793, usuario:TANIA  BEDON CASTILLO,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.46/32
add comment=FIBER max-limit=1k/1k name="id:801, usuario:ROGER CULLA GARCIA, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.238/32
add comment=FIBER max-limit=1k/1k name="id:806, usuario:EMELY YENIFER ESPINOZA\
    \_OBREGON, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.109/32
add comment=WIRELESS max-limit=1k/1k name="id:810, usuario:Daniel Fernando Ram\
    irez Espindola, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS\
    " target=192.168.9.18/32
add comment=WIRELESS max-limit=20M/20M name="id:813, usuario:CRISTIAN ALBERTO \
    PANTA CHAVEZ, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.131/32
add comment=FIBER max-limit=1k/1k name="id:818, usuario:Marco Loarte Rimac, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.181/32
add comment=FIBER max-limit=1k/1k name="id:820, usuario:Jean Pierre Martin Qui\
    jano Magui\C3\B1a, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.237/32
add comment=FIBER max-limit=1k/1k name="id:821, usuario:JOHAN OMAR TARAZONA ME\
    NDOZA, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.244/32
add comment=FIBER max-limit=1k/1k name="id:826, usuario:JOSEPH BRANDON LEE GAM\
    BINI SIFUENTES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.105/32
add comment=FIBER max-limit=1k/1k name="id:834, usuario:ANALY KELY RAMIREZ GAR\
    CIA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.101/32
add comment=WIRELESS max-limit=1k/1k name="id:842, usuario:JORGE LUIS RIOS MUR\
    RUGARRA, lugar:la villa, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.88.230/32
add comment=FIBER max-limit=1k/1k name="id:845, usuario:LIZBETT DONATO ARMILLO\
    N, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.187/32
add comment=WIRELESS max-limit=1k/1k name="id:849, usuario:JOSE DANTE LLASHAG \
    BAUTISTA, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.138/32
add comment=FIBER max-limit=1k/1k name="id:850, usuario:LAYDI DIANA ESPINOZA V\
    ILLANUEVA, lugar:la villa, nap:ML-0-1, plan:Internet100, tipo:FIBER" \
    target=192.168.26.206/32
add comment=FIBER max-limit=1k/1k name="id:855, usuario:BEKER VARGAS HURTADO, \
    lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.218/32
add comment=WIRELESS max-limit=1k/1k name="id:858, usuario:Kennedy Bonillo Cap\
    illo Llashac, lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" \
    target=192.168.93.44/32
add comment=FIBER max-limit=1k/1k name="id:860, usuario:ZACARIAS MORE COVE\C3\
    \91AS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.207/32
add comment=FIBER max-limit=1k/1k name="id:863, usuario:El\C3\ADas Eleodoro Go\
    nz\C3\A1les Rojas, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.25/32
add comment=FIBER max-limit=1k/1k name="id:866, usuario:ESTEBAN QUISPE LOPEZ, \
    lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.132/32
add comment=WIRELESS max-limit=1k/1k name="id:868, usuario:ALEJANDRO JUAN RIMA\
    C LEON, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.33.41/32
add comment=FIBER max-limit=1k/1k name="id:870, usuario:LUIS VILLANUEVA, lugar\
    :la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.253/32
add comment=WIRELESS max-limit=1k/1k name="id:871, usuario:JORGE ROMERO, lugar\
    :san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.7/32
add comment=FIBER max-limit=1k/1k name="id:884, usuario:HENRY PAUCAR, lugar:la\
    \_villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=192.168.26.32/32
add comment=WIRELESS max-limit=1k/1k name="id:885, usuario:RICHARD CERPA agro \
    medanos 09, lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.9.15/32
add comment=WIRELESS max-limit=20M/20M name="id:886, usuario:MIRIAN HERRERA, l\
    ugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.20.32/32
add comment=FIBER max-limit=1k/1k name="id:888, usuario:GLORIA ANDREA MONTES J\
    ARA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.29/32
add comment=FIBER max-limit=1k/1k name="id:899, usuario:PAULINO KUNCHIKUI, lug\
    ar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.26.43/32
add comment=FIBER max-limit=1k/1k name="id:900, usuario:JOSE DOMINGO LESCANO F\
    ERNANDO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.194/32
add comment=FIBER max-limit=1k/1k name="id:901, usuario:YONEL Barberia Del Mar\
    \_Maiz SOSA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.18/32
add comment=WIRELESS max-limit=1k/1k name="id:905, usuario:PETER CAMARA ALVA, \
    lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.58/32
add comment=WIRELESS max-limit=1k/1k name="id:910, usuario:FANNY IBETH ORTIZ R\
    OMERO, lugar:san pedro, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.88.7/32
add comment=FIBER max-limit=1k/1k name="id:912, usuario:SARA ESCALANTE, lugar:\
    la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.243/32
add comment=FIBER max-limit=200M/200M name="id:914, usuario:SANTOSA FELICITA G\
    ARCIA ESPADA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.14/32
add comment=FIBER max-limit=1k/1k name="id:917, usuario:WILFREDO MORI, lugar:l\
    a villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.95/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:918, usuario:DENIS GONZALES\
    , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" target=\
    192.168.26.176/32
add comment=FIBER max-limit=1k/1k name="id:920, usuario:ZENA VICTORIA DOMINGUE\
    Z ESPIRITU, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.195/32
add comment=WIRELESS max-limit=1k/1k name="id:921, usuario:ARIANA QUISPE, luga\
    r:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.81/32
add comment=FIBER max-limit=1k/1k name="id:925, usuario:SHADAY HUERTA, lugar:l\
    a villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.219/32
add comment=FIBER max-limit=1k/1k name="id:929, usuario:JOHANY STEFANI VALCAZA\
    R JAIMES, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.216/32
add comment=WIRELESS max-limit=1k/1k name="id:930, usuario:EMER ALEJANDRO MEND\
    OZA ESPINOZA, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.26.144/32
add comment=FIBER max-limit=1k/1k name="id:935, usuario:MAURICIO ALCANTARA SAA\
    VEDRA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.42/32
add comment=WIRELESS max-limit=1k/1k name="id:945, usuario:BRIYIT ARACELLI ROM\
    ERO MENDOZA, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.92/32
add comment=WIRELESS max-limit=1k/1k name="id:946, usuario:CARMEN DELGADO, lug\
    ar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.227/32
add comment=FIBER max-limit=30M/30M name="id:949, usuario:LORENA JEAN CARLOS, \
    lugar:9 de octubre, nap:ML-0-1, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.93.130/32
add comment=FIBER max-limit=1k/1k name="id:951, usuario:YONEL SOSA, lugar:la v\
    illa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.145/32
add comment=FIBER max-limit=1k/1k name="id:952, usuario:JULIO RODRIGUEZ FERRET\
    .  SAN LUIS, lugar:la villa, nap:ML-0-1, plan:INTERNET 60, tipo:FIBER" \
    target=192.168.25.140/32
add comment=WIRELESS max-limit=1k/1k name="id:954, usuario:JAIRO NICANOR ROSAL\
    ES OJEDA, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.195/32
add comment=WIRELESS max-limit=1k/1k name="id:957, usuario:ANALI CANTARO, luga\
    r:la ensenada, plan:wireless 70 , tipo:WIRELESS" target=192.168.49.194/32
add comment=FIBER max-limit=200M/200M name="id:958, usuario:GLORIA null, lugar\
    :la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.22.83/32
add comment=FIBER max-limit=1k/1k name="id:961, usuario:MIRIAN MINCHOLA, lugar\
    :la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.109/32
add comment=FIBER max-limit=1k/1k name="id:962, usuario:CRISTIAN SOLORZANO, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.49/32
add comment=WIRELESS max-limit=1k/1k name="id:963, usuario:casa due\C3\B1o CAC\
    HORRO, lugar:la villa, plan:wireless 100, tipo:WIRELESS" target=\
    192.168.93.76/32
add comment=FIBER max-limit=200M/200M name="id:964, usuario:GEORGINA MEZA, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.210/32
add comment=FIBER max-limit=1k/1k name="id:966, usuario:DINA VANESSA RAMIREZ O\
    SORIO, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.177/32
add comment=WIRELESS max-limit=1k/1k name="id:969, usuario:PABLO LLASHAG, luga\
    r:roy martin, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.113/32
add comment=WIRELESS max-limit=1k/1k name="id:973, usuario:NELVER OBLITAS, lug\
    ar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.91/32
add comment=FIBER max-limit=1k/1k name="id:978, usuario:AMELIA URETA, lugar:la\
    \_villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.239/32
add comment=ONLY_TV_FIBER max-limit=20M/20M name="id:984, usuario:ROY MARTIN, \
    lugar:luvio, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.93.175/32
add comment=FIBER max-limit=200M/200M name="id:989, usuario:MARCO SEVILLANO, l\
    ugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.51/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:991, usuario:ANABEL GARCIA \
    TORRES, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.25.64/32
add comment=FIBER max-limit=1k/1k name="id:992, usuario:POLLERIA ROYS, lugar:l\
    a villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.179/32
add comment=FIBER max-limit=1k/1k name="id:993, usuario:CHIFA ANGEL, lugar:la \
    villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.45/32
add comment=WIRELESS max-limit=1k/1k name="id:994, usuario:PAMELA ROY MARTIN, \
    lugar:roy martin, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.20.30/32
add comment=FIBER max-limit=200M/200M name="id:995, usuario:PABLO null, lugar:\
    la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.93.47/32
add comment=WIRELESS max-limit=1k/1k name="id:1000, usuario:LIZ SILUPU, lugar:\
    9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.248/32
add comment=WIRELESS max-limit=1k/1k name="id:1002, usuario:LURDES PAULINA nul\
    l, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.9.34/32
add comment=WIRELESS max-limit=1k/1k name="id:1003, usuario:PATRICIA CARRILLO \
    SUCUYTANA, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.72/32
add comment=FIBER max-limit=1k/1k name="id:1006, usuario:PABLO BENITO, lugar:9\
    \_de octubre, nap:ML-0-1, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.9.42/32
add comment=FIBER max-limit=1k/1k name="id:1009, usuario:ILMER SOTO, lugar:cas\
    a blanca, nap:ML-0-1, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.211/32
add comment=FIBER max-limit=1k/1k name="id:1010, usuario:ELENA CUYA LEZAMETA 1\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.26/32
add comment=FIBER max-limit=1k/1k name="id:1011, usuario:MIRIAN ZAMUDIO, lugar\
    :la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.241/32
add comment=WIRELESS max-limit=1k/1k name="id:1014, usuario:MASIEL VILLANUEVA \
    HUERTA, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.26.118/32
add comment=FIBER max-limit=1k/1k name="id:1019, usuario:LESLI FRIAS, lugar:la\
    \_villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.110/32
add comment=FIBER max-limit=1k/1k name="id:1020, usuario:MIRIAN MOGOLLON, luga\
    r:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.180/32
add comment=WIRELESS max-limit=1k/1k name="id:1022, usuario:DANIEL LIPE NORABU\
    ENA MOLINA, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.181/32
add comment=WIRELESS max-limit=1k/1k name="id:1024, usuario:BOTICA SOLFARMA, l\
    ugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.69/32
add comment=WIRELESS max-limit=1k/1k name="id:1026, usuario:JAIME DE LA CRUZ Q\
    UIRCA, lugar:la villa, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.22.130/32
add comment=WIRELESS max-limit=1k/1k name="id:1031, usuario:JEAN CARLOS WALI, \
    lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.126/32
add comment=FIBER max-limit=200M/1k name="id:1032, usuario:ROY MARTIN, lugar:r\
    oy martin, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.22.9/32
add comment=FIBER max-limit=1k/1k name="id:1044, usuario:JOSE ANGEL Garcia, lu\
    gar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.38/32
add comment=WIRELESS max-limit=1k/1k name="id:1046, usuario:LUIS ALVARADO, lug\
    ar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.9.13/32
add comment=FIBER max-limit=1k/1k name="id:1050, usuario:YORDI MENDOZA, lugar:\
    la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.94/32
add comment=FIBER max-limit=1k/1k name="id:1053, usuario:MISAEL MANRIQUE BAJON\
    ERO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.226/32
add comment=WIRELESS max-limit=1k/1k name="id:1057, usuario:GEOVANA GARRO BAZA\
    N, lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.95.48/32
add comment=WIRELESS max-limit=1k/1k name="id:1059, usuario:PITER SALINAS, lug\
    ar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.116/32
add comment=FIBER max-limit=1k/1k name="id:1063, usuario:ROBERTO GERONIMO, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.254/32
add comment=WIRELESS max-limit=1k/1k name="id:1067, usuario:THALIA Dom\C3\ADng\
    uez huerta, lugar:luvio, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.189/32
add comment=FIBER max-limit=1k/1k name="id:1077, usuario:ENDERSON WILSON null,\
    \_lugar:la villa, nap:ML-0-1, plan:Internet100, tipo:FIBER" target=\
    192.168.25.50/32
add comment=FIBER max-limit=1k/1k name="id:1079, usuario:JUAN JOSIAS OBREGON R\
    OJAS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.129/32
add comment=FIBER max-limit=1k/1k name="id:1081, usuario:YOHAN CAURINO, lugar:\
    la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.57/32
add comment=WIRELESS max-limit=1k/1k name="id:1082, usuario:VILMA AMADO PAUCAR\
    , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.20.33/32
add comment=FIBER max-limit=1k/1k name="id:1087, usuario:Simeon QUISPE GALARZA\
    \_taller, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.229/32
add comment=FIBER max-limit=1k/1k name="id:1093, usuario:MAYRA ALEJANDRA PORTI\
    LLA VILLAVICENCIO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER\
    " target=192.168.22.117/32
add comment=FIBER max-limit=1k/1k name="id:1102, usuario:CECILIA VICTORIA PERA\
    LTA MINCHOLA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.226/32
add comment=FIBER max-limit=200M/200M name="id:1103, usuario:JUGUERIA HUAYLAS,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.45/32
add comment=FIBER max-limit=1k/1k name="id:1104, usuario:ELIUS INGA RUIZ malon\
    e, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.129/32
add comment=WIRELESS max-limit=1k/1k name="id:1108, usuario:Michel Edgar Ojeda\
    \_malvaceda, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.137/32
add comment=WIRELESS max-limit=1k/1k name="id:1109, usuario:JULIA HUAMAN CARRA\
    SCO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.112/32
add comment=FIBER max-limit=1k/1k name="id:1115, usuario:JAIRO TREJO HUERTA, l\
    ugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.155/32
add comment=WIRELESS max-limit=1k/1k name="id:1119, usuario:BRISA ALEXANDRA QU\
    IJANO ARANA, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.47/32
add comment=FIBER max-limit=200M/200M name="id:1120, usuario:MELISA SANCHES, l\
    ugar:santa constansa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.44/32
add comment=FIBER max-limit=1k/1k name="id:1126, usuario:DIEGO ROJAS BORRA, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.250/32
add comment=FIBER max-limit=1k/1k name="id:1127, usuario:JORGE LUIS TOCAS LOPE\
    Z, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.58/32
add comment=WIRELESS max-limit=1k/1k name="id:1134, usuario:ROGER GUILLERMO ME\
    JIA ONCOY, lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.199/32
add comment=FIBER max-limit=1k/1k name="id:1135, usuario:HUGO CARVAJAL BENITES\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.61/32
add comment=FIBER max-limit=1k/1k name="id:1137, usuario:RIGOBERTO BUSTAMANTE \
    BENAVIDES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.37/32
add comment=FIBER max-limit=1k/1k name="id:1138, usuario:ESTHER VEGA MARQUINO,\
    \_lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.39/32
add comment=FIBER max-limit=1k/1k name="id:1149, usuario:JULIA GUTIERREZ VALIE\
    NTE, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.42/32
add comment=WIRELESS max-limit=1k/1k name="id:1152, usuario:MILENA FIGUEROA, l\
    ugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.88.31/32
add comment=WIRELESS max-limit=1k/1k name="id:1154, usuario:JOSE ALEX TACILLA \
    JULCA, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.104/32
add comment=FIBER max-limit=1k/1k name="id:1155, usuario:GIOVANA JOSE PIJO, lu\
    gar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.57/32
add comment=WIRELESS max-limit=1k/1k name=\
    "id:1158, usuario:SONIA CALDAS, lugar:la villa, plan:basico, tipo:FIBER" \
    target=192.168.22.24/32
add comment=FIBER max-limit=1k/1k name="id:1159, usuario:Anthony CHERO RIOS, l\
    ugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.26.153/32
add comment=FIBER max-limit=1k/1k name="id:1161, usuario:CARLOS MOLINA Pap\C3\
    \A1 de celeste, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.54/32
add comment=WIRELESS max-limit=1k/1k name="id:1163, usuario:COLEGIO LA VILLA C\
    OLEGIO LA VILLA, lugar:la villa, plan:wireless 150, tipo:WIRELESS" \
    target=192.168.26.161/32
add comment=FIBER max-limit=1k/1k name="id:1164, usuario:Botica solfarma los j\
    ardines, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.34/32
add comment=FIBER max-limit=1k/1k name="id:1165, usuario:Delia Hilda Ruiz Riqu\
    elme, lugar:la villa, nap:LV -5-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.90/32
add comment=FIBER max-limit=1k/1k name="id:1168, usuario:Vanessa ORTIZ, lugar:\
    9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.199/32
add comment=FIBER max-limit=1k/1k name="id:1170, usuario:Oscar TOCAS LOPEZ, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.156/32
add comment=FIBER max-limit=1k/1k name="id:1172, usuario:Robert Alvarado Verde\
    , lugar:casa blanca, nap:ML-0-1, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.200.52/32
add comment=WIRELESS max-limit=1k/1k name="id:1175, usuario:Claudio Rivas zapa\
    ta, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.3/32
add comment=WIRELESS max-limit=1k/1k name="id:1176, usuario:sabrina yuliet vil\
    oria santaella, lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" \
    target=192.168.210.4/32
add comment=FIBER max-limit=1k/1k name="id:1181, usuario:Marco Antonio Loarte \
    Rimac, lugar:la villa, nap:LV -6-6, plan:basico, tipo:FIBER" target=\
    192.168.210.6/32
add comment=WIRELESS max-limit=1k/1k name="id:1182, usuario:Vladimir mendoza r\
    imac, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.22/32
add comment=FIBER max-limit=200M/200M name="id:1183, usuario:daniel ramirez es\
    calante, lugar:la villa, nap:LV -2-8, plan:basico, tipo:FIBER" target=\
    192.168.210.7/32
add comment=WIRELESS max-limit=1k/1k name="id:1187, usuario:moises misael mora\
    les vega, lugar:la villa, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.25.83/32
add comment=FIBER max-limit=1k/1k name="id:1193, usuario:yanely perez tarazona\
    , lugar:la villa, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.210.14/32
add comment=FIBER max-limit=1k/1k name="id:1195, usuario:aldo gonzales velasqu\
    ez, lugar:la villa, nap:LV -6-1, plan:basico, tipo:FIBER" target=\
    192.168.210.16/32
add comment=WIRELESS max-limit=200M/200M name="id:1207, usuario:lizet lucero G\
    aray, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.22/32
add comment=WIRELESS max-limit=1k/1k name="id:1222, usuario:edicia maluquiz ra\
    mirez, lugar:la villa, plan:wireless 60 , tipo:WIRELESS" target=\
    192.168.210.26/32
add comment=FIBER max-limit=1k/1k name="id:1225, usuario:HERIBERTO RAMOS ORTIZ\
    , lugar:la villa, nap:ML-0-6, plan:Internet70, tipo:FIBER" target=\
    192.168.210.29/32
add comment=FIBER max-limit=1k/1k name="id:1226, usuario:ANGEL FERNANDO ESPINO\
    ZA TORRES, lugar:la villa, nap:LV -6-4, plan:Internet70, tipo:FIBER" \
    target=192.168.210.30/32
add comment=FIBER max-limit=1k/1k name="id:1227, usuario:santa vasquez trejo, \
    lugar:casa blanca, nap:CB -11,-6, plan:basico, tipo:FIBER" target=\
    192.168.210.31/32
add comment=FIBER max-limit=1k/1k name="id:1235, usuario:claudio rivas zapata,\
    \_lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.210.33/32
add comment=FIBER max-limit=1k/1k name="id:1239, usuario:yola irma neyra ruiz \
    SOLFARMA, lugar:la villa, nap:LV -6-5, plan:basico, tipo:FIBER" target=\
    192.168.26.51/32
add comment=FIBER max-limit=1k/1k name="id:1244, usuario:galime meza sanchez, \
    lugar:santa anita, nap:SA -9-6, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.37/32
add comment=WIRELESS max-limit=1k/1k name="id:1245, usuario:junior castillo ra\
    mirez, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.38/32
add comment=FIBER max-limit=1k/1k name="id:1247, usuario:fernando Carrasco esp\
    inoza, lugar:casa blanca, nap:CB -12-3, plan:basico, tipo:FIBER" target=\
    192.168.210.40/32
add comment=FIBER max-limit=1k/1k name="id:1255, usuario:nivardo caurino rosal\
    es, lugar:la villa, nap:LV -4-2, plan:basico, tipo:FIBER" target=\
    192.168.210.47/32
add comment=FIBER max-limit=1k/1k name="id:1256, usuario:lucio aguirre zambran\
    o (cambio), lugar:la villa, nap:LV -5-4, plan:basico, tipo:FIBER" target=\
    192.168.210.48/32
add comment=WIRELESS max-limit=1k/1k name="id:1263, usuario:Dorila Hilda Salin\
    as Herrada, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.55/32
add comment=WIRELESS max-limit=1k/1k name="id:1264, usuario:omar sanchez arqui\
    igo, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.156/32
add comment=WIRELESS max-limit=1k/1k name="id:1265, usuario:FRANKLIN NU\C3\91E\
    Z VIDAL, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.57/32
add comment=WIRELESS max-limit=1k/1k name="id:1266, usuario:Elizabeth patricia\
    \_carrillo sucuytana, lugar:san jeronimo, plan:basico_wireless 50, tipo:WI\
    RELESS" target=192.168.210.58/32
add comment=FIBER max-limit=1k/1k name="id:1267, usuario:leticia lorena gonzal\
    es espinoza, lugar:santa anita, nap:SA -9-3, plan:basico, tipo:FIBER" \
    target=192.168.210.59/32
add comment=FIBER max-limit=1k/1k name="id:1268, usuario:Diana rumay vasquez, \
    lugar:luvio, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.210.60/32
add comment=FIBER max-limit=1k/1k name="id:1272, usuario:lucio aguirre zambran\
    o, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.210.67/32
add comment=FIBER max-limit=1k/1k name="id:1274, usuario:esteban quispe lope, \
    lugar:la villa, nap:LV -5-7, plan:basico, tipo:FIBER" target=\
    192.168.210.69/32
add comment=FIBER max-limit=1k/1k name="id:1285, usuario:geancarlos estacio du\
    ran, lugar:luvio, nap:LB -7-4, plan:empresa_150, tipo:WIRELESS" target=\
    192.168.210.73/32
add comment=FIBER max-limit=1k/1k name="id:1286, usuario:edwin de la cruz caur\
    ino, lugar:la villa, nap:LV -1-1, plan:basico, tipo:FIBER" target=\
    192.168.210.74/32
add comment=FIBER max-limit=1k/1k name="id:1288, usuario:Ronaldo Rumi reyes, l\
    ugar:la villa, nap:LV -1-6, plan:basico, tipo:FIBER" target=\
    192.168.210.76/32
add comment=FIBER max-limit=1k/1k name="id:1290, usuario:saul kunchikui akuts,\
    \_lugar:la villa, nap:LV -6-4, plan:basico, tipo:FIBER" target=\
    192.168.210.78/32
add comment=WIRELESS max-limit=1k/1k name="id:1296, usuario:CULTIVOS ORGANICOS\
    \_ FUNDO DARIO ADOLFO, lugar:casa blanca, plan:empresa 690, tipo:WIRELESS" \
    target=192.168.88.70/32
add comment=WIRELESS max-limit=1k/1k name="id:1298, usuario:AGRO COMERCIAL  AG\
    RICOLA PROGRES  , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.33.19/32
add comment=WIRELESS max-limit=1k/1k name="id:1306, usuario:AGROVIVEROS PERU S\
    .A.C. null, lugar:9 de octubre, plan:wireless 100, tipo:WIRELESS" target=\
    192.168.88.63/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1343, usuario:Luis albe\
    rto  Tadeo Cancha , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.26.234/32
add comment=FIBER max-limit=1k/1k name="id:1344, usuario:Jhan Piero Jaimes Car\
    los, lugar:la villa, nap:ML-0-4, plan:Internet100, tipo:FIBER" target=\
    192.168.55.254/32
add comment=FIBER max-limit=1k/1k name="id:1354, usuario:GREGORIA  CASTILLO CA\
    LLE , lugar:tiwinza, nap:TW -13-2, plan:basico, tipo:FIBER" target=\
    192.168.210.91/32
add comment=FIBER max-limit=1k/1k name="id:1355, usuario:ingrid paredes shuan,\
    \_lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.210.92/32
add comment=WIRELESS max-limit=1k/1k name="id:1356, usuario:esperanza  velasqu\
    ez espinoza, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.93/32
add comment=FIBER max-limit=1k/1k name="id:1360, usuario:Bella mata trejo, lug\
    ar:casa blanca, nap:CB -11,-6, plan:basico, tipo:FIBER" target=\
    192.168.26.29/32
add comment=FIBER max-limit=1k/1k name="id:1363, usuario:Luis Angel Perez Nava\
    rro , lugar:la villa, nap:SA -9-2, plan:basico, tipo:FIBER" target=\
    192.168.26.167/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1365, usuario:MIGUEL RA\
    MIREZ SOLORZANO PIZZERIA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_\
    FIBER" target=192.168.26.228/32
add comment=FIBER max-limit=1k/1k name="id:1367, usuario:Diego Montes Rodrigue\
    z, lugar:la villa, nap:LV -5-7, plan:Internet70, tipo:FIBER" target=\
    192.168.26.66/32
add comment=WIRELESS max-limit=1k/1k name="id:1368, usuario:Marcia Nicol Casti\
    llo Sevillano, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.21/32
add comment=WIRELESS max-limit=1k/1k name="id:1369, usuario:Carmen Mavila Cast\
    illo Espinoza, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.22/32
add comment=WIRELESS max-limit=1k/1k name="id:1370, usuario:Evelin null, lugar\
    :9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.9.38/32
add comment=WIRELESS max-limit=1k/1k name="id:1371, usuario:Cristian Lastra Hu\
    anca, lugar:la villa, plan:basico, tipo:FIBER" target=192.168.25.178/32
add comment=WIRELESS max-limit=1k/1k name="id:1379, usuario:Jacinto yovera pac\
    herres, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.33.94/32
add comment=WIRELESS max-limit=1k/1k name="id:1385, usuario:edgar almenara ram\
    os, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.98/32
add comment=WIRELESS max-limit=1k/1k name="id:1387, usuario:Edwin Yuler Espino\
    za Robles , lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.100/32
add comment=WIRELESS max-limit=1k/1k name="id:1389, usuario:arnold  quispe per\
    ez, lugar:luvio, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.210.102/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1395, usuario:FRANKLIN JOEL\
    \_ABRAMONTE CALLE, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER\
    " target=192.168.210.108/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1396, usuario:Mar\C3\ADa El\
    ena Nina Llavilla , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBE\
    R" target=192.168.210.109/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1397, usuario:clinton inga \
    ruiz , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" target=\
    192.168.210.110/32
add comment=FIBER max-limit=1k/1k name="id:1399, usuario:victor  garcia menese\
    s, lugar:la villa, nap:LV -5-8, plan:basico, tipo:FIBER" target=\
    192.168.210.112/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1404, usuario:briyitt Geral\
    dine  anaya vasquez , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBE\
    R" target=192.168.210.117/32
add comment=FIBER max-limit=1k/1k name="id:1414, usuario:jann antonio lozada, \
    lugar:la villa, nap:LV -5-5, plan:basico, tipo:FIBER" target=\
    192.168.210.121/32
add comment=FIBER max-limit=1k/1k name="id:1423, usuario:Ana yadeci ga\C3\B1an\
    \_castro, lugar:la villa, nap:LV -4-2, plan:basico, tipo:FIBER" target=\
    192.168.210.128/32
add comment=FIBER max-limit=1k/1k name="id:1425, usuario:GRISELI ARLET  TARAZO\
    NA DE LA CRUZ , lugar:la ensenada, nap:EN -8-4, plan:basico, tipo:FIBER" \
    target=192.168.210.133/32
add comment=FIBER max-limit=1k/1k name="id:1430, usuario:rosa paola caballero \
    caqui, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.137/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1431, usuario:EGERLIN CAROL\
    INA CALLEJONES  RANGEL, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FI\
    BER" target=192.168.210.138/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1436, usuario:amancio march\
    ino Francisco , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.144/32
add comment=FIBER max-limit=1k/1k name="id:1440, usuario:lucio  aguirre zambra\
    no, lugar:la villa, nap:LV -4-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.146/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1442, usuario:HEDILBERT\
    O MODESTO ZARZOSA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.148/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1443, usuario:cristian  vel\
    asquez santos , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.149/32
add comment=WIRELESS max-limit=1k/1k name="id:1444, usuario:Mauricio  vargas ,\
    \_lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.150/32
add comment=WIRELESS max-limit=1k/1k name="id:1445, usuario:alberto panta yamu\
    naque , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.151/32
add comment=FIBER max-limit=1k/1k name="id:1451, usuario:mayra  estelle davila\
    , lugar:la villa, nap:LV -6-2, plan:basico, tipo:FIBER" target=\
    192.168.210.158/32
add comment=FIBER max-limit=1k/1k name="id:1453, usuario:GRIFO LUIS CARLOS HUA\
    MANI GUILLEN, lugar:tiwinza, nap:CB -12-3, plan:DUO INTERNET 50MB v2, tipo\
    :FIBER" target=192.168.210.160/32
add comment=FIBER max-limit=1k/1k name="id:1456, usuario:nicol eleodoro valent\
    in, lugar:la villa, nap:LV -6-3, plan:basico, tipo:FIBER" target=\
    192.168.210.162/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1462, usuario:Dante Job Lla\
    shag Bautista, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.165/32
add comment=FIBER max-limit=200M/200M name="id:1463, usuario:RICARDO  GUTIERRE\
    Z VARGAS, lugar:la villa, nap:LV -6-1, plan:basico, tipo:FIBER" target=\
    192.168.210.166/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1470, usuario:cristina sanc\
    hez ramirez, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.173/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1472, usuario:MAGDALENA PAU\
    LINO ZEVALLOS, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.175/32
add comment=FIBER max-limit=1k/1k name="id:1478, usuario:waly roca , lugar:la \
    villa, nap:ML-0-5, plan:basico, tipo:FIBER" target=192.168.210.180/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1479, usuario:eliseo herrer\
    a campos, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.22.146/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1481, usuario:LADISLAO ESTE\
    LLE BUITRON, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.181/32
add comment=FIBER max-limit=1k/1k name="id:1483, usuario:Erika Pilar vasquez t\
    rujillo taller CHUCKY, lugar:la villa, nap:LV -5-3, plan:duo_basico 80, ti\
    po:FIBER" target=192.168.210.184/32
add comment=FIBER max-limit=1k/1k name="id:1484, usuario:yerson  mendoza julca\
    , lugar:la villa, nap:LV -15-5, plan:basico, tipo:FIBER" target=\
    192.168.210.185/32
add comment=FIBER max-limit=1k/1k name="id:1486, usuario:LINDA DELIA LUCERO VE\
    GA, lugar:la villa, nap:LV -15-5, plan:basico, tipo:FIBER" target=\
    192.168.210.187/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1489, usuario:anibal  sifue\
    ntes felix, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.189/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1492, usuario:Robert rosale\
    s cuya, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" target=\
    192.168.210.192/32
add comment=WIRELESS max-limit=1k/1k name="id:1493, usuario:ana yanina huerta \
    espinoza, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.193/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1497, usuario:Elvis guido  \
    pecan huerta, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.194/32
add comment=FIBER max-limit=1k/1k name="id:1498, usuario:JOSE PIO BARDALES ESP\
    INOZA, lugar:la villa, nap:ML-0-8, plan:basico, tipo:FIBER" target=\
    192.168.210.195/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1500, usuario:Florinda ceci\
    lia Silva trejo, lugar:casa blanca, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.197/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1502, usuario:mario mendoza\
    \_salazar, lugar:la villa, plan:PLAZA AUTOSERVICIO, tipo:FIBER" target=\
    192.168.210.199/32
add comment=FIBER max-limit=1k/1k name="id:1504, usuario:yonel  sosa plaza nue\
    va villa, lugar:la villa, nap:LV -1-3, plan:basico, tipo:FIBER" target=\
    192.168.210.201/32
add comment=FIBER max-limit=1k/1k name="id:1505, usuario:humberto Mercedes jua\
    quin , lugar:la villa, nap:LV -4-4, plan:basico, tipo:FIBER" target=\
    192.168.210.202/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1506, usuario:manuel arenas\
    \_gutierrez, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.147/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1509, usuario:osiel sanchez\
    \_sieza , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.203/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1522, usuario:PEDRO DAN\
    IEL  JARA ZORRILLA, lugar:santa anita, plan:cable_basico35, tipo:ONLY_TV_F\
    IBER" target=192.168.210.63/32
add comment=FIBER max-limit=1k/1k name="id:1523, usuario:ELIZABETH PATRICIA  C\
    ARRILLO SUCUYTANA, lugar:la villa, nap:LV -6-4, plan:basico, tipo:FIBER" \
    target=192.168.210.215/32
add comment=FIBER max-limit=1k/1k name="id:1524, usuario:elenind  principe vel\
    asquez, lugar:la villa, nap:LV -4-4, plan:Internet70, tipo:FIBER" target=\
    192.168.210.216/32
add comment=WIRELESS max-limit=25M/25M name="id:1530, usuario:NAO MILTON QUI\
    \C3\91ONES VERDE, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELE\
    SS" target=192.168.210.219/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1531, usuario:JENY RUMAY VA\
    SQUEZ , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.218/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1532, usuario:DIEGO TRIGOZO\
    \_AGUIRRE, lugar:san jeronimo, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.217/32
add comment=FIBER max-limit=1k/1k name="id:1533, usuario:AYDE YOYSEN MATTOS BA\
    UTISTA , lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" target=\
    192.168.210.227/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1534, usuario:AURORA  SALDA\
    \C3\91A MALLQUI, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.220/32
add comment=FIBER max-limit=1k/1k name="id:1540, usuario:Antonia  Principe Ber\
    rospi, lugar:la villa, nap:LV -3-7, plan:basico, tipo:FIBER" target=\
    192.168.210.226/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1544, usuario:KAREN ALICIA \
    CIELO FLORES, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.229/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1545, usuario:ALFREDO AVELA\
    \_AVELDAO, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.230/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1546, usuario:CLAUDIO FLORE\
    S NAVARRO, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.231/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1547, usuario:KATY  BARRANT\
    ES, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.232/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1548, usuario:RUPAY EGUSQUI\
    ZA, lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.233/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1549, usuario:RAMIRO  MAITA\
    \_ESCOBAL, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.234/32
add comment=FIBER max-limit=1k/1k name="id:1553, usuario:Aldo Gonzales Velazqu\
    es , lugar:santa anita, nap:SA -9-2, plan:basico, tipo:FIBER" target=\
    192.168.210.238/32
add comment=FIBER max-limit=1k/1k name="id:1561, usuario:Carlos  Mendoza Mendo\
    za, lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" target=\
    192.168.210.246/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1562, usuario:Josee Antonio\
    \_ Carhuapoma Cabrera, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIB\
    ER" target=192.168.210.243/32
add comment=FIBER max-limit=1k/1k name="id:1563, usuario:Victor Martin  Aguero\
    \_Sosa , lugar:la villa, nap:LV -5-4, plan:basico, tipo:FIBER" target=\
    192.168.210.247/32
add comment=FIBER max-limit=1k/1k name="id:1565, usuario:Roger  Gonzales Velas\
    quez, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.249/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1573, usuario:Santy Raniel \
    Pinto, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.167/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1575, usuario:NELSON CASTIL\
    LEJO FERNANDEZ , lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.222/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1576, usuario:DINA BAZAN MO\
    RALES, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.239/32
add comment=WIRELESS max-limit=1k/1k name="id:1585, usuario:Richard Vargas Fel\
    iciano, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.220/32
add comment=FIBER max-limit=1k/1k name="id:1607, usuario:Fernando  Rosales Cot\
    rina, lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" target=\
    192.168.22.222/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1610, usuario:Hilda Cabello\
    \_Herrera, lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.159/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1611, usuario:ERICK  QUIJAN\
    O VELASQUEZ , lugar:la villa, plan:cable_basico35, tipo:ONLY_TV_FIBER" \
    target=192.168.210.200/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1613, usuario:ORLANDO SUXSO\
    \_CALISAYA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.22.12/32
add comment=FIBER max-limit=1k/1k name="id:1622, usuario:JUAN CARLOS  CASTILLO\
    \_CALLE, lugar:casa blanca, nap:CB -11,-7, plan:cable_basico, tipo:ONLY_TV\
    _FIBER" target=192.168.22.138/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1625, usuario:Sara Jenny Di\
    az Zabala, lugar:santa constansa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.13/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1630, usuario:Josue Rey\
    naldo Yamunaque Aguilar, lugar:luvio, plan:cable_basico, tipo:ONLY_TV_FIBE\
    R" target=192.168.211.18/32
add comment=WIRELESS max-limit=1k/1k name="id:1635, usuario:KIELA  CRUZ LUNA, \
    lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.23/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1636, usuario:FLORIANO SIFU\
    ENTES ARANDA , lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.24/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1638, usuario:JHON LUDMER E\
    NCARNACION ALMINCO, lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIB\
    ER" target=192.168.211.26/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1639, usuario:TANIA  PAUCAR\
    \_AMADO, lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.27/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1659, usuario:MARILU  RAFAE\
    L TORRES , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.95.60/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1661, usuario:WAGNER VASQUE\
    Z UTRILLA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.38/32
add comment=FIBER max-limit=1k/1k name="id:1666, usuario:frank  deivi ramos Co\
    trina , lugar:la villa, nap:LV -4-3, plan:basico, tipo:FIBER" target=\
    192.168.211.42/32
add comment=ONLY_TV_FIBER max-limit=200M/200M name="id:1669, usuario:KHATERINE\
    \_ CARDENAS FERNANDEZ, lugar:santa constansa, plan:cable_basico, tipo:ONLY\
    _TV_FIBER" target=192.168.211.45/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1671, usuario:Miguel  MALVA\
    S , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.47/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1674, usuario:CARMEN PENA Z\
    EVALLOS, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.50/32
add comment=WIRELESS max-limit=1k/1k name="id:1675, usuario:YANET GERALDINE YA\
    NAC MONTES, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.211.51/32
add comment=FIBER max-limit=200M/200M name="id:1678, usuario:azucena huares al\
    ejos , lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" target=\
    192.168.211.151/32
add comment=WIRELESS max-limit=1k/1k name="id:1680, usuario:EDGAR ANTONIO CUEV\
    A UBALDO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.211.56/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1684, usuario:VILMA OLORTEG\
    I CERNA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.60/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1685, usuario:LORENZO NOE  \
    BLAS PAUCAR, lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.61/32
add comment=FIBER max-limit=1k/1k name="id:1703, usuario:patricia  carrillo nu\
    evo, lugar:la villa, nap:null, plan:basico, tipo:FIBER" target=\
    192.168.211.71/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1720, usuario:Roxana Lopez \
    Cotrina, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.75/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1727, usuario:JOSE BENAVIDE\
    S CHUSDEN , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.81/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1728, usuario:Yhon Arteaga \
    Benavides, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.82/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1730, usuario:SOLEDAD ORELL\
    ANA VILLANUEVA , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.84/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1738, usuario:ZAIDA  GABIDI\
    A TORRES, lugar:la ensenada, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.211.92/32
add comment=ONLY_TV_FIBER max-limit=1k/1k name="id:1753, usuario:EVELIN BAZAN \
    MUNOZ, lugar:9 de octubre, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.107/32
add max-limit=100M/100M name="id:1777, usuario:hdhdhdjh jdudjdudu, lugar:9 de \
    octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.130/32
add max-limit=10M/20M name="id:1778, usuario:jdjfjfjfjdju jdjddjdjduf, lugar:l\
    a villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.131/32
add comment=FIBER max-limit=1k/1k name="id:690, usuario:DONATILDA PORRAS RODRI\
    GUEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.164/32
add comment=FIBER max-limit=1k/1k name="id:708, usuario:ANALIO JUAN DE DIOS SA\
    LAS MEDALLA, lugar:la villa, nap:ML-0-1, plan:plan rikos chiken, tipo:FIBE\
    R" target=192.168.26.47/32
add comment=FIBER max-limit=1k/1k name="id:728, usuario:ALEXANDER JOEL ANAYA R\
    IMARACHIN, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.167/32
add comment=FIBER max-limit=1k/1k name="id:839, usuario:MARISOL IDALIA ESPINOZ\
    A DIAZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.185/32
add comment=FIBER max-limit=1k/1k name="id:902, usuario:EL FOGON POLLERIA, lug\
    ar:la villa, nap:ML-0-1, plan:Internet100, tipo:FIBER" target=\
    192.168.25.252/32
add comment=WIRELESS max-limit=1k/1k name="id:903, usuario:LUCIANO HIRED, luga\
    r:la villa, plan:wireless 70 , tipo:WIRELESS" target=192.168.22.86/32
add comment=WIRELESS max-limit=1k/1k name="id:956, usuario:JUDITH COLONIA, lug\
    ar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.212/32
add comment=FIBER max-limit=1k/1k name="id:1058, usuario:ELENA CUYA LEZAMETA 2\
    , lugar:la villa, nap:ML-0-1, plan:Internet100, tipo:FIBER" target=\
    192.168.88.57/32
add comment=FIBER max-limit=1k/1k name="id:1382, usuario:naomi liset bravo tol\
    edo, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.210.97/32
add comment=WIRELESS max-limit=200M/200M name="id:1390, usuario:g\C3\A9nesis c\
    rystina OLORTEGUI marcelo, lugar:9 de octubre, plan:basico_wireless 50, ti\
    po:WIRELESS" target=192.168.210.103/32
add comment=FIBER max-limit=1k/1k name="id:1421, usuario:franco vivanco  quisp\
    e, lugar:la villa, nap:LV -4-2, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.129/32
add comment=WIRELESS max-limit=1k/1k name="id:1460, usuario:ELENA  CUYA LEZAME\
    TA, lugar:las piedras, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.164/32
add comment=FIBER max-limit=1k/1k name="id:1539, usuario:Antony Chero Rios, lu\
    gar:luvio, nap:LB -7-3, plan:Internet70, tipo:FIBER" target=\
    192.168.210.225/32
add comment=WIRELESS max-limit=1k/1k name="id:1771, usuario:ALEX PEREZ VIDAL, \
    lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.124/32
add max-limit=100M/100M name="id:1822, usuario:Ageo Quinones Verde, lugar:nuev\
    e de octubre nap:, plan:, tipo:" target=192.168.211.166/32
add max-limit=400M/400M name="id:1823, usuario:Segundina Julia Colonia Gargate\
    , lugar:, nap:, plan:, tipo:" target=192.168.211.160/32
add comment=FIBER max-limit=1k/1k name="id:745, usuario:ABNER LIBER JUAREZ MAT\
    TOS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.43/32
add comment=FIBER max-limit=1k/1k name="id:828, usuario:MIRIAN SAHARA RIOS PAJ\
    UELO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.21/32
add max-limit=20M/20M name="desconocido 220.200" target=192.168.220.200/32
add comment=FIBER max-limit=1k/1k name="id:721, usuario:Karen Paola Bedon Rami\
    rez, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.188/32
add comment=FIBER max-limit=1k/1k name="id:874, usuario:JULIO HERBOZO, lugar:l\
    a villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.19/32
add comment=FIBER max-limit=1k/1k name="id:1833, usuario:DONATO AMADOR MALVAS \
    IZQUIERDO, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.19/32
add comment=WIRELESS max-limit=1k/1k name="id:1049, usuario:SARA ELIZABETH CUE\
    VA FERNANDEZ, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.29/32
add comment=WIRELESS max-limit=1k/1k name="id:1086, usuario:CYNDI HURTADO, lug\
    ar:las piedras, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.104/32
add comment=WIRELESS max-limit=1k/1k name="id:1171, usuario:NELLY REYNA GARGAT\
    E CASTILLO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.158/32
add comment=WIRELESS max-limit=1k/1k name="id:1359, usuario:Aurora Salda\C3\B1\
    a mallqui, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.96/32
add comment=FIBER max-limit=1k/1k name="id:1631, usuario:Carlos Edwin Carhuapo\
    ma Vela, lugar:la villa, nap:LV -5-5, plan:basico, tipo:FIBER" target=\
    192.168.211.19/32
add comment=FIBER max-limit=1k/1k name="id:1761, usuario:JOSE MARIA SERNAQUE R\
    AMOS, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.211.114/32
add comment=FIBER max-limit=1k/1k name="id:1791, usuario:Nataly Jara Reman, lu\
    gar:luvio, nap:LB -7-1, plan:Internet70, tipo:FIBER" target=\
    192.168.211.142/32
add comment=WIRELESS max-limit=1k/1k name="id:649, usuario:NELSON JACINTO TOCA\
    S VASQUEZ, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.29/32
add comment=FIBER max-limit=1k/1k name="id:783, usuario:YOIMER ELI NU\C3\91EZ \
    DELGADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.30/32
add comment=FIBER max-limit=1k/1k name="id:848, usuario:Jorge Luis Dugard Este\
    lle, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.171/32
add comment=FIBER max-limit=200M/200M name="id:1511, usuario:gregorio castillo\
    \_sotelo , lugar:la villa, nap:LV -4-5, plan:basico, tipo:FIBER" target=\
    192.168.210.206/32
add comment=FIBER max-limit=1k/1k name="id:1783, usuario:Tito Manuel Baneo Sil\
    va , lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.133/32
add comment=FIBER max-limit=1k/1k name="id:1805, usuario:Ulises Mori Sifuentes\
    \_, lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.211.154/32
add comment=FIBER max-limit=1k/1k name="id:827, usuario:Sayra Jael Salda\C3\B1\
    a Sigue\C3\B1as, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.126/32
add comment=FIBER max-limit=1k/1k name="id:990, usuario:CARMEN SANTOS CLAROS, \
    lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.23/32
add comment=WIRELESS max-limit=200M/200M name="id:1040, usuario:ELMER ORLANDO \
    SANCHEZ MARQUINA, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.221.34/32
add comment=WIRELESS max-limit=1k/1k name="id:1090, usuario:Juan Hugo Ramirez \
    Flores, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.164/32
add comment=WIRELESS max-limit=1k/1k name="id:1212, usuario:Andr\C3\A9s Sa\C3\
    \BAl  Mendoza Espinoza, lugar:9 de octubre, plan:basico_wireless 50, tipo:\
    WIRELESS" target=192.168.210.25/32
add comment=FIBER max-limit=1k/1k name="id:1352, usuario:marcelina  aniceto ma\
    llqui, lugar:la villa, nap:LV -5-3, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.89/32
add comment=FIBER max-limit=1k/1k name="id:1366, usuario:Mar\C3\ADa Esmerita C\
    astillo Calle, lugar:la villa, nap:LV -5-7, plan:basico, tipo:FIBER" \
    target=192.168.200.51/32
add comment=FIBER max-limit=1k/1k name="id:1584, usuario:ALEXANDER MARTIN HUAR\
    CAYA MIRAVAL , lugar:la villa, nap:LV -6-6, plan:basico, tipo:FIBER" \
    target=192.168.22.162/32
add comment=WIRELESS max-limit=1k/1k name="id:1617, usuario:LIDIA TOCTO OLIVAR\
    \_, lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.140/32
add comment=FIBER max-limit=1k/1k name="id:1645, usuario:ALCIRA  SOTO CESPEDES\
    , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=192.168.211.29/32
add comment=FIBER max-limit=1k/1k name="id:1764, usuario:DEYSI BRONCANO COTRIN\
    A, lugar:santa anita, nap:SA -9-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.117/32
add comment=FIBER max-limit=400M/400M name="id:1772, usuario:Luis Jose Rios Pa\
    juelo, lugar:luvio, nap:LB -7-1, plan:Internet100, tipo:FIBER" target=\
    192.168.211.125/32
add comment=FIBER max-limit=200M/200M name="id:1825, usuario:SANDRA CAROLINA Z\
    ERPA ANCAJIMA, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.11/32
add comment=FIBER max-limit=200M/200M name="id:1852, usuario:EDER PERCY NIETO \
    VEGA, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.38/32
add comment=FIBER max-limit=1k/1k name="id:1853, usuario:YAIR ROBERT SIFUENTES\
    \_MEGIA, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.39/32
add comment=FIBER max-limit=200M/200M name="id:1874, usuario:LUIS BARBOZA ORTI\
    Z, lugar:luvio, nap:LB -7-5, plan:basico, tipo:FIBER" target=\
    192.168.220.58/32
add comment=FIBER max-limit=1k/1k name="id:1900, usuario:FLOR NELLY GARCIA DE \
    LA CRUZ, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.84/32
add comment=FIBER max-limit=1k/1k name="id:1932, usuario:Nicol Utrilla Soto, l\
    ugar:casa blanca, nap:CB -12-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.220.110/32
add comment=WIRELESS max-limit=1k/1k name="id:724, usuario:CARMEN ROSA RIMAC M\
    ONTES, lugar:la villa, plan:basico, tipo:FIBER" target=192.168.26.125/32
add comment=WIRELESS max-limit=1k/1k name="id:852, usuario:ANDREA EUROSINA ONO\
    FRE CUYA, lugar:las piedras, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.22.75/32
add comment=WIRELESS max-limit=1k/1k name="id:881, usuario:SAMUEL GAMARRA, lug\
    ar:luvio, plan:wireless 70 , tipo:WIRELESS" target=192.168.93.222/32
add comment=WIRELESS max-limit=1k/1k name="id:889, usuario:EDGAR ALEJANDRO MIN\
    CHOLA CALDERON, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.25.184/32
add comment=FIBER max-limit=1k/1k name="id:923, usuario:YUSA TORIBIO, lugar:la\
    \_villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.200.46/32
add comment=FIBER max-limit=1k/1k name="id:1141, usuario:HECTOR BRAVO SIGUE\C3\
    \91AS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.143/32
add comment=FIBER max-limit=1k/1k name="id:1275, usuario:milagros huaman sanch\
    ez, lugar:la ensenada, nap:EN -8-1, plan:basico, tipo:FIBER" target=\
    192.168.210.70/32
add comment=FIBER max-limit=1k/1k name="id:1297, usuario:AGRO QUIMICO NILTON  \
    , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=192.168.25.157/32
add comment=WIRELESS max-limit=1k/1k name="id:1350, usuario:YUBER AVILA PRINCI\
    PE , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.87/32
add comment=FIBER max-limit=1k/1k name="id:1468, usuario:Yessica yesela  espin\
    oza obregon , lugar:la villa, nap:LV -5-3, plan:basico, tipo:FIBER" \
    target=192.168.210.171/32
add comment=FIBER max-limit=1k/1k name="id:1574, usuario:CLARA  ZAMUDIO HUERTA\
    , lugar:la villa, nap:LV -6-7, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.26.52/32
add comment=FIBER max-limit=200M/200M name="id:1887, usuario:Santa Marta Romer\
    o Tafur de Tamara, lugar:la villa, nap:LV -6-3, plan:basico, tipo:FIBER" \
    target=192.168.220.71/32
add comment=FIBER max-limit=200M/200M name="id:1921, usuario:HERMELINDA VASQUE\
    Z PRINCIPE, lugar:la ensenada, nap:EN -8-1, plan:basico, tipo:FIBER" \
    target=192.168.220.99/32
add comment=FIBER max-limit=200M/200M name="id:1477, usuario:NILTON ELIAS  DE \
    LA CRU ROJAS , lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.179/32
add disabled=yes max-limit=1k/1k name="id:1160, usuario:MOISES PANADERIA ALMIR\
    CO PUJAY, lugar:la villa, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.128/32
add max-limit=200M/200M name="id:1604, usuario:Rafael Alberto Sanchez Vasquez,\
    \_lugar:la ensenada, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.22.169/32
add max-limit=20M/20M name="id:1, usuario:Tomasa  Egusquiza blanco, lugar:luvi\
    o, plan:duo_basico 80, tipo:FIBER" target=192.168.26.65/32
add comment=FIBER max-limit=1k/1k name="id:955, usuario:HOTEL GOURMET, lugar:l\
    a villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.31/32
add comment=FIBER max-limit=1k/1k name="id:981, usuario:MEILIN ESTACIO, lugar:\
    la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.226/32
add comment=FIBER max-limit=1k/1k name="id:1012, usuario:LORENA LIZBETH MARTIN\
    EZ REQUELME, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.213/32
add comment=FIBER max-limit=1k/1k name="id:1351, usuario:Lidia cigue\C3\B1as e\
    spinosa , lugar:la villa, nap:LV -15-3, plan:basico, tipo:FIBER" target=\
    192.168.210.88/32
add comment=FIBER max-limit=200M/200M name="id:1406, usuario:mirian  zamudio h\
    uerta, lugar:la villa, nap:LV -6-8, plan:basico, tipo:FIBER" target=\
    192.168.210.119/32
add comment=FIBER max-limit=200M/200M name="id:1883, usuario:Lesly  Bravo Caqu\
    i, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIBER" target=\
    192.168.220.67/32
add comment=FIBER max-limit=200M/200M name="id:1892, usuario:Maximino Teodosio\
    \_Navarro Aylas, lugar:9 de octubre, nap:LV -6-2, plan:basico, tipo:FIBER" \
    target=192.168.220.76/32
add comment=FIBER max-limit=1k/1k name="id:1913, usuario:SHERLITH ISMINIO INSA\
    PILLO, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" target=\
    192.168.220.91/32
add comment=FIBER disabled=yes max-limit=1k/1k name="id:2048, usuario:Carlos p\
    aulino cabinas, lugar:la villa, nap:LV -6-5, plan:Cabinas, tipo:FIBER" \
    target=192.168.99.178/32
add comment=FIBER max-limit=200M/200M name="id:615, usuario:SOLANGEL COROMOTO \
    JIMENEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.92/32
add comment=FIBER max-limit=200M/200M name="id:616, usuario:EMILIZ PIERINA PIA\
    MO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.161/32
add comment=FIBER max-limit=200M/200M name="id:618, usuario:PROSPERO CHINCHANO\
    \_ATERO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.59/32
add comment=WIRELESS max-limit=20M/20M name="id:620, usuario:GABRIEL QUISPE LO\
    PE, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.26.11/32
add comment=WIRELESS max-limit=20M/20M name="id:621, usuario:Antenor Mauricio \
    Sifuentes Rodriguez, lugar:las colinas, plan:basico_wireless 50, tipo:WIRE\
    LESS" target=192.168.88.128/32
add comment=WIRELESS max-limit=20M/20M name="id:622, usuario:ANTONIO FERNANDO \
    PAULINO RIVERA, lugar:las piedras, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.207/32
add comment=FIBER max-limit=40M/80M name="id:623, usuario:Cesar Moises Blanco \
    Garro, lugar:la villa, nap:ML-0-1, plan: INTERNET 40MB, tipo:FIBER" \
    target=192.168.25.78/32
add comment=FIBER max-limit=30M/30M name="id:625, usuario:IVAN  YAURI ALEGRE, \
    lugar:pampa yaro, nap:ML-0-1, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.93.194/32
add comment=WIRELESS max-limit=20M/20M name="id:626, usuario:MACARIO JOSE QUIR\
    OZ FLORES, lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.5/32
add comment=WIRELESS max-limit=20M/20M name="id:628, usuario:Reyna maura Sifue\
    ntes Mej\C3\ADa, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.9.43/32
add comment=WIRELESS max-limit=20M/20M name="id:630, usuario:ALBERTO PASCUAL C\
    HIROQUE ALVARADO, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.197/32
add comment=WIRELESS max-limit=15M/30M name="id:631, usuario:ALVINA SALINAS VA\
    LVERDE, lugar:las colinas, plan:wireless 100, tipo:WIRELESS" target=\
    192.168.93.73/32
add comment=WIRELESS max-limit=200M/200M name="id:633, usuario:VICTORIA ISABEL\
    \_Espinoza Gonzales, lugar:santa anita, plan:basico_wireless 50, tipo:WIRE\
    LESS" target=192.168.26.232/32
add comment=WIRELESS max-limit=200M/200M name="id:634, usuario:FAUSTINO MUROYA\
    \_TADEO, lugar:las colinas, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.33.13/32
add comment=WIRELESS max-limit=200M/200M name="id:636, usuario:Maura Aurora Ba\
    lcazar Gonz\C3\A1lez, lugar:san jeronimo, plan:basico_wireless 50, tipo:WI\
    RELESS" target=192.168.88.156/32
add comment=FIBER max-limit=200M/200M name="id:637, usuario:VICTOR HUGO CANOVA\
    \_MASATO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.139/32
add comment=FIBER max-limit=200M/200M name="id:638, usuario:HECTOR JORGE MAYTA\
    \_HURTADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.40/32
add comment=FIBER max-limit=200M/200M name="id:639, usuario:ANA CECILIA ESCALA\
    NTE RAMIREZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.119/32
add comment=WIRELESS max-limit=20M/20M name="id:640, usuario:Jos\C3\A9 Carlos \
    Carrillo Diestra, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELE\
    SS" target=192.168.93.79/32
add comment=WIRELESS max-limit=25M/25M name="id:641, usuario:CORSINO  OSCAR MA\
    LLQUI RIMAC, lugar:la villa, plan:wireless 60 , tipo:WIRELESS" target=\
    192.168.25.136/32
add comment=FIBER max-limit=200M/200M name="id:642, usuario:CARLOS MOLINA cole\
    gio hadwar, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.77/32
add comment=FIBER max-limit=300M/300M name="id:644, usuario:Kelly Noelia arroy\
    o JANAMPA , lugar:9 de octubre, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.9.12/32
add comment=FIBER max-limit=200M/200M name="id:646, usuario:Felix SIMEON Tadeo\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.122/32
add comment=FIBER max-limit=200M/200M name="id:647, usuario:ISAIAS JOSE HUERTA\
    \_MEDRANO, lugar:santa constansa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.93.171/32
add comment=WIRELESS max-limit=30M/30M name="id:648, usuario:AUGUSTO VALVERDE \
    JIMENEZ, lugar:las colinas, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.22.30/32
add comment=FIBER max-limit=200M/200M name="id:650, usuario:JUAN ALEJANDRO BAR\
    TOLO CUIZANO, lugar:9 de octubre, nap:ML-0-1, plan:duo_basico 80, tipo:FIB\
    ER" target=192.168.9.11/32
add comment=WIRELESS max-limit=200M/200M name="id:651, usuario:JAIME ULISES AL\
    VA ASIS, lugar:santa constansa, plan:basico, tipo:FIBER" target=\
    192.168.93.253/32
add comment=FIBER max-limit=200M/200M name="id:653, usuario:Mauro Marcos Huama\
    n Guzm\C3\A1n, lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.148/32
add comment=FIBER max-limit=200M/200M name="id:654, usuario:Elmer Hildebrando \
    Vega Garcia, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.20/32
add comment=WIRELESS max-limit=300M/300M name="id:655, usuario:MAURICIO TENORI\
    O CORDOVA, lugar:la ensenada, plan:Internet70, tipo:FIBER" target=\
    192.168.25.247/32
add comment=FIBER max-limit=200M/200M name="id:658, usuario:GABRIEL NARCIZO MU\
    NOZ VILLANUEVA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.53/32
add comment=FIBER max-limit=200M/200M name="id:659, usuario:MARIA VICTORIA VAL\
    VERDE VALVERDE, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.10/32
add comment=WIRELESS max-limit=20M/20M name="id:660, usuario:Armando Percy Hon\
    orio Baz\C3\A1n, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.9.41/32
add comment=WIRELESS max-limit=200M/200M name="id:661, usuario:Susy Salinas Hu\
    amani, lugar:la villa, plan:basico, tipo:FIBER" target=192.168.93.209/32
add comment=FIBER max-limit=200M/200M name="id:663, usuario:Hugo Cirilo Pinedo\
    \_Avila, lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.209/32
add comment=WIRELESS max-limit=30M/30M name="id:665, usuario:Herny Rolando Car\
    rillo Diestra, lugar:san jeronimo, plan:wireless 70 , tipo:WIRELESS" \
    target=192.168.93.82/32
add comment=FIBER max-limit=200M/200M name="id:666, usuario:Jose Antonio Blas \
    Malvaceda, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.232/32
add comment=FIBER max-limit=200M/200M name="id:667, usuario:Luz Elizabeth Chir\
    oque Pareja, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.12/32
add comment=WIRELESS max-limit=25M/25M name="id:668, usuario:ROCIO SHIRLEY HUR\
    TADO ALEJO, lugar:la villa, plan:wireless 60 , tipo:WIRELESS" target=\
    192.168.25.53/32
add comment=FIBER max-limit=1k/1k name="id:671, usuario:PAOLA PATRICIA SANCHEZ\
    \_HUERTA, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.25.116/32
add comment=FIBER max-limit=200M/200M name="id:672, usuario:JUSTINA FAUSTINA V\
    ELASQUEZ ASENCIOS, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FI\
    BER" target=192.168.25.104/32
add comment=FIBER max-limit=200M/200M name="id:673, usuario:ERNESTA UTRILLA PI\
    NEDA DE CANOVA, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER\
    " target=192.168.25.113/32
add comment=FIBER max-limit=200M/200M name="id:674, usuario:Roc\C3\ADo del Pil\
    ar Sirlupu Lavado KOLKY, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, t\
    ipo:FIBER" target=192.168.25.166/32
add comment=WIRELESS max-limit=20M/20M name="id:675, usuario:DANIEL SAAVEDRA C\
    HINO, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.20.24/32
add comment=FIBER max-limit=200M/200M name="id:676, usuario:Elmer  Tito Tocas \
    Vasquez, lugar:pampa yaro, nap:ML-0-1, plan:basico_wireless 50, tipo:WIREL\
    ESS" target=192.168.22.14/32
add comment=FIBER max-limit=200M/200M name="id:678, usuario:Fredy Yvan Caceres\
    \_Perez casa, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.227/32
add comment=FIBER max-limit=200M/200M name="id:680, usuario:NIVARDO MELVIN AMA\
    DO PAUCAR, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.136/32
add comment=FIBER max-limit=300M/300M name="id:681, usuario:ANDREA JOHANA PALE\
    RMO ALVAREZ, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.26.150/32
add comment=WIRELESS max-limit=20M/20M name="id:682, usuario:HOHBERG RAMIRO PA\
    DILLA HUERTA, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.42/32
add comment=WIRELESS max-limit=200M/200M name="id:683, usuario:JULIO GABRIEL R\
    OJAS ESPINOZA, lugar:la villa, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.200.48/32
add comment=FIBER max-limit=200M/200M name="id:684, usuario:MONICA GOMEZ ROJAS\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.168/32
add comment=WIRELESS max-limit=25M/25M name="id:686, usuario:FIORELLA ELIZABET\
    H BLAS RIVERA, lugar:la villa, plan:wireless 60 , tipo:WIRELESS" target=\
    192.168.25.84/32
add comment=FIBER max-limit=200M/200M name="id:687, usuario:CARLOS ENRIQUE NAP\
    AN ESPILCO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.231/32
add comment=WIRELESS max-limit=20M/20M name="id:688, usuario:Amadeo Huanca Qui\
    spe, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.225/32
add comment=FIBER max-limit=200M/200M name="id:691, usuario:SUSY CRISTHI CACER\
    ES PEREZ, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.166/32
add comment=FIBER max-limit=200M/200M name="id:692, usuario:LIZETH ESTEFANNI D\
    E LA CRUZ FUENTES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.121/32
add comment=WIRELESS max-limit=20M/20M name="id:694, usuario:Celia Angela Bron\
    cano Castillo, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.44/32
add comment=FIBER max-limit=200M/200M name="id:698, usuario:DANIEL JUNIOR ROJA\
    S ESPINOZA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.239/32
add comment=FIBER max-limit=200M/200M name="id:700, usuario:ALEX OMAR NICHO ES\
    COBEDO, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.222/32
add comment=FIBER max-limit=200M/200M name="id:702, usuario:NINFUE ALVARADO CR\
    UZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.32/32
add comment=FIBER max-limit=500M/500M name="id:703, usuario:SARA MARUJA BLAS S\
    OLORZANO, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.88.178/32
add comment=FIBER max-limit=200M/200M name="id:704, usuario:Diana Mautino Poli\
    n, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.165/32
add comment=FIBER max-limit=200M/200M name="id:705, usuario:JOSE PRADA RAMOS, \
    lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.21/32
add comment=FIBER max-limit=200M/200M name="id:706, usuario:KUBIEKI NELSINHO C\
    AQUI QUISPE, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.156/32
add comment=FIBER max-limit=1k/1k name="id:707, usuario:JHUNIOR HUAYANAY GUZMA\
    N, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.39/32
add comment=FIBER max-limit=200M/200M name="id:709, usuario:BERTHA IRLINY VEGA\
    \_ALCEDO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.172/32
add comment=FIBER max-limit=200M/200M name="id:710, usuario:ELVA JULIA LOARTE \
    GONZALES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.154/32
add comment=FIBER max-limit=200M/200M name="id:711, usuario:FLOR MARGARITA SOT\
    O OBREGON, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.127/32
add comment=FIBER max-limit=200M/200M name="id:712, usuario:PAUL ANTONIO NIETO\
    \_DOLORIET, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.243/32
add comment=FIBER max-limit=200M/200M name="id:716, usuario:YOANA YAQUELINE OC\
    HOA DIAZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.146/32
add comment=FIBER max-limit=200M/200M name="id:717, usuario:ANDRE DE LA CRUZ E\
    SPINOZA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.127/32
add comment=FIBER max-limit=200M/200M name="id:719, usuario:ABIGAIL SIFUENTES \
    BLAS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.141/32
add comment=FIBER max-limit=200M/200M name="id:723, usuario:SEGUNDO MANUEL YAM\
    UNAQUE YAMUNAQUE, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.204/32
add comment=FIBER max-limit=200M/200M name="id:726, usuario:FELIX YONATAN MAYO\
    \_MARTINEZ, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.140/32
add comment=WIRELESS max-limit=200M/200M name="id:727, usuario:MIRIAN ROZANA G\
    UERRERO YAURI, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.200.34/32
add comment=FIBER max-limit=200M/200M name="id:729, usuario:ANDRES VEGA DELGAD\
    O, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.44/32
add comment=FIBER max-limit=200M/200M name="id:731, usuario:HILDA VIOLETA DOMI\
    NGUEZ AQUINO, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.231/32
add comment=FIBER max-limit=200M/200M name="id:732, usuario:MARIO ANDRES VILCH\
    EZ VALVERDE, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.117/32
add comment=WIRELESS max-limit=200M/200M name="id:733, usuario:Jhonnel Merlyn \
    Baz\C3\A1n S\C3\A1nchez, lugar:la villa, plan:basico_wireless 50, tipo:WIR\
    ELESS" target=192.168.95.210/32
add comment=FIBER max-limit=200M/200M name="id:734, usuario:Miriam Gissela Mel\
    endez Rosales, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.93.238/32
add comment=FIBER max-limit=200M/200M name="id:738, usuario:DEISY TERESA SIGUE\
    NAS RODRIGUEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.69/32
add comment=FIBER max-limit=200M/200M name="id:739, usuario:Botica  YenniFarma\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.228/32
add comment=WIRELESS max-limit=200M/200M name="id:741, usuario:Ana Lucia Jara \
    Vasquez, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.159/32
add comment=WIRELESS max-limit=20M/20M name="id:742, usuario:YENER YOEL PIZARR\
    O RAMIREZ, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.56/32
add comment=FIBER max-limit=200M/200M name="id:743, usuario:FERRETERIA INDUSTR\
    IA PIZARRO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.88.59/32
add comment=WIRELESS max-limit=200M/200M name="id:744, usuario:JUDITH MIREYA E\
    SPINOZA GUIMAREY, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.54/32
add comment=WIRELESS max-limit=20M/20M name="id:749, usuario:GUADALUPE LURDES \
    BLANCO SALVADOR, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.93.241/32
add comment=WIRELESS max-limit=20M/20M name="id:750, usuario:ROGER MARTIN HUER\
    TA MAMANI, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.25.249/32
add comment=FIBER max-limit=200M/200M name="id:751, usuario:Yoselyn michell Si\
    fuentes Balcazar, lugar:santa anita, nap:ML-0-1, plan:duo_basico 80, tipo:\
    FIBER" target=192.168.26.154/32
add comment=FIBER max-limit=200M/200M name="id:753, usuario:MARISEL CASTILLEJO\
    \_BRIOSO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.20.46/32
add comment=FIBER max-limit=200M/200M name="id:754, usuario:ANA LISBETH LA TOR\
    RE COLLANTES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.124/32
add comment=WIRELESS max-limit=20M/20M name="id:756, usuario:JEAN  LUIS LOPEZ,\
    \_lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.142/32
add comment=FIBER max-limit=200M/200M name="id:759, usuario:Jeiner Rodriguez B\
    enavides, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.85/32
add comment=FIBER max-limit=200M/200M name="id:760, usuario:DINA MORI (TITO AL\
    VARAD), lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.38/32
add comment=WIRELESS max-limit=20M/20M name="id:761, usuario:LIZ MORELIA ROSAL\
    ES AZA\C3\91A, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.49.70/32
add comment=FIBER max-limit=200M/200M name="id:763, usuario:MATEO SIFUENTES JA\
    RAMILLO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.211/32
add comment=FIBER max-limit=200M/200M name="id:764, usuario:Christian Felipe G\
    amarra Escalante, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.18/32
add comment=FIBER max-limit=200M/200M name="id:765, usuario:Christian Felipe G\
    amarra Escalante, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.169/32
add comment=FIBER max-limit=200M/200M name="id:766, usuario:Christian Felipe G\
    amarra Escalante, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.210/32
add comment=FIBER max-limit=200M/200M name="id:767, usuario:EYSER NEHEMIAS CAR\
    RANZA RODRIGUEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.46/32
add comment=FIBER max-limit=200M/200M name="id:769, usuario:ERICK GUSTAVO PORT\
    OCARRERO HOCES, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.9.26/32
add comment=FIBER max-limit=200M/200M name="id:770, usuario:Roberto Carlos Qui\
    nones Ramirez, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.25.96/32
add comment=FIBER max-limit=1k/1k name="id:773, usuario:Juan Manuel Blas Espin\
    oza, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.150/32
add comment=FIBER max-limit=200M/200M name="id:775, usuario:ROXANA ISIDRO CULL\
    A, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.33/32
add comment=FIBER max-limit=200M/200M name="id:777, usuario:MILER OLIVAS OCA\
    \C3\91A, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.229/32
add comment=WIRELESS max-limit=200M/200M name="id:779, usuario:CLINTON JESUS L\
    IMAS BAUTISTA, lugar:La Victoria, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.165/32
add comment=FIBER max-limit=200M/200M name="id:781, usuario:Margiori Tananta R\
    eyna, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.88.197/32
add comment=FIBER max-limit=200M/200M name="id:782, usuario:DELSA VANESSA MEND\
    OZA CRUZ, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.25.186/32
add comment=FIBER max-limit=200M/200M name="id:787, usuario:LUIS ANTHONY TUYA \
    ROSAS, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.208/32
add comment=FIBER max-limit=200M/200M name="id:790, usuario:KELVIN GARCIA CAUR\
    INO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.152/32
add comment=WIRELESS max-limit=200M/200M name="id:791, usuario:VICTOR FLORES S\
    IFUENTES, lugar:la villa, plan:basico, tipo:FIBER" target=\
    192.168.33.34/32
add comment=FIBER max-limit=200M/200M name="id:792, usuario:MARILYN LUZMILA CE\
    RNA  BLAS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.99/32
add comment=WIRELESS max-limit=200M/200M name="id:794, usuario:CRISTOFER ALEJA\
    NDRO DOMINGUEZ MENDOZA, lugar:luvio, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.22.38/32
add comment=FIBER max-limit=300M/300M name="id:795, usuario:MARIBEL DELINA BLA\
    S RAMIREZ, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.26.204/32
add comment=FIBER max-limit=200M/200M name="id:796, usuario:EVELYN CALIXTO HUA\
    NCA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.165/32
add comment=FIBER max-limit=200M/200M name="id:797, usuario:JAIR RENZO ZU\C3\
    \91IGA CHAVEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.41/32
add comment=WIRELESS max-limit=300M/300M name="id:798, usuario:THALIA ELIZABET\
    H CHALAN BAUTISTA, lugar:la villa, plan:Internet70, tipo:FIBER" target=\
    192.168.26.236/32
add comment=FIBER max-limit=200M/200M name="id:799, usuario:MARCO ANTONIO PAUC\
    AR SIGUE\C3\91AZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.147/32
add comment=FIBER max-limit=200M/200M name="id:800, usuario:MIGUEL ANGEL TEODO\
    RO QUISPE SIGUE\C3\91AS 2, lugar:la villa, nap:ML-0-1, plan:basico, tipo:F\
    IBER" target=192.168.25.177/32
add comment=FIBER max-limit=300M/300M name="id:802, usuario:LILY MARITZA ANAYA\
    \_GARAY, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.126/32
add comment=FIBER max-limit=200M/200M name="id:803, usuario:MIGUEL ALBERTO LEO\
    N OYOLA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.170/32
add comment=FIBER max-limit=200M/200M name="id:804, usuario:DEYSI AMERICA DELG\
    ADO CAMPOS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.190/32
add comment=FIBER max-limit=200M/200M name="id:805, usuario:KATERINE YANINA DE\
    LGADO CAMPOS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.116/32
add comment=FIBER max-limit=200M/200M name="id:807, usuario:LUIS ALBERTO TARAZ\
    ONA TOLEDO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.86/32
add comment=WIRELESS max-limit=200M/200M name="id:808, usuario:MARICRUZ DEL AN\
    GEL SALDA\C3\91A SOLIS, lugar:san jeronimo, plan:basico_wireless 50, tipo:\
    WIRELESS" target=192.168.26.33/32
add comment=WIRELESS max-limit=1k/1k name="id:809, usuario:Graciela Yomira Ten\
    orio la Torre, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.123/32
add comment=FIBER max-limit=200M/200M name="id:811, usuario:MARIA ELENA DOMING\
    UEZ ESPINOZA, lugar:tiwinza, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.244/32
add comment=FIBER max-limit=200M/200M name="id:812, usuario:BETSABET NACENSIA \
    ESPINOZA TORRES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.35/32
add comment=WIRELESS max-limit=20M/20M name="id:814, usuario:Gregorio Rafael A\
    guirre rivera, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.88/32
add comment=FIBER max-limit=200M/200M name="id:815, usuario:Beker Pax Isidro c\
    ulla, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.196/32
add comment=FIBER max-limit=200M/200M name="id:816, usuario:JESSICA ROXANA MOR\
    Y PAJUELO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.103/32
add comment=FIBER max-limit=200M/200M name="id:817, usuario:JENNIFER ELIANA PA\
    JUELO BLAS, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.173/32
add comment=WIRELESS max-limit=20M/20M name="id:819, usuario:NAYELI MIRELLA VI\
    LLAREAL VILLANUEVA, lugar:las piedras, plan:basico_wireless 50, tipo:WIREL\
    ESS" target=192.168.22.65/32
add comment=FIBER max-limit=300M/300M name="id:822, usuario:Maricielo Zarzosa \
    Rufino, lugar:la ensenada, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.25.218/32
add comment=FIBER max-limit=200M/200M name="id:823, usuario:DANIEL CARLOS REYE\
    S HILARIO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.191/32
add comment=WIRELESS max-limit=200M/200M name="id:824, usuario:ANDERSON ALONSO\
    \_QUISPE, lugar:La Victoria, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.140/32
add comment=WIRELESS max-limit=20M/20M name="id:825, usuario:Carlos Daniel Guz\
    man Pardo, lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.95.43/32
add comment=WIRELESS max-limit=200M/200M name="id:829, usuario:COMERCIAL DELCY\
    , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.87/32
add comment=WIRELESS max-limit=200M/200M name="id:830, usuario:MAURY PAOLY DIO\
    NICIO VERDE, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.19/32
add comment=WIRELESS max-limit=200M/200M name="id:831, usuario:YAMILE SOLANGE \
    PASCACIO TAMAYO, lugar:san pedro, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.34/32
add comment=FIBER max-limit=200M/200M name="id:832, usuario:AYRTON BRAYAN RUPP\
    \_ALVARADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.114/32
add comment=FIBER max-limit=200M/200M name="id:833, usuario:TATIANA LORENA COR\
    TEZ VILLANUEVA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.225/32
add comment=FIBER max-limit=200M/200M name="id:835, usuario:KEVIN DIEGO MONZON\
    \_ORBEGOSO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.36/32
add comment=FIBER max-limit=200M/200M name="id:836, usuario:JONNY PEDRO BLAS D\
    IONICIO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.211/32
add comment=FIBER max-limit=200M/200M name="id:837, usuario:ELIONI MEJIA COLCH\
    ADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.98/32
add comment=WIRELESS max-limit=20M/20M name="id:838, usuario:yordani FERNANDEZ\
    \_ACUNA, lugar:La Victoria, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.215/32
add comment=WIRELESS max-limit=20M/20M name="id:840, usuario:Patricia Nora Tru\
    jillo Vela, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.169.22.170/32
add comment=FIBER max-limit=200M/200M name="id:841, usuario:Percy  Franshescol\
    y Torres  Loayza, lugar:roy martin, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.93.110/32
add comment=FIBER max-limit=200M/200M name="id:843, usuario:LEONEL BENIGNO CAS\
    TILLO YOVERA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.143/32
add comment=FIBER max-limit=200M/200M name="id:846, usuario:JEHINER GUEVARA RA\
    MOS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.68/32
add comment=WIRELESS max-limit=20M/20M name="id:847, usuario:JAIR ROBERTO SOTO\
    \_MARTINEZ, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.150/32
add comment=WIRELESS max-limit=20M/20M name="id:851, usuario:KEYBI LEIDY DOMIN\
    GUEZ JACINTO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.88.166/32
add comment=FIBER max-limit=1k/1k name="id:853, usuario:MARY MAR ORTIZ PALACIO\
    S, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.25.106/32
add comment=FIBER max-limit=200M/200M name="id:854, usuario:JEAN CARLOS GRANDA\
    \_FLORES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.112/32
add comment=FIBER max-limit=200M/200M name="id:856, usuario:DARWIN - BEKER VAR\
    GAS HURTADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.60/32
add comment=WIRELESS max-limit=20M/20M name="id:857, usuario:Waldir Alvarado H\
    errera, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.26.23/32
add comment=FIBER max-limit=200M/200M name="id:859, usuario:YOHAN RIGOBERTO MO\
    GOLLON VERDE, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.9.30/32
add comment=WIRELESS max-limit=20M/20M name="id:862, usuario:Jorge Luis More F\
    lores, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.33/32
add comment=FIBER max-limit=200M/200M name="id:865, usuario:MARCO ANTONIO NIET\
    O DOLORIET, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.198/32
add comment=FIBER max-limit=1k/1k name="id:867, usuario:VICTORIA FLOIDA SAAVED\
    RA MU\C3\91OZ, lugar:la villa, nap:ML-0-1, plan:cable_basico, tipo:ONLY_TV\
    _FIBER" target=192.168.26.214/32
add comment=WIRELESS max-limit=200M/200M name="id:869, usuario:EUSTAQUIO TRUJI\
    LLO PANTOJA, lugar:santa constansa, plan:basico, tipo:FIBER" target=\
    192.168.22.8/32
add comment=WIRELESS max-limit=20M/20M name="id:872, usuario:NELSON ALVARADO C\
    RUZ, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.122/32
add comment=FIBER max-limit=200M/200M name="id:873, usuario:CARLOS ROCA, lugar\
    :la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.75/32
add comment=FIBER max-limit=200M/200M name="id:875, usuario:ALICIA ROJAS ESPIN\
    OZA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.62/32
add comment=WIRELESS max-limit=20M/20M name="id:876, usuario:TEODOMIRO RAUL SA\
    LAS OBREGON, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.25.205/32
add comment=FIBER max-limit=200M/200M name="id:877, usuario:YULISA MATOS CASTI\
    LLO, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.55/32
add comment=WIRELESS max-limit=20M/20M name="id:878, usuario:VENER JOSUE ALVAR\
    EZ VICTORIO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.16/32
add comment=FIBER max-limit=200M/200M name="id:879, usuario:MABEL TENORIO LA T\
    ORRE, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.221/32
add comment=FIBER max-limit=200M/200M name="id:882, usuario:ALEJANDRINO BLAS C\
    HAVEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.124/32
add comment=FIBER max-limit=200M/200M name="id:883, usuario:ANALI CANTARO, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.39/32
add comment=FIBER max-limit=200M/200M name="id:887, usuario:BEATRIZ TRUJILLO, \
    lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.128/32
add comment=FIBER max-limit=200M/200M name="id:890, usuario:YOVANA RAMOS POLIN\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.27/32
add comment=FIBER max-limit=200M/200M name="id:891, usuario:Wilber Bazan Melga\
    rejo, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.215/32
add comment=FIBER max-limit=200M/200M name="id:892, usuario:JUAN ROJAS ZELAYA,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.191/32
add comment=FIBER max-limit=200M/200M name="id:893, usuario:ELIZABET JARA BRIO\
    SO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.113/32
add comment=WIRELESS max-limit=20M/20M name="id:895, usuario:LUCAS VILELA, lug\
    ar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.33.28/32
add comment=WIRELESS max-limit=200M/200M name="id:896, usuario:Gregorio  TRUJI\
    LLO PANTOJA, lugar:santa constansa, plan:basico, tipo:FIBER" target=\
    192.168.93.177/32
add comment=FIBER max-limit=200M/200M name="id:897, usuario:FUNDO ROMERO, luga\
    r:luvio, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.93.54/32
add comment=FIBER max-limit=200M/200M name="id:904, usuario:VIVIANA KARINA ALV\
    A FLORES, lugar:santa constansa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.93.212/32
add comment=FIBER max-limit=200M/200M name="id:906, usuario:ABELIZ DIAZ, lugar\
    :casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.122/32
add comment=FIBER max-limit=300M/300M name="id:908, usuario:KELY NEYDA ANAYA O\
    BREGON, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.26.149/32
add comment=WIRELESS max-limit=20M/20M name="id:909, usuario:EDGAR CASTRO RIPA\
    LDO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.62/32
add comment=FIBER max-limit=200M/200M name="id:911, usuario:VICTOR HUAMAN MARI\
    NO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.54/32
add comment=WIRELESS max-limit=20M/20M name="id:913, usuario:EMER OMAR ANAYA, \
    lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.26.182/32
add comment=FIBER max-limit=200M/200M name="id:915, usuario:MARCIAL CHUQUINO C\
    RUZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.35/32
add comment=WIRELESS max-limit=20M/20M name="id:916, usuario:RICARDO CARRILLO \
    DIESTRA, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.221/32
add comment=WIRELESS max-limit=20M/20M name="id:919, usuario:FUNDO BUENA VISTA\
    , lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.220/32
add comment=WIRELESS max-limit=20M/20M name="id:922, usuario:ROVINSON GAVINO Z\
    ORRILLA, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.49.202/32
add comment=WIRELESS max-limit=20M/20M name="id:924, usuario:DIANA GONZALES, l\
    ugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.88.150/32
add comment=FIBER max-limit=300M/300M name="id:926, usuario:HYLTTON MIGUEL SUA\
    REZ NONATO, lugar:roy martin, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.88.200/32
add comment=FIBER max-limit=1k/1k name="id:927, usuario:ANA SOSA GUADALUPE, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.196/32
add comment=FIBER max-limit=200M/200M name="id:931, usuario:FELIPE PE\C3\91A, \
    lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.219/32
add comment=FIBER max-limit=200M/200M name="id:932, usuario:LEONARDO ZORRILLA,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.97/32
add comment=FIBER max-limit=200M/200M name="id:933, usuario:LIVIA MARUJA MONTE\
    S JARA, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.200.45/32
add comment=FIBER max-limit=200M/200M name="id:934, usuario:AURELIA LUIS INGA,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.123/32
add comment=FIBER max-limit=300M/300M name="id:937, usuario:LUIS WILFREDO CHUN\
    GA MENDOZA, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.26.202/32
add comment=WIRELESS max-limit=200M/200M name="id:938, usuario:GREGORIA PELAYA\
    \_CASTILLO MATTA, lugar:la villa, plan:basico, tipo:FIBER" target=\
    192.168.26.171/32
add comment=FIBER max-limit=200M/200M name="id:939, usuario:bonifacia ayala, l\
    ugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.175/32
add comment=WIRELESS max-limit=200M/200M name="id:940, usuario:WILMER OCA\C3\
    \91A DIAZ, lugar:la villa, plan:basico, tipo:FIBER" target=\
    192.168.22.36/32
add comment=FIBER max-limit=20M/20M name="id:941, usuario:ANIBAL LUCAS DIAZ MA\
    TOS, lugar:casa blanca, nap:ML-0-1, plan:basico_wireless 50, tipo:WIRELESS\
    " target=192.168.93.214/32
add comment=WIRELESS max-limit=200M/200M name="id:942, usuario:JACKY PEREZ AGE\
    NTE, lugar:la villa, plan:basico, tipo:FIBER" target=192.168.93.243/32
add comment=FIBER max-limit=200M/200M name="id:943, usuario:MARY AMANCIO PEREZ\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.240/32
add comment=WIRELESS max-limit=20M/20M name="id:944, usuario:MARIELA MONIER ME\
    NDOZA FLORES, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.26.152/32
add comment=FIBER max-limit=200M/200M name="id:947, usuario:edelmira  asunci\
    \C3\B3n orbegoso, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.158/32
add comment=WIRELESS max-limit=20M/20M name="id:948, usuario:FUNDO ROY MARTIN,\
    \_lugar:roy martin, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.106/32
add comment=WIRELESS max-limit=200M/200M name="id:950, usuario:CHIFA JACKY CHA\
    NG, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.33.14/32
add comment=WIRELESS max-limit=20M/20M name="id:953, usuario:ALDO ALONSO QUISP\
    E, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.158/32
add comment=FIBER max-limit=300M/300M name="id:959, usuario:FERNANDO QUIROZ MU\
    \C3\91OZ, lugar:la villa, nap:LV -3-1, plan:Internet70, tipo:FIBER" \
    target=192.168.210.56/32
add comment=FIBER max-limit=200M/200M name="id:965, usuario:RAUL HUATA agro me\
    danos villa, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.121/32
add comment=WIRELESS max-limit=20M/20M name="id:967, usuario:MERCEDES ALVA FLO\
    RES, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.153/32
add comment=FIBER max-limit=200M/200M name="id:968, usuario:JACKY PEREZ CASA, \
    lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.242/32
add comment=FIBER max-limit=200M/200M name="id:970, usuario:BOTICA MARTIN, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.139/32
add comment=FIBER max-limit=200M/200M name="id:971, usuario:DEYVIS GARCIA TOCA\
    S, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.110/32
add comment=FIBER max-limit=200M/200M name="id:972, usuario:Vicenta Paulina es\
    tetica vega de valverde, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIB\
    ER" target=192.168.26.147/32
add comment=FIBER max-limit=200M/200M name="id:974, usuario:CLAUDIA SOFIA POMA\
    \_CACHIQUE, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.220/32
add comment=WIRELESS max-limit=20M/20M name="id:975, usuario:JUAN CARLOS MAZA \
    OJEDA, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.26.130/32
add comment=WIRELESS max-limit=20M/20M name="id:976, usuario:JULIO RICHARD PAI\
    SIC ROJAS, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.11/32
add comment=WIRELESS max-limit=20M/20M name="id:977, usuario:ALEXIS GINO CHAVE\
    Z TRUJILLO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.245/32
add comment=FIBER max-limit=200M/200M name="id:979, usuario:ESBILDA JIMENA ALV\
    AREZ CABELLO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.136/32
add comment=FIBER max-limit=200M/200M name="id:980, usuario:ANA MARIA DAVILA S\
    ANCHEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.159/32
add comment=WIRELESS max-limit=20M/20M name="id:982, usuario:JULIA ANAYA, luga\
    r:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.26.119/32
add comment=WIRELESS max-limit=20M/20M name="id:983, usuario:JACK ALDAIR VERDE\
    \_RAMIREZ, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.211/32
add comment=FIBER max-limit=200M/200M name="id:985, usuario:ZULMA FABIAN VILLA\
    NUEVA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.216/32
add comment=FIBER max-limit=200M/200M name="id:986, usuario:JUAN CARLOS TARAZO\
    NA BRAVO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.134/32
add comment=FIBER max-limit=200M/200M name="id:987, usuario:SONIA EDMUNDO, lug\
    ar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.101/32
add comment=FIBER max-limit=200M/200M name="id:988, usuario:YOVER ESPINOZA, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.88.58/32
add comment=WIRELESS max-limit=20M/20M name="id:996, usuario:Lizet Dora Carbaj\
    al Castillo, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.9.46/32
add comment=FIBER max-limit=200M/200M name="id:1001, usuario:ROSMERY TAIS RODR\
    IGUEZ CORONADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.31/32
add comment=FIBER max-limit=200M/200M name="id:1004, usuario:JESUS ESTEBAN GUA\
    RDIA MENTANZA, lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.95.21/32
add comment=FIBER max-limit=200M/200M name="id:1005, usuario:EMILIANO CUYA, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.187/32
add comment=WIRELESS max-limit=30M/30M name="id:1007, usuario:Fundo escorpio  \
    CACHORRO, lugar:la villa, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.22.154/32
add comment=FIBER max-limit=200M/200M name="id:1008, usuario:ANIBAL ARANA SAND\
    ON, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.233/32
add comment=FIBER max-limit=200M/200M name="id:1013, usuario:CHIFA DIDI, lugar\
    :la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.115/32
add comment=FIBER max-limit=200M/200M name="id:1015, usuario:MARIELA TRUJILLO \
    SANCHEZ, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.214/32
add comment=FIBER max-limit=200M/200M name="id:1017, usuario:FLOR BELLA FLORES\
    \_LEIVA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.73/32
add comment=FIBER max-limit=200M/200M name="id:1018, usuario:OSMAN JAIME RIVER\
    A LINO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.40/32
add comment=FIBER max-limit=200M/200M name="id:1021, usuario:RUBEL INGA CAQUI,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.100/32
add comment=WIRELESS max-limit=200M/200M name="id:1025, usuario:JEFRI ARQUINIG\
    O, lugar:santa constansa, plan:basico, tipo:FIBER" target=\
    192.168.93.196/32
add comment=WIRELESS max-limit=20M/20M name="id:1027, usuario:ROSANA DIANA BRO\
    NCANO CASTILLO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS\
    " target=192.168.93.185/32
add comment=FIBER max-limit=200M/200M name="id:1028, usuario:JUAN PABLO JARA R\
    AMOS, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.235/32
add comment=WIRELESS max-limit=200M/200M name="id:1029, usuario:ALISON MELENDE\
    Z, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.135/32
add comment=WIRELESS max-limit=20M/20M name="id:1030, usuario:JUAN ALBERTO HUE\
    RTAS CASTRO, lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.25.241/32
add comment=FIBER max-limit=200M/200M name="id:1033, usuario:VIT CLAVI SANTIES\
    TEBAN ANTONIO, lugar:las colinas, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.90/32
add comment=FIBER max-limit=200M/200M name="id:1034, usuario:MOTO SERVICIO CAS\
    TILLO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.166/32
add comment=WIRELESS max-limit=200M/200M name="id:1035, usuario:Flor  CUEVA Li\
    vias, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.10/32
add comment=WIRELESS max-limit=1k/1k name="id:1036, usuario:CESAR gomez rumay,\
    \_lugar:la ensenada, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.49.197/32
add comment=FIBER max-limit=200M/200M name="id:1037, usuario:CORSO ROSALES, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.209/32
add comment=FIBER max-limit=200M/200M name="id:1038, usuario:ALDO JAIME PEREZ \
    \_FLORES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.123/32
add comment=FIBER max-limit=200M/200M name="id:1039, usuario:YITO MANRIQUE, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.25/32
add comment=WIRELESS max-limit=200M/200M name="id:1041, usuario:HUGO SHUAN, lu\
    gar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.26.245/32
add comment=FIBER max-limit=200M/200M name="id:1042, usuario:IAN TAPIA, lugar:\
    la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.142/32
add comment=FIBER max-limit=200M/200M name="id:1043, usuario:MARIA VARGAS, lug\
    ar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.135/32
add comment=FIBER max-limit=200M/200M name="id:1045, usuario:ARIANA LOZANO, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.203/32
add comment=WIRELESS max-limit=200M/200M name="id:1048, usuario:CARLOS CASTILL\
    O, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.25.173/32
add comment=WIRELESS max-limit=200M/200M name="id:1051, usuario:SANDRA SOLIS, \
    lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.135/32
add comment=FIBER max-limit=200M/200M name="id:1052, usuario:ROY PORTAL, lugar\
    :la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.26.111/32
add comment=FIBER max-limit=200M/200M name="id:1054, usuario:HEBERT TORIBIO JI\
    MENEZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.138/32
add comment=FIBER max-limit=200M/200M name="id:1055, usuario:EDGAR CASTRO RIPA\
    LDO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.193/32
add comment=WIRELESS max-limit=300M/300M name="id:1056, usuario:ALEX HUACCHO, \
    lugar:la villa, plan:wireless 70 , tipo:WIRELESS" target=192.168.88.9/32
add comment=WIRELESS max-limit=200M/200M name="id:1060, usuario:ENRIQUE RAMIRE\
    Z, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.95.232/32
add comment=FIBER max-limit=200M/200M name="id:1061, usuario:EDUARDO ESPINOZA,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.79/32
add comment=FIBER max-limit=200M/200M name="id:1064, usuario:ABRAM CHAVEZ LINC\
    OL, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.194/32
add comment=FIBER max-limit=200M/200M name="id:1065, usuario:Santa rios JULCA,\
    \_lugar:tiwinza, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.25.235/32
add comment=FIBER max-limit=200M/200M name="id:1066, usuario:MANUEL NICOLAS AQ\
    UINO JAIME, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.183/32
add comment=FIBER max-limit=200M/200M name="id:1068, usuario:FELA ALARCON, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.163/32
add comment=FIBER max-limit=300M/300M name="id:1070, usuario:JAIRO FASANANDO T\
    APULLINA, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.22.141/32
add comment=FIBER max-limit=300M/300M name="id:1071, usuario:VALERIO JEREMIAS \
    VALENZUELA GUILLEN, lugar:luvio, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.25.212/32
add comment=FIBER max-limit=200M/200M name="id:1073, usuario:JOSE CARCAMO, lug\
    ar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.234/32
add comment=FIBER max-limit=200M/200M name="id:1074, usuario:VICTOR LUIS OBREG\
    ON CAQUE, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.157/32
add comment=WIRELESS max-limit=20M/20M name="id:1075, usuario:LUZMILA EDITH OR\
    TIZ ESPINOZA, lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.161/32
add comment=FIBER max-limit=200M/200M name="id:1076, usuario:MIJAEL EDGAR GARC\
    IA ROJAS, lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.250/32
add comment=WIRELESS max-limit=20M/20M name="id:1078, usuario:LUIS DAMIAN ESPI\
    NOZA RUFINO, lugar:san roberto, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.95.39/32
add comment=FIBER max-limit=200M/200M name="id:1083, usuario:OSCAR COTRINA CAU\
    RINO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.34/32
add comment=FIBER max-limit=200M/200M name="id:1084, usuario:BOTICA PITER, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.212/32
add comment=WIRELESS max-limit=20M/20M name="id:1085, usuario:HIPOLITA Sifuent\
    es , lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.99/32
add comment=FIBER max-limit=200M/200M name="id:1091, usuario:EVELIN GARCIA, lu\
    gar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.217/32
add comment=FIBER max-limit=200M/200M name="id:1092, usuario:JORGE ORTEGA CUST\
    ODIO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.170/32
add comment=FIBER max-limit=200M/200M name="id:1094, usuario:ELIZABETH SIGUE\
    \C3\91AS ROSALES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.22/32
add comment=FIBER max-limit=200M/200M name="id:1095, usuario:FIORELA DECENA AG\
    UILAR, lugar:la ensenada, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.25.216/32
add comment=FIBER max-limit=200M/200M name="id:1096, usuario:BONIFACIO ESPINOZ\
    A, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.22/32
add comment=FIBER max-limit=200M/200M name="id:1097, usuario:JAVIER CARRILLO, \
    lugar:san jeronimo, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.200.11/32
add comment=FIBER max-limit=200M/200M name="id:1098, usuario:TADEO QUINO, luga\
    r:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.120/32
add comment=FIBER max-limit=200M/200M name="id:1100, usuario:JUAN CARLOS RUIZ,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.119/32
add comment=FIBER max-limit=200M/200M name="id:1101, usuario:JESUS ALBERTO SAL\
    DA\C3\91A GUTIERREZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.63/32
add comment=WIRELESS max-limit=200M/200M name="id:1105, usuario:edilberto vari\
    llas, lugar:luvio, plan:basico, tipo:FIBER" target=192.168.26.188/32
add comment=FIBER max-limit=300M/300M name="id:1106, usuario:ANDRES ALBERTO CA\
    URINO, lugar:9 de octubre, nap:ML-0-1, plan:Duo100, tipo:FIBER" target=\
    192.168.9.37/32
add comment=WIRELESS max-limit=20M/20M name="id:1107, usuario:YUSMEDI IBARRA P\
    ETIT, lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.213/32
add comment=FIBER max-limit=200M/200M name="id:1110, usuario:JHONATAN ABRAHAM \
    TINOCO RUPAY, lugar:casa blanca, nap:ML-0-1, plan:duo_basico 80, tipo:FIBE\
    R" target=192.168.26.48/32
add comment=FIBER max-limit=200M/200M name="id:1111, usuario:LUCILA VELASQUEZ,\
    \_lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.67/32
add comment=FIBER max-limit=200M/200M name="id:1112, usuario:LUPE SARA GUTIERR\
    EZ UGARTE, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.25.122/32
add comment=FIBER max-limit=200M/200M name="id:1113, usuario:ELSA CORONEL, lug\
    ar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.230/32
add comment=FIBER max-limit=200M/200M name="id:1114, usuario:JAVIER JACINTO MI\
    LLA  ESPINOZA, lugar:santa anita, nap:ML-0-1, plan:duo_basico 80, tipo:FIB\
    ER" target=192.168.26.44/32
add comment=FIBER max-limit=200M/200M name="id:1116, usuario:MINIMARKET DE TOD\
    O, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.213/32
add comment=FIBER max-limit=200M/200M name="id:1117, usuario:TEOFILA LAINI PAL\
    OMINO, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.246/32
add comment=FIBER max-limit=400M/400M name="id:1118, usuario:JUAN MANUEL LLANO\
    S Fdo Hilarion, lugar:casa blanca, nap:ML-0-1, plan:Internet100, tipo:FIBE\
    R" target=192.168.22.139/32
add comment=FIBER max-limit=200M/200M name="id:1121, usuario:YANINA OLGA HURTA\
    DO SALAZAR, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.134/32
add comment=WIRELESS max-limit=300M/300M name=ensenada target=\
    192.168.93.69/32
add comment=FIBER max-limit=200M/200M name="id:1123, usuario:WALTER PINEDO, lu\
    gar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.252/32
add comment=WIRELESS max-limit=20M/20M name="id:1124, usuario:CARLOS ADALBERTO\
    \_CALDAS COLLAS, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.22.121/32
add comment=WIRELESS max-limit=200M/200M name="id:1125, usuario:GERAL CASTRO, \
    lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.88.195/32
add comment=FIBER max-limit=200M/200M name="id:1128, usuario:MAIKOL BUSTAMANTE\
    \_FRIAS, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.200/32
add comment=FIBER max-limit=200M/200M name="id:1129, usuario:LUIS CARLOS TOCAS\
    \_VASQUEZ, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.200.7/32
add comment=FIBER max-limit=200M/200M name="id:1130, usuario:NOLAN ALVAREZ CAB\
    ELLO, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" target=\
    192.168.200.17/32
add comment=FIBER max-limit=200M/200M name="id:1131, usuario:NOMBERTO VITALIAN\
    O CASANOVA SAENZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.60/32
add comment=FIBER max-limit=200M/200M name="id:1132, usuario:LUIS ANTONIO MORA\
    LES CADILLO, lugar:la ensenada, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER\
    " target=192.168.200.12/32
add comment=WIRELESS max-limit=20M/20M name="id:1133, usuario:FILFREDO JAIME F\
    ERNANDEZ ACUNA, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.155/32
add comment=FIBER max-limit=200M/200M name="id:1136, usuario:Alex gomez cerna,\
    \_lugar:casa blanca, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.200.36/32
add comment=WIRELESS max-limit=200M/200M name="id:1139, usuario:LUIS ANICETO M\
    ALLQUI, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.19/32
add comment=WIRELESS max-limit=20M/20M name="id:1142, usuario:AMERICO SAUCEDO,\
    \_lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.88.160/32
add comment=FIBER max-limit=200M/200M name="id:1143, usuario:POLINARIO CAURINO\
    \_MELGAREJO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.56/32
add comment=WIRELESS max-limit=20M/20M name="id:1144, usuario:DAVID GUARDIA TR\
    EJO, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.9.51/32
add comment=FIBER max-limit=200M/200M name="id:1146, usuario:JENNER SAAVEDRA T\
    ENORIO, lugar:la ensenada, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.42/32
add comment=FIBER max-limit=200M/200M name="id:1147, usuario:NANDO AMADOR BAUT\
    ISTA LEON, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.71/32
add comment=FIBER max-limit=200M/200M name="id:1148, usuario:CALA ORDINOLA, lu\
    gar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.95.51/32
add comment=FIBER max-limit=200M/200M name="id:1150, usuario:Irma Jovita barre\
    ra de la cruz, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.200.43/32
add comment=FIBER max-limit=200M/200M name="id:1151, usuario:JIANMARCO HILARIO\
    \_ROJAS RIMARACHIN, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.200.44/32
add comment=FIBER max-limit=200M/200M name="id:1153, usuario:MARIA DE LOS ANGE\
    LES SAENZ LOAYSA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.22.82/32
add comment=FIBER max-limit=200M/200M name="id:1156, usuario:JEANCARLOS WALDIR\
    \_MONTES BAZAN, lugar:la ensenada, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.25.237/32
add comment=FIBER max-limit=300M/300M name="id:1162, usuario:GRIFO SANTA CLARA\
    \_MIRIAM ZAMUDIO, lugar:la villa, nap:ML-0-1, plan:Internet70, tipo:FIBER" \
    target=192.168.25.246/32
add comment=FIBER max-limit=1k/1k name="id:1166, usuario:Yesenia RAMIREZ, luga\
    r:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.26.37/32
add comment=FIBER max-limit=200M/200M name="id:1167, usuario:JEAN CARLOS SOTO \
    DIAZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.36/32
add comment=WIRELESS max-limit=200M/200M name="id:1169, usuario:Junior RIVERO,\
    \_lugar:san pedro, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.48/32
add comment=FIBER max-limit=200M/200M name="id:1174, usuario:JHOSTIN QUISPE GO\
    NZALES, lugar:santa anita, nap:SA -9-2, plan:basico, tipo:FIBER" target=\
    192.168.210.2/32
add comment=FIBER max-limit=200M/200M name="id:1177, usuario:Nely Moreno Pined\
    o, lugar:luvio, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.5/32
add comment=FIBER max-limit=200M/200M name="id:1186, usuario:epifania gambini \
    lopez, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.62/32
add comment=FIBER max-limit=200M/200M name="id:1188, usuario:Arnaldo Principe \
    Fermin, lugar:las colinas, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.11/32
add comment=FIBER max-limit=200M/200M name="id:1192, usuario:luci evangelista \
    cruz, lugar:la villa, nap:LV -5-8, plan:basico, tipo:FIBER" target=\
    192.168.210.13/32
add comment=WIRELESS max-limit=20M/20M name="id:1194, usuario:melisa karolyne \
    andaque chanduvi, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.15/32
add comment=FIBER max-limit=200M/200M name="id:1196, usuario:juan chavez blas,\
    \_lugar:la villa, nap:LV -5-7, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.17/32
add comment=FIBER max-limit=1k/1k name="id:1197, usuario:ever loarte gonzales,\
    \_lugar:la villa, nap:LV -5-7, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.18/32
add comment=FIBER max-limit=200M/200M name="id:1198, usuario:basilio avelino t\
    oribio principe, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.210.19/32
add comment=FIBER max-limit=200M/200M name="id:1199, usuario:maria elena ojeda\
    \_jimenez de huincho, lugar:la villa, nap:LV -3-5, plan:duo_basico 80, tip\
    o:FIBER" target=192.168.210.20/32
add comment=FIBER max-limit=1k/1k name="id:1204, usuario:gladys rios murrugarr\
    a, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.63/32
add comment=WIRELESS max-limit=20M/20M name="id:1205, usuario:yolvi ader garay\
    \_oncoy, lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.143/32
add comment=FIBER max-limit=200M/200M name="id:1206, usuario:Katherine toscano\
    \_garcia, lugar:9 de octubre, nap:, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.21/32
add comment=FIBER max-limit=200M/200M name="id:1210, usuario:YARDENY MARGARITA\
    \_MINELLI SANCHEZ JARA, lugar:la villa, nap:ML-0-2, plan:basico, tipo:FIBE\
    R" target=192.168.210.23/32
add comment=FIBER max-limit=200M/200M name="id:1211, usuario:karla Vanessa cac\
    eres reyes, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.24/32
add comment=FIBER max-limit=200M/200M name="id:1223, usuario:augusto valverde \
    jimenez, lugar:la villa, nap:LV -5-8, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.27/32
add comment=FIBER max-limit=300M/300M name="id:1224, usuario:fundo roy martin \
    oficina principal Roy martin, lugar:roy martin, nap:ML-0-1, plan:Internet7\
    0, tipo:FIBER" target=192.168.210.28/32
add comment=FIBER max-limit=200M/200M name="id:1234, usuario:Julia  Garcia Dam\
    ian, lugar:la villa, nap:LV -5-8, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.32/32
add comment=WIRELESS max-limit=20M/20M name="id:1240, usuario:Roger agurto pri\
    ncipe, lugar:san pedro, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.35/32
add comment=FIBER max-limit=200M/200M name="id:1243, usuario:MARJHORY (vilma) \
    RAMIREZ AMADO, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.210.36/32
add comment=WIRELESS max-limit=20M/20M name="id:1246, usuario:livia castillo s\
    ifuentes, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.39/32
add comment=WIRELESS max-limit=20M/20M name="id:1248, usuario:raul gargate cas\
    tillo, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.41/32
add comment=WIRELESS max-limit=200M/200M name="id:1249, usuario:DILVER OSWALDO\
    \_AGUILAR CASTILLO, lugar:santa anita, plan:basico_wireless 50, tipo:WIREL\
    ESS" target=192.168.210.42/32
add comment=WIRELESS max-limit=200M/200M name="id:1250, usuario:Jessica ariza \
    miguel, lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.43/32
add comment=FIBER max-limit=200M/200M name="id:1253, usuario:henry ba\C3\B1ez \
    calderon, lugar:la villa, nap:LV -5-3, plan:basico, tipo:FIBER" target=\
    192.168.210.45/32
add comment=FIBER max-limit=200M/200M name="id:1254, usuario:pablo medina rami\
    rez, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.46/32
add comment=FIBER max-limit=200M/200M name="id:1258, usuario:yelsin pedro Diaz\
    \_alvarez, lugar:la villa, nap:LV -5-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.50/32
add comment=FIBER max-limit=200M/200M name="id:1259, usuario:cristian meza Rod\
    riguez, lugar:la villa, nap:LV -1-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.51/32
add comment=WIRELESS max-limit=20M/20M name="id:1260, usuario:EDELMIRA FILOMEN\
    A GAMARRA RIVERA, lugar:La Victoria, plan:basico_wireless 50, tipo:WIRELES\
    S" target=192.168.210.52/32
add comment=WIRELESS max-limit=30M/30M name="id:1261, usuario:diego franco mon\
    tes, lugar:san jeronimo, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.210.53/32
add comment=FIBER max-limit=200M/200M name="id:1262, usuario:miriam minchola C\
    alderon, lugar:la villa, nap:LV -2-4, plan:basico, tipo:FIBER" target=\
    192.168.210.54/32
add comment=FIBER max-limit=200M/200M name="id:1269, usuario:David Oscata Guti\
    errez, lugar:la villa, nap:LV -2-7, plan:basico, tipo:FIBER" target=\
    192.168.210.61/32
add comment=FIBER max-limit=200M/200M name="id:1270, usuario:violeta maribel r\
    imac espinoza, lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" \
    target=192.168.210.64/32
add comment=FIBER max-limit=200M/200M name="id:1273, usuario:Maria Fernanda Es\
    cobal Silva, lugar:la villa, nap:LV -5-5, plan:basico, tipo:FIBER" \
    target=192.168.210.68/32
add comment=FIBER max-limit=200M/200M name="id:1283, usuario:yeni paucar espin\
    oza, lugar:tiwinza, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.71/32
add comment=FIBER max-limit=200M/200M name="id:1284, usuario:tania cesy laguna\
    \_albornoz, lugar:la villa, nap:ML-0-7, plan:basico, tipo:FIBER" target=\
    192.168.210.72/32
add comment=FIBER max-limit=200M/200M name="id:1289, usuario:carmen delgado ch\
    inchay, lugar:roy martin, nap:LB -7-4, plan:basico, tipo:FIBER" target=\
    192.168.210.77/32
add comment=FIBER max-limit=200M/200M name="id:1291, usuario:maycoll Chirre ca\
    cha, lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.79/32
add comment=WIRELESS max-limit=20M/20M name="id:1292, usuario:Vilma Pacherres \
    Escobedo, lugar:luvio, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.80/32
add comment=FIBER max-limit=200M/200M name="id:1294, usuario:kelly  sante macu\
    ri, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.81/32
add comment=WIRELESS max-limit=200M/200M name="id:1299, usuario:AGROPECUARIA G\
    ONZALES E.I.R.L. , lugar:la villa, plan:INTERNET 60, tipo:FIBER" target=\
    192.168.25.52/32
add comment=FIBER max-limit=200M/200M name="id:1300, usuario:MOLINO EL KRIADOR\
    \_, lugar:la villa, nap:LV -2-6, plan:basico, tipo:FIBER" target=\
    192.168.26.180/32
add comment=FIBER max-limit=200M/200M name="id:1301, usuario:AGROASESORES S.A.\
    C. , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.25.118/32
add comment=FIBER max-limit=200M/200M name="id:1302, usuario:HOGAR INDUSTRIAL \
    ALIMENTARIA E.I.R.L. Hinali, lugar:la villa, nap:, plan:basico, tipo:FIBER\
    " target=192.168.26.199/32
add comment=FIBER max-limit=200M/200M name="id:1303, usuario:WASSER SYSTEM S.A\
    .C , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.25.236/32
add comment=FIBER max-limit=200M/200M name="id:1305, usuario:AGRICOLA M Y C / \
    AGRIFRUT   , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.22.42/32
add comment=WIRELESS max-limit=200M/200M name="id:1307, usuario:agronegocios p\
    ara todos LOS CAMPOS , lugar:la villa, plan:basico, tipo:FIBER" target=\
    192.168.88.60/32
add comment=WIRELESS max-limit=40M/40M name="id:1308, usuario:FRUTAS DEL SUR ,\
    \_lugar:luvio, plan:EMPRESA 767, tipo:WIRELESS" target=192.168.88.25/32
add comment=FIBER max-limit=200M/200M name="id:1309, usuario:FUNDO SAN MICHELE\
    \_, lugar:luvio, nap:, plan:basico, tipo:FIBER" target=192.168.26.49/32
add comment=WIRELESS max-limit=20M/20M name="id:1313, usuario:GABRIELA QUISPE \
    NAVARRO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.101/32
add comment=FIBER max-limit=200M/200M name="id:1342, usuario:Neiva patricia Bl\
    as mautino, lugar:la villa, nap:ML-0-4, plan:basico, tipo:FIBER" target=\
    192.168.210.83/32
add comment=FIBER max-limit=200M/200M name="id:1345, usuario:isaura  solano es\
    pinoza, lugar:la ensenada, nap:EN -8-6, plan:basico, tipo:FIBER" target=\
    192.168.210.10/32
add comment=FIBER max-limit=200M/200M name="id:1346, usuario:zacarias more cov\
    eas, lugar:la villa, nap:LV -3-3, plan:basico, tipo:FIBER" target=\
    192.168.210.66/32
add comment=FIBER max-limit=200M/200M name="id:1347, usuario:liz miriam herrer\
    a zapata , lugar:la villa, nap:LV -2-5, plan:basico, tipo:FIBER" target=\
    192.168.210.84/32
add comment=FIBER max-limit=200M/200M name="id:1348, usuario:alejandro rimac l\
    eon , lugar:la villa, nap:LV -5-8, plan:basico, tipo:FIBER" target=\
    192.168.210.85/32
add comment=FIBER max-limit=200M/200M name="id:1349, usuario:moises Valverde V\
    ara, lugar:la villa, nap:LV -4-2, plan:basico, tipo:FIBER" target=\
    192.168.210.86/32
add comment=WIRELESS max-limit=20M/20M name="id:1357, usuario:pedro perez pala\
    cios , lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.94/32
add comment=FIBER max-limit=200M/200M name="id:1358, usuario:talia tania corpu\
    s robles, lugar:casa blanca, nap:CB -12-1, plan:basico, tipo:FIBER" \
    target=192.168.210.95/32
add comment=FIBER max-limit=200M/200M name="id:1362, usuario:Jean Carlos Carra\
    sco Sanchez, lugar:la ensenada, nap:SA -9-2, plan:basico, tipo:FIBER" \
    target=192.168.26.24/32
add comment=FIBER max-limit=200M/200M name="id:1364, usuario:Raul Espinoza Jar\
    amillo, lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.200.41/32
add comment=WIRELESS max-limit=20M/20M name="id:1384, usuario:cesar cristian  \
    osorio perez, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.82/32
add comment=FIBER max-limit=200M/200M name="id:1386, usuario:jose luis carbaja\
    l lucero, lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.99/32
add comment=FIBER max-limit=200M/200M name="id:1388, usuario:milagros  Lopez q\
    uepque, lugar:casa blanca, nap:CB -11-6, plan:basico, tipo:FIBER" target=\
    192.168.210.101/32
add comment=FIBER max-limit=200M/200M name="id:1391, usuario:marco loarte rima\
    c, lugar:la villa, nap:LV -4-2, plan:basico, tipo:FIBER" target=\
    192.168.210.104/32
add comment=FIBER max-limit=200M/200M name="id:1392, usuario:carlos romero car\
    bajal , lugar:luvio, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.210.105/32
add comment=FIBER max-limit=200M/200M name="id:1393, usuario:denies  sanchez a\
    gurto, lugar:la villa, nap:LV -2-5, plan:basico, tipo:FIBER" target=\
    192.168.210.106/32
add comment=FIBER max-limit=200M/200M name="id:1394, usuario:miguel angel chuq\
    uiyauri perez , lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" \
    target=192.168.210.107/32
add comment=WIRELESS max-limit=1k/1k name="id:1398, usuario:nolberta ibarra ve\
    ga , lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.210.111/32
add comment=FIBER max-limit=1k/1k name="id:1401, usuario:EULOGIO JORGE HIRLAND\
    O ROBERT, lugar:la villa, nap:, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.210.114/32
add comment=FIBER max-limit=200M/200M name="id:1402, usuario:juan  salda\C3\B1\
    a morales, lugar:la villa, nap:LV -6-8, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.115/32
add comment=FIBER max-limit=200M/200M name="id:1403, usuario:Rosnely vargas or\
    begoso , lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" target=\
    192.168.210.116/32
add comment=FIBER max-limit=300M/300M name="id:1409, usuario:Cyntia Karina ara\
    nda palomares , lugar:la villa, nap:LV -2-5, plan:Internet70, tipo:FIBER" \
    target=192.168.210.120/32
add comment=FIBER max-limit=200M/200M name="id:1415, usuario:IRIS NAHOMY ROJAS\
    \_RIVERA, lugar:la villa, nap:LV -6-4, plan:basico, tipo:FIBER" target=\
    192.168.210.122/32
add comment=WIRELESS max-limit=20M/20M name="id:1417, usuario:WALTER ORTIZ BON\
    IFACIO, lugar:tiwinza, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.124/32
add comment=FIBER max-limit=200M/200M name="id:1418, usuario:RICARDO NESTOR RO\
    MERO CURO, lugar:luvio, nap:LB -7-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.125/32
add comment=WIRELESS max-limit=20M/20M name="id:1419, usuario:CELENE MIDORY SE\
    MINARIO VELASQUEZ , lugar:casa blanca, plan:basico_wireless 50, tipo:WIREL\
    ESS" target=192.168.210.126/32
add comment=FIBER max-limit=200M/200M name="id:1420, usuario:yolmin soto villa\
    vicencio , lugar:casa blanca, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.127/32
add comment=FIBER max-limit=200M/200M name="id:1422, usuario:alberto montes or\
    tiz, lugar:la villa, nap:LV -5-2, plan:basico, tipo:FIBER" target=\
    192.168.210.130/32
add comment=FIBER max-limit=200M/200M name="id:1424, usuario:JUAN MARCO VARGAS\
    \_COTRINA , lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" target=\
    192.168.210.132/32
add comment=FIBER max-limit=200M/200M name="id:1427, usuario:Gregorio Rojas, l\
    ugar:la villa, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.210.135/32
add comment=FIBER max-limit=200M/200M name="id:1428, usuario:santosa felicitas\
    \_garcia espada, lugar:la villa, nap:ML-0-6, plan:basico, tipo:FIBER" \
    target=192.168.210.131/32
add comment=FIBER max-limit=200M/200M name="id:1429, usuario:aquiles castillo \
    nu\C3\B1ez, lugar:casa blanca, nap:CB -12-1, plan:basico, tipo:FIBER" \
    target=192.168.210.136/32
add comment=FIBER max-limit=1k/1k name="id:1432, usuario:hector caurino melgar\
    ejo, lugar:la ensenada, nap:EN -8-1, plan:cable_basico, tipo:ONLY_TV_FIBER\
    " target=192.168.210.139/32
add comment=FIBER max-limit=200M/200M name="id:1433, usuario:Posta la villa, l\
    ugar:la villa, nap:LV -6-3, plan:basico, tipo:FIBER" target=\
    192.168.210.140/32
add comment=FIBER max-limit=200M/200M name="id:1434, usuario:ROLY  BENITES VID\
    AL, lugar:la villa, nap:LV -5-7, plan:basico, tipo:FIBER" target=\
    192.168.210.141/32
add comment=FIBER max-limit=200M/200M name="id:1439, usuario:CESAR ANTONIO PAC\
    HECO IRAZABAL, lugar:la villa, nap:LV -6-5, plan:basico, tipo:FIBER" \
    target=192.168.210.145/32
add comment=FIBER max-limit=200M/200M name="id:1441, usuario:alfredo caurino r\
    osales , lugar:la villa, nap:LV -5-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.26.242/32
add comment=FIBER max-limit=200M/200M name="id:1446, usuario:marco cavalcanti \
    , lugar:luvio, nap:LB -7-5, plan:basico, tipo:FIBER" target=\
    192.168.210.152/32
add comment=FIBER max-limit=200M/200M name="id:1447, usuario:RUTH ISABEL GUTIE\
    RREZ PALACIOS , lugar:la villa, nap:LV -5-1, plan:basico, tipo:FIBER" \
    target=192.168.210.153/32
add comment=FIBER max-limit=30M/30M name="id:1448, usuario:FUNDO  SANTA CECILI\
    A, lugar:luvio, nap:LB -7-1, plan:emp_210, tipo:FIBER" target=\
    192.168.210.154/32
add comment=FIBER max-limit=200M/200M name="id:1449, usuario:ROCIO DEL PILAR S\
    IRLUPU LAVADO KOLKY, lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" \
    target=192.168.210.155/32
add comment=FIBER max-limit=200M/200M name="id:1450, usuario:magdalena Leandro\
    \_Osorio, lugar:la villa, nap:LV -6-7, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.157/32
add comment=FIBER max-limit=200M/200M name="id:1452, usuario:mayra  estelle da\
    vila, lugar:la villa, nap:LV -6-2, plan:basico, tipo:FIBER" target=\
    192.168.200.47/32
add comment=FIBER max-limit=200M/200M name="id:1454, usuario:Jaener Herrera Ca\
    stillo, lugar:la villa, nap:LV -5-8, plan:basico, tipo:FIBER" target=\
    192.168.210.161/32
add comment=FIBER max-limit=400M/400M name="id:1457, usuario:JOSEPH ABRAHAN MO\
    RALES BRAVO, lugar:9 de octubre, nap:, plan:Internet100, tipo:FIBER" \
    target=192.168.210.163/32
add comment=FIBER max-limit=200M/200M name="id:1458, usuario:JAVIER ARMAS GONZ\
    ALES, lugar:la ensenada, nap:EN -8-6, plan:basico, tipo:FIBER" target=\
    192.168.25.248/32
add comment=FIBER max-limit=200M/200M name="id:1459, usuario:Benjamin ramos ca\
    qui, lugar:casa blanca, nap:CB -11-3, plan:basico, tipo:FIBER" target=\
    192.168.25.224/32
add comment=FIBER max-limit=200M/200M name="id:1465, usuario:Akila lucila Loar\
    te Leon, lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.168/32
add comment=FIBER max-limit=200M/200M name="id:1466, usuario:Liliana  Ochoa Di\
    az, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.210.169/32
add comment=FIBER max-limit=200M/200M name="id:1467, usuario:emer gantu villan\
    ueva, lugar:la villa, nap:LV -5-8, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.170/32
add comment=FIBER max-limit=200M/200M name="id:1469, usuario:ricardo  gutierre\
    z vargas, lugar:la villa, nap:LV -6-1, plan:basico, tipo:FIBER" target=\
    192.168.210.172/32
add comment=FIBER max-limit=200M/200M name="id:1471, usuario:Nelson Garcia Liv\
    ias, lugar:san jeronimo, nap:SJ -4-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.174/32
add comment=WIRELESS max-limit=20M/20M name="id:1473, usuario:lida isidro obre\
    gon , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.176/32
add comment=FIBER max-limit=200M/200M name="id:1475, usuario:dina  ramirez oso\
    rio, lugar:santa anita, nap:SA -9-2, plan:basico, tipo:FIBER" target=\
    192.168.210.178/32
add comment=FIBER max-limit=300M/300M name="id:1476, usuario:DUILER alvarez lo\
    yola, lugar:la villa, nap:ML-0-5, plan:Internet70, tipo:FIBER" target=\
    192.168.26.67/32
add comment=WIRELESS max-limit=200M/200M name="id:1480, usuario:eliankar chave\
    z cotrina , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.182/32
add comment=FIBER max-limit=150M/150M name="id:1482, usuario:RICARDO COLEGIO G\
    UTIERREZ VARGAS , lugar:la villa, nap:LV -6-1, plan:PLAN COLEGIO, tipo:FIB\
    ER" target=192.168.210.183/32
add comment=FIBER max-limit=200M/200M name="id:1485, usuario:clever garcia sig\
    ue\C3\B1as, lugar:la villa, nap:LV -4-5, plan:basico, tipo:FIBER" target=\
    192.168.210.186/32
add comment=FIBER max-limit=200M/200M name="id:1488, usuario:Porfirio Teodoro \
    Benavides Chusden, lugar:la villa, nap:LV -15-6, plan:basico, tipo:FIBER" \
    target=192.168.210.188/32
add comment=WIRELESS max-limit=20M/20M name="id:1490, usuario:susan de la cruz\
    \_rojas , lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.190/32
add comment=WIRELESS max-limit=30M/30M name="id:1491, usuario:ALDO CAMAIORA IT\
    URRIAGA puquial, lugar:9 de octubre, plan:PUQUIAL, tipo:WIRELESS" target=\
    192.168.210.191/32
add comment=FIBER max-limit=200M/200M name="id:1499, usuario:andre  Sigue\F1as\
    \_rojas KOLKY, lugar:la villa, nap:ML-0-7, plan:basico, tipo:FIBER" \
    target=192.168.210.196/32
add comment=FIBER max-limit=200M/200M name="id:1501, usuario:magaly  cespedes \
    chaupis, lugar:la villa, nap:LV -3-6, plan:basico, tipo:FIBER" target=\
    192.168.210.198/32
add comment=FIBER max-limit=200M/200M name="id:1507, usuario:mirian  vega vela\
    squez , lugar:la villa, nap:LV -1-6, plan:basico, tipo:FIBER" target=\
    192.168.210.142/32
add comment=WIRELESS max-limit=20M/20M name="id:1508, usuario:dina pinedo sali\
    na, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.204/32
add comment=WIRELESS max-limit=20M/20M name="id:1510, usuario:keni dominguez ,\
    \_lugar:pampa yaro, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.205/32
add comment=FIBER max-limit=200M/200M name="id:1513, usuario:italo  obregon ra\
    mirez , lugar:la villa, nap:LV -3-5, plan:basico, tipo:FIBER" target=\
    192.168.210.207/32
add comment=FIBER max-limit=200M/200M name="id:1514, usuario:gabriel narcizo  \
    mu\C3\B1oz villanueva, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBE\
    R" target=192.168.210.208/32
add comment=FIBER max-limit=200M/200M name="id:1515, usuario:yarko jhonelo sol\
    ano tadeo, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.210.209/32
add comment=FIBER max-limit=200M/200M name="id:1516, usuario:paola padilla tor\
    res, lugar:casa blanca, nap:CB -12-1, plan:basico, tipo:FIBER" target=\
    192.168.210.210/32
add comment=FIBER max-limit=200M/200M name="id:1517, usuario:elias julian gonz\
    ales lopez, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.210.211/32
add comment=FIBER max-limit=200M/200M name="id:1518, usuario:KATHERIN  CHANDUV\
    I SOSA, lugar:luvio, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.212/32
add comment=FIBER max-limit=200M/200M name="id:1520, usuario:lisbet pajuelo du\
    rand, lugar:tiwinza, nap:TW -13-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.214/32
add comment=WIRELESS max-limit=30M/30M name="id:1535, usuario:JORGE CRISTIANO \
    RAMIREZ COTRINA, lugar:san jeronimo, plan:wireless 70 , tipo:WIRELESS" \
    target=192.168.210.221/32
add comment=FIBER max-limit=300M/300M name="id:1538, usuario:EFRAIN ROBERTO HU\
    ARANCA CHOQUE, lugar:la villa, nap:LV -5-1, plan:Internet70, tipo:FIBER" \
    target=192.168.210.224/32
add comment=WIRELESS max-limit=20M/20M name="id:1543, usuario:JAVIER PABLO ADA\
    N CAJALEON, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.228/32
add comment=FIBER max-limit=1k/1k name="id:1550, usuario:Evelyn karina Quijano\
    \_Arana, lugar:santa anita, nap:SA -9-2, plan:basico, tipo:FIBER" target=\
    192.168.210.235/32
add comment=FIBER max-limit=200M/200M name="id:1551, usuario:juan diaz culla, \
    lugar:la villa, nap:LV -1-2, plan:basico, tipo:FIBER" target=\
    192.168.210.236/32
add comment=WIRELESS max-limit=20M/20M name="id:1552, usuario:Raul  Diaz Veram\
    endi, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.237/32
add comment=FIBER max-limit=300M/300M name="id:1555, usuario:Carlos  Romero Ca\
    rbajal, lugar:la villa, nap:LV -2-4, plan:Internet100, tipo:FIBER" \
    target=192.168.210.240/32
add comment=FIBER max-limit=200M/200M name="id:1557, usuario:luis requelme  ti\
    tulas paula sangama, lugar:la ensenada, nap:LV -15-3, plan:basico, tipo:FI\
    BER" target=192.168.210.242/32
add comment=FIBER max-limit=200M/200M name="id:1558, usuario:CATALINA ANAYA GU\
    ERRA, lugar:la villa, nap:LV -15-1, plan:basico, tipo:FIBER" target=\
    192.168.210.241/32
add comment=FIBER max-limit=200M/200M name="id:1559, usuario:ERICK ESTIVEN  ES\
    TELLE BRIGIDO, lugar:la villa, nap:LV -5-3, plan:basico, tipo:FIBER" \
    target=192.168.210.244/32
add comment=FIBER max-limit=200M/200M name="id:1560, usuario:JORGE ANTONIO  PA\
    JUELO QUIROZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.210.245/32
add comment=FIBER max-limit=200M/200M name="id:1564, usuario:Fabiana Meza Caqu\
    i, lugar:la villa, nap:LV -15-2, plan:basico, tipo:FIBER" target=\
    192.168.210.248/32
add comment=FIBER max-limit=200M/200M name="id:1568, usuario:Iberon  Oca\C3\B1\
    a Caqui, lugar:la villa, nap:LV -15-3, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.253/32
add comment=FIBER max-limit=200M/200M name="id:1569, usuario:BRYAM JEANPIER MO\
    RALES BUSTAMANTE , lugar:9 de octubre, nap:, plan:duo_basico 80, tipo:FIBE\
    R" target=192.168.210.251/32
add comment=FIBER max-limit=200M/200M name="id:1570, usuario:Frank Antony Naop\
    ari Parco, lugar:la villa, nap:LV -5-7, plan:basico, tipo:FIBER" target=\
    192.168.210.254/32
add comment=WIRELESS max-limit=15M/30M name="id:1571, usuario:LUIS ANGEL PALAC\
    IOS HUERTA, lugar:DESCONOCIDO, plan:wireless 100, tipo:WIRELESS" target=\
    192.168.210.252/32
add comment=FIBER max-limit=200M/200M name="id:1572, usuario:Juan  Ramirez Mu\
    \C3\B1oz , lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.25.41/32
add comment=FIBER max-limit=200M/200M name="id:1578, usuario:Carmen Rosa Sainz\
    \_Dominguez, lugar:la villa, nap:LV -6-1, plan:basico, tipo:FIBER" \
    target=192.168.22.160/32
add comment=FIBER max-limit=200M/200M name="id:1579, usuario:Luz Angelica Marc\
    elo Valenzuela , lugar:la villa, nap:LV -2-6, plan:basico, tipo:FIBER" \
    target=192.168.22.161/32
add comment=FIBER max-limit=200M/200M name="id:1580, usuario:MIGUEL ANGEL HERN\
    ANDEZ GUEDEZ, lugar:la villa, nap:LV -4-1, plan:basico, tipo:FIBER" \
    target=192.168.211.36/32
add comment=FIBER max-limit=200M/200M name="id:1586, usuario:Estrella Aquino J\
    aime, lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.22.165/32
add comment=FIBER max-limit=1k/1k name="id:1601, usuario:ESTEFANI AYALA MONTES\
    INO, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.22.20/32
add comment=WIRELESS max-limit=20M/20M name="id:1602, usuario:LIZ SAYURI CARLO\
    S CERNA, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.166/32
add comment=WIRELESS max-limit=20M/20M name="id:1603, usuario:Flor Yasmin Carl\
    os Cerna, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.22.168/32
add comment=FIBER max-limit=200M/200M name="id:1605, usuario:Karina  Hinostroz\
    a Veliz, lugar:la villa, nap:LV -1-7, plan:basico, tipo:FIBER" target=\
    192.168.22.221/32
add comment=WIRELESS max-limit=200M/200M name="id:1606, usuario:Alder Amancio \
    Paulino, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.22.164/32
add comment=FIBER max-limit=200M/200M name="id:1609, usuario:Walter  Rojas Vil\
    ladeza , lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.22.170/32
add comment=WIRELESS max-limit=20M/20M name="id:1612, usuario:ROXANA ROSALES C\
    RUZ, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.75/32
add comment=FIBER max-limit=200M/200M name="id:1615, usuario:Yofre  Parioma Ja\
    rpi, lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" target=\
    192.168.22.18/32
add comment=FIBER max-limit=200M/200M name="id:1616, usuario:RUPERTA  PINEDA M\
    ALLQUI, lugar:santa anita, nap:SA -9-3, plan:basico, tipo:FIBER" target=\
    192.168.22.132/32
add comment=FIBER max-limit=200M/200M name="id:1619, usuario:CELINA  AYALA FLO\
    RES, lugar:la villa, nap:LV -1-6, plan:basico, tipo:FIBER" target=\
    192.168.22.133/32
add comment=FIBER max-limit=200M/200M name="id:1621, usuario:ANIBAL  TRUJILLO \
    PADILLA, lugar:la villa, nap:LV -15-2, plan:basico, tipo:FIBER" target=\
    192.168.211.10/32
add comment=WIRELESS max-limit=200M/200M name="id:1623, usuario:MARCO  LOARTE \
    RIMAC, lugar:santa anita, plan:basico, tipo:FIBER" target=\
    192.168.211.11/32
add comment=FIBER max-limit=200M/200M name="id:1624, usuario:MANUEL  CAURINO M\
    ELGAREJO, lugar:la villa, nap:LV -1-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.12/32
add comment=FIBER max-limit=200M/200M name="id:1626, usuario:Ronel  Pardo Torr\
    es , lugar:la villa, nap:LV -15-6, plan:basico, tipo:FIBER" target=\
    192.168.211.14/32
add comment=FIBER max-limit=200M/200M name="id:1627, usuario:Omar Blas Loarte,\
    \_lugar:la villa, nap:LV -6-7, plan:basico, tipo:FIBER" target=\
    192.168.211.15/32
add comment=FIBER max-limit=200M/200M name="id:1628, usuario:Botica  Mary Fem,\
    \_lugar:la villa, nap:LV -1-1, plan:basico, tipo:FIBER" target=\
    192.168.211.16/32
add comment=FIBER max-limit=200M/200M name="id:1629, usuario:Valentin Quispe C\
    ardenas, lugar:casa blanca, nap:CB -12-3, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.17/32
add comment=WIRELESS max-limit=200M/200M name="id:1632, usuario:Yelitsa Utrill\
    a Obregon , lugar:la ensenada, plan:basico, tipo:FIBER" target=\
    192.168.211.20/32
add comment=FIBER max-limit=200M/200M name="id:1633, usuario:Arminda Del Carme\
    n Angulo Perdomo, lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" \
    target=192.168.211.21/32
add comment=FIBER max-limit=400M/400M name="id:1634, usuario:JUAN RENZO HILARI\
    O PLAZA AUTOSERVICIO, lugar:la villa, nap:LV -2-5, plan:PLAZA AUTOSERVICIO\
    , tipo:FIBER" target=192.168.211.22/32
add comment=FIBER max-limit=200M/200M name="id:1646, usuario:AZUCENA SUSY GANT\
    U VILLANUEVA , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.30/32
add comment=FIBER max-limit=200M/200M name="id:1647, usuario:GAVI DANIELA  GAR\
    AY FLORES , lugar:casa blanca, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.31/32
add comment=FIBER max-limit=1k/1k name="id:1648, usuario:SANTIGO ZENOBIO  DAMI\
    AN CAURINO, lugar:la villa, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.32/32
add comment=FIBER max-limit=200M/200M name="id:1653, usuario:MARIA  COLLANTES \
    FLORES , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.33/32
add comment=FIBER max-limit=200M/200M name="id:1656, usuario:MANUELA  SILVA PA\
    NAIFO , lugar:tiwinza, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.34/32
add comment=FIBER max-limit=200M/200M name="id:1657, usuario:CARLOS  ROSALES E\
    SPINOZA, lugar:tiwinza, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.35/32
add comment=FIBER max-limit=200M/200M name="id:1660, usuario:EDINSON HERRERA S\
    OLANO, lugar:la villa, nap:LV -15-2, plan:basico, tipo:FIBER" target=\
    192.168.211.37/32
add comment=FIBER max-limit=1k/1k name="id:1663, usuario:vilma Alvarez cabello\
    , lugar:la villa, nap:LV -15-6, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.40/32
add comment=WIRELESS max-limit=20M/20M name="id:1665, usuario:lucy briceno  ce\
    ruche , lugar:casa blanca, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.211.41/32
add comment=FIBER max-limit=200M/200M name="id:1667, usuario:ARISTOFANES  SOTO\
    \_CESPEDES , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.43/32
add comment=FIBER max-limit=200M/200M name="id:1668, usuario:SOFIA  CHIROQUE L\
    OPEZ, lugar:san bosco, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.44/32
add comment=FIBER max-limit=200M/200M name="id:1670, usuario:CESAR BLAS SIGUEN\
    AS, lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.46/32
add comment=FIBER max-limit=200M/200M name="id:1672, usuario:pedro daniel jara\
    \_zorrilla, lugar:tiwinza, nap:TW -13-5, plan:basico, tipo:FIBER" target=\
    192.168.211.48/32
add comment=FIBER max-limit=200M/200M name="id:1676, usuario:Andres caurino cu\
    lla, lugar:la villa, nap:LV -1-3, plan:basico, tipo:FIBER" target=\
    192.168.211.52/32
add comment=FIBER max-limit=200M/200M name="id:1677, usuario:roberto villanuev\
    a viera, lugar:la villa, nap:LV -6-4, plan:basico, tipo:FIBER" target=\
    192.168.211.53/32
add comment=FIBER max-limit=200M/200M name="id:1679, usuario:ROBERTO VILLANUEV\
    A VIERA, lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" target=\
    192.168.211.55/32
add comment=WIRELESS max-limit=1k/1k name="id:1681, usuario:NOLBERTA IBARRA VE\
    GA, lugar:la villa, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.57/32
add comment=FIBER max-limit=200M/200M name="id:1682, usuario:jose luis  guille\
    n espinoza, lugar:la villa, nap:LV -2-8, plan:basico, tipo:FIBER" target=\
    192.168.211.58/32
add comment=FIBER max-limit=200M/200M name="id:1683, usuario:ronel sifuentes a\
    randa, lugar:la villa, nap:LV -15-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.59/32
add comment=WIRELESS max-limit=20M/20M name="id:1686, usuario:AIDE AZANERO VAS\
    QUEZ , lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.62/32
add comment=FIBER max-limit=200M/200M name="id:1687, usuario:yover edison  cha\
    ves ortis , lugar:la villa, nap:LV -5-5, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.63/32
add comment=FIBER max-limit=200M/200M name="id:1695, usuario:camila  savedra m\
    onrroy, lugar:9 de octubre, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.64/32
add comment=FIBER max-limit=200M/200M name="id:1696, usuario:susy  salinas hua\
    mani , lugar:la villa, nap:LV -6-7, plan:basico, tipo:FIBER" target=\
    192.168.211.65/32
add comment=FIBER max-limit=200M/200M name="id:1697, usuario:jesus quiros lope\
    s, lugar:la villa, nap:LV -15-4, plan:basico, tipo:FIBER" target=\
    192.168.211.66/32
add comment=FIBER max-limit=1k/1k name="id:1698, usuario:jorge  Quijano , luga\
    r:la villa, nap:LV -2-6, plan:cable_basico, tipo:ONLY_TV_FIBER" target=\
    192.168.211.67/32
add comment=FIBER max-limit=200M/200M name="id:1699, usuario:Antonela  Ramos A\
    rtiaga, lugar:la villa, nap:LV -15-5, plan:basico, tipo:FIBER" target=\
    192.168.211.68/32
add comment=FIBER max-limit=200M/200M name="id:1701, usuario:NICANOR ALEJANDRO\
    \_MESA URBE, lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.69/32
add comment=FIBER max-limit=200M/200M name="id:1702, usuario:HOHBERG RAMIRO PA\
    DILLA HUERTA, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" \
    target=192.168.211.70/32
add comment=WIRELESS max-limit=200M/200M name="id:1705, usuario:BETSI ISIDRO O\
    BREGON, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.72/32
add comment=FIBER max-limit=200M/200M name="id:1706, usuario:ELMER NAVARRO CON\
    DOR , lugar:santa anita, nap:SA -9-6, plan:basico, tipo:FIBER" target=\
    192.168.211.73/32
add comment=FIBER max-limit=200M/200M name="id:1707, usuario:Ademir Juarez Car\
    ranza, lugar:la villa, nap:LV -2-7, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.74/32
add comment=WIRELESS max-limit=20M/20M name="id:1719, usuario:Adelina  Izquier\
    do Matoz, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.77/32
add comment=WIRELESS max-limit=200M/200M name="id:1721, usuario:Norma Laurente\
    \_Toribio, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.211.78/32
add comment=FIBER max-limit=300M/300M name="id:1724, usuario:ARON EGUIZABAL LI\
    MAS, lugar:la villa, nap:, plan:Internet70, tipo:FIBER" target=\
    192.168.211.79/32
add comment=FIBER max-limit=200M/200M name="id:1725, usuario:OSCAR  CHAVEZ VEG\
    A, lugar:la ensenada, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.76/32
add comment=FIBER max-limit=200M/200M name="id:1726, usuario:Jose Ballejo Rami\
    rez, lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.80/32
add comment=WIRELESS max-limit=20M/20M name="id:1731, usuario:Alberto principe\
    \_berrospi, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.211.85/32
add comment=FIBER max-limit=200M/200M name="id:1732, usuario:rocio del pilar  \
    sirlupu  lavado, lugar:la villa, nap:LV -2-6, plan:basico, tipo:FIBER" \
    target=192.168.211.86/32
add comment=WIRELESS max-limit=200M/200M name="id:1733, usuario:YOSELIN ROSARI\
    O CELEDONIO BRIGIDO , lugar:santa anita, plan:basico_wireless 50, tipo:WIR\
    ELESS" target=192.168.211.87/32
add comment=FIBER max-limit=1k/1k name="id:1734, usuario:Sonia Caldas Paulino,\
    \_lugar:la villa, nap:LV -5-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.88/32
add comment=FIBER max-limit=200M/200M name="id:1735, usuario:MARI JENNY ROSALE\
    S CRUZ casa, lugar:la villa, nap:ML-0-5, plan:basico, tipo:FIBER" target=\
    192.168.211.89/32
add comment=FIBER max-limit=200M/200M name="id:1736, usuario:HUMBERTO ELISEO G\
    OMEZ COTRINA, lugar:la villa, nap:LV -4-5, plan:basico, tipo:FIBER" \
    target=192.168.211.90/32
add comment=FIBER max-limit=1k/1k name="id:1737, usuario:ISABEL CASTRO CALLE ,\
    \_lugar:la villa, nap:, plan:basico, tipo:FIBER" target=192.168.211.91/32
add comment=WIRELESS max-limit=20M/20M name="id:1739, usuario:JAIME  SAVEDRA M\
    ANTILLA, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.93/32
add comment=FIBER max-limit=200M/200M name="id:1740, usuario:Celestino Monroy \
    Ramirez, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.211.94/32
add comment=FIBER max-limit=200M/200M name="id:1741, usuario:Maria Blas Torres\
    , lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.211.95/32
add comment=FIBER max-limit=200M/200M name="id:1742, usuario:VICTOR HUGO TRUJI\
    LLO QUISPE, lugar:9 de octubre, nap:, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.96/32
add comment=FIBER max-limit=200M/200M name="id:1743, usuario:Cristian Abila Ra\
    mirez, lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.97/32
add comment=FIBER max-limit=200M/200M name="id:1744, usuario:Sergio Figueredo,\
    \_lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.211.98/32
add comment=FIBER max-limit=200M/200M name="id:1745, usuario:Sergio Figueredo,\
    \_lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" target=\
    192.168.211.99/32
add comment=FIBER max-limit=1k/1k name="id:1746, usuario:ESTEVAN JESUS  RAMIRE\
    Z SILVA , lugar:9 de octubre, nap:ML-0-2, plan:cable_basico, tipo:ONLY_TV_\
    FIBER" target=192.168.211.101/32
add comment=FIBER max-limit=200M/200M name="id:1747, usuario:ROMEL CAQUI GAYTA\
    N, lugar:la villa, nap:ML-0-7, plan:basico, tipo:FIBER" target=\
    192.168.211.102/32
add comment=FIBER max-limit=200M/200M name="id:1749, usuario:GISELA CHOTA CHEN\
    CHARI, lugar:la villa, nap:ML-0-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.103/32
add comment=FIBER max-limit=200M/200M name="id:1750, usuario:ABRAHAM MAGENCIO \
    USURIAGA, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.211.104/32
add comment=FIBER max-limit=200M/200M name="id:1751, usuario:VICTOR ALVA PARED\
    ES, lugar:9 de octubre, nap:ML-0-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.105/32
add comment=FIBER max-limit=200M/200M name="id:1752, usuario:YOSELYN DURAND CE\
    DANO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.211.106/32
add comment=FIBER max-limit=200M/200M name="id:1754, usuario:ELENA POLET ESTEL\
    LE DAVILA, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.211.108/32
add comment=FIBER max-limit=200M/200M name="id:1755, usuario:BELEN ARQUINIGO R\
    OSAS, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.211.109/32
add comment=FIBER max-limit=200M/200M name="id:1757, usuario:MARIA GARCIA TIMA\
    NA, lugar:la villa, nap:LV -6-2, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.110/32
add comment=FIBER max-limit=200M/200M name="id:1758, usuario:CILOS MARTIN MILL\
    A ESPINOZA, lugar:santa anita, nap:SA -9-2, plan:basico, tipo:FIBER" \
    target=192.168.211.111/32
add comment=FIBER max-limit=200M/200M name="id:1759, usuario:jose antonio aren\
    as sarmiento, lugar:la ensenada, nap:EN -8-1, plan:basico, tipo:FIBER" \
    target=192.168.211.112/32
add comment=FIBER max-limit=200M/200M name="id:1760, usuario:JUAN LUGO, lugar:\
    la villa, nap:ML-0-7, plan:basico, tipo:FIBER" target=192.168.211.113/32
add comment=FIBER max-limit=200M/200M name="id:1762, usuario:RICARDO NOEL GARC\
    IA GANTU, lugar:la villa, nap:LV -3-6, plan:basico, tipo:FIBER" target=\
    192.168.211.115/32
add comment=FIBER max-limit=200M/200M name="id:1763, usuario:ALEX GINO CHAVEZ \
    TRUJILLO, lugar:santa constansa, nap:CB -11-7, plan:basico, tipo:FIBER" \
    target=192.168.211.116/32
add comment=FIBER max-limit=200M/200M name="id:1765, usuario:OSMAN RIVERA casa\
    , lugar:la villa, nap:LV -6-2, plan:basico, tipo:FIBER" target=\
    192.168.211.118/32
add comment=FIBER max-limit=200M/200M name="id:1766, usuario:PITER PAUCAR COTR\
    INA, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.211.119/32
add comment=FIBER max-limit=200M/200M name="id:1767, usuario:alex trujillo caq\
    ui, lugar:la villa, nap:LV -1-2, plan:basico, tipo:FIBER" target=\
    192.168.211.120/32
add comment=FIBER max-limit=200M/200M name="id:1768, usuario:JUAN VARGAS COTRI\
    NA DOS, lugar:la villa, nap:ML-0-5, plan:basico, tipo:FIBER" target=\
    192.168.211.121/32
add comment=FIBER max-limit=200M/200M name="id:1769, usuario:ROXANA MINA MATOS\
    , lugar:santa anita, nap:SA -9-2, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.122/32
add comment=FIBER max-limit=200M/200M name="id:1770, usuario:FILMER LARIANCO H\
    ERRERA, lugar:9 de octubre, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.123/32
add comment=FIBER max-limit=200M/200M name="id:1773, usuario:Juliana  Huarac C\
    adillo , lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.211.126/32
add comment=FIBER max-limit=200M/200M name="id:1774, usuario:Cesar  Paredes Fl\
    orencio, lugar:santa anita, nap:SA -9-3, plan:basico, tipo:FIBER" target=\
    192.168.211.127/32
add comment=FIBER max-limit=200M/200M name="id:1775, usuario:MAURO SEVERIANO E\
    CHEBARRIA PAULINO, lugar:la ensenada, nap:EN -8-8, plan:duo_basico 80, tip\
    o:FIBER" target=192.168.211.128/32
add comment=WIRELESS max-limit=200M/200M name="id:1778, usuario:nick Encarnaci\
    on, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.100/32
add comment=FIBER max-limit=200M/200M name="id:1779, usuario:Wiliam  Meza Tara\
    zona , lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.129/32
add comment=FIBER max-limit=200M/200M name="id:1780, usuario:Yoolvi Yordan Vas\
    quez Cabello, lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.130/32
add comment=WIRELESS max-limit=20M/20M name="id:1781, usuario:Saul  Leon Lagun\
    a , lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.131/32
add comment=FIBER max-limit=400M/400M name="id:1782, usuario:Janet Aguirre Car\
    los, lugar:la villa, nap:LV -2-3, plan:Internet100, tipo:FIBER" target=\
    192.168.211.132/32
add comment=FIBER max-limit=200M/200M name="id:1784, usuario:Cesar  Gomez Ruma\
    y , lugar:la villa, nap:LV -5-7, plan:basico, tipo:FIBER" target=\
    192.168.211.134/32
add comment=FIBER max-limit=200M/200M name="id:1785, usuario:Lili Catiri Banez\
    \_, lugar:la villa, nap:LV -3-3, plan:basico, tipo:FIBER" target=\
    192.168.211.135/32
add comment=FIBER max-limit=200M/200M name="id:1787, usuario:Sol Petit Caicedo\
    , lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.211.137/32
add comment=FIBER max-limit=200M/200M name="id:1788, usuario:Yulisa Anali Aran\
    da Rios, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.211.139/32
add comment=FIBER max-limit=200M/200M name="id:1789, usuario:Yaquelin Rojas Ca\
    mpos , lugar:la ensenada, nap:EN -8-5, plan:basico, tipo:FIBER" target=\
    192.168.211.140/32
add comment=FIBER max-limit=200M/200M name="id:1790, usuario:Epifanio Rodrigue\
    z Cusma, lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.141/32
add comment=FIBER max-limit=200M/200M name="id:1792, usuario:Zaida  Gavidia To\
    rres, lugar:la ensenada, nap:EN -8-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.143/32
add comment=FIBER max-limit=300M/300M name="id:1793, usuario:Raul Alberto  Hua\
    ta Zerpa Agro Medanos , lugar:9 de octubre, nap:ML-0-2, plan:Internet70, t\
    ipo:FIBER" target=192.168.211.144/32
add comment=FIBER max-limit=300M/300M name="id:1794, usuario:Noel Hidalgo Rami\
    rez, lugar:la villa, nap:LV -1-5, plan:Internet70, tipo:FIBER" target=\
    192.168.211.145/32
add comment=FIBER max-limit=200M/200M name="id:1795, usuario:Moises  Rimac Leo\
    n , lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.211.146/32
add comment=FIBER max-limit=200M/200M name="id:1796, usuario:Jorge Ramirez Sal\
    as , lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" target=\
    192.168.211.147/32
add comment=WIRELESS max-limit=20M/20M name="id:1797, usuario:Wendy Damian Mau\
    ricio, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.211.148/32
add comment=FIBER max-limit=200M/200M name="id:1800, usuario:Eliseo Herrera Ca\
    mpos, lugar:la villa, nap:LV -5-4, plan:basico, tipo:FIBER" target=\
    192.168.211.149/32
add comment=FIBER max-limit=200M/200M name="id:1801, usuario:YANET MULTISERVIC\
    IOS YANAC MONTES, lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" \
    target=192.168.211.150/32
add comment=FIBER max-limit=200M/200M name="id:1803, usuario:Karina Pari Izagu\
    irre, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.211.152/32
add comment=FIBER max-limit=200M/200M name="id:1804, usuario:Gladys Sanchez Me\
    ndez, lugar:casa blanca, nap:CB -12-1, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.153/32
add comment=FIBER max-limit=1k/1k name="id:1806, usuario:Victor Espinoza Flore\
    s , lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.155/32
add comment=FIBER max-limit=200M/200M name="id:1807, usuario:Maximo Durand Flo\
    res, lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.156/32
add comment=FIBER max-limit=200M/200M name="id:1809, usuario:Edit  Ureta Rojas\
    , lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.211.158/32
add comment=FIBER max-limit=200M/200M name="id:1810, usuario:Jhordy Felix Caqu\
    i, lugar:la villa, nap:LV -5-7, plan:Internet70, tipo:FIBER" target=\
    192.168.211.159/32
add comment=FIBER max-limit=200M/200M name="id:1814, usuario:abel urbano  lean\
    o, lugar:la merced, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.161/32
add comment=FIBER max-limit=20M/45M name="id:1815, usuario:Fundo  pamajosa san\
    ta anita, lugar:santa anita, nap:ML-0-1, plan:wireless 150, tipo:WIRELESS" \
    target=192.168.211.162/32
add comment=FIBER max-limit=200M/200M name="id:1816, usuario:Zenon  saavedra v\
    elasquez, lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.163/32
add comment=FIBER max-limit=200M/200M name="id:1817, usuario:Yostin Casemiro C\
    orpus, lugar:la ensenada, nap:EN -8-5, plan:basico, tipo:FIBER" target=\
    192.168.211.164/32
add comment=FIBER max-limit=1k/1k name="id:1820, usuario:Yoana Velasquez Moral\
    es, lugar:la merced, nap:LB -7-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.165/32
add comment=FIBER max-limit=200M/200M name="id:1821, usuario:ely quinones verd\
    e, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.8/32
add comment=FIBER max-limit=200M/200M name="id:1822, usuario:Ageo Quinones Ver\
    de, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.211.39/32
add comment=FIBER max-limit=200M/200M name="id:1824, usuario:LUIS ALBERTO LUCA\
    S EGUSQUIZA, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.10/32
add comment=FIBER max-limit=200M/200M name="id:1826, usuario:Jose Vasquez Rome\
    l, lugar:tiwinza, nap:TW -13-5, plan:basico, tipo:FIBER" target=\
    192.168.220.12/32
add comment=FIBER max-limit=200M/200M name="id:1827, usuario:KIMBERLY YARIKZA \
    LLASHAG MEDRANO, lugar:9 de octubre, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.13/32
add comment=FIBER max-limit=200M/200M name="id:1828, usuario:EUGENIA DOMINGA C\
    OLONIA QUIJANO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.14/32
add comment=FIBER max-limit=200M/200M name="id:1829, usuario:ANGIE TAYLIN SULL\
    ON MONTANO, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.15/32
add comment=FIBER max-limit=200M/200M name="id:1830, usuario:MARCOS GRABIEL TR\
    UJILLO RAMIREZ, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.16/32
add comment=FIBER max-limit=200M/200M name="id:1831, usuario:EUGENIO  CARBAJAL\
    \_CIVICO, lugar:9 de octubre, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.17/32
add comment=FIBER max-limit=200M/200M name="id:1832, usuario:MILTON JOSMEL PIN\
    EDA MARINO, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.18/32
add comment=FIBER max-limit=200M/200M name="id:1834, usuario:alina del pilar m\
    artines oropeza, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.20/32
add comment=FIBER max-limit=200M/200M name="id:1835, usuario:ROMULO CRESENCIO \
    MALVAS IZQUIERDO, lugar:9 de octubre, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.21/32
add comment=FIBER max-limit=200M/200M name="id:1836, usuario:JERRY NANDITO HUA\
    TA TREJO, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.22/32
add comment=FIBER max-limit=200M/200M name="id:1837, usuario:DELIA LUISA MALVA\
    S IZQUIERDO, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.23/32
add comment=FIBER max-limit=200M/200M name="id:1839, usuario:LIMBER IZQUIERDO \
    JARAMILLO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.25/32
add comment=FIBER max-limit=200M/200M name="id:1840, usuario:LIZ CARLOS NAJERA\
    , lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.220.26/32
add comment=FIBER max-limit=200M/200M name="id:1841, usuario:GLORIA LIZBETH NI\
    ETO VEGA, lugar:la villa, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.220.27/32
add comment=FIBER max-limit=200M/200M name="id:1842, usuario:JONAS NEFRAIN ROJ\
    AS OLORTEGUI, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.28/32
add comment=FIBER max-limit=200M/200M name="id:1843, usuario:EVER ROLLY MAUTIN\
    O DIONICIO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.29/32
add comment=FIBER max-limit=200M/200M name="id:1844, usuario:EMILIANO NIK NIET\
    O VEGA, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.220.30/32
add comment=FIBER max-limit=200M/200M name="id:1845, usuario:ARMANDA VILMA RIM\
    AC SIGUENAS, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.31/32
add comment=FIBER max-limit=200M/200M name="id:1846, usuario:EDITH CARMEN SANC\
    HEZ MENDOZA, lugar:9 de octubre, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.32/32
add comment=FIBER max-limit=200M/200M name="id:1847, usuario:NAHIN JUAQUIN PIN\
    EDO, lugar:la villa, nap:ML-0-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.220.33/32
add comment=FIBER max-limit=200M/200M name="id:1848, usuario:ALANIS ANTEET MEJ\
    IA ATACHAGUA, lugar:9 de octubre, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.34/32
add comment=FIBER max-limit=200M/200M name="id:1849, usuario:LENIN OMAR AVELLA\
    NEDA RUFASTO, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.35/32
add comment=FIBER max-limit=200M/200M name="id:1850, usuario:DAVID ALFREDO TUP\
    IA ROJAS, lugar:la villa, nap:ML-0-5, plan:basico, tipo:FIBER" target=\
    192.168.220.36/32
add comment=FIBER max-limit=200M/200M name="id:1851, usuario:CESAR ANTONIO BRO\
    NCANO LOPEZ, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.37/32
add comment=FIBER max-limit=200M/200M name="id:1854, usuario:INOCENTA MANZUETA\
    \_ PAUCAR AMADOR, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.220.40/32
add comment=FIBER max-limit=200M/200M name="id:1855, usuario:Deyvy Jhojan  Chi\
    nchay Obregon, lugar:la villa, nap:ML-0-2, plan:basico, tipo:FIBER" \
    target=192.168.220.41/32
add comment=FIBER max-limit=200M/200M name="id:1856, usuario:ADAIMER WILFREDO \
    ABREGU SACRAMENTO, lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" \
    target=192.168.220.42/32
add comment=FIBER max-limit=200M/200M name="id:1857, usuario:Gilberto Fabian M\
    orales, lugar:tiwinza, nap:TW -13-5, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.43/32
add comment=FIBER max-limit=200M/200M name="id:1858, usuario:KAREM PATRICIA RO\
    CA CEDANO, lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.220.44/32
add comment=FIBER max-limit=200M/200M name="id:1859, usuario:ERICA VALERIA TEO\
    DORI CORDOVA, lugar:la merced, nap:LV -2-2, plan:basico, tipo:FIBER" \
    target=192.168.220.45/32
add comment=FIBER max-limit=200M/200M name="id:1860, usuario:DEMETRIO TEODORO \
    VILLARREAL, lugar:luvio, nap:LB -7-1, plan:basico, tipo:FIBER" target=\
    192.168.220.46/32
add comment=FIBER max-limit=200M/200M name="id:1861, usuario:Susana Yanet Pala\
    cios Chiroque, lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" \
    target=192.168.220.47/32
add comment=FIBER max-limit=200M/200M name="id:1864, usuario:ANGEL JHONATAN AR\
    MAS ROSALES, lugar:la merced, nap:LB -7-1, plan:basico, tipo:FIBER" \
    target=192.168.220.48/32
add comment=FIBER max-limit=200M/200M name="id:1865, usuario:NARCIZO RUIZ ARQU\
    INIGO , lugar:la merced, nap:ML-0-4, plan:basico, tipo:FIBER" target=\
    192.168.220.49/32
add comment=FIBER max-limit=200M/200M name="id:1866, usuario:Sarahi Rodriguez \
    Quinones, lugar:la villa, nap:ML-0-2, plan:basico, tipo:FIBER" target=\
    192.168.220.50/32
add comment=FIBER max-limit=200M/200M name="id:1867, usuario:Oswaldo Abidonio \
    Ureta Gomez , lugar:la merced, nap:LB -7-1, plan:duo_basico 80, tipo:FIBER\
    " target=192.168.220.51/32
add comment=FIBER max-limit=200M/200M name="id:1868, usuario:Marino Cesar Mora\
    \_Tadeo, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.52/32
add comment=FIBER max-limit=200M/200M name="id:1869, usuario:Magda Angelica  P\
    izarro La chira, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" \
    target=192.168.220.53/32
add comment=FIBER max-limit=200M/200M name="id:1870, usuario:Alisson Mellanie \
    \_Simbron Facundo, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" \
    target=192.168.220.54/32
add comment=FIBER max-limit=200M/200M name="id:1871, usuario:Erick Torres Paju\
    elo, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.55/32
add comment=FIBER max-limit=200M/200M name="id:1872, usuario:FELISITA HUERTA S\
    AMANES, lugar:la merced, nap:LB -7-3, plan:basico, tipo:FIBER" target=\
    192.168.220.56/32
add comment=FIBER max-limit=200M/200M name="id:1873, usuario:BRIGIDA ESPINOZA \
    JARA, lugar:la merced, nap:LB -7-4, plan:basico, tipo:FIBER" target=\
    192.168.220.57/32
add comment=FIBER max-limit=200M/200M name="id:1875, usuario:Mirian Gladys Yno\
    cente Ramos, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" \
    target=192.168.220.59/32
add comment=FIBER max-limit=200M/200M name="id:1876, usuario:Riabel  Requena L\
    eon , lugar:la villa, nap:LV -2-8, plan:basico, tipo:FIBER" target=\
    192.168.220.60/32
add comment=FIBER max-limit=200M/200M name="id:1877, usuario:LORENZO ARMAS TOR\
    IBIO, lugar:la merced, nap:LB -7-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.61/32
add comment=FIBER max-limit=200M/200M name="id:1878, usuario:EDELMIRA BERTA RA\
    FAEL RIOS, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" target=\
    192.168.220.62/32
add comment=FIBER max-limit=200M/200M name="id:1879, usuario:FEDERICO HUAMANI \
    PAUCAR, lugar:9 de octubre, nap:LV -6-5, plan:basico, tipo:FIBER" target=\
    192.168.220.63/32
add comment=FIBER max-limit=200M/200M name="id:1880, usuario:Baudilia  Cueva H\
    errera, lugar:9 de octubre, nap:LV -6-1, plan:basico, tipo:FIBER" target=\
    192.168.220.64/32
add comment=FIBER max-limit=200M/200M name="id:1881, usuario:PRICILA FLORA CAU\
    RINO RAMIREZ, lugar:la villa, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.65/32
add comment=FIBER max-limit=200M/200M name="id:1882, usuario:Marithsa Oyola Fr\
    ancisco de Huanca, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIBE\
    R" target=192.168.220.66/32
add comment=FIBER max-limit=200M/200M name="id:1884, usuario:Eduardo Obregon S\
    antillan, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIBER" \
    target=192.168.220.68/32
add comment=FIBER max-limit=200M/200M name="id:1885, usuario:Luis Alberto  Tam\
    ara Romero, lugar:9 de octubre, nap:LV -6-2, plan:basico, tipo:FIBER" \
    target=192.168.220.69/32
add comment=FIBER max-limit=200M/200M name="id:1886, usuario:SAyDA DEMETRIA MO\
    YA HUAITAN, lugar:9 de octubre, nap:LV -3-4, plan:basico, tipo:FIBER" \
    target=192.168.220.70/32
add comment=FIBER max-limit=200M/200M name="id:1888, usuario:Eusebio Segundo  \
    Gonzales Sanchez, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIBER\
    " target=192.168.220.72/32
add comment=FIBER max-limit=200M/200M name="id:1889, usuario:Valentin Moises  \
    Bautista Carpa, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIBER" \
    target=192.168.220.73/32
add comment=FIBER max-limit=200M/200M name="id:1890, usuario:Mariorry Prudenci\
    a  Gilberto Flores, lugar:9 de octubre, nap:LV -6-3, plan:basico, tipo:FIB\
    ER" target=192.168.220.74/32
add comment=FIBER max-limit=200M/200M name="id:1891, usuario:Cornelio Mejia Es\
    piritu, lugar:la villa, nap:LV -2-7, plan:basico, tipo:FIBER" target=\
    192.168.220.75/32
add comment=WIRELESS max-limit=20M/20M name="id:1893, usuario:Isabel Loaysa So\
    telo, lugar:san miguel, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.220.77/32
add comment=FIBER max-limit=200M/200M name="id:1894, usuario:RUBEN HONORIO VEG\
    A VALVERDE, lugar:9 de octubre, nap:ML-0-5, plan:basico, tipo:FIBER" \
    target=192.168.220.78/32
add comment=FIBER max-limit=200M/200M name="id:1895, usuario:Arleni Noemi Reym\
    undo Inga, lugar:la villa, nap:LV -6-3, plan:basico, tipo:FIBER" target=\
    192.168.220.79/32
add comment=FIBER max-limit=200M/200M name="id:1896, usuario:Candy Sanchez Cha\
    vez, lugar:las colinas, nap:LC -7-7, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.80/32
add comment=FIBER max-limit=200M/200M name="id:1897, usuario:Lizbeth  Paucar O\
    rtiz, lugar:la villa, nap:LV -5-4, plan:basico, tipo:FIBER" target=\
    192.168.220.81/32
add comment=FIBER max-limit=200M/200M name=\
    "id:1898, usuario:Fiorela Blas rivera, lugar: la villa" target=\
    192.168.220.82/32
add comment=WIRELESS max-limit=1k/1k name="id:1899, usuario:Edelmira Solorzano\
    \_Mendoza, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.83/32
add comment=FIBER max-limit=200M/200M name="id:1905, usuario:pilar chavez cotr\
    ina, lugar:la villa, nap:LV -15-1, plan:basico, tipo:FIBER" target=\
    192.168.220.85/32
add comment=FIBER max-limit=200M/200M name="id:1906, usuario:jovana valverde m\
    endoza, lugar:tiwinza, nap:TW -13-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.86/32
add comment=FIBER max-limit=200M/200M name="id:1907, usuario:Herminia  Saccsar\
    a Jauregui, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.220.87/32
add comment=FIBER max-limit=200M/200M name="id:1908, usuario:Martin Blas River\
    a , lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.220.88/32
add comment=FIBER max-limit=200M/200M name="id:1911, usuario:OSCAR OMAR BARDAL\
    ES VALDIVIA, lugar:la ensenada, nap:EN -8-5, plan:basico, tipo:FIBER" \
    target=192.168.220.89/32
add comment=FIBER max-limit=1k/1k name="id:1912, usuario:Basilio Diaz Capcha, \
    lugar:santa anita, nap:SA -9-4, plan:cable_basico, tipo:ONLY_TV_FIBER" \
    target=192.168.220.90/32
add comment=FIBER max-limit=200M/200M name="id:1914, usuario:JENIFER KATIUSCA \
    PAREDES MEJIA, lugar:la ensenada, nap:EN -8-5, plan:basico, tipo:FIBER" \
    target=192.168.220.92/32
add comment=FIBER max-limit=200M/200M name="id:1915, usuario:ALICIA MARIELA AR\
    MAS GONZALES, lugar:la ensenada, nap:EN -8-7, plan:basico, tipo:FIBER" \
    target=192.168.220.93/32
add comment=FIBER max-limit=200M/200M name="id:1916, usuario:LEILA JANET REYES\
    \_CARBAJAL, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" \
    target=192.168.220.94/32
add comment=FIBER max-limit=200M/200M name="id:1917, usuario:ANDREA ISABEL PAR\
    EDES SHUAN, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" \
    target=192.168.220.95/32
add comment=FIBER max-limit=200M/200M name="id:1918, usuario:JHANIZ BETSABE VI\
    DAL PAREDES, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" \
    target=192.168.220.96/32
add comment=FIBER max-limit=200M/200M name="id:1919, usuario:Alejandro Lugo So\
    lano, lugar:la villa, nap:LV -4-3, plan:basico, tipo:FIBER" target=\
    192.168.220.97/32
add comment=FIBER max-limit=200M/200M name="id:1920, usuario:NOLA JULIA BANEZ \
    CALDERON, lugar:la ensenada, nap:EN -8-1, plan:basico, tipo:FIBER" \
    target=192.168.220.98/32
add comment=FIBER max-limit=200M/200M name="id:1922, usuario:Nilton Fransisco \
    Utrilla , lugar:la villa, nap:LV -5-5, plan:basico, tipo:FIBER" target=\
    192.168.220.100/32
add comment=FIBER max-limit=200M/200M name="id:1927, usuario:Magdalena  Tranca\
    \_Alva, lugar:luvio, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.105/32
add comment=FIBER max-limit=200M/200M name="id:1928, usuario:Angela Quispe Rim\
    ac, lugar:san jeronimo, nap:SJ -4-6, plan:basico, tipo:FIBER" target=\
    192.168.220.106/32
add comment=FIBER max-limit=200M/200M name="id:1929, usuario:Maria  Monroy Hos\
    es, lugar:9 de octubre, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.107/32
add comment=FIBER max-limit=200M/200M name="id:1930, usuario:Ariana  Aquino Me\
    jia, lugar:la ensenada, nap:EN -8-6, plan:basico, tipo:FIBER" target=\
    192.168.220.108/32
add comment=FIBER max-limit=200M/200M name="id:1931, usuario:Lisbeth Yamunaque\
    \_Yamunaque, lugar:la ensenada, nap:EN -8-7, plan:basico, tipo:FIBER" \
    target=192.168.220.109/32
add comment=FIBER max-limit=200M/200M name="id:1933, usuario:Elva Loarte Gonza\
    les, lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" target=\
    192.168.220.111/32
add comment=FIBER max-limit=200M/200M name="id:1934, usuario:Kevin ayala mendo\
    za, lugar:la villa, nap:LV -1-1, plan:basico, tipo:FIBER" target=\
    192.168.220.101/32
add comment=FIBER max-limit=200M/200M name="id:1938, usuario:jennifer eche pal\
    omino, lugar:la villa, nap:LV -3-8, plan:basico, tipo:FIBER" target=\
    192.168.220.102/32
add comment=FIBER max-limit=200M/200M name="id:1939, usuario:cecilio romero ca\
    ldas, lugar:luvio, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.103/32
add comment=FIBER max-limit=300M/300M name="id:1940, usuario:jose reina guevar\
    a, lugar:tiwinza, nap:TW -13-6, plan:Internet70, tipo:FIBER" target=\
    192.168.220.104/32
add comment=FIBER max-limit=200M/200M name="id:1942, usuario:Jonathan Yovera G\
    arcia, lugar:9 de octubre, nap:ML-0-5, plan:basico, tipo:FIBER" target=\
    192.168.220.112/32
add comment=WIRELESS max-limit=200M/200M name="id:1943, usuario:Doria Mejia Ca\
    rlos, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.220.113/32
add comment=FIBER max-limit=200M/200M name="id:1944, usuario:Pamela Sanchez Ag\
    urto, lugar:la ensenada, nap:EN -8-5, plan:basico, tipo:FIBER" target=\
    192.168.220.114/32
add comment=FIBER max-limit=200M/200M name="id:1945, usuario:Gabriela Calixto \
    Blaz, lugar:la villa, nap:LV -5-6, plan:basico, tipo:FIBER" target=\
    192.168.220.115/32
add comment=FIBER max-limit=200M/200M name="id:1946, usuario:JUAN RIOS AGURTO,\
    \_lugar:9 de octubre, nap:ML-0-4, plan:basico, tipo:FIBER" target=\
    192.168.220.116/32
add comment=FIBER max-limit=200M/200M name="id:1947, usuario:Eugenia Tarazona \
    Diaz, lugar:santa anita, nap:SA -9-5, plan:basico, tipo:FIBER" target=\
    192.168.220.117/32
add comment=FIBER max-limit=200M/200M name="id:1949, usuario:Reynaldo Yamunaqu\
    e Aguilar, lugar:luvio, nap:LB -7-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.118/32
add comment=FIBER max-limit=200M/200M name="id:1951, usuario:Josue Melendrez M\
    alvasedra, lugar:la villa, nap:LV -2-3, plan:basico, tipo:FIBER" target=\
    192.168.220.119/32
add comment=FIBER max-limit=200M/200M name="id:1953, usuario:Leydi Aguirre Roj\
    as, lugar:la villa, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.120/32
add comment=FIBER max-limit=200M/200M name="id:1954, usuario:Rene Espinoza Esp\
    inoza , lugar:luvio, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.121/32
add comment=FIBER max-limit=200M/200M name="id:1955, usuario:Mariluz Ocana Esp\
    inoza, lugar:9 de octubre, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.122/32
add comment=FIBER max-limit=200M/200M name="id:1956, usuario:Idania Caurino Ca\
    urino, lugar:la villa, nap:ML-0-5, plan:basico, tipo:FIBER" target=\
    192.168.220.123/32
add comment=FIBER max-limit=200M/200M name="id:1957, usuario:Maria Chavez Caur\
    ino, lugar:9 de octubre, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.124/32
add comment=WIRELESS max-limit=200M/200M name="id:1958, usuario:Ruben  Espinoz\
    a Gonzales, lugar:santa anita, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.125/32
add comment=FIBER max-limit=200M/200M name="id:1959, usuario:Susy Valverde Men\
    doza, lugar:tiwinza, nap:TW -13-6, plan:basico, tipo:FIBER" target=\
    192.168.220.126/32
add comment=FIBER max-limit=200M/200M name="id:1963, usuario:Alex Lescano Malu\
    quis, lugar:la villa, nap:LV -3-2, plan:basico, tipo:FIBER" target=\
    192.168.220.127/32
add comment=FIBER max-limit=200M/200M name="id:1964, usuario:Cielo Rojas Salas\
    , lugar:la villa, nap:LV -6-1, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.220.128/32
add comment=FIBER max-limit=200M/200M name="id:1965, usuario:Esteban Villavice\
    ncio Manzanilla, lugar:santa anita, nap:SA -9-5, plan:basico, tipo:FIBER" \
    target=192.168.220.129/32
add comment=FIBER max-limit=200M/200M name="id:1966, usuario:Euliterio Rojas U\
    trilla, lugar:la villa, nap:LV -5-5, plan:basico, tipo:FIBER" target=\
    192.168.220.130/32
add comment=FIBER max-limit=200M/200M name="id:1967, usuario:Eber Mariluz Maqu\
    in, lugar:tiwinza, nap:TW -13-3, plan:basico, tipo:FIBER" target=\
    192.168.220.131/32
add comment=WIRELESS max-limit=200M/200M name="id:1968, usuario:Mikaela Ramos \
    Camones, lugar:La Victoria, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.132/32
add comment=FIBER max-limit=200M/200M name="id:1970, usuario:MERCEDES RUMI AGU\
    IRRE, lugar:la villa, nap:LV -5-4, plan:basico, tipo:FIBER" target=\
    192.168.220.133/32
add comment=FIBER max-limit=200M/200M name="id:1977, usuario:Yoseph Fernandez \
    Ruiz, lugar:la villa, nap:LV -3-3, plan:basico, tipo:FIBER" target=\
    192.168.220.134/32
add comment=FIBER max-limit=200M/200M name="id:1978, usuario:YANET YOVANA APAZ\
    A CCALLO, lugar:9 de octubre, nap:LV -4-4, plan:basico, tipo:FIBER" \
    target=192.168.220.135/32
add comment=FIBER max-limit=200M/200M name="id:1979, usuario:EDITH MANUELA BRA\
    VO CIRIACO, lugar:9 de octubre, nap:LV -6-5, plan:duo_basico 80, tipo:FIBE\
    R" target=192.168.220.136/32
add comment=FIBER max-limit=200M/200M name="id:1980, usuario:Joni Blas Dionici\
    o , lugar:la villa, nap:LV -3-3, plan:basico, tipo:FIBER" target=\
    192.168.220.137/32
add comment=FIBER max-limit=200M/200M name="id:1981, usuario:Diego Vasquez Bar\
    ranzuela, lugar:la villa, nap:LV -3-7, plan:basico, tipo:FIBER" target=\
    192.168.220.138/32
add comment=FIBER max-limit=200M/200M name="id:1982, usuario:Kety Leiva Brabo,\
    \_lugar:la villa, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.139/32
add comment=FIBER max-limit=200M/200M name="id:1983, usuario:Hilda Francis Aya\
    la Sudario, lugar:la villa, nap:LV -2-7, plan:basico, tipo:FIBER" target=\
    192.168.220.140/32
add comment=WIRELESS max-limit=200M/200M name="id:1984, usuario:Cristina  Truj\
    illo Minaya, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.141/32
add comment=FIBER max-limit=200M/200M name="id:1986, usuario:Silvia Paola Poma\
    \_Guillermo, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" \
    target=192.168.220.142/32
add comment=WIRELESS max-limit=20M/20M name="id:1987, usuario:EMILIA  LORENZO \
    MALVAS, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.143/32
add comment=FIBER max-limit=200M/200M name="id:1988, usuario:Nancy Fernandez G\
    raus, lugar:la villa, nap:LV -6-4, plan:basico, tipo:FIBER" target=\
    192.168.220.144/32
add comment=FIBER max-limit=200M/200M name="id:1989, usuario:Evelin Castillo L\
    una, lugar:la villa, nap:LV -4-3, plan:basico, tipo:FIBER" target=\
    192.168.220.145/32
add comment=FIBER max-limit=200M/200M name="id:1990, usuario:Nancy  Jara Oliva\
    s, lugar:la merced, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.146/32
add comment=WIRELESS max-limit=200M/200M name="id:1991, usuario:Efrain Gonzale\
    s Nolasco, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.147/32
add comment=WIRELESS max-limit=20M/20M name="id:1992, usuario:Lila  Rojas Zela\
    ya, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.220.148/32
add comment=FIBER max-limit=200M/200M name="id:1993, usuario:Beatriz Gomez Roj\
    as, lugar:la villa, nap:LV -6-7, plan:basico, tipo:FIBER" target=\
    192.168.220.149/32
add comment=FIBER max-limit=200M/200M name="id:1994, usuario:Pedro Pablo Rodri\
    guez Granados , lugar:luvio, nap:LB -7-3, plan:basico, tipo:FIBER" \
    target=192.168.220.150/32
add comment=FIBER max-limit=200M/200M name="id:1995, usuario:Alex Ramos Silva \
    , lugar:la villa, nap:LV -4-5, plan:basico, tipo:FIBER" target=\
    192.168.220.151/32
add comment=FIBER max-limit=200M/200M name="id:1996, usuario:Junior Tuncar Enc\
    arnacion, lugar:9 de octubre, nap:ML-0-6, plan:basico, tipo:FIBER" \
    target=192.168.220.152/32
add comment=FIBER max-limit=200M/200M name="id:1997, usuario:Yojani Sernaque I\
    suiza, lugar:la villa, nap:ML-0-6, plan:basico, tipo:FIBER" target=\
    192.168.220.153/32
add comment=WIRELESS max-limit=200M/200M name="id:1998, usuario:Hermila Trujil\
    lo Malqui, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.154/32
add comment=FIBER max-limit=200M/200M name="id:1999, usuario:Yessica Geronimo \
    Mejia, lugar:la villa, nap:LV -15-2, plan:basico, tipo:FIBER" target=\
    192.168.220.155/32
add comment=WIRELESS max-limit=200M/200M name="id:2000, usuario:Mirian Salvado\
    r Rey, lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.156/32
add comment=FIBER max-limit=200M/200M name="id:2001, usuario:Marcelo Rojas Par\
    edez, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" target=\
    192.168.220.157/32
add comment=FIBER max-limit=200M/200M name="id:2003, usuario:Jose Vasquez Garc\
    ia, lugar:la villa, nap:LV -5-4, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.220.159/32
add comment=FIBER max-limit=200M/200M name="id:2004, usuario:William Jhonathan\
    \_Tenorio Malqui, lugar:la villa, nap:LV -3-1, plan:basico, tipo:FIBER" \
    target=192.168.220.160/32
add comment=FIBER max-limit=200M/200M name="id:2005, usuario:Katerine Cardenas\
    \_Fernandez, lugar:casa blanca, nap:CB -11-1, plan:duo_basico 80, tipo:FIB\
    ER" target=192.168.220.161/32
add comment=FIBER max-limit=200M/200M name="id:2006, usuario:Yurico Cardenas F\
    ernandez, lugar:casa blanca, nap:CB -11-1, plan:basico, tipo:FIBER" \
    target=192.168.220.162/32
add comment=FIBER max-limit=200M/200M name="id:2007, usuario:Alfredo Abela Ave\
    ndano, lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" target=\
    192.168.220.163/32
add comment=FIBER max-limit=200M/200M name="id:2008, usuario:Roxana Alvite Aqu\
    ino, lugar:casa blanca, nap:CB -12-3, plan:basico, tipo:FIBER" target=\
    192.168.220.164/32
add comment=FIBER max-limit=200M/200M name="id:2009, usuario:Guillermo trabejo\
    \_Meneses, lugar:9 de octubre, nap:NO-005, plan:basico, tipo:FIBER" \
    target=192.168.220.165/32
add comment=FIBER max-limit=200M/200M name="id:2011, usuario:Melquiades Lopez \
    Dominguez, lugar:9 de octubre, nap:NO-005, plan:basico, tipo:FIBER" \
    target=192.168.220.166/32
add comment=FIBER max-limit=1k/1k name="id:2013, usuario:Dany Pinedo Rufino, l\
    ugar:roy martin, nap:LB -7-2, plan:basico, tipo:FIBER" target=\
    192.168.220.167/32
add comment=FIBER max-limit=200M/200M name="id:2014, usuario:Anita Vasquez Asa\
    niero, lugar:la villa, nap:LV -2-7, plan:basico, tipo:FIBER" target=\
    192.168.220.168/32
add comment=FIBER max-limit=200M/200M name="id:2015, usuario:Luis Enrique Ramo\
    s Chahua, lugar:la merced, nap:LM-002, plan:basico, tipo:FIBER" target=\
    192.168.220.169/32
add comment=FIBER max-limit=200M/200M name="id:2026, usuario:mily nostades rub\
    ina, lugar:la villa, nap:ML-0-7, plan:basico, tipo:FIBER" target=\
    192.168.220.170/32
add comment=FIBER max-limit=200M/200M name="id:2027, usuario:Pamela Selene  La\
    rianco, lugar:la merced, nap:LM-002, plan:basico, tipo:FIBER" target=\
    192.168.220.171/32
add comment=FIBER max-limit=200M/200M name="id:2028, usuario:Maximiliana  Roma\
    n Robles, lugar:casa blanca, nap:CB -11-4, plan:basico, tipo:FIBER" \
    target=192.168.220.172/32
add comment=FIBER max-limit=200M/200M name="id:2029, usuario:Eddy De la cruz M\
    edico, lugar:la ensenada, nap:EN -8-4, plan:basico, tipo:FIBER" target=\
    192.168.220.173/32
add comment=FIBER max-limit=200M/200M name="id:2030, usuario:Jose Alberto Zerp\
    a Roldan, lugar:9 de octubre, nap:NO-042, plan:basico, tipo:FIBER" \
    target=192.168.220.174/32
add comment=WIRELESS max-limit=20M/20M name="id:2031, usuario:Jeovanni Andrade\
    \_Figueroa, lugar:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.175/32
add comment=FIBER max-limit=200M/200M name="id:2032, usuario:leonidas de la cr\
    uz murrugarra , lugar:la ensenada, nap:EN -8-7, plan:basico, tipo:FIBER" \
    target=192.168.220.176/32
add comment=FIBER max-limit=300M/300M name="id:2034, usuario:municipalidad  se\
    renazgo, lugar:la villa, nap:LV -6-2, plan:Internet70, tipo:FIBER" \
    target=192.168.220.177/32
add comment=WIRELESS max-limit=200M/200M name="id:2035, usuario:Gaby Platino F\
    abian, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.220.178/32
add comment=FIBER max-limit=200M/200M name="id:2036, usuario:ligorio bravo ver\
    amendi, lugar:santa constansa, nap:SA -9-1, plan:basico, tipo:FIBER" \
    target=192.168.220.179/32
add comment=FIBER max-limit=200M/200M name="id:2037, usuario:Yanela Ramos Chah\
    ua, lugar:casa blanca, nap:CB -11-4, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.180/32
add comment=FIBER max-limit=200M/200M name="id:2039, usuario:Sergio Giraldo Ta\
    razona, lugar:la villa, nap:LV -1-4, plan:basico, tipo:FIBER" target=\
    192.168.220.181/32
add comment=FIBER max-limit=200M/200M name="id:2040, usuario:Rosa Melisa Casti\
    llo Espinoza, lugar:9 de octubre, nap:NO-011, plan:basico, tipo:FIBER" \
    target=192.168.220.182/32
add comment=FIBER max-limit=200M/200M name="id:2041, usuario:Jose Luis Fernand\
    ez Mendez, lugar:9 de octubre, nap:NO-010, plan:basico, tipo:FIBER" \
    target=192.168.220.183/32
add comment=FIBER max-limit=200M/200M name="id:2043, usuario:Iden Elias Compon\
    ido Villanueva, lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" \
    target=192.168.220.184/32
add comment=FIBER max-limit=200M/200M name="id:2044, usuario:Jan Fran Rance, l\
    ugar:la villa, nap:LV -3-7, plan:basico, tipo:FIBER" target=\
    192.168.220.185/32
add comment=FIBER max-limit=200M/200M name="id:2050, usuario:wilfreda Principe\
    \_Berrospi, lugar:la villa, nap:LV -3-7, plan:basico, tipo:FIBER" target=\
    192.168.220.187/32
add comment=FIBER max-limit=200M/200M name="id:2051, usuario:Melisia Lopez Gre\
    goria, lugar:la villa, nap:LV -5-3, plan:basico, tipo:FIBER" target=\
    192.168.220.189/32
add comment=FIBER max-limit=200M/200M name="id:2052, usuario:Ceferina Reyes Ra\
    ymundo, lugar:casa blanca, nap:CB -11-8, plan:basico, tipo:FIBER" target=\
    192.168.220.190/32
add comment=FIBER max-limit=200M/200M name="id:2053, usuario:CARLOS MENDOZA ME\
    NDOZA, lugar:la villa, nap:LV -4-1, plan:basico, tipo:FIBER" target=\
    192.168.220.191/32
add comment=WIRELESS max-limit=20M/20M name="id:2055, usuario:Lorenzo Ramirez \
    Balcazar, lugar:san jeronimo, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.220.193/32
add comment=FIBER max-limit=200M/200M name="id:2059, usuario:Henry Banies Cald\
    eron , lugar:la villa, nap:LV -1-1, plan:basico, tipo:FIBER" target=\
    192.168.220.195/32
add comment=FIBER max-limit=200M/200M name="id:2060, usuario:Lisette  Bazan Al\
    ejos, lugar:9 de octubre, nap:NO-042, plan:basico, tipo:FIBER" target=\
    192.168.220.196/32
add comment=FIBER max-limit=200M/200M name="id:2062, usuario:Jair Renzo Zuniga\
    \_Chavez, lugar:la villa, nap:LV -5-8, plan:basico, tipo:FIBER" target=\
    192.168.220.197/32
add comment=FIBER max-limit=200M/200M name="id:2063, usuario:Florisa  Castillo\
    n Parco, lugar:la ensenada, nap:EN -8-6, plan:basico, tipo:FIBER" target=\
    192.168.220.198/32
add comment=WIRELESS max-limit=20M/20M name="id:2064, usuario:Eugenia Sotelo P\
    onte, lugar:las piedras, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.220.199/32
add comment=WIRELESS max-limit=20M/20M name="id:2065, usuario:Juan Manuel Llan\
    os Fdo Hilarios antena, lugar:casa blanca, plan:basico_wireless 50, tipo:W\
    IRELESS" target=192.168.220.200/32
add comment=FIBER max-limit=200M/200M name="id:2067, usuario:Alex Cruz Rivera,\
    \_lugar:casa blanca, nap:CB -11-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.202/32
add comment=FIBER max-limit=200M/200M name="id:2070, usuario:Martha Fernandez \
    Tineo, lugar:casa blanca, nap:CB -11-8, plan:basico, tipo:FIBER" target=\
    192.168.220.203/32
add comment=FIBER max-limit=1k/1k name="id:2072, usuario:Liliana Contreras Tor\
    res, lugar:la villa, nap:LV -6-1, plan:basico, tipo:FIBER" target=\
    192.168.220.204/32
add comment=FIBER max-limit=200M/200M name="id:2073, usuario:lucila espinoza a\
    bal, lugar:santa anita, nap:SA -9-1, plan:basico, tipo:FIBER" target=\
    192.168.220.205/32
add comment=FIBER max-limit=200M/200M name="id:2074, usuario:franklin yerson r\
    equejo mijuahuanca, lugar:la villa, nap:LV -2-6, plan:basico, tipo:FIBER" \
    target=192.168.220.206/32
add comment=FIBER max-limit=200M/200M name="id:2076, usuario:silvia epifania l\
    opez vidal, lugar:santa anita, nap:SA -9-2, plan:duo_basico 80, tipo:FIBER\
    " target=192.168.220.208/32
add comment=FIBER max-limit=200M/200M name="id:2077, usuario:marilu davila veg\
    a, lugar:la villa, nap:LV -4-5, plan:basico, tipo:FIBER" target=\
    192.168.220.209/32
add comment=FIBER max-limit=200M/200M name="id:2081, usuario:nelina cerna caqu\
    i, lugar:la villa, nap:ML-0-7, plan:basico, tipo:FIBER" target=\
    192.168.220.210/32
add comment=FIBER max-limit=200M/200M name="id:2082, usuario:eliasa  vega carl\
    os, lugar:la ensenada, nap:EN -8-6, plan:basico, tipo:FIBER" target=\
    192.168.220.211/32
add comment=FIBER max-limit=200M/200M name="id:2083, usuario:willian ramirez s\
    ilva, lugar:la ensenada, nap:EN -8-8, plan:basico, tipo:FIBER" target=\
    192.168.220.212/32
add comment=FIBER max-limit=200M/200M name="id:2085, usuario:elsa Herrera  oca\
    na, lugar:la villa, nap:LV -2-5, plan:basico, tipo:FIBER" target=\
    192.168.220.213/32
add comment=FIBER max-limit=200M/200M name="id:2108, usuario:Marlene Campos Ve\
    ga, lugar:santa anita, nap:SA -9-2, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.220.214/32
add comment=FIBER max-limit=200M/200M name="id:2110, usuario:jhon lucas, lugar\
    :la merced, nap:LM-002, plan:basico, tipo:FIBER" target=\
    192.168.220.216/32
add max-limit=200M/200M name=\
    "id:2113, usuario:jessica paucar ramirez, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.217/32
add max-limit=200M/200M name=\
    "id:2116, usuario:Ana Maria mendoza ramos, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.219/32
add max-limit=200M/200M name=\
    "id:2121, usuario:rosmel ramirez blaz, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.220/32
add comment=WIRELESS max-limit=300M/300M name="id:864, usuario:Pedro Mariano C\
    AMAIORA de Lama FUNDO Sta VICTORIA, lugar:9 de octubre, plan:wireless 70 ,\
    \_tipo:WIRELESS" target=192.168.93.203/32
add max-limit=200M/200M name=\
    "id:2124, usuario:vilma  penadillo claudio, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.221/32
add max-limit=200M/200M name=\
    "id:2126, usuario:Jorge campos, lugar:, nap:, plan:, tipo:" target=\
    192.168.220.222/32
add max-limit=200M/200M name="id:2127, usuario:Teodora Albina  Jacinto Cabello\
    , lugar:, nap:, plan:, tipo:" target=192.168.220.223/32
add max-limit=200M/200M name=\
    "id:2128, usuario:Edelmira  Sifuentes Pinedo, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.224/32
add comment=FIBER max-limit=200M/200M name="id:1088, usuario:CESAR  HILARIO MA\
    LLA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.245/32
add comment=WIRELESS max-limit=200M/200M name="id:1047, usuario:AIDA SIFUENTES\
    \_IZQUIERDO, lugar:la villa, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.93.251/32
add comment=FIBER max-limit=200M/200M name="id:1252, usuario:Mauricio vargas p\
    erez, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.210.44/32
add max-limit=200M/200M name=\
    "id:2131, usuario:miguel valenzuela, lugar:, nap:, plan:, tipo:" target=\
    192.168.220.225/32
add comment=FIBER max-limit=200M/200M name="id:1729, usuario:Neyser Dominguez \
    Cruz, lugar:la villa, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.211.83/32
add max-limit=200M/200M name=\
    "id:2132, usuario:cesar  milla , lugar:, nap:, plan:, tipo:" target=\
    192.168.220.226/32
add max-limit=200M/200M name=\
    "id:2133, usuario:maricruz gaspar , lugar:, nap:, plan:, tipo:" target=\
    192.168.220.227/32
add max-limit=200M/200M name=\
    "id:2134, usuario:Ana  Sosa Guadalupe , lugar:, nap:, plan:, tipo:" \
    target=192.168.220.228/32
add comment=FIBER max-limit=200M/200M name="id:1519, usuario:kenji  aliaga baz\
    an , lugar:9 de octubre, nap:, plan:basico, tipo:FIBER" target=\
    192.168.210.213/32
add max-limit=200M/200M name=\
    "id:2137, usuario:Deysi armas, lugar:, nap:, plan:, tipo:" target=\
    192.168.220.230/32
add comment=FIBER max-limit=200M/200M name="id:1808, usuario:Herlinda Marchino\
    \_Chinchano, lugar:la villa, nap:LV -2-2, plan:basico, tipo:FIBER" \
    target=192.168.211.157/32
add max-limit=300M/300M name="id:2141, usuario:Roberto Carlos Quinones Ramirez\
    , lugar:, nap:, plan:, tipo:" target=192.168.220.232/32
add max-limit=200M/200M name=\
    "id:2142, usuario:yina herrera , lugar:, nap:, plan:, tipo:" target=\
    192.168.220.233/32
add max-limit=200M/200M name=\
    "id:2143, usuario:brisa quijano, lugar:, nap:, plan:, tipo:" target=\
    192.168.210.177/32
add comment=FIBER max-limit=300M/300M name="id:1474, usuario:brisa  quijano ar\
    ana, lugar:9 de octubre, nap:, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.177/32
add max-limit=200M/200M name=\
    "id:2145, usuario:nely latorre fermin, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.234/32
add comment=FIBER max-limit=200M/200M name="id:1536, usuario:DAVID ANICETO MAL\
    LQUI, lugar:la villa, nap:LV -2-5, plan:basico, tipo:FIBER" target=\
    192.168.210.223/32
add max-limit=200M/200M name=\
    "id:2146, usuario:yoselin ruiz, lugar:, nap:, plan:, tipo:" target=\
    192.168.220.235/32
add max-limit=20M/20M name=\
    "id:2147, usuario:ricardo sifuentes escalante, lugar:, plan:, tipo:" \
    target=192.168.220.236/32
add max-limit=200M/200M name=\
    "id:2148, usuario:yordan bravo  Quispe, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.237/32
add max-limit=20M/20M name=\
    "id:2149, usuario:teodoro gomez lucas, lugar:, plan:, tipo:" target=\
    192.168.220.238/32
add max-limit=200M/200M name=\
    "id:2151, usuario:Valia  pizango, lugar:, nap:, plan:, tipo:" target=\
    192.168.220.239/32
add max-limit=200M/200M name=\
    "id:2153, usuario:Julio Pineda torres, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.240/32
add comment=FIBER max-limit=200M/200M name="id:1566, usuario:Efrain Blanco Cir\
    ilo, lugar:la villa, nap:LV -1-4, plan:duo_basico 80, tipo:FIBER" target=\
    192.168.210.250/32
add max-limit=20M/20M name=\
    "id:2154, usuario:omar lizana santacruz, lugar:, plan:, tipo:" target=\
    192.168.220.241/32
add max-limit=200M/200M name=\
    "id:2155, usuario:enrique aguirre carlos, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.242/32
add max-limit=200M/200M name=\
    "id:2157, usuario:nancy sonia vautista polin, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.243/32
add comment=FIBER max-limit=200M/200M name="id:1023, usuario:BRITALDO DIAZ, lu\
    gar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.26.17/32
add max-limit=200M/200M name="id:2160, usuario:aldo omar gonsales velasquez, l\
    ugar:, nap:, plan:, tipo:" target=192.168.220.244/32
add max-limit=200M/200M name="id:2161, usuario:alejandro sabino estrada salina\
    s, lugar:, nap:, plan:, tipo:" target=192.168.220.245/32
add max-limit=200M/200M name=\
    "id:2162, usuario:Melisa Mirsa Mayu Martinez, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.246/32
add max-limit=200M/200M name="id:2163, usuario:Eduardo  zevillano Dominguez, l\
    ugar:, nap:, plan:, tipo:" target=192.168.220.247/32
add max-limit=200M/200M name=\
    "id:2164, usuario:Brigida  Rojas Zorrilla, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.248/32
add max-limit=200M/200M name=\
    "id:2165, usuario:liseth lucero garay, lugar:, nap:, plan:, tipo:" \
    target=192.168.220.249/32
add comment=FIBER max-limit=200M/200M name="id:1069, usuario:TALLER 9 carla ba\
    zan, lugar:9 de octubre, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.246/32
add comment=FIBER max-limit=200M/200M name="id:1416, usuario:evair clavijo qui\
    to, lugar:la villa, nap:LV -3-2, plan:basico, tipo:FIBER" target=\
    192.168.210.123/32
add max-limit=20M/20M name=\
    "id:2169, usuario:Elba Pajuelo Jaramillo, lugar:, plan:, tipo:" target=\
    192.168.221.10/32
add max-limit=200M/200M name=\
    "id:2170, usuario:Cleber  Morales Hidaldo , lugar:, nap:, plan:, tipo:" \
    target=192.168.221.11/32
add max-limit=200M/200M name=\
    "id:2175, usuario:Jesus Lopez Pascual, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.15/32
add max-limit=200M/200M name=\
    "id:2176, usuario:Luzmila de la cruz Caurino, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.16/32
add comment=FIBER max-limit=200M/200M name="id:1642, usuario:ALFREDO  RUBINO E\
    USTAQUIO , lugar:la villa, nap:, plan:basico, tipo:FIBER" target=\
    192.168.211.28/32
add max-limit=200M/200M name=\
    "id:2177, usuario:ivan gamez ocana, lugar:, nap:, plan:, tipo:" target=\
    192.168.221.17/32
add max-limit=200M/200M name=\
    "id:2187, usuario:Raquel Porras Carlos, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.18/32
add max-limit=200M/200M name="id:2206, usuario:Gabriel Antonio Perez Figueroa,\
    \_lugar:, nap:, plan:, tipo:" target=192.168.221.19/32
add max-limit=400M/400M name=\
    "id:2207, usuario:Angelo  Lucero Culla, lugar:, nap:, plan:, tipo:" \
    target=192.168.88.77/32
add comment=WIRELESS max-limit=200M/200M name="id:1405, usuario:rosa  chavez c\
    hoca, lugar:santa constansa, plan:basico, tipo:FIBER" target=\
    192.168.210.118/32
add comment=FIBER max-limit=200M/200M name="id:936, usuario:NENA MOLINA, lugar\
    :la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=192.168.25.81/32
add comment=FIBER max-limit=200M/200M name="id:1099, usuario:KELLY DILVER MASA\
    \_CALLE, lugar:santa anita, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.203/32
add max-limit=200M/200M name=\
    "id:2208, usuario:David Local Aniceto Malqui, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.21/32
add max-limit=980M/980M name="queue1zte ctv" target=192.168.88.82/32
add comment=WIRELESS max-limit=20M/20M name="id:1189, usuario:omar asto soria,\
    \_lugar:9 de octubre, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.210.12/32
add max-limit=200M/200M name=\
    "id:2212, usuario:Raul Efrain Castro Medico, lugar:, nap:, plan:, tipo:" \
    target=192.168.88.76/32
add max-limit=200M/200M name="id:2214, usuario:Mariannys del valle Sanchez Rum\
    bo, lugar:, nap:, plan:, tipo:" target=192.168.221.22/32
add comment=FIBER max-limit=200M/200M name="id:1838, usuario:ESTANISLAO LARIAN\
    CO SALINAS, lugar:9 de octubre, nap:ML-0-3, plan:basico, tipo:FIBER" \
    target=192.168.220.24/32
add max-limit=200M/200M name=\
    "id:2219, usuario:Alejandrina Vigo Salcedo, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.23/32
add max-limit=20M/20M name=\
    "id:2220, usuario:Diana Maribel  Limas Patricio, lugar:, plan:, tipo:" \
    target=192.168.221.24/32
add max-limit=200M/200M name=\
    "id:2222, usuario:LUPO Munoz Paredes , lugar:, nap:, plan:, tipo:" \
    target=192.168.221.25/32
add max-limit=200M/200M name=\
    "id:2136, usuario:CORA MENDOZA, lugar:, plan:, tipo:" target=\
    192.168.220.233/32
add max-limit=300M/300M name=\
    "id:2137, usuario:PEDRITO QUISPE, lugar:, plan:, tipo:" target=\
    192.168.220.234/32
add max-limit=200M/200M name=\
    "id:2223, usuario:GIANCARLO INPA MEJIA, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.26/32
add max-limit=200M/200M name=\
    "id:2224, usuario:RAUL  EUSTAQUIO BARROZO, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.27/32
add max-limit=200M/200M name=\
    "id:2225, usuario:Nolberto Aquino Leon, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.28/32
add max-limit=20M/20M name=\
    "id:2226, usuario:HJFJFFJFH DHHDHDHDDH, lugar:, plan:, tipo:" target=\
    192.168.221.29/32
add max-limit=200M/200M name=\
    "id:2232, usuario:BFJFJFHF HDHDHFHFH, lugar:, nap:, plan:, tipo:" target=\
    192.168.221.30/32
add max-limit=200M/200M name=\
    "id:2226, usuario:MARIA TRUJILLO SIFUENTES, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.29/32
add max-limit=200M/200M name=\
    "id:2227, usuario:RUSBEL SIFUENTES VASQUEZ, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.30/32
add comment=FIBER max-limit=200M/200M name="id:1257, usuario:angel toledo advi\
    cula, lugar:la villa, nap:LV -4-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.210.49/32
add comment=FIBER max-limit=200M/200M name="id:1637, usuario:MIGUEL  RAMIREZ S\
    OLORZANO , lugar:la villa, nap:LV -1-3, plan:basico, tipo:FIBER" target=\
    192.168.211.25/32
add max-limit=200M/200M name="id:2228, usuario:JOEL ANTHONY JARAMILLO TARAZONA\
    , lugar:, nap:, plan:, tipo:" target=192.168.221.31/32
add comment=FIBER max-limit=200M/200M name="id:999, usuario:MARYORITH BRIGGITT\
    E HERRERA FUENTES, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.26.247/32
add max-limit=30M/30M name=\
    "id:2229, usuario:PEDRITO NO ME ACUERDO, lugar:, plan:, tipo:" target=\
    192.168.221.32/32
add max-limit=30M/30M name=\
    "id:2230, usuario:DSSSD FDFFFF, lugar:, plan:, tipo:" target=\
    192.168.221.33/32
add comment=FIBER max-limit=200M/200M name="id:1016, usuario:PETHER SALINAS PI\
    NEDO, lugar:las colinas, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.70/32
add max-limit=200M/200M name=\
    "id:2230, usuario:JERRY CUELO PISCO, lugar:, nap:, plan:, tipo:" target=\
    192.168.221.33/32
add comment=FIBER max-limit=200M/200M name="id:685, usuario:EDWIN RONALD SAAVE\
    DRA PATRICIO, lugar:santa anita, nap:ML-0-1, plan:duo_basico 80, tipo:FIBE\
    R" target=192.168.93.127/32
add comment=WIRELESS max-limit=20M/20M name="id:880, usuario:JOSE SANDON, luga\
    r:santa constansa, plan:basico_wireless 50, tipo:WIRELESS" target=\
    192.168.93.174/32
add comment=FIBER max-limit=200M/200M name="id:1312, usuario:VICTOR FLORES TIC\
    LIA, lugar:la ensenada, nap:, plan:basico, tipo:FIBER" target=\
    192.168.22.17/32
add max-limit=200M/200M name=\
    "id:2231, usuario:JHOM DIAZ FERNANDEZ, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.34/32
add max-limit=1G/1G name=\
    "id:2232, usuario:PATRICIA CARILLO CUITANA, lugar:, nap:, plan:, tipo:" \
    target=192.168.88.90/32
add comment=WIRELESS max-limit=20M/20M name="id:735, usuario:JOSELIN LUCERO BE\
    NITES MORENO, lugar:luvio, plan:wireless 100, tipo:WIRELESS" target=\
    192.168.22.47/32
add comment=FIBER max-limit=200M/200M name="id:960, usuario:SONIA BENAVIDES, l\
    ugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.80/32
add max-limit=200M/200M name="id:2233, usuario:ROSMEL RODOLFO TARAZONA BRAVO, \
    lugar:, nap:, plan:, tipo:" target=192.168.221.36/32
add max-limit=200M/200M name=\
    "id:2234, usuario:ADELAYDO FAELO VASQUEZ , lugar:, nap:, plan:, tipo:" \
    target=192.168.221.37/32
add max-limit=200M/200M name=\
    "id:2235, usuario:QUINO CAURINO JEYSON, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.38/32
add max-limit=200M/200M name=\
    "id:2236, usuario:DARWIN CALLE YAMUCA, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.39/32
add comment=WIRELESS max-limit=20M/20M name="id:1426, usuario:yever larianco j\
    imenes , lugar:las colinas, plan:basico_wireless 50, tipo:WIRELESS" \
    target=192.168.210.134/32
add max-limit=200M/200M name=\
    "id:2237, usuario:JOEL ALONSO SANCHEZ, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.40/32
add comment=FIBER max-limit=200M/200M name="id:1062, usuario:JOSE LUIS ESPINOZ\
    A PADILLA, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.25.72/32
add comment=FIBER max-limit=400M/400M name="id:1823, usuario:Segundina Julia C\
    olonia Gargate, lugar:9 de octubre, nap:ML-0-2, plan:Internet100, tipo:FIB\
    ER" target=192.168.211.167/32
add max-limit=200M/200M name=\
    "id:2238, usuario:KALEB ESPINOZA GUZMAN, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.41/32
add comment=FIBER max-limit=200M/200M name="id:1608, usuario:Aldo Gonzales Vel\
    asques, lugar:la villa, nap:LV -6-3, plan:basico, tipo:FIBER" target=\
    192.168.22.224/32
add comment=FIBER max-limit=200M/200M name="id:1786, usuario:Joshimar Huaman P\
    erez, lugar:casa blanca, nap:CB -11-6, plan:duo_basico 80, tipo:FIBER" \
    target=192.168.211.136/32
add max-limit=200M/200M name=\
    "id:2245, usuario:ESTHER LOPEZ COTRINA, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.42/32
add comment=WIRELESS max-limit=30M/30M name="id:1089, usuario:FUNDO CAROLINA, \
    lugar:9 de octubre, plan:wireless 70 , tipo:WIRELESS" target=\
    192.168.88.28/32
add max-limit=200M/200M name=\
    "id:2246, usuario:FERNADO RAMIREZ ALVITES, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.43/32
add max-limit=200M/200M name="id:2247, usuario:ALEXIS JOEL PRINCIPE ALCANTARA,\
    \_lugar:, nap:, plan:, tipo:" target=192.168.221.44/32
add max-limit=200M/200M name=\
    "id:2248, usuario:NORMA SOLORZANO MORALES, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.45/32
add max-limit=200M/200M name=\
    "id:2249, usuario:ERIKA JAIME, lugar:, nap:, plan:, tipo:" target=\
    192.168.221.46/32
add max-limit=200M/200M name="id:2250, usuario:WALTHER MIGUEL NICHO QUISPE, lu\
    gar:, nap:, plan:, tipo:" target=192.168.221.47/32
add comment=FIBER max-limit=200M/200M name="id:861, usuario:LARITA ZABALA, lug\
    ar:santa constansa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.93.229/32
add comment=FIBER max-limit=400M/400M name="id:1089, usuario:FUNDO CAROLINA, l\
    ugar:9 de octubre, nap:ML-0-1, plan:Internet100, tipo:FIBER" target=\
    192.168.88.24/32
add max-limit=200M/200M name=\
    "id:2251, usuario:REYDA CABALLERO BRANDAN, lugar:, nap:, plan:, tipo:" \
    target=192.168.221.48/32
add comment=FIBER max-limit=200M/200M name="id:844, usuario:CALIP ISAIAS RIOS \
    RAMIREZ, lugar:la villa, nap:ML-0-1, plan:basico, tipo:FIBER" target=\
    192.168.22.15/32
add comment=FIBER max-limit=200M/200M name="id:997, usuario:MARTHA ELENA ROJAS\
    \_DIESTRA, lugar:san jeronimo, nap:ML-0-1, plan:basico, tipo:FIBER" \
    target=192.168.200.8/32
add comment=FIBER max-limit=200M/200M name="id:2002, usuario:Olivia Matias Pau\
    lino, lugar:la villa, nap:LV -15-5, plan:basico, tipo:FIBER" target=\
    192.168.220.158/32
add comment=FIBER max-limit=200M/200M name="id:1673, usuario:Elmer Jhony de la\
    \_cruz caurino, lugar:la ensenada, nap:EN -8-1, plan:basico, tipo:FIBER" \
    target=192.168.211.49/32
add comment=FIBER max-limit=200M/200M name="id:1400, usuario:liz yanet paucar \
    velasquez , lugar:luvio, nap:LB -7-3, plan:basico, tipo:FIBER" target=\
    192.168.210.113/32
/queue type
set 0 pfifo-limit=500
set 4 kind=pcq pcq-classifier=dst-address
/interface bridge port
add bridge=LAN interface=sfp-sfpplus2
add bridge=LAN interface=ether8
add bridge=LAN interface=ether7
add bridge=LAN interface=ether6
add bridge=LAN interface=ether5
add bridge=LAN interface=ether4
/ip neighbor discovery-settings
set discover-interface-list=!all lldp-med-net-policy-vlan=1
/interface pppoe-server server
add disabled=no interface=LAN max-mru=1480 max-mtu=1480 one-session-per-host=\
    yes service-name="PPOE CLIENTES"
/ip address
add address=192.168.22.1/24 interface=LAN network=192.168.22.0
add address=192.168.88.1/24 interface=LAN network=192.168.88.0
add address=192.168.33.1/24 interface=LAN network=192.168.33.0
add address=192.168.20.1/24 interface=LAN network=192.168.20.0
add address=192.168.49.1/24 interface=LAN network=192.168.49.0
add address=192.168.50.1/24 interface=LAN network=192.168.50.0
add address=192.168.25.1/24 interface=LAN network=192.168.25.0
add address=192.168.95.1/24 interface=LAN network=192.168.95.0
add address=192.168.93.1/24 interface=LAN network=192.168.93.0
add address=10.11.104.88/24 interface=ether3 network=10.11.104.0
add address=192.168.168.1/24 interface=LAN network=192.168.168.0
add address=192.168.9.1/24 interface=LAN network=192.168.9.0
add address=111.111.111.7/24 comment="AUXILIAR BALANCEO" interface=ether1 \
    network=111.111.111.0
add address=192.168.99.1/24 comment="solo para poder ver ap de la ensenada" \
    interface=LAN network=192.168.99.0
add address=192.168.26.1/24 comment="LAN CLIENTES" interface=LAN network=\
    192.168.26.0
add address=192.168.55.1/24 comment="LAN CLIENTES GAMERS" interface=LAN \
    network=192.168.55.0
add address=192.168.100.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.100.0
add address=192.168.0.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.0.0
add address=192.168.1.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.1.0
add address=192.168.200.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.200.0
add address=192.168.201.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.201.0
add address=192.168.210.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.210.0
add address=192.168.211.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.211.0
add address=38.224.231.2/27 comment="CORPORACION TARAZONA" interface=\
    "SERVICIO CORP.TARAZONA" network=38.224.231.0
add address=8.243.126.160 comment="IP 8 CORP TARAZONA" interface=\
    "SERVICIO CORP.TARAZONA" network=8.243.126.160
add address=8.243.126.161 interface="SERVICIO CORP.TARAZONA" network=\
    8.243.126.161
add address=8.243.126.162 interface="SERVICIO CORP.TARAZONA" network=\
    8.243.126.162
add address=8.243.126.163 interface="SERVICIO CORP.TARAZONA" network=\
    8.243.126.163
add address=192.168.123.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.123.0
add address=192.168.220.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.220.0
add address=192.168.44.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.44.0
add address=192.168.221.1/24 comment="NO BORRAR - GENERADO POR ISP ADMIN" \
    interface=LAN network=192.168.221.0
add address=192.168.175.1/24 interface=LAN network=192.168.175.0
add address=10.255.255.1/30 interface=gre-ispadmin-vps network=10.255.255.0
/ip dns
set allow-remote-requests=yes servers=8.8.8.8,8.8.4.4
/ip firewall address-list
add address=192.168.22.0/24 list=FULL-QoS
add address=192.168.88.0/24 list=FULL-QoS
add address=192.168.33.0/24 list=FULL-QoS
add address=192.168.55.0/24 list=FULL-QoS
add address=192.168.49.0/24 list=FULL-QoS
add address=192.168.50.0/24 list=FULL-QoS
add address=192.11.25.0/24 list=FULL-QoS
add address=192.168.20.0/24 list=FULL-QoS
add address=192.168.93.0/24 list=FULL-QoS
add address=192.168.95.0/24 list=FULL-QoS
add address=192.168.95.89 list=Empresas
add address=192.168.88.4 comment="FUNDO SAN CARLOS" list=Empresas
add address=192.168.9.28 comment="FUNDO CAROLINA" list=Empresas
add address=192.168.95.93 list=Empresas
add address=192.168.88.62 comment="FUNDO EL PARAISO" list=Empresas
add address=192.168.88.154 comment="GRIFO MIRIAM SAMUDIO" list=Empresas
add address=192.168.88.70 comment="RIKOS CHIKEN" list=Empresas
add address=192.168.88.63 comment="AGRIVIVEROS QUIROZ" list=Empresas
add address=192.168.22.215 list=Empresas
add address=192.168.25.211 comment=DANIELYENTIVIDANECTAR list=Empresas
add address=192.168.25.212 comment="VALERIO LUBIO ULTIMO" list=Empresas
add address=192.168.88.10 comment="GIGAFIBER TORRE" list=Empresas
add address=192.168.193.200-192.168.193.250 list=Empresas
add address=192.168.25.45 comment="CHIFA ANGEL" list=Empresas
add address=192.168.88.0/24 list=VIP
add address=192.168.88.215 list=Empresas
add address=192.168.25.141 comment="abigail sifuentes" list=Empresas
add address=192.168.26.141 comment="BRAYAN ALVARES MILAGRO COMPA CHIUACA" \
    list=Empresas
add address=192.168.25.218 comment="MARICIELO PALERMO ENCENADA" disabled=yes \
    list=Empresas
add address=192.168.25.44 comment="JLIO DELGADO COSTADO TALLE ELMER" list=\
    Empresas
add address=192.168.25.222 comment="ALEX SEGURIDAD LLANTERIA" list=Empresas
add address=192.168.22.86 comment="LUCIANO RADIO SERRO" list=Empresas
add address=192.168.25.252 comment="EMERSON FOGON PLN 100" list=Empresas
add address=192.168.88.99 list=Empresas
add address=192.168.108.0/24 comment=ISPADMIN list=ISPADMIN
add address=192.168.1.0/24 comment=ISPADMIN list=ISPADMIN
add address=192.168.26.47 comment="RIKOS CHIKEN PARQUE NUEVO" list=Empresas
add address=192.168.100.0/24 comment=ISPADMIN list=ISPADMIN
add address=amz.smartolt.com list=CloudOLT
add address=192.168.26.161 comment="COLEGIO LA VILLA HORACIO ZEBALLOS" list=\
    Empresas
add address=192.168.26.139 comment="ROSY TAPIA" disabled=yes list=Empresas
add address=192.168.88.67 list="UDP PRIORITY"
add address=192.168.88.99 list="UDP PRIORITY"
add address=192.168.25.15 list="UDP PRIORITY"
add address=192.168.26.124 list="UDP PRIORITY"
add address=108.167.157.247 comment="ip amigable" list=Empresas
add address=200.115.21.189 list="PAGINAS VARIOS"
add address=190.116.49.139 list="PAGINAS VARIOS"
add address=cel.sis.gob.pe list="PAGINAS VARIOS"
add address=www.cajahuancayo.com.pe list="PAGINAS VARIOS"
add address=www.clarovideo.com list="PAGINAS VARIOS"
add address=212.85.13.47 comment="Backend externo autorizado" list=\
    api_whitelist
add address=192.168.88.99 comment="Backend local autorizado" list=\
    api_whitelist
add address=www.yanbal.com list="PAGINAS VARIOS"
add address=maya.yanbal.com comment=www.yanbal.com list="PAGINAS VARIOS"
add address=www.maya.yanbal.com list="PAGINAS VARIOS"
add address=179.6.232.8 comment="yambal login" list="PAGINAS VARIOS"
add address=www.vuce.gob.pe comment=www.yanbal.com list="PAGINAS VARIOS"
add address=authorize.vuce.gob.pe comment=www.yanbal.com list=\
    "PAGINAS VARIOS"
add address=192.168.88.60 list="Clientes con paginas problematicas"
add address=192.168.25.73 list="Clientes con paginas problematicas"
add address=192.168.22.174 list="Clientes con paginas problematicas"
add address=192.168.93.69 comment="pereza toshiro" list=\
    "Clientes con paginas problematicas"
add address=192.168.25.216 comment="fiorela decena " list=\
    "Clientes con paginas problematicas"
add address=192.168.25.119 list="Clientes con paginas problematicas"
add address=192.168.25.52 list="Clientes con paginas problematicas"
add address=192.168.221.17 comment="ivan gamez oca\F1a - direc tv" list=\
    "Clientes con paginas problematicas"
add list="Clientes con paginas problematicas"
add address=192.168.210.122 list="Clientes con paginas problematicas"
add address=192.168.210.140 comment="Posta la villa - SIS " list=\
    "Clientes con paginas problematicas"
add address=192.168.22.54 comment="JUDIT ESPINOZA GUIMAREY" list=\
    "Clientes con paginas problematicas"
add address=192.168.26.46 comment="EYSER CARRANZA" list=\
    "Clientes con paginas problematicas"
add address=192.168.99.169 comment="JONATAN PRUEVA TEST" list=\
    "Clientes con paginas problematicas"
add address=192.168.25.124 comment="DEUDOR: ALEJANDRINO BLAS CHAVEZ" list=\
    deudores
add address=192.168.211.88 comment="DEUDOR: SONIA CALDAS PAULINO" list=\
    deudores
add address=192.168.211.39 comment="DEUDOR: AGEO QUINONES VERDE" list=\
    deudores
add address=192.168.220.138 comment="DEUDOR: DIEGO VASQUEZ BARRANZUELA" list=\
    deudores
add address=192.168.220.185 comment="DEUDOR: JAN FRAN RANCE" list=deudores
add address=192.168.33.28 comment="DEUDOR: LUCAS VILELA" list=deudores
add address=192.168.49.202 comment="DEUDOR: ROVINSON GAVINO ZORRILLA" list=\
    deudores
add address=192.168.25.110 comment="DEUDOR: DEYVIS GARCIA TOCAS" list=\
    deudores
add address=192.168.26.111 comment="DEUDOR: ROY PORTAL" list=deudores
add address=192.168.25.79 comment="DEUDOR: EDUARDO ESPINOZA" list=deudores
add address=192.168.200.17 comment="DEUDOR: NOLAN ALVAREZ CABELLO" list=\
    deudores
add address=192.168.210.35 comment="DEUDOR: ROGER AGURTO PRINCIPE" disabled=\
    yes list=deudores
add address=192.168.210.42 comment="DEUDOR: DILVER OSWALDO AGUILAR CASTILLO" \
    disabled=yes list=deudores
add address=192.168.210.61 comment="DEUDOR: DAVID OSCATA GUTIERREZ" disabled=\
    yes list=deudores
add address=192.168.210.118 comment="DEUDOR: ROSA  CHAVEZ CHOCA" disabled=yes \
    list=deudores
add address=192.168.210.135 comment="DEUDOR: GREGORIO ROJAS" list=deudores
add address=192.168.210.139 comment="DEUDOR: HECTOR CAURINO MELGAREJO" list=\
    deudores
add address=192.168.210.223 comment="DEUDOR: DAVID ANICETO MALLQUI" list=\
    deudores
add address=192.168.210.242 comment=\
    "DEUDOR: LUIS REQUELME  TITULAS PAULA SANGAMA" list=deudores
add address=192.168.211.40 comment="DEUDOR: VILMA ALVAREZ CABELLO" list=\
    deudores
add address=192.168.211.41 comment="DEUDOR: LUCY BRICENO  CERUCHE " disabled=\
    yes list=deudores
add address=192.168.211.44 comment="DEUDOR: SOFIA  CHIROQUE LOPEZ" list=\
    deudores
add address=192.168.211.87 comment=\
    "DEUDOR: YOSELIN ROSARIO CELEDONIO BRIGIDO " list=deudores
add address=192.168.211.105 comment="DEUDOR: VICTOR ALVA PAREDES" disabled=\
    yes list=deudores
add address=192.168.211.100 comment="DEUDOR: NICK ENCARNACION" list=deudores
add address=192.168.220.24 comment="DEUDOR: ESTANISLAO LARIANCO SALINAS" \
    list=deudores
add address=192.168.220.31 comment="DEUDOR: ARMANDA VILMA RIMAC SIGUENAS" \
    list=deudores
add address=192.168.220.51 comment="DEUDOR: OSWALDO ABIDONIO URETA GOMEZ " \
    list=deudores
add address=192.168.220.59 comment="DEUDOR: MIRIAN GLADYS YNOCENTE RAMOS" \
    list=deudores
add address=192.168.220.89 comment="DEUDOR: OSCAR OMAR BARDALES VALDIVIA" \
    list=deudores
add address=192.168.220.95 comment="DEUDOR: ANDREA ISABEL PAREDES SHUAN" \
    disabled=yes list=deudores
add address=192.168.220.96 comment="DEUDOR: JHANIZ BETSABE VIDAL PAREDES" \
    disabled=yes list=deudores
add address=192.168.220.108 comment="DEUDOR: ARIANA  AQUINO MEJIA" list=\
    deudores
add address=192.168.220.118 comment="DEUDOR: REYNALDO YAMUNAQUE AGUILAR" \
    list=deudores
add address=192.168.220.168 comment="DEUDOR: ANITA VASQUEZ ASANIERO" list=\
    deudores
add address=192.168.220.241 comment="DEUDOR: OMAR LIZANA SANTACRUZ" list=\
    deudores
add address=192.168.220.244 comment="DEUDOR: ALDO OMAR GONSALES VELASQUEZ" \
    disabled=yes list=deudores
add address=192.168.88.28 comment="DEUDOR: FUNDO CAROLINA" disabled=yes list=\
    deudores
add address=192.168.210.138 comment="EGERLIN CAROLINA CALLEJONES  RANGEL" \
    list=deudores
add address=192.168.26.63 comment="GLADYS RIOS MURRUGARRA" list=deudores
add address=192.168.220.238 disabled=yes list=deudor
add address=192.168.93.203 comment=\
    "PEDRO MARIANO CAMAIORA, FUNDO LA VICTORIA" list=deudores
add address=212.85.13.47 comment="ispAdmin VPS OLT SSH" list=IspAdminVPS
add address=192.168.88.160 comment="AMERICO SAUCEDO" disabled=yes list=\
    deudores
add address=192.168.220.219 comment="ANA MARIA MENDOZA RAMOS " list=deudores
add address=192.168.210.117 comment="BRIYITT GERALDINE  ANAYA VASQUEZ " list=\
    deudores
add address=192.168.26.234 comment="LUIS ALBERTO TADEO CANCHA " list=deudores
add address=192.168.26.10 comment="MARIA VALVERDE VALVERDE" list=deudores
add address=192.168.211.110 comment="MARIA GARCIA TIMANA" disabled=yes list=\
    deudores
add address=192.168.22.169 comment="RAFAEL ALBERTO SANCHEZ VASQUEZ" disabled=\
    yes list=deudores
add address=192.168.22.11 comment="JULIO RICHARD PAISIC ROJAS " list=deudores
add address=192.168.25.106 comment="MARI MAR ORTIZ PALACIOS" list=deudores
/ip firewall filter
add action=accept chain=input comment="Permitir API desde IPs seguras" \
    dst-port=8728 protocol=tcp src-address-list=api_whitelist
add action=add-src-to-address-list address-list=api_connection1 \
    address-list-timeout=5m chain=input comment="Primer intento API" \
    connection-state=new dst-port=8728 protocol=tcp src-address-list=\
    !api_whitelist
add action=add-src-to-address-list address-list=api_connection2 \
    address-list-timeout=15m chain=input comment="Segundo intento API" \
    connection-state=new dst-port=8728 protocol=tcp src-address-list=\
    api_connection1
add action=add-src-to-address-list address-list=api_connection3 \
    address-list-timeout=1h chain=input comment="Tercer intento API" \
    connection-state=new dst-port=8728 protocol=tcp src-address-list=\
    api_connection2
add action=add-src-to-address-list address-list=api_bruteforce_blacklist \
    address-list-timeout=1d chain=input comment=\
    "IP bloqueada por fuerza bruta API" connection-state=new dst-port=8728 \
    protocol=tcp src-address-list=api_connection3
add action=drop chain=input comment="Bloquear IPs por ataques API" dst-port=\
    8728 protocol=tcp src-address-list=api_bruteforce_blacklist
add action=accept chain=input comment="Permitir API si no est en blacklist" \
    dst-port=8728 protocol=tcp src-address-list=!api_bruteforce_blacklist
add action=drop chain=forward comment="CORTADO POR DEUDA - LISTA DE DEUDORES" \
    src-address-list=deudores
/ip firewall mangle
add action=mark-connection chain=prerouting connection-mark=no-mark \
    in-interface=sfp-sfpplus1 new-connection-mark=ISP1_conn passthrough=yes
add action=mark-connection chain=prerouting connection-mark=no-mark \
    in-interface=ether1 new-connection-mark=ISP2_conn passthrough=yes
add action=mark-routing chain=prerouting comment=\
    "Clientes problematicos -> Tarazona (forzado)" in-interface=LAN \
    new-routing-mark=toTarazona passthrough=no src-address-list=\
    "Clientes con paginas problematicas"
add action=mark-routing chain=prerouting connection-mark=ISP1_conn \
    in-interface=LAN new-routing-mark=to_ISP1 passthrough=no
add action=mark-routing chain=prerouting connection-mark=ISP2_conn \
    in-interface=LAN new-routing-mark=to_ISP2 passthrough=no
add action=mark-routing chain=output connection-mark=ISP1_conn \
    new-routing-mark=to_ISP1 passthrough=no
add action=mark-routing chain=output connection-mark=ISP2_conn \
    new-routing-mark=to_ISP2 passthrough=no
add action=mark-packet chain=prerouting dst-address-list="PAGINAS VARIOS" \
    new-packet-mark=pagina passthrough=yes
add action=mark-packet chain=prerouting comment="CLIENTES IP 8.X" \
    dst-address-list="Clientes con paginas problematicas" new-packet-mark=\
    no-mark passthrough=yes
/ip firewall nat
add action=src-nat chain=srcnat comment="SNAT problematicos -> 8.243.126.161" \
    routing-mark=toTarazona to-addresses=8.243.126.161
add action=src-nat chain=srcnat comment="NAT CORPORACION TARAZONA" \
    dst-address-list="PAGINAS VARIOS" out-interface="SERVICIO CORP.TARAZONA" \
    to-addresses=8.243.126.161
add action=masquerade chain=srcnat comment="NAT CORPORACION TARAZONA" \
    out-interface="SERVICIO CORP.TARAZONA"
add action=masquerade chain=srcnat comment="NAT RESPALDO" out-interface=\
    ether1
add action=dst-nat chain=dstnat comment=CloudOLT dst-address=38.224.231.2 \
    dst-port=2333 protocol=tcp src-address-list=CloudOLT to-addresses=\
    10.11.104.2 to-ports=23
add action=dst-nat chain=dstnat comment=CloudOLT dst-address=38.224.231.2 \
    dst-port=2322 protocol=tcp src-address-list=CloudOLT to-addresses=\
    10.11.104.2 to-ports=22
add action=dst-nat chain=dstnat comment=CloudOLT dst-address=38.224.231.2 \
    dst-port=2161 protocol=udp src-address-list=CloudOLT to-addresses=\
    10.11.104.2 to-ports=161
add action=dst-nat chain=dstnat disabled=yes dst-address=192.168.100.11 \
    dst-port=8080 protocol=tcp src-address=192.168.88.99 to-addresses=\
    111.111.111.1 to-ports=8080
add action=dst-nat chain=dstnat comment="Forward TCP 9092 to 192.168.44.2" \
    disabled=yes dst-port=9092 protocol=tcp to-addresses=192.168.44.2 \
    to-ports=9092
add action=masquerade chain=srcnat comment="IspAdmin VPS GRE -> OLT LAN SNAT" \
    disabled=yes dst-address=10.11.104.0/24 src-address=10.255.255.0/30
/ip route
add check-gateway=ping comment="Default marcada -> Tarazona" distance=1 \
    gateway=38.224.231.1 routing-mark=toTarazona
add check-gateway=ping comment="ISP 2" disabled=yes distance=1 gateway=\
    111.111.111.1 routing-mark=to_ISP2
add comment="GATEWAY CORPORACION TARAZONA" distance=1 gateway=38.224.231.1
add comment=RESPALDO disabled=yes distance=2 gateway=111.111.111.1
/ip service
set telnet disabled=yes
set ftp disabled=yes
set www disabled=yes
set api-ssl disabled=yes
/ppp secret
add comment="1-0-372 LOS MAITAS KOKI TORRE" name=KOKIMAITA#EDCCC2@DC8822* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.227 \
    service=pppoe
add name=minimarketdetodo#484466@B99003* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.213 service=pppoe
add name=LESCANOLUSMERI#484465@B9F8E7* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.194 service=pppoe
add name=MONTESORIOLSERRO#ABE449@896081* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.210 service=pppoe
add comment=0-12-201 name=CECILIAPERALTA#484465@B978B5* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.226 service=pppoe
add comment="2-20-202 AGRO HINALI" name=AGROLATIENDADELCAMPO#EDCC75@DCD8CF* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.248 \
    service=pppoe
add comment=0-13-203 name=DENISGONZALESLIBRERIA#EDCCC1@DCE056* password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.176 service=\
    pppoe
add comment=2-27-205 name=MILAGROSCHUNGA#484465@B928B2* password=112233 \
    profile="PLAN 70" remote-address=192.168.26.202 service=pppoe
add comment=2-28-206 name=BARBERIASHADAYHUERTAS#EDCCC3@DC6004* password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.219 service=\
    pppoe
add comment="15-22-208 ANA SOSA CASA" name=\
    YONELSOSACASAJARDINES#ABE442@8958D0* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.196 service=pppoe
add comment=15-24-211 name=EDISONHERRERA#EDCCCF@DC107D* password=112233 \
    profile="PLAN 70 NEW" remote-address=192.168.26.198 service=pppoe
add comment=15-26-213 name=SULMAFABIAN#EDCCC4@DC4804* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.216 service=pppoe
add comment=1-21-214 name=MIGUELSUNIGA#EDCCED@DCA87A* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.172 service=pppoe
add comment=2-29-215 name=YENICHAVEZ#EDCCC4@DC5073* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.201 service=pppoe
add comment=2-30-216 name=PABLOQUISPEAGRO#D4CCC9@A15879* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.230 service=pppoe
add comment="15-27-217 ESTACIO-YUSUM-AMPAM casa sombrero" name=\
    ESTACIOYUUMTPLINK password=112233 profile="PLAN 70" remote-address=\
    192.168.26.163 service=pppoe
add comment=6-52-218 name=RADIOSTUDIOSANTAROSA#EDCCEC@DC40C4* password=112233 \
    profile="PLAN 70" remote-address=192.168.26.186 service=pppoe
add comment="6-56-219 MARIA HUACHI" disabled=yes name=\
    MARIABLASHUACHI#EDCCD0@DCA836* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.214 service=pppoe
add comment="6-57-220 ESPALDA DE BASE " name=\
    MANUELAQUINOESTRELLA#EDCCD0@DCC032* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.183 service=pppoe
add comment=5-47-221 name=MAYAFELIPE#EDCCEC@DC68A1* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.247 service=pppoe
add comment="5-60-301     HERMANO MARCO NIETO GRASINTETICO" name=\
    MARCONIETOAJENTECAMPO#EDCCCF@DC60BC* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.205 service=pppoe
add comment=5-50-225 name=EMILIANOCUYA#EDCCB3@DC90AD* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.187 service=pppoe
add comment="MAMA DE PACO" name=SARAESCALANTE#48442E@B960CF* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.243 service=pppoe
add comment="15-28-228 FRENTE ANTENA CLARO" name=LLANETVALENTINEpadminadminEp \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.167 \
    service=pppoe
add comment=6-58-229 name=MIRIANZAMUDIOCASANUEVA#EDCC7C@DC4884* password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.241 service=\
    pppoe
add comment=6-59-230 name=LEIDYESPINOZAVILLA#484466@B9381B* password=112233 \
    profile="PLAN 100" remote-address=192.168.26.206 service=pppoe
add comment=6-61-233 name=VILMAPOLLITOORTIZ#484438@B95824* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.221 service=pppoe
add comment=3-15-232 name=OMAREMERGEM#EDCCC2@DCA017* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.182 service=pppoe
add comment="7-0-234  JHOJAN TARAZONA PAPA" name=JOHANTARAZONA#484465@B9480D* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.244 \
    service=pppoe
add comment="6-60-235 EN SU CASA DE KEVIN MONZON" name=\
    EUSEBIACHAVEZ#EDCCCE@DC60F7* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.195 service=pppoe
add comment="7-3-238 LUVIO COSTADO DOMINGUES NEGRO" name=\
    EVELINGARCIALUVIO#484464@B978A7* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.217 service=pppoe
add comment="4-2-241 HIJO DE HUAILAS" name=\
    JEANPIERREQUIJANOHUAYLAS#EDCC71@DCD86D* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.237 service=pppoe
add comment=5-41-242 name=MAIKOLBUSTAMANTE#EDCCC4@DC0821* password=112233 \
    profile="PLAN 70" remote-address=192.168.26.200 service=pppoe
add comment="6-62-364  COSTADO ELIDA LIBRERIA CENTRAL" name=\
    RRAQUELBARRIENTOSHUAWEI password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.193 service=pppoe
add comment="7-4-244 TALIA DOMIGUES PRIM CHACALON" name=\
    TALIADOMINGUESLUVIO#EDCCC1@DC48A6* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.189 service=pppoe
add comment=3-17-249 name=ANIBALARANA#484465@B920D9* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.233 service=pppoe
add comment=3-18-250 name=ZACARIASMORE#EDCCC1@DCE078* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.207 service=pppoe
add comment=2-31-251 name=POLLERIAROYS#EDCC28@DC508C* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.179 service=pppoe
add comment=3-19-252 name=DANIELSUMOCHACALON#484426@B918F1* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.220 service=pppoe
add comment="5-63-353   MOTOSERVICE CASTILLO CAMBIO 3" name=\
    MOTOSERVICECASTILLO#EDCC75@DC38BB* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.166 service=pppoe
add comment=7-5-254 name=AIDALUBIOULTIMO#EDCCC4@DC4004* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.188 service=pppoe
add comment="6-68-258  - 6/6/2022" name=DIEGOROJASDIESTRA#EDCCC1@DC903D* \
    password=112233 profile="PLAN 70 NEW" remote-address=192.168.26.250 \
    service=pppoe
add comment="1-26-259 -  07/06/2022" name=ROBERTOGERONIMO#EDCC71@DCA8CB* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.254 \
    service=pppoe
add comment=6-69-261 name=MIGUELRAMIREZPALITOS#484464@B938D8* password=112233 \
    profile="PLAN 100 NW" remote-address=192.168.26.228 service=pppoe
add comment="2-32-262    9/06/2022" name=BOTICAPITER#EDCCC3@DC6860* password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.212 service=\
    pppoe
add comment=3-20-263 name=DANIELCOTRINA#D4CCBD@A10802* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.178 service=pppoe
add comment="6-71-265     COSTADO BODEGA YOVANA PAJUELO 11/062022" name=\
    JENIFERPAJUELOBLAS#EDCC71@DC3080* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.173 service=pppoe
add comment="6-72-266    16/06/2022" name=BEKERVARGASLAB#EDCCC3@DC685E* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.218 \
    service=pppoe
add comment=9-1-268 name=ABELABALSNTANITA#484468@B93893* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.174 service=pppoe
add comment="5-55-271       23/06/2022 COSTADO ALFRED" name=\
    LUISALBERTO#484464@B938F1* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.234 service=pppoe
add comment="3-1-328        13/07/2022 POR FILADELFIA " name=\
    MONICAGOMEZFILADELFIA#EDCC29@DC402D* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.168 service=pppoe
add comment="4-10-279    ATRAS DE BARBERIA SHADAY  9/07/2022" name=\
    DONATILAPORRAS password=112233 profile="PLAN 50 SOLES" remote-address=\
    192.168.26.164 service=pppoe
add comment=5-58-280 name="GREGORIA JARDINES" password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.171 service=pppoe
add comment="15-19-281      09/07/2022 LOS JARDINES" name=VICTORIAESCALANTE \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.169 \
    service=pppoe
add comment="POR EL SERRO COST DISCOTECA" name=EVELINCALIXTOHUAW password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.165 service=\
    pppoe
add comment=1-28-283 name=MIGUELLEONLAVILLA password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.170 service=pppoe
add comment="2-33-284       13/07/2022" name=\
    ROBERTOVILLANUEVAGRINGO#484465@B9A09E* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.223 service=pppoe
add comment="2-34-285      13/07/2022   ATRAS DEL AGRO DE LOS QUISPE" name=\
    PAPAQUISPECUARTO#EDCCCE@DCC0D8* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.199 service=pppoe
add comment=0-15-287 name=MILEROLIVASMLGRO#EDCCED@DCD815* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.229 service=pppoe
add comment="0-16-288     14/07/2022 FAM JOSE QUISPE" name=\
    TEOFILAINIMILAGRO#EDCCCF@DC6862* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.246 service=pppoe
add comment="1-29-292    16/07/2022   ESTABLO" name=\
    ROJERCUYAESTABLO#EDCCC4@DC7832* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.238 service=pppoe
add comment="9-4-290 " name=HUGOSHUANSNTANITA#EDCC71@DC18F4* password=112233 \
    profile="PLAN 50 SOLES" remote-address=192.168.26.245 service=pppoe
add comment=9-5-291 name=CRISTIANMONTALVOSNTANITA#484465@B9782A* password=\
    112233 profile="PLAN 50 SOLES" remote-address=192.168.26.177 service=\
    pppoe
add comment="5-39-297   LOS JARDINES" name=ALFREDTIENDA-M-#EDCCC1@DC305B* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.242 \
    service=pppoe
add comment="11-0-298     COSTADO DE CANCHA CASABLANCA" name=\
    HUGOPINEDO#EDCCCF@DCC8A0* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.209 service=pppoe
add comment="2-35-300   JULIOCHEFVENITO LA VILLA PARADERO HUARAL" name=\
    JULIOCHEFVENITOLAVILLA#484466@B91011* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.190 service=pppoe
add comment="2-36-302 BODEGA ARIANA CHOLI" name=\
    ARIANACHOLILAVILLA#EDCCC0@DC68C7* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.203 service=pppoe
add comment="2-37-303    JUNIOR HERMANO DE JULIO CHEF" name=\
    JUNIORSEGURIDAD#EDCCCF@DCC0CB* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.239 service=pppoe
add comment="11-2-304  CASABLANCA WALTER PINEDO" name=\
    WALTERPINEDOCASAB#484464@B928D0* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.252 service=pppoe
add comment="1-35-321       AMANCIO ESTABLO 30/07/2022" name=\
    AMANCIOAMBRE#EDCCED@DC6870* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.240 service=pppoe
add comment="0-17-306     30/07/2022  el MILAGRO GALLOS" name=\
    JARARAMOSJUANPABLO#484468@B98890* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.235 service=pppoe
add comment="6-28-307   MARIBEL BLAS CHANCHO BLAS nando" name=\
    MARIBELBLAS#484465@B9D882* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.204 service=pppoe
add comment="9-6-309 YOJAN HERRERA SANTNITA CRUZANDO CAMPO" name=\
    YOJANHERRERASNTANITA#484438@B9003F* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.232 service=pppoe
add comment="2-38-210   CALLEJON SUBIDA AL SERRO COMISARIA" name=\
    MATEOSIFUENTESFIRA#EDCCB0@DC70D1* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.211 service=pppoe
add comment="2   EN EL SERRO AMIGO CHIUACA" name=DANIELREYES#EDCC77@DC88F0* \
    password=112233 profile="PLAN 70" remote-address=192.168.26.191 service=\
    pppoe
add comment=6-73-312 name=MARIANACHALAN#EDCCEC@DCC0E0* password=112233 \
    profile="PLAN 100" remote-address=192.168.26.236 service=pppoe
add comment="9-7-313     CIELO SANTANITA ENTRADA CALLE 2" name=\
    Cielosantaanita#EDCCC2@DC10BD* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.156 service=pppoe
add comment="9-8-314 MISHEL SIFUENTES SANTANITA" name=\
    Mishelsantaanita#EDCCD0@DCE851* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.154 service=pppoe
add comment="1-33-315  CHAMO CASA DIEGO 06/08/2022" name=\
    Cheroantony#484465@B968A0* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.153 service=pppoe
add comment="7-7-316 MARIELA LUVIO CHARLI" name=\
    MARIELAMENDOZALUVIO#484465@B9C8C3* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.152 service=pppoe
add comment="6-74-317    MAIRA ESTELLE FRENTE COLEGIO" name=\
    MAIRAESTELLECOLEG#EDCCCE@DCF0FD* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.151 service=pppoe
add comment="1-34-318  POLLO PAJUELO KRIKRI" name=KRIKRIPAJULO#EDCCEC@DC38D6* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.150 \
    service=pppoe
add name=OMAREMERHERMANA#48442E@B968B3* password=112233 profile="PLAN 70 NEW" \
    remote-address=192.168.26.149 service=pppoe
add comment="11-3-320 CAROL HUAMAN CASABLANCA" name=\
    karolhuaman#48442E@B958AC* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.148 service=pppoe
add comment="2-40-322 ESTETICA PAOLA COSTADO CARPI 10/08/2022" name=\
    ESTETICAPAOLA#484438@B9E886* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.147 service=pppoe
add comment=\
    "15-31-323    12/08/2022    MOCRO CHOFER FELIX ALFRENTE NINFER  PRESTADO" \
    name=MOISESRIMACMOCRO#484476@B9881A* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.146 service=pppoe
add comment="15-32-324    12/08/2022  AMELIA CONTRATISTA AFRENTE DE RUBEL" \
    name=speedy password=speedy profile="PLAN 150 SOLES NEW" remote-address=\
    192.168.26.145 service=pppoe
add comment="7-8-325    ABUELO DE YOJAN MENDOZA LUVIO COSTADO DE CAMPO" name=\
    EMERMENDOZAABUELO#EDCCCE@DCD0D7* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.144 service=pppoe
add comment="1-24-326    13/08/2022    COSTADO CASA DE CHINCUY POR LA TORRE" \
    name=LEONELCASTILLO#EDCC71@DC38CD* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.143 service=pppoe
add comment="1-32-327     IAN TAPIA CAJAMARQUINO" name=\
    IANTAPIACAJA#EDCCC0@DC78D9* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.142 service=pppoe
add comment="0-18-329    AMIGO DE CHIUCACA LEO EL MILAGRO" name=\
    BRAYANALVAREZ#EDCCC3@DCB81D* password=112233 profile="PLAN 150 SOLES NEW" \
    remote-address=192.168.26.141 service=pppoe
add comment="1-37-331    23/08/2022    ROSY TAPIA CASA ESTABLO" name=\
    ROSYTAPIAESTABLO#EDCCD0@DCF073* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.139 service=pppoe
add comment="YOIMER TALLER MOTOS STIKERS" name=YOIMERNUNES#D4CCB0@A140E4* \
    password=112233 profile="PLAN 50 SOLES" remote-address=192.168.26.138 \
    service=pppoe
add comment="1-40-134   YORDY PIMO VANESA ESTABLO  31/08/2022" name=\
    YORDYCRZVANE.TPINK password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.137 service=pppoe
add comment="15-5-335 XIMENA ALVARES JARDINES 01/09/2022" name=\
    XIMENAALVARESJARDINES#484427@B9A05F* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.136 service=pppoe
add comment="6-67-335  MARIA VRGAS FAM WAILAS COSTADO DE COLEGIO 05/09/2022" \
    name=MARIAVARGASFAMWAILAS#EDCCC1@DCF8AC* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.135 service=pppoe
add comment="3-5-337 ATRAS DE GRIFO RIVAS 06/09/2022" name=\
    YANINAHURTADOGRIFORIVAS#EDCCC4@DC207E* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.134 service=pppoe
add comment="11-4-339 PEREGIL MIJAEL CASABLANCA CAMPO LOSA" name=\
    PEREJILMIJAEL#484489@B91891* password=112233 profile="PLAN 70 NEW" \
    remote-address=192.168.26.131 service=pppoe
add comment="11-5-340 FAMILLAR PEREGIL CASABLANCA 07/09/2022" name=\
    JUANCARLOSMAZACASABLAN#EDCCD0@DC2023* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.130 service=pppoe
add name=POLLOMALONEALBANIL#EDCC71@DCA0E3* password=112233 profile=\
    "PLAN 50 SOLES" remote-address=192.168.26.129 service=pppoe
add comment=\
    "12-0-341  BEATRIS TRUJILLO FRENTE DE TANQUE CASABLANCA 12/09/2022" name=\
    BEATRISTRUJLLOCASABLA#EDCCC2@DCB0EC* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.128 service=pppoe
add comment="9-9-342 SEGUNDA ENTRADA SANTANITA 15/09/2022" name=\
    FLORSOTOSNTANITA#484468@B9587B* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.127 service=pppoe
add comment="6-75-343 JAEL SALDA\D1A PARQUE VIOLETA #EDCCC3@DCD0A6*" name=\
    JAELSALDANALAVILLA#484468@B9587B* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.126 service=pppoe
add comment="4-0-344 CARMEN PRIMA LUCHO ACHORADA" name=\
    CARMENRIMACDRAG#EDCC28@DC50DF* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.125 service=pppoe
add comment="4-1-345  SAUL FLORES CASA CATIRI 17/09/2022" name=\
    SAULFLORESSAU2#48449B@B9A020* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.124 service=pppoe
add comment="15-33-346 COSTADO JEAMPIERO JARDINES 17/09/2022" name=\
    ALDOPEREZJARDINES#484493@B9D01B* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.123 service=pppoe
add comment=\
    "12-1-347  ALBRENTE DE TANQUE CASABLANCA 2 CLIENTES JUNTOS 10/09/2022" \
    name=ABELIDIAZCASABLANCA#D4CCBD@A11889* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.122 service=pppoe
add comment="2-42-348 AGRO MEDANOS ALFRENTE TALLR ORTIZ" name=\
    AGROMEDANOSRICHARD#484465@B938C6* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.121 service=pppoe
add comment="15-34-349  TADEO QUINO ULTIMA CAJA PRIMAVERA  19/09/2022" name=\
    TADEOQUINOULTICAJA#484438@B9205F* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.120 service=pppoe
add comment="3-11-350 JULIA ANAYA INTERNET CABINAS MERCADO" name=\
    JULIAANAYAMERCAD#EDCC28@DCC8E6* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.119 service=pppoe
add comment="5-62-352  MASIEL MARILU BAR" name=MASIELMARILU#484438@B94848* \
    password=112233 profile="PLAN 70" remote-address=192.168.26.118 service=\
    pppoe
add comment="15-13-151   JEMPIERO PRIMO WANLY" name=JEAMPIEROJARDINESTPLINK \
    password=112233 profile="PLAN 100 NW" remote-address=192.168.55.254 \
    service=pppoe
add comment="1-42-354 DEISI DELGADO CASA MAMA" name=\
    DEISIDELGADOMAMA#EDCCC2@DC20D7* password=112233 profile="PLAN 50 SOLES" \
    remote-address=192.168.26.116 service=pppoe
add comment="5-64-355  CHIFA DIDI SARTEN  ORO 04/10/2022" name=\
    CHIFADIDISARTENORO#EDCCEC@DCF8E0* password=112233 profile="PLAN 70 NEW" \
    remote-address=192.168.26.115 service=pppoe
add comment="5-65-356 MARIORTIZ LAGUNA BODEG JARDINES 04/10/2022" name=\
    LUISAORTIZBODEGJARDNES#484465@B948D8* password=112233 profile=\
    "PLAN 70 NEW" remote-address=192.168.26.114 service=pppoe
add comment=\
    "5-66-357 ELIZABET JARAMILLO BRIOSO FRENTE A LUISA ORTIZ 04/10/2022" \
    name=ELIZABETHJARABRIOSO#EDCCD0@DC680A* password=112233 profile=\
    "PLAN 70 NEW" remote-address=192.168.26.113 service=pppoe
add comment=\
    "2-43-358  JEANCARLOSGRANDA ATRAS POLLERIAS ROYS LA VILLA 05/10/2022" \
    name=JEANCARLOSGRANDAATRASROYS#EDCCB0@DCF80C* password=112233 profile=\
    "PLAN 70" remote-address=192.168.26.112 service=pppoe
add comment="5-67-35 ROY PORTAL CASA MINCHOLA LOS JARDINES 07/10/2022" name=\
    ROYPORTALCASAMIN#EDCCC1@DC88B2* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.111 service=pppoe
add comment="CASA ERIKA MINCHOLA LA VILLA 12/10/2022" name=\
    MIRIAMMINCHOLALAVILL#EDCCC3@DC1033* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.110 service=pppoe
add comment="1-42-361 TIENDA SOLGAS JOSE FRIAS COSTADO MAITA 13/10/2022" \
    name=LESLIFRIASJOSE#EDCCEC@DC38E7* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.109 service=pppoe
add comment="5-68-362  YASMIN MARI\D1O INQ MARILU MASIEL 14/10/2022" name=\
    YASMINMARINOMARILU#EDCCC1@DC7859* password=112233 profile="PLAN 70" \
    remote-address=192.168.26.108 service=pppoe
add name=antonycherolubio password=nohacker3217 profile="PLAN 70 NEW" \
    remote-address=192.168.210.231 service=pppoe
add name=11223344 password=112233 profile="PLAN 150 SOLES NEW" \
    remote-address=192.168.22.189
/system clock
set time-zone-name=America/Lima
/system identity
set name="GIGAFIBER 1036"
/system logging
set 3 disabled=yes
/system package update
set channel=upgrade
/system scheduler
add disabled=yes interval=1m name=failover on-event=failover policy=\
    ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
    start-date=mar/24/2023 start-time=15:07:23
/system script
add dont-require-permissions=no name=failover owner=gigafiber2023 policy=\
    ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon source=":\
    local gwPrimaryComment \"GATEWAY_FIBERLUX\"\r\
    \n:local gwBackupComment \"GATEWAY_RESPALDO\"\r\
    \n:local gwPrimary 38.43.135.105\r\
    \n:local gwBackup 111.111.111.1\r\
    \n:local pingResultPrimary\r\
    \n:local pingResultBackup\r\
    \n\r\
    \n:set \$pingResultPrimary [/ping count=20 address=8.8.8.8  interface=sfp-\
    sfpplus1 routing-table=to_ISP1]\r\
    \n:set \$pingResultBackup [/ping count=20 address=8.8.8.8 interface=ether1\
    \_routing-table=to_ISP2]\r\
    \n\r\
    \n:if (\$pingResultPrimary=0 && \$pingResultBackup=0) do={\r\
    \n  /ip route enable [find gateway=\$gwPrimary && comment=\$gwPrimaryComme\
    nt]\r\
    \n  /ip route disable [find gateway=\$gwBackup && comment=\$gwBackupCommen\
    t]\r\
    \n  :log info \"Ambas gateways estan caidas, se dirige el tr\E1fico por la\
    \_ruta principal \$gwPrimary\"\r\
    \n  :put \"Ambas gateways estan caidas, se dirige el tr\E1fico por la ruta\
    \_principal \$gwPrimary\"\r\
    \n} else={\r\
    \n  :if (\$pingResultPrimary >= 12) do={\r\
    \n    /ip route enable [find gateway=\$gwPrimary && comment=\$gwPrimaryCom\
    ment]\r\
    \n    /ip route disable [find gateway=\$gwBackup && comment=\$gwBackupComm\
    ent]\r\
    \n    :log info \"La gateway principal \$gwPrimary es estable, se dirige e\
    l tr\E1fico por ella\"\r\
    \n    :put \"La gateway principal \$gwPrimary es estable, se dirige el tr\
    \E1fico por ella\"\r\
    \n  } else={\r\
    \n    /ip route disable [find gateway=\$gwPrimary && comment=\$gwPrimaryCo\
    mment]\r\
    \n    /ip route enable [find gateway=\$gwBackup && comment=\$gwBackupComme\
    nt]\r\
    \n    :log info \"La gateway de respaldo \$gwBackup es estable, se dirige \
    el tr\E1fico por ella\"\r\
    \n    :put \"La gateway de respaldo \$gwBackup es estable, se dirige el tr\
    \E1fico por ella\"\r\
    \n  }\r\
    \n}\r\
    \n"
add dont-require-permissions=no name=example owner=gigafiber2023 policy=\
    ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon source=":\
    local queueList [:toarray [/queue simple find]]\r\
    \n:foreach queueId in=\$queueList do={\r\
    \n  :local queueInfo [/queue simple get \$queueId]\r\
    \n  :local targetIP [:pick \$queueInfo 24]\r\
    \n\r\
    \n  :if (\$targetIP != \"\" and \$targetIP != \"0\") do={\r\
    \n    :put (\$targetIP)\r\
    \n  }\r\
    \n}"
add dont-require-permissions=no name=script1 owner=gigafiber2023 policy=\
    ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon source=":\
    foreach arpEntry in=[/ip arp find] do={\r\
    \n  :local ipAddress [/ip arp get \$arpEntry address]\r\
    \n  :put (\$ipAddress)\r\
    \n}\r\
    \n"
/tool graphing interface
add interface=sfp-sfpplus1
/tool graphing resource
add
