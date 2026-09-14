#!/usr/bin/env bash

DRIVE_BUDGET_BYTES="${DRIVE_BUDGET_BYTES:-10737418240}"
DRIVE_FREE_MARGIN_BYTES="${DRIVE_FREE_MARGIN_BYTES:-1073741824}"
REMOTE_NAME="${REMOTE_NAME:-gdrive}"
MYSQL_REMOTE_DIR="${MYSQL_REMOTE_DIR:-gigafiberDatabaseBackups}"
MYSQL_REMOTE_INCLUDE="${MYSQL_REMOTE_INCLUDE:-ispadmin_*.sql.gz}"

backup_source_env_files() {
  local f
  for f in /opt/gigafiber/.env /opt/gigafiber/scripts/backup.env; do
    if [[ -f "$f" ]]; then
      set -a
      # shellcheck disable=SC1090
      source "$f"
      set +a
    fi
  done
}

drive_about_json() {
  rclone about "${REMOTE_NAME}:" --json 2>/dev/null || true
}

drive_used_bytes() {
  local json bytes
  json="$(drive_about_json)"
  bytes="$(printf '%s' "$json" | sed -n 's/.*"used"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
  if [[ -z "$bytes" ]]; then
    json="$(rclone size "${REMOTE_NAME}:" --json 2>/dev/null || true)"
    bytes="$(printf '%s' "$json" | sed -n 's/.*"bytes"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
  fi
  if [[ -z "$bytes" ]]; then
    echo 0
  else
    echo "$bytes"
  fi
}

drive_free_bytes() {
  local json bytes
  json="$(drive_about_json)"
  bytes="$(printf '%s' "$json" | sed -n 's/.*"free"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
  if [[ -z "$bytes" ]]; then
    echo 0
  else
    echo "$bytes"
  fi
}

prune_remote_min_age() {
  local remote_dir="$1"
  local include="$2"
  local days="$3"
  rclone delete "${REMOTE_NAME}:${remote_dir}" \
    --drive-use-trash=false \
    --include "${include}" \
    --min-age "${days}d" || true
}

delete_oldest_remote_match() {
  local remote_dir="$1"
  local include="$2"
  local oldest
  oldest="$(rclone lsf "${REMOTE_NAME}:${remote_dir}" \
    --files-only \
    --format "p" \
    --order-by modtime,ascending \
    --include "${include}" 2>/dev/null | head -1 || true)"
  if [[ -z "$oldest" ]]; then
    return 1
  fi
  rclone deletefile "${REMOTE_NAME}:${remote_dir}/${oldest}" --drive-use-trash=false || \
    rclone delete "${REMOTE_NAME}:${remote_dir}" --drive-use-trash=false --include "${oldest}" || true
  echo "$oldest"
}

enforce_drive_budget_for_upload() {
  local needed_bytes="$1"
  local log_fn="${2:-echo}"
  local used free target deleted rounds budget_cap
  needed_bytes="${needed_bytes:-0}"
  used="$(drive_used_bytes)"
  free="$(drive_free_bytes)"
  budget_cap="$DRIVE_BUDGET_BYTES"
  target=$((budget_cap - DRIVE_FREE_MARGIN_BYTES - needed_bytes))
  if (( target < 0 )); then
    target=0
  fi
  rounds=0
  "$log_fn" "Drive about used=${used} B free=${free} B; budget_cap=${budget_cap} B; target_before_upload=${target} B (need ${needed_bytes} B + margin ${DRIVE_FREE_MARGIN_BYTES} B)"
  while { (( used > target )) || (( free < needed_bytes + DRIVE_FREE_MARGIN_BYTES && free > 0 )); } && (( rounds < 200 )); do
    deleted="$(delete_oldest_remote_match "$MYSQL_REMOTE_DIR" "$MYSQL_REMOTE_INCLUDE" || true)"
    if [[ -z "$deleted" ]]; then
      "$log_fn" "WARNING: cannot free more Drive space (no more ${MYSQL_REMOTE_INCLUDE} in ${MYSQL_REMOTE_DIR})"
      break
    fi
    "$log_fn" "[BUDGET-DELETE] ${MYSQL_REMOTE_DIR}/${deleted}"
    rounds=$((rounds + 1))
    used="$(drive_used_bytes)"
    free="$(drive_free_bytes)"
  done
  if (( used + needed_bytes > budget_cap )); then
    "$log_fn" "WARNING: Drive still over budget after prune (used=${used} B need=${needed_bytes} B cap=${budget_cap} B)"
    return 1
  fi
  if (( free > 0 && free < needed_bytes )); then
    "$log_fn" "WARNING: Drive free ${free} B < needed ${needed_bytes} B"
    return 1
  fi
  return 0
}

is_mysql_drive_upload_hour() {
  if [[ "${BACKUP_FORCE_UPLOAD:-0}" == "1" ]]; then
    return 0
  fi
  local hour
  hour="$(TZ=America/Lima date +%H)"
  case "$hour" in
    00|06|12|18) return 0 ;;
    *) return 1 ;;
  esac
}
