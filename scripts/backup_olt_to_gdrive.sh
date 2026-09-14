#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/backup_gdrive_common.sh"
backup_source_env_files

OLT_SSH_HOST="${BACKUP_OLT_SSH_HOST:-10.11.104.2}"
OLT_SSH_PORT="${BACKUP_OLT_SSH_PORT:-22}"
OLT_SSH_USER="${BACKUP_OLT_SSH_USER:-oltadmin}"
OLT_PASS="${BACKUP_OLT_PASSWORD:-${OLT_GATEWAY_PASSWORD:-}}"
TFTP_HOST="${BACKUP_OLT_TFTP_HOST:-212.85.13.47}"
TFTP_DIR="${BACKUP_OLT_TFTP_DIR:-/tmp/olt-backup}"
PREFIX="${BACKUP_OLT_PREFIX:-ma5608t}"
LOCAL_DIR="${BACKUP_OLT_LOCAL_DIR:-/opt/gigafiber/oltBackups}"
REMOTE_DIR="${BACKUP_OLT_REMOTE_DIR:-oltBackups}"
LOG_FILE="${BACKUP_OLT_LOG:-/var/log/olt-backup.log}"
LOCK_FILE="/var/run/olt-backup.lock"
EXPECT_SAVE_CFG="${SCRIPT_DIR}/olt-tftp-save-cfg.expect"
EXPECT_CFG="${SCRIPT_DIR}/olt-tftp-backup-cfg.expect"
EXPECT_DATA="${SCRIPT_DIR}/olt-tftp-backup-data.expect"
RETENTION_DAYS="${BACKUP_OLT_REMOTE_RETENTION_DAYS:-14}"
LOCAL_RETENTION_MINUTES="${BACKUP_OLT_LOCAL_RETENTION_MINUTES:-720}"
MIN_CFG_BYTES=50000
MIN_DAT_BYTES=100000
SAVE_WAIT_SECS="${OLT_BACKUP_SAVE_WAIT_SECS:-180}"
SESSION_WAIT_SECS="${OLT_BACKUP_SESSION_WAIT_SECS:-30}"

TS="$(date +%F_%H-%M-%S)"
BASE="${PREFIX}_${TS}"
CFG_FILE="${BASE}.cfg"
DAT_FILE="${BASE}.dat"

log(){ echo "[$(date '+%F %T')] $*" | tee -a "$LOG_FILE"; }

wait_for_file() {
  local path="$1"
  local min_bytes="$2"
  local label="$3"
  local stable=0
  local last_size=-1
  local i size
  for i in $(seq 1 72); do
    if [[ -f "$path" ]]; then
      size=$(stat -c%s "$path" 2>/dev/null || echo 0)
      if (( size >= min_bytes )); then
        if (( size == last_size )); then
          stable=$((stable + 1))
          if (( stable >= 3 )); then
            log "${label} ready: ${path} (${size} B)"
            return 0
          fi
        else
          stable=0
          last_size=$size
        fi
      else
        last_size=$size
        stable=0
      fi
    fi
    sleep 5
  done
  return 1
}

run_expect() {
  local label="$1"
  shift
  local session_log="$1"
  shift
  log "Running ${label}"
  set +e
  "$@" 2>&1 | tee -a "$session_log"
  local rc=${PIPESTATUS[0]}
  set -e
  log "${label} finished rc=${rc}"
  return "$rc"
}

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "WARNING: another OLT backup is running; exiting"
  exit 0
fi

cleanup_tftpd() {
  pkill -f 'in.tftpd' 2>/dev/null || true
}
trap cleanup_tftpd EXIT

mkdir -p "$LOCAL_DIR" "$TFTP_DIR"
chmod 777 "$TFTP_DIR" >/dev/null 2>&1 || true

log "Starting OLT backup"

if [[ -z "$OLT_PASS" ]]; then
  log "ERROR: BACKUP_OLT_PASSWORD (or OLT_GATEWAY_PASSWORD) is empty"
  exit 1
fi

if [[ ! -x "$EXPECT_SAVE_CFG" || ! -x "$EXPECT_CFG" || ! -x "$EXPECT_DATA" ]]; then
  log "ERROR: missing expect scripts in ${SCRIPT_DIR}"
  exit 1
fi

if [[ -x "${SCRIPT_DIR}/setup-vps-olt-gre.sh" ]]; then
  if ! "${SCRIPT_DIR}/setup-vps-olt-gre.sh" >/tmp/olt-gre-setup.log 2>&1; then
    log "WARNING: GRE setup reported failure; checking reachability anyway"
    cat /tmp/olt-gre-setup.log >>"$LOG_FILE" || true
  fi
fi
reachable=0
for i in $(seq 1 15); do
  if ping -c 1 -W 3 "$OLT_SSH_HOST" >/dev/null 2>&1; then
    reachable=1
    break
  fi
  sleep 2
done
if (( reachable != 1 )); then
  log "ERROR: OLT ${OLT_SSH_HOST} unreachable (GRE/route?)"
  exit 1
fi
log "OLT reachability OK"

pkill -f 'in.tftpd' 2>/dev/null || true
sleep 1
/usr/sbin/in.tftpd --listen --user root --address 0.0.0.0:69 --create --secure "$TFTP_DIR" \
  9>&- >/tmp/tftpd-olt.log 2>&1 &
sleep 1
ufw allow 69/udp >/dev/null 2>&1 || true

rm -f "${TFTP_DIR}/${CFG_FILE}" "${TFTP_DIR}/${DAT_FILE}" 2>/dev/null || true

SESSION_LOG="${LOCAL_DIR}/${BASE}-session.log"
: > "$SESSION_LOG"

log "Waiting ${SESSION_WAIT_SECS}s for previous OLT SSH sessions to clear"
sleep "$SESSION_WAIT_SECS"

set +e
run_expect "save-cfg" "$SESSION_LOG" \
  "$EXPECT_SAVE_CFG" "$OLT_PASS" "$OLT_SSH_HOST" "$OLT_SSH_PORT" "$OLT_SSH_USER"
set -e

log "Waiting ${SAVE_WAIT_SECS}s for OLT save configuration to finish"
sleep "$SAVE_WAIT_SECS"

set +e
run_expect "cfg-backup" "$SESSION_LOG" \
  "$EXPECT_CFG" "$CFG_FILE" "$TFTP_HOST" "$OLT_PASS" \
  "$OLT_SSH_HOST" "$OLT_SSH_PORT" "$OLT_SSH_USER"
set -e
wait_for_file "${TFTP_DIR}/${CFG_FILE}" "$MIN_CFG_BYTES" "CFG" || true

log "Waiting ${SESSION_WAIT_SECS}s before data backup session"
sleep "$SESSION_WAIT_SECS"

set +e
run_expect "data-backup" "$SESSION_LOG" \
  "$EXPECT_DATA" "$DAT_FILE" "$TFTP_HOST" "$OLT_PASS" \
  "$OLT_SSH_HOST" "$OLT_SSH_PORT" "$OLT_SSH_USER"
set -e
wait_for_file "${TFTP_DIR}/${DAT_FILE}" "$MIN_DAT_BYTES" "DAT" || true

if [[ ! -f "${TFTP_DIR}/${CFG_FILE}" ]]; then
  log "ERROR: missing config file ${CFG_FILE} in ${TFTP_DIR}"
  exit 1
fi
if [[ ! -f "${TFTP_DIR}/${DAT_FILE}" ]]; then
  log "ERROR: missing data file ${DAT_FILE} in ${TFTP_DIR}"
  exit 1
fi

CFG_SIZE=$(stat -c%s "${TFTP_DIR}/${CFG_FILE}")
DAT_SIZE=$(stat -c%s "${TFTP_DIR}/${DAT_FILE}")
if (( CFG_SIZE < MIN_CFG_BYTES )); then
  log "ERROR: config file too small (${CFG_SIZE} B)"
  exit 1
fi
if (( DAT_SIZE < MIN_DAT_BYTES )); then
  log "ERROR: data file too small (${DAT_SIZE} B)"
  exit 1
fi

cp -f "${TFTP_DIR}/${CFG_FILE}" "${LOCAL_DIR}/${CFG_FILE}"
cp -f "${TFTP_DIR}/${DAT_FILE}" "${LOCAL_DIR}/${DAT_FILE}"
chmod 600 "${LOCAL_DIR}/${CFG_FILE}" "${LOCAL_DIR}/${DAT_FILE}" || true

log "Local files created: ${CFG_FILE} (${CFG_SIZE} B), ${DAT_FILE} (${DAT_SIZE} B)"

find "$LOCAL_DIR" -type f \( -name "${PREFIX}_*.cfg" -o -name "${PREFIX}_*.dat" -o -name "${PREFIX}_*-session.log" \) \
  -mmin "+${LOCAL_RETENTION_MINUTES}" -print -delete \
  | sed 's/^/[LOCAL-DELETE] /' | tee -a "$LOG_FILE" || true

if ! rclone lsd "${REMOTE_NAME}:" >/dev/null 2>&1; then
  log "WARNING: rclone remote '${REMOTE_NAME}' not configured. Skipping upload."
  log "OLT backup job finished"
  exit 0
fi

NEED_BYTES=$((CFG_SIZE + DAT_SIZE))
prune_remote_min_age "$REMOTE_DIR" "${PREFIX}_*" "$RETENTION_DAYS"
log "Remote retention cleanup executed (>${RETENTION_DAYS} days)"
enforce_drive_budget_for_upload "$NEED_BYTES" log || true

set +e
rclone copyto "${LOCAL_DIR}/${CFG_FILE}" "${REMOTE_NAME}:${REMOTE_DIR}/${CFG_FILE}"
rc1=$?
rclone copyto "${LOCAL_DIR}/${DAT_FILE}" "${REMOTE_NAME}:${REMOTE_DIR}/${DAT_FILE}"
rc2=$?
set -e
if (( rc1 != 0 || rc2 != 0 )); then
  log "ERROR: rclone copyto failed rc_cfg=${rc1} rc_dat=${rc2} (remote prune already ran)"
  exit 1
fi
log "Uploaded to Google Drive: ${REMOTE_DIR}/${CFG_FILE} and ${DAT_FILE}"
log "OLT backup job finished"
