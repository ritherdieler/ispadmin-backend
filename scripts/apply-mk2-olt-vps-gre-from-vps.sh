#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
RSC="${ROOT}/mikrotik-mk2-olt-vps-gre.rsc"
SETUP="${ROOT}/setup-vps-olt-gre.sh"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run on VPS as root" >&2
  exit 1
fi

if [[ ! -f "$RSC" ]]; then
  echo "Missing $RSC" >&2
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
if [[ -z "$MYSQL_ROOT_PASSWORD" ]]; then
  echo "Set MYSQL_ROOT_PASSWORD or SPRING_DATASOURCE_PASSWORD" >&2
  exit 1
fi

MK_PASS=$(docker exec mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ispadmin -N -e "SELECT password FROM network_device WHERE id=8;" 2>/dev/null)
MK_USER=$(docker exec mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ispadmin -N -e "SELECT username FROM network_device WHERE id=8;" 2>/dev/null)
MK_HOST="${MK2_PUBLIC_IP:-38.224.231.4}"

export SSHPASS="$MK_PASS"
REMOTE_RSC="mk2-gre-vps.rsc"
sshpass -e scp -o StrictHostKeyChecking=no "$RSC" "${MK_USER}@${MK_HOST}:${REMOTE_RSC}"
sshpass -e ssh -o StrictHostKeyChecking=no "${MK_USER}@${MK_HOST}" "/import file-name=${REMOTE_RSC}"

MK_PUBLIC_IP="$MK_HOST" "$SETUP"

echo "MK2 GRE + VPS route OK; verify: ping -c1 10.11.104.2"
