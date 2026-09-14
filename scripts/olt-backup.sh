#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${OLT_BACKUP_DIR:-$ROOT/backups/olt}"
TFTP_HOST="${OLT_TFTP_HOST:-212.85.13.47}"
OLT_PASS="${OLT_GATEWAY_PASSWORD:-}"
VPS_PASS="${DEPLOY_SSH_PASSWORD:-}"
VPS_USER="${VPS_USER:-root}"
VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_TFTP_DIR="/tmp/olt-backup"

if [[ -z "$OLT_PASS" ]]; then
  echo "Defina OLT_GATEWAY_PASSWORD" >&2
  exit 1
fi
if [[ -z "$VPS_PASS" ]]; then
  if [[ -f "$ROOT/scripts/deploy.config.local" ]]; then
    # shellcheck disable=SC1091
    source "$ROOT/scripts/deploy.config.local"
    VPS_PASS="${DEPLOY_SSH_PASSWORD:-}"
  fi
fi
if [[ -z "$VPS_PASS" ]]; then
  echo "Defina DEPLOY_SSH_PASSWORD o scripts/deploy.config.local" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
CFG="ma5608t-${STAMP}.cfg"
DAT="ma5608t-${STAMP}.dat"
mkdir -p "$BACKUP_DIR"

echo "Preparando TFTP en $VPS_HOST..."
sshpass -p "$VPS_PASS" ssh -o StrictHostKeyChecking=no "${VPS_USER}@${VPS_HOST}" bash -s <<REMOTE
set -e
mkdir -p $VPS_TFTP_DIR
chmod 777 $VPS_TFTP_DIR
pkill -f in.tftpd 2>/dev/null || true
sleep 1
nohup /usr/sbin/in.tftpd --listen --user root --address 0.0.0.0:69 --create --secure $VPS_TFTP_DIR >/tmp/tftpd.log 2>&1 &
ufw allow 69/udp >/dev/null 2>&1 || true
REMOTE

echo "Backup nativo OLT → TFTP $TFTP_HOST ..."
"$ROOT/scripts/olt-tftp-backup.expect" "$CFG" "$DAT" "$TFTP_HOST" "$OLT_PASS" \
  2>&1 | tee "$BACKUP_DIR/ma5608t-${STAMP}-tftp-session.log" || {
  echo "Backup config parcial o sesión cortada; intentando solo data..." >&2
  "$ROOT/scripts/olt-tftp-backup-data.expect" "$DAT" "$TFTP_HOST" "$OLT_PASS" \
    2>&1 | tee -a "$BACKUP_DIR/ma5608t-${STAMP}-data-retry.log" || true
}

echo "Descargando archivos desde VPS..."
sshpass -p "$VPS_PASS" scp -o StrictHostKeyChecking=no \
  "${VPS_USER}@${VPS_HOST}:${VPS_TFTP_DIR}/${CFG}" "$BACKUP_DIR/" 2>/dev/null || true
sshpass -p "$VPS_PASS" scp -o StrictHostKeyChecking=no \
  "${VPS_USER}@${VPS_HOST}:${VPS_TFTP_DIR}/${DAT}" "$BACKUP_DIR/" 2>/dev/null || true

cat > "$BACKUP_DIR/ma5608t-${STAMP}-manifest.txt" <<MANIFEST
timestamp=$STAMP
olt_host=${OLT_HOST:-10.11.104.2}
tftp_server=$TFTP_HOST
config_file=$CFG
data_file=$DAT
MANIFEST

ls -la "$BACKUP_DIR/$CFG" "$BACKUP_DIR/$DAT" 2>/dev/null || {
  echo "Revise logs en $BACKUP_DIR" >&2
  exit 1
}
echo "Backup completado en $BACKUP_DIR"
