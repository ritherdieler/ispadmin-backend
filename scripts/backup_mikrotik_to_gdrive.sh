#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/backup_gdrive_common.sh"
backup_source_env_files

MK_HOST="${BACKUP_MIKROTIK_HOST:-38.224.231.2}"
MK_USER="${BACKUP_MIKROTIK_USER:-gigafiber2023}"
MK_PASS="${BACKUP_MIKROTIK_PASSWORD:-}"
PREFIX="${BACKUP_MIKROTIK_PREFIX:-administrativeMK}"
LOCAL_DIR="${BACKUP_MIKROTIK_LOCAL_DIR:-/opt/gigafiber/mikrotikBackups}"
REMOTE_DIR="${BACKUP_MIKROTIK_REMOTE_DIR:-mikrotikBackups}"
LOG_FILE="${BACKUP_MIKROTIK_LOG:-/var/log/mikrotik-backup.log}"
REMOTE_RETENTION_DAYS="${BACKUP_MIKROTIK_REMOTE_RETENTION_DAYS:-14}"
LOCAL_RETENTION_MINUTES="${BACKUP_MIKROTIK_LOCAL_RETENTION_MINUTES:-720}"

TS="$(date +%F_%H-%M-%S)"
BASE="${PREFIX}_${TS}"
BIN_FILE="${BASE}.backup"
RSC_FILE="${BASE}.rsc"

log(){ echo "[$(date '+%F %T')] $*" | tee -a "$LOG_FILE"; }

if [[ -z "$MK_PASS" ]]; then
  log "ERROR: BACKUP_MIKROTIK_PASSWORD is empty (set in /opt/gigafiber/scripts/backup.env)"
  exit 1
fi

mkdir -p "$LOCAL_DIR"
log "Starting MikroTik backup"
sshpass -p "$MK_PASS" ssh -o ConnectTimeout=20 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "$MK_USER@$MK_HOST" "/system backup save name=${BASE}; /export file=${BASE}" >/dev/null
sshpass -p "$MK_PASS" scp -o ConnectTimeout=20 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "$MK_USER@$MK_HOST:${BIN_FILE}" "$LOCAL_DIR/${BIN_FILE}"
sshpass -p "$MK_PASS" scp -o ConnectTimeout=20 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "$MK_USER@$MK_HOST:${RSC_FILE}" "$LOCAL_DIR/${RSC_FILE}"
sshpass -p "$MK_PASS" ssh -o ConnectTimeout=20 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  "$MK_USER@$MK_HOST" "/file remove [find name=${BIN_FILE}]; /file remove [find name=${RSC_FILE}]" >/dev/null || true
log "Local files created: ${BIN_FILE}, ${RSC_FILE}"

find "$LOCAL_DIR" -type f -name "${PREFIX}_*" -mmin "+${LOCAL_RETENTION_MINUTES}" -print -delete \
  | sed 's/^/[LOCAL-DELETE] /' | tee -a "$LOG_FILE" || true

if ! rclone lsd "${REMOTE_NAME}:" >/dev/null 2>&1; then
  log "WARNING: rclone remote '${REMOTE_NAME}' not configured. Skipping upload."
  log "MikroTik backup job finished"
  exit 0
fi

BIN_BYTES="$(stat -c%s "$LOCAL_DIR/${BIN_FILE}" 2>/dev/null || stat -f%z "$LOCAL_DIR/${BIN_FILE}")"
RSC_BYTES="$(stat -c%s "$LOCAL_DIR/${RSC_FILE}" 2>/dev/null || stat -f%z "$LOCAL_DIR/${RSC_FILE}")"
NEED_BYTES=$((BIN_BYTES + RSC_BYTES))

prune_remote_min_age "$REMOTE_DIR" "${PREFIX}_*" "$REMOTE_RETENTION_DAYS"
log "Remote retention cleanup executed (>${REMOTE_RETENTION_DAYS} days)"
enforce_drive_budget_for_upload "$NEED_BYTES" log || true

set +e
rclone copyto "$LOCAL_DIR/${BIN_FILE}" "${REMOTE_NAME}:${REMOTE_DIR}/${BIN_FILE}"
rc1=$?
rclone copyto "$LOCAL_DIR/${RSC_FILE}" "${REMOTE_NAME}:${REMOTE_DIR}/${RSC_FILE}"
rc2=$?
set -e
if (( rc1 != 0 || rc2 != 0 )); then
  log "ERROR: rclone copyto failed rc_bin=${rc1} rc_rsc=${rc2} (remote prune already ran)"
  exit 1
fi
log "Uploaded to Google Drive: ${REMOTE_DIR}/${BIN_FILE} and ${RSC_FILE}"
log "MikroTik backup job finished"
