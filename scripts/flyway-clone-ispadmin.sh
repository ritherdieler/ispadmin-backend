#!/usr/bin/env bash
# Clone Core schema ispadmin (mysqldump = SELECT) into a scratch schema and apply
# Flyway baseline 38 plus V39–V54. Refuses ispadmin and ispadmin_staging as the target.
#
# Allowed SCRATCH_SCHEMA: ispadmin_flyway_clone or scratch_*
#
# Usage:
#   SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --dry-run
#   SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --local
#   SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --vps
#
# --vps uses SSH + docker exec mysql8033. Password is read from the container
# env and never printed. Do not commit MYSQL_PWD / DB_PASSWORD.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATION_DIR="$PROJECT_DIR/core/src/main/resources/db/migration"

SOURCE_SCHEMA="${MYSQL_SOURCE_SCHEMA:-ispadmin}"
SCRATCH_SCHEMA="${SCRATCH_SCHEMA:-ispadmin_flyway_clone}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8033}"
VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
MODE=""
KEEP_DUMP=0

usage() {
  cat <<'EOF'
Usage: scripts/flyway-clone-ispadmin.sh --dry-run|--local|--vps [--keep-dump]

Env:
  SCRATCH_SCHEMA          ispadmin_flyway_clone (default) or scratch_*
  MYSQL_SOURCE_SCHEMA     ispadmin (SELECT dump only)
  MYSQL_CONTAINER         mysql8033
  VPS_HOST / VPS_USER     SSH target for --vps
  SSH_IDENTITY_FILE       optional SSH key
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run|--local|--vps)
      MODE="${1#--}"
      shift
      ;;
    --keep-dump)
      KEEP_DUMP=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$MODE" ]]; then
  usage
  exit 1
fi

is_forbidden_schema() {
  local name="$1"
  [[ "$name" == "ispadmin" || "$name" == "ispadmin_staging" || "$name" == "mysql" || "$name" == "sys" ]]
}

is_allowed_scratch() {
  local name="$1"
  [[ "$name" == "ispadmin_flyway_clone" || "$name" == scratch_* ]]
}

if is_forbidden_schema "$SCRATCH_SCHEMA" || ! is_allowed_scratch "$SCRATCH_SCHEMA"; then
  echo "Refuse scratch schema '$SCRATCH_SCHEMA' (forbidden; use ispadmin_flyway_clone or scratch_*)" >&2
  exit 1
fi

if [[ "$SOURCE_SCHEMA" != "ispadmin" ]]; then
  echo "Refuse source schema '$SOURCE_SCHEMA' (SELECT dump is only allowed from ispadmin)" >&2
  exit 1
fi

MIGRATIONS=()
while IFS= read -r _mig; do
  [ -n "$_mig" ] && MIGRATIONS+=("$_mig")
done <<EOF
$(find "$MIGRATION_DIR" -maxdepth 1 -name 'V*.sql' -print \
    | awk -F/ '{print $NF}' \
    | awk -F__ '{n=$1; sub(/^V/,"",n); if (n+0>=39 && n+0<=54) print}' \
    | sort -t_ -k1.2,1n)
EOF
if [[ ${#MIGRATIONS[@]} -eq 0 ]]; then
  echo "No V39–V54 files under $MIGRATION_DIR" >&2
  exit 1
fi

MIGRATION_PATHS=()
for name in "${MIGRATIONS[@]}"; do
  MIGRATION_PATHS+=("$MIGRATION_DIR/$name")
done

echo "Plan:"
echo "  source : $SOURCE_SCHEMA (mysqldump, SELECT only)"
echo "  scratch: $SCRATCH_SCHEMA (DDL/DML only here)"
echo "  mode   : $MODE"
echo "  migrations:"
printf '    %s\n' "${MIGRATIONS[@]}"

if [[ "$MODE" == "dry-run" ]]; then
  echo "dry-run: no dump, no DDL."
  exit 0
fi

ssh_opts=()
if [[ -n "${SSH_IDENTITY_FILE:-}" ]]; then
  ssh_opts+=(-i "$SSH_IDENTITY_FILE")
fi

mysql_extra=()
if [[ -n "${MYSQL_DEFAULTS_FILE:-}" ]]; then
  mysql_extra+=(--defaults-extra-file="$MYSQL_DEFAULTS_FILE")
fi

local_mysql() {
  mysql "${mysql_extra[@]}" -N -e "$1"
}

local_apply_file() {
  mysql "${mysql_extra[@]}" --default-character-set=utf8mb4 --force "$2" < "$1"
}

remote_mysql() {
  local sql="$1"
  ssh "${ssh_opts[@]}" "${VPS_USER}@${VPS_HOST}" \
    "ROOTPW=\$(docker exec ${MYSQL_CONTAINER} printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" ${MYSQL_CONTAINER} mysql -uroot -N -e $(printf '%q' "$sql")"
}

remote_apply_file() {
  ssh "${ssh_opts[@]}" "${VPS_USER}@${VPS_HOST}" \
    "ROOTPW=\$(docker exec ${MYSQL_CONTAINER} printenv MYSQL_ROOT_PASSWORD); docker exec -i -e MYSQL_PWD=\"\$ROOTPW\" ${MYSQL_CONTAINER} mysql -uroot --default-character-set=utf8mb4 --force ${2}" \
    < "$1"
}

record_history() {
  local runner="$1"
  local rank="$2"
  local version="$3"
  local description="$4"
  local script="$5"
  local elapsed="$6"
  local status="$7"
  "$runner" "INSERT INTO \`${SCRATCH_SCHEMA}\`.flyway_schema_history
    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
    VALUES (${rank}, '${version}', '${description}', 'SQL', '${script}', NULL, 'flyway-clone', ${elapsed}, ${status});"
}

prepare_history() {
  local runner="$1"
  "$runner" "CREATE DATABASE IF NOT EXISTS \`${SCRATCH_SCHEMA}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  "$runner" "CREATE TABLE IF NOT EXISTS \`${SCRATCH_SCHEMA}\`.flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50) NULL,
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT NULL,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success TINYINT(1) NOT NULL,
    PRIMARY KEY (installed_rank)
  );"
  "$runner" "INSERT INTO \`${SCRATCH_SCHEMA}\`.flyway_schema_history
    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
    SELECT 1, '38', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'flyway-clone', 0, 1
    FROM DUAL
    WHERE NOT EXISTS (
      SELECT 1 FROM \`${SCRATCH_SCHEMA}\`.flyway_schema_history WHERE version = '38'
    );"
}

apply_migrations() {
  local runner="$1"
  local apply_file="$2"
  local version description rank start ended elapsed status
  rank=2
  local failed=0
  for file in "${MIGRATION_PATHS[@]}"; do
    version="$(basename "$file" | sed -E 's/^V([0-9]+)__.*/\1/')"
    description="$(basename "$file" | sed -E 's/^V[0-9]+__([^.]+)\.sql$/\1/' | tr '_' ' ')"
    echo "Applying V${version} ${description}..."
    start="$(date +%s)"
    if "$apply_file" "$file" "$SCRATCH_SCHEMA"; then
      status=1
      echo "  PASS V${version}"
    else
      status=0
      failed=1
      echo "  FAIL V${version}"
    fi
    ended="$(date +%s)"
    elapsed=$((ended - start))
    record_history "$runner" "$rank" "$version" "$description" "$(basename "$file")" "$elapsed" "$status"
    rank=$((rank + 1))
    if [[ "$status" -eq 0 ]]; then
      echo "Stopped at V${version}. Later files were not applied."
      return 1
    fi
  done
  echo "Clone Flyway finished: all V39–V54 success=1 on ${SCRATCH_SCHEMA}"
  return "$failed"
}

if [[ "$MODE" == "local" ]]; then
  if ! command -v mysqldump >/dev/null || ! command -v mysql >/dev/null; then
    echo "mysql/mysqldump not on PATH" >&2
    exit 1
  fi
  DUMP="$(mktemp /tmp/ispadmin-flyway-clone.XXXXXX.sql)"
  echo "Dumping ${SOURCE_SCHEMA} -> ${DUMP} (local, SELECT)"
  mysqldump "${mysql_extra[@]}" \
    --single-transaction --routines --triggers --quick \
    "$SOURCE_SCHEMA" > "$DUMP"
  local_mysql "CREATE DATABASE IF NOT EXISTS \`${SCRATCH_SCHEMA}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  mysql "${mysql_extra[@]}" "$SCRATCH_SCHEMA" < "$DUMP"
  prepare_history local_mysql
  apply_migrations local_mysql local_apply_file
  if [[ "$KEEP_DUMP" -eq 0 ]]; then
    rm -f "$DUMP"
  else
    echo "Kept dump at $DUMP"
  fi
  exit 0
fi

if [[ "$MODE" == "vps" ]]; then
  echo "Dumping ${SOURCE_SCHEMA} on VPS into ${SCRATCH_SCHEMA} (SELECT dump; DDL only on scratch)"
  ssh "${ssh_opts[@]}" "${VPS_USER}@${VPS_HOST}" \
    "ROOTPW=\$(docker exec ${MYSQL_CONTAINER} printenv MYSQL_ROOT_PASSWORD)
     docker exec -e MYSQL_PWD=\"\$ROOTPW\" ${MYSQL_CONTAINER} mysql -uroot -e \"CREATE DATABASE IF NOT EXISTS \\\`${SCRATCH_SCHEMA}\\\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; DROP DATABASE IF EXISTS \\\`${SCRATCH_SCHEMA}\\\`; CREATE DATABASE \\\`${SCRATCH_SCHEMA}\\\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\"
     docker exec -e MYSQL_PWD=\"\$ROOTPW\" ${MYSQL_CONTAINER} mysqldump -uroot --single-transaction --routines --triggers --quick ${SOURCE_SCHEMA} \
       | docker exec -i -e MYSQL_PWD=\"\$ROOTPW\" ${MYSQL_CONTAINER} mysql -uroot ${SCRATCH_SCHEMA}"
  prepare_history remote_mysql
  apply_migrations remote_mysql remote_apply_file
  exit 0
fi

echo "Unknown mode $MODE" >&2
exit 1
