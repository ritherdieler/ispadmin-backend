#!/usr/bin/env bash
# Amplía wg-olt (VPS ↔ MK2) para enrutar pools de abonados hacia GenieACS Connection Request.
#
# Requisitos:
#   - Ejecutar en el VPS como root (212.85.13.47)
#   - Túnel wg-olt ya operativo (MK2 peer 38.224.231.4:51830)
#   - Después aplicar mk2-genieacs-cr-forward.rsc en MK2
#
# Uso en VPS:
#   bash apply-genieacs-wg-customer-routes.sh
#   bash apply-genieacs-wg-customer-routes.sh --dry-run
set -euo pipefail

WG_CONF="/etc/wireguard/wg-olt.conf"
DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

# Sincronizado con ip_pool prod (gateway .1/24 → red .0/24). Regenerar desde MySQL si cambia.
CUSTOMER_CIDRS="192.168.0.0/24,192.168.9.0/24,192.168.20.0/24,192.168.22.0/24,192.168.25.0/24,192.168.26.0/24,192.168.30.0/24,192.168.33.0/24,192.168.49.0/24,192.168.88.0/24,192.168.93.0/24,192.168.95.0/24,192.168.123.0/24,192.168.200.0/24,192.168.210.0/24,192.168.211.0/24,192.168.212.0/24,192.168.213.0/24,192.168.220.0/24,192.168.221.0/24,192.168.250.0/24,192.168.252.0/22,192.169.22.0/24"

NEW_ALLOWED="10.255.255.1/32, 10.11.104.0/24, ${CUSTOMER_CIDRS}"

if [[ ! -f "$WG_CONF" ]]; then
  echo "No existe $WG_CONF" >&2
  exit 1
fi

echo "AllowedIPs propuesto:"
echo "  $NEW_ALLOWED"
echo

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "(dry-run, sin cambios)"
  exit 0
fi

cp -a "$WG_CONF" "${WG_CONF}.bak-genieacs-$(date +%Y%m%d%H%M%S)"

python3 - <<PY
from pathlib import Path
import re
path = Path("$WG_CONF")
text = path.read_text()
new_allowed = "$NEW_ALLOWED"
text, n = re.subn(
    r"^AllowedIPs\s*=.*$",
    f"AllowedIPs = {new_allowed}",
    text,
    count=1,
    flags=re.M,
)
if n != 1:
    raise SystemExit("No se encontró línea AllowedIPs en wg-olt.conf")
path.write_text(text)
print("Actualizado:", path)
PY

wg syncconf wg-olt <(wg-quick strip wg-olt)
echo "wg syncconf OK"

IFS=',' read -ra NETS <<< "$CUSTOMER_CIDRS"
for net in "${NETS[@]}"; do
  ip route replace "$net" dev wg-olt
done
# Rutas OLT (por si syncconf no las dejó)
ip route replace 10.11.104.0/24 dev wg-olt 2>/dev/null || true
ip route replace 10.255.255.1/32 dev wg-olt 2>/dev/null || true

echo "Rutas vía wg-olt (muestra):"
ip route | grep wg-olt | head -8
echo "..."
echo "total wg-olt routes: $(ip route | grep -c wg-olt || true)"
