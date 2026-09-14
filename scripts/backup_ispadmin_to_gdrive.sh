#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/backup_gdrive_common.sh"
backup_source_env_files

DB_CONTAINER="${BACKUP_MYSQL_CONTAINER:-mysql8033}"
DB_NAME="${BACKUP_MYSQL_DB:-ispadmin}"
DB_USER="${BACKUP_MYSQL_USER:-root}"
DB_PASS="${BACKUP_MYSQL_PASSWORD:-}"
LOCAL_DIR="${BACKUP_MYSQL_LOCAL_DIR:-/opt/gigafiber/gigafiberDatabaseBackups}"
REMOTE_DIR="${MYSQL_REMOTE_DIR}"
LOG_FILE="${BACKUP_MYSQL_LOG:-/var/log/ispadmin-backup.log}"
REMOTE_RETENTION_DAYS="${BACKUP_MYSQL_REMOTE_RETENTION_DAYS:-7}"
LOCAL_RETENTION_MINUTES="${BACKUP_MYSQL_LOCAL_RETENTION_MINUTES:-720}"

TS="$(date +%F_%H-%M-%S)"
FILE="${DB_NAME}_${TS}.sql.gz"
LOCAL_FILE="${LOCAL_DIR}/${FILE}"

log(){ echo "[$(date '+%F %T')] $*" | tee -a "$LOG_FILE"; }

if [[ -z "$DB_PASS" ]]; then
  log "ERROR: BACKUP_MYSQL_PASSWORD is empty (set in /opt/gigafiber/scripts/backup.env)"
  exit 1
fi

mkdir -p "$LOCAL_DIR"
log "Starting backup: $DB_NAME"
docker exec "$DB_CONTAINER" sh -c "exec mysqldump -u${DB_USER} -p'${DB_PASS}' --single-transaction --quick --routines --events ${DB_NAME}" | gzip > "$LOCAL_FILE"
log "Local backup created: $LOCAL_FILE ($(stat -c%s "$LOCAL_FILE" 2>/dev/null || stat -f%z "$LOCAL_FILE") B)"

find "$LOCAL_DIR" -type f -name "${DB_NAME}_*.sql.gz" -mmin "+${LOCAL_RETENTION_MINUTES}" -print -delete \
  | sed 's/^/[LOCAL-DELETE] /' | tee -a "$LOG_FILE" || true

if ! is_mysql_drive_upload_hour; then
  log "Skipping Drive upload (not a 6h slot in America/Lima; set BACKUP_FORCE_UPLOAD=1 to override)"
  log "Backup job finished (local only)"
  exit 0
fi

if ! rclone lsd "${REMOTE_NAME}:" >/dev/null 2>&1; then
  log "WARNING: rclone remote '${REMOTE_NAME}' not configured yet. Skipping upload."
  log "Backup job finished"
  exit 0
fi

FILE_BYTES="$(stat -c%s "$LOCAL_FILE" 2>/dev/null || stat -f%z "$LOCAL_FILE")"
prune_remote_min_age "$REMOTE_DIR" "${DB_NAME}_*.sql.gz" "$REMOTE_RETENTION_DAYS"
log "Remote retention cleanup executed (>${REMOTE_RETENTION_DAYS} days)"
enforce_drive_budget_for_upload "$FILE_BYTES" log || true

set +e
rclone copyto "$LOCAL_FILE" "${REMOTE_NAME}:${REMOTE_DIR}/${FILE}"
upload_rc=$?
set -e
if (( upload_rc != 0 )); then
  log "ERROR: rclone copyto failed rc=${upload_rc} (remote prune already ran)"
  exit "$upload_rc"
fi
log "Uploaded to Google Drive: ${REMOTE_NAME}:${REMOTE_DIR}/${FILE}"
log "Backup job finished"
