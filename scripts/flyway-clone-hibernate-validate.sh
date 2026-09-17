#!/usr/bin/env bash
# Hibernate validate against ispadmin_flyway_clone (or scratch_*) after Flyway.
# Refuses ispadmin and ispadmin_staging. No deploy. No writes to live prod.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCRATCH_SCHEMA="${SCRATCH_SCHEMA:-ispadmin_flyway_clone}"

is_forbidden_schema() {
  local name="$1"
  [[ "$name" == "ispadmin" || "$name" == "ispadmin_staging" || "$name" == "mysql" || "$name" == "sys" ]]
}

is_allowed_scratch() {
  local name="$1"
  [[ "$name" == "ispadmin_flyway_clone" || "$name" == scratch_* ]]
}

if is_forbidden_schema "$SCRATCH_SCHEMA" || ! is_allowed_scratch "$SCRATCH_SCHEMA"; then
  echo "Refuse schema '$SCRATCH_SCHEMA'" >&2
  exit 1
fi

if [[ -z "${CORE_FLYWAY_CLONE_JDBC:-}" || -z "${CORE_FLYWAY_CLONE_PASSWORD:-}" ]]; then
  echo "CORE_FLYWAY_CLONE_JDBC and CORE_FLYWAY_CLONE_PASSWORD are required" >&2
  exit 1
fi

catalog="${CORE_FLYWAY_CLONE_JDBC##*/}"
catalog="${catalog%%\?*}"
if is_forbidden_schema "$catalog"; then
  echo "Refuse JDBC catalog '$catalog'" >&2
  exit 1
fi

cd "$PROJECT_DIR"
exec ./gradlew :core:test --tests "com.dscorp.wispadmin.wispadmin.schema.CoreFlywayCloneHibernateValidateTest"
