#!/usr/bin/env bash
set -euo pipefail

OPTIONAL_KEYS="observability netdiag servicehealth"
SPLIT_KEYS="traffic oltgateway acs"
PACKAGE_PREFIX="WEB-INF/classes/com/dscorp/wispadmin"

usage() {
  cat <<'EOF'
Usage: scripts/subsystems.sh [--with key,key] [--write-dir DIR] [--props-dir DIR]

Prints:
  SUBSYSTEM_EXCLUDES=WEB-INF/classes/com/dscorp/wispadmin/<pkg>/**,...
  SUBSYSTEM_DISABLED_KEYS=servicehealth,traffic,...

--with lists optional subsystems to keep in the WAR. Default: none.
traffic, oltgateway and acs classes always ship in their own WARs; --with only enables HTTP clients
(olt.gateway.client-enabled / acs.client-enabled / traffic.client-enabled).
--props-dir rewrites olt.gateway.client-enabled (and acs.client-enabled when --with acs) in application*.properties under that dir.
EOF
}

WITH=""
WRITE_DIR=""
PROPS_DIR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --with)
      WITH="${2:-}"
      shift 2
      ;;
    --write-dir)
      WRITE_DIR="${2:-}"
      shift 2
      ;;
    --props-dir)
      PROPS_DIR="${2:-}"
      shift 2
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

is_split() {
  local candidate="$1"
  local known
  for known in $SPLIT_KEYS; do
    if [[ "$known" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

is_known() {
  local candidate="$1"
  local known
  if is_split "$candidate"; then
    return 0
  fi
  for known in $OPTIONAL_KEYS; do
    if [[ "$known" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

is_kept() {
  local candidate="$1"
  local key
  [[ -z "$WITH" ]] && return 1
  local IFS=','
  for key in $WITH; do
    key="$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    [[ -z "$key" ]] && continue
    if [[ "$key" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

if [[ -n "$WITH" ]]; then
  IFS=',' read -r -a requested <<< "$WITH"
  for key in "${requested[@]}"; do
    key="$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    [[ -z "$key" ]] && continue
    if ! is_known "$key"; then
      echo "Unknown subsystem: $key" >&2
      exit 1
    fi
  done
fi

excludes=""
disabled=""
enabled_props=""
for key in $OPTIONAL_KEYS; do
  if is_kept "$key"; then
    enabled_props="${enabled_props}gigafiber.subsystems.${key}.enabled=true\n"
  else
    if [[ -n "$excludes" ]]; then
      excludes="${excludes},"
    fi
    excludes="${excludes}${PACKAGE_PREFIX}/${key}/**"
    if [[ -n "$disabled" ]]; then
      disabled="${disabled},"
    fi
    disabled="${disabled}${key}"
    enabled_props="${enabled_props}gigafiber.subsystems.${key}.enabled=false\n"
  fi
done

for key in $SPLIT_KEYS; do
  if [[ -n "$excludes" ]]; then
    excludes="${excludes},"
  fi
  excludes="${excludes}${PACKAGE_PREFIX}/${key}/**"
  if [[ -n "$disabled" ]]; then
    disabled="${disabled},"
  fi
  disabled="${disabled}${key}"
  if is_kept "$key"; then
    enabled_props="${enabled_props}gigafiber.subsystems.${key}.enabled=true\n"
  else
    enabled_props="${enabled_props}gigafiber.subsystems.${key}.enabled=false\n"
  fi
done

if is_kept traffic; then
  enabled_props="${enabled_props}traffic.client-enabled=true\n"
else
  enabled_props="${enabled_props}traffic.client-enabled=false\n"
fi
if is_kept oltgateway; then
  enabled_props="${enabled_props}olt.gateway.client-enabled=true\n"
  enabled_props="${enabled_props}olt.gateway.enabled=false\n"
  enabled_props="${enabled_props}olt.gateway.acs.enabled=true\n"
else
  enabled_props="${enabled_props}olt.gateway.client-enabled=false\n"
fi
if is_kept acs; then
  enabled_props="${enabled_props}acs.client-enabled=true\n"
fi


if [[ -z "$excludes" ]]; then
  SUBSYSTEM_EXCLUDES="WEB-INF/classes/__no_subsystem_exclude__/**"
else
  SUBSYSTEM_EXCLUDES="$excludes"
fi
SUBSYSTEM_DISABLED_KEYS="$disabled"

echo "SUBSYSTEM_EXCLUDES=${SUBSYSTEM_EXCLUDES}"
echo "SUBSYSTEM_DISABLED_KEYS=${SUBSYSTEM_DISABLED_KEYS}"

if [[ -n "$WRITE_DIR" ]]; then
  mkdir -p "$WRITE_DIR"
  printf '%s\n' "$SUBSYSTEM_EXCLUDES" > "$WRITE_DIR/subsystem-excludes.txt"
  if is_kept servicehealth; then
    enabled_props="${enabled_props}gigafiber.scheduling.enabled=true\n"
    enabled_props="${enabled_props}service.health.enabled=true\n"
    enabled_props="${enabled_props}service.health.acs-enabled=true\n"
    enabled_props="${enabled_props}service.health.actions-enabled=true\n"
  fi
  if is_kept oltgateway; then
    enabled_props="${enabled_props}service.health.optical-enabled=true\n"
  fi
  if is_kept servicehealth && is_kept oltgateway; then
    enabled_props="${enabled_props}service.health.optical-pull-mode=live-sns\n"
  fi
  if is_kept netdiag; then
    enabled_props="${enabled_props}net.diag.enabled=true\n"
    enabled_props="${enabled_props}net.diag.snmp.trap.udp-enabled=false\n"
    enabled_props="${enabled_props}net.diag.syslog.udp-enabled=false\n"
  fi
  printf '%b' "$enabled_props" > "$WRITE_DIR/subsystem-enabled.properties"
fi

if [[ -n "$PROPS_DIR" ]]; then
  client_value=false
  if is_kept oltgateway; then
    client_value=true
  fi
  for f in application.properties application-prod.properties application-staging.properties; do
    if [[ -f "$PROPS_DIR/$f" ]] && grep -q '^olt\.gateway\.client-enabled=' "$PROPS_DIR/$f"; then
      sed -i.bak "s/^olt\\.gateway\\.client-enabled=.*/olt.gateway.client-enabled=${client_value}/" "$PROPS_DIR/$f"
      rm -f "$PROPS_DIR/$f.bak"
    fi
  done
  if is_kept acs; then
    for f in application.properties application-prod.properties application-staging.properties; do
      if [[ -f "$PROPS_DIR/$f" ]] && grep -q '^acs\.client-enabled=' "$PROPS_DIR/$f"; then
        sed -i.bak "s/^acs\\.client-enabled=.*/acs.client-enabled=true/" "$PROPS_DIR/$f"
        rm -f "$PROPS_DIR/$f.bak"
      fi
    done
  fi
fi
