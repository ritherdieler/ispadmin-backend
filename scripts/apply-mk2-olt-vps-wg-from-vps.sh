#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE="${ROOT}/mikrotik-mk2-olt-vps-wg.rsc"
SETUP="${ROOT}/setup-vps-olt-wg.sh"
SECRETS_DIR="${SECRETS_DIR:-/opt/gigafiber/secrets}"
KEYS_FILE="${SECRETS_DIR}/wg-olt.keys"
WG_CONF="/etc/wireguard/wg-olt.conf"

VPS_PUBLIC_IP="${VPS_PUBLIC_IP:-212.85.13.47}"
MK_HOST="${MK2_PUBLIC_IP:-38.224.231.4}"
MK_WG_PORT="${MK_WG_PORT:-51830}"
VPS_WG_PORT="${VPS_WG_PORT:-51820}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run on VPS as root" >&2
  exit 1
fi

if [[ ! -f "$TEMPLATE" ]]; then
  echo "Missing $TEMPLATE" >&2
  exit 1
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
if [[ -z "$MYSQL_ROOT_PASSWORD" ]]; then
  if [[ -f /opt/gigafiber/.env ]]; then
    # shellcheck disable=SC1091
    source /opt/gigafiber/.env 2>/dev/null || true
  fi
fi
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
if [[ -z "$MYSQL_ROOT_PASSWORD" ]] && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx mysql8033; then
  MYSQL_ROOT_PASSWORD="$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD 2>/dev/null || true)"
fi
if [[ -z "$MYSQL_ROOT_PASSWORD" ]]; then
  echo "Set MYSQL_ROOT_PASSWORD or ensure mysql8033 exposes MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

if ! command -v wg >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y wireguard-tools
fi

umask 077
mkdir -p "$SECRETS_DIR"

if [[ ! -f "$KEYS_FILE" ]]; then
  VPS_PRIV=$(wg genkey)
  VPS_PUB=$(printf '%s' "$VPS_PRIV" | wg pubkey)
  MK2_PRIV=$(wg genkey)
  MK2_PUB=$(printf '%s' "$MK2_PRIV" | wg pubkey)
  cat >"$KEYS_FILE" <<EOF
VPS_PRIVATE_KEY=${VPS_PRIV}
VPS_PUBLIC_KEY=${VPS_PUB}
MK2_PRIVATE_KEY=${MK2_PRIV}
MK2_PUBLIC_KEY=${MK2_PUB}
EOF
  chmod 600 "$KEYS_FILE"
  echo "Generated ${KEYS_FILE}"
else
  # shellcheck disable=SC1090
  source "$KEYS_FILE"
  if [[ -z "${VPS_PRIVATE_KEY:-}" || -z "${MK2_PUBLIC_KEY:-}" || -z "${MK2_PRIVATE_KEY:-}" || -z "${VPS_PUBLIC_KEY:-}" ]]; then
    echo "Invalid ${KEYS_FILE}" >&2
    exit 1
  fi
fi

# shellcheck disable=SC1090
source "$KEYS_FILE"

mkdir -p /etc/wireguard
cat >"$WG_CONF" <<EOF
[Interface]
Address = 10.255.255.2/30
ListenPort = ${VPS_WG_PORT}
PrivateKey = ${VPS_PRIVATE_KEY}

[Peer]
PublicKey = ${MK2_PUBLIC_KEY}
Endpoint = ${MK_HOST}:${MK_WG_PORT}
AllowedIPs = 10.255.255.1/32, 10.11.104.0/24
PersistentKeepalive = 25
EOF
chmod 600 "$WG_CONF"

REMOTE_RSC="mk2-wg-vps.rsc"
TMP_RSC=$(mktemp)
export TEMPLATE MK2_PRIVATE_KEY VPS_PUBLIC_KEY TMP_RSC
python3 <<'PY'
import os
from pathlib import Path
text = Path(os.environ["TEMPLATE"]).read_text()
text = text.replace("REPLACE_MK2_PRIVATE_KEY", os.environ["MK2_PRIVATE_KEY"])
text = text.replace("REPLACE_VPS_PUBLIC_KEY", os.environ["VPS_PUBLIC_KEY"])
Path(os.environ["TMP_RSC"]).write_text(text)
PY

MK_PASS=$(docker exec mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ispadmin -N -e "SELECT password FROM network_device WHERE id=8;" 2>/dev/null)
MK_USER=$(docker exec mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ispadmin -N -e "SELECT username FROM network_device WHERE id=8;" 2>/dev/null)

export SSHPASS="$MK_PASS"
sshpass -e scp -o StrictHostKeyChecking=no "$TMP_RSC" "${MK_USER}@${MK_HOST}:${REMOTE_RSC}"
sshpass -e ssh -o StrictHostKeyChecking=no "${MK_USER}@${MK_HOST}" "/import file-name=${REMOTE_RSC}"
rm -f "$TMP_RSC"

systemctl disable --now vps-olt-gre.service 2>/dev/null || true

MK_PUBLIC_IP="$MK_HOST" "$SETUP"

echo "MK2 WireGuard + VPS route OK; verify: ping -c1 10.11.104.2"
