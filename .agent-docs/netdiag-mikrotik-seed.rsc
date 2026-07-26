# NetDiag seed — RouterOS 7 Netwatch / logging / SNMP (DOCUMENTADO, NO auto-aplicar)
# Target de referencia: MK1 38.224.231.2 (RouterOS 7.23.2)
# Revisar y aplicar manualmente en ventana de mantenimiento.
# El backend NUNCA ejecuta este script contra routers de producción.

# --- Netwatch upstream (HTTP + DNS) ---
/tool netwatch
add name=upstream-http type=http-get host=http://1.1.1.1 interval=30s timeout=5s \
    comment="netdiag-upstream" disabled=no
add name=upstream-dns type=dns host=1.1.1.1 interval=30s timeout=5s \
    comment="netdiag-upstream" disabled=no

# --- Logging remoto (syslog UDP hacia VPS netdiag) ---
# Ajustar remote=IP_DEL_VPS y puerto (default netdiag 5514)
/system logging action
add name=netdiag-remote target=remote remote=212.85.13.47 remote-port=5514 \
    src-address=0.0.0.0 bsd-syslog=yes syslog-facility=local0

/system logging
add action=netdiag-remote topics=interface,warning
add action=netdiag-remote topics=bridge,warning
add action=netdiag-remote topics=system,critical
add action=netdiag-remote topics=ppp,info

# --- SNMP traps (push) ---
# Ajustar trap-generators / trap-target al VPS (UDP 1620 o relay HTTP)
/snmp
set enabled=yes contact="noc@gigafiber" location="MK1"
/snmp community
set [find default=yes] name=public write-access=no
/snmp
set trap-generators=interfaces,start-trap,temp-exception \
    trap-target=212.85.13.47 \
    trap-version=2 \
    trap-community=public

# --- Verificación rápida ---
# /tool netwatch print
# /system logging print
# /snmp print
# /interface ethernet monitor sfp-sfpplus1 once
