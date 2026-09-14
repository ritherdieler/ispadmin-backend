# GenieACS Connection Request: VPS (10.255.255.2) → ONUs (TR-069 :7547) vía wg-ispadmin-vps
# Aplicar en MK2 (38.224.231.4) con usuario admin RouterOS.
# Idempotente: no duplica si el comment ya existe.

:if ([:len [/ip firewall filter find where comment="GenieACS Connection Request from VPS"]] = 0) do={
  /ip firewall filter add chain=forward action=accept protocol=tcp dst-port=7547 \
    src-address=10.255.255.2 in-interface=wg-ispadmin-vps \
    place-before=[/ip firewall filter find where comment="IspAdmin VPS WG forward"] \
    comment="GenieACS Connection Request from VPS"
}

:if ([:len [/ip firewall filter find where comment="GenieACS CR return to VPS"]] = 0) do={
  /ip firewall filter add chain=forward action=accept connection-state=established,related \
    out-interface=wg-ispadmin-vps \
    place-before=[/ip firewall filter find where comment="IspAdmin VPS WG forward"] \
    comment="GenieACS CR return to VPS"
}
