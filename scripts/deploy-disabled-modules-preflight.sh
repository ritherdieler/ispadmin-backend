#!/usr/bin/env bash
# Preflight: flags off/missing that break the FIBER/TR-069 (or sibling) chain.
# Prints warnings and asks for confirmation before a deploy proceeds.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DEPLOY_ENV=""
PROPS=""
YES=0

usage() {
  cat <<'EOF'
Usage: scripts/deploy-disabled-modules-preflight.sh --env prod|staging [--properties FILE] [--yes]

Scan application-{env}.properties for disabled/missing modules that would
compromise a deploy (TR-069 NA, Gateway NoOp, ACS off, clients pointing
at the wrong context-path).

If any issue is found:
  - interactive TTY: ask confirmation [y/N]
  - otherwise: abort unless --yes or DEPLOY_CONFIRM_DISABLED_MODULES=yes
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      DEPLOY_ENV="${2:-}"
      shift 2
      ;;
    --properties)
      PROPS="${2:-}"
      shift 2
      ;;
    --yes)
      YES=1
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

if [[ "$DEPLOY_ENV" != "prod" && "$DEPLOY_ENV" != "staging" ]]; then
  echo "Invalid --env (use prod or staging)" >&2
  usage
  exit 1
fi

if [[ -z "$PROPS" ]]; then
  PROPS="$PROJECT_DIR/core/src/main/resources/application-${DEPLOY_ENV}.properties"
fi
if [[ ! -f "$PROPS" ]]; then
  echo "Missing properties file: $PROPS" >&2
  exit 1
fi

if [[ "${DEPLOY_CONFIRM_DISABLED_MODULES:-}" == "yes" ]]; then
  YES=1
fi

if [[ "$DEPLOY_ENV" == "staging" ]]; then
  CONTEXT="ispadmin-staging"
else
  CONTEXT="ispadmin"
fi

prop_value() {
  local key="$1"
  awk -F= -v key="$key" '
    /^[ \t]*#/ { next }
    /^[ \t]*$/ { next }
    {
      line = $0
      sub(/[ \t]*#.*/, "", line)
      sub(/^[ \t]*/, "", line)
      split(line, parts, "=")
      if (parts[1] == key) {
        val = substr(line, index(line, "=") + 1)
        gsub(/^[ \t]+|[ \t]+$/, "", val)
        last = val
      }
    }
    END { if (last != "") print last }
  ' "$PROPS"
}

is_effectively_true() {
  local val="$1"
  case "$val" in
    true) return 0 ;;
    *':true}') return 0 ;;
    *) return 1 ;;
  esac
}

issues=()

require_true() {
  local key="$1"
  local why="$2"
  local val
  val="$(prop_value "$key")"
  if [[ -z "$val" ]]; then
    issues+=("$key (ausente; default false) → $why")
    return
  fi
  if is_effectively_true "$val"; then
    return
  fi
  issues+=("$key=$val → $why")
}

require_present() {
  local key="$1"
  local why="$2"
  local val
  val="$(prop_value "$key")"
  if [[ -z "$val" ]]; then
    issues+=("$key (ausente) → $why")
  fi
}

require_url_context() {
  local key="$1"
  local why="$2"
  local val
  val="$(prop_value "$key")"
  if [[ -z "$val" ]]; then
    issues+=("$key (ausente) → $why")
    return
  fi
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    if [[ "$val" != *"/ispadmin-staging"* ]]; then
      issues+=("$key=$val (esperado *$CONTEXT*) → $why")
    fi
  else
    if [[ "$val" != *"/ispadmin"* || "$val" == *"/ispadmin-staging"* ]]; then
      issues+=("$key=$val (esperado *$CONTEXT* y no staging) → $why")
    fi
  fi
}

require_true "gigafiber.subsystems.acs.enabled" "ACS no arranca in-process; provision TR-069 no existe"
require_true "gigafiber.subsystems.oltgateway.enabled" "Gateway sin persistencia; activate ONU falla"
require_true "olt.gateway.client-enabled" "Core no llama al Gateway; OLT/TR-069 no arrancan"
require_true "olt.gateway.acs.enabled" "Gateway usa NoOpAcsCpeClient; TR-069 queda NA (ACS client disabled)"
require_url_context "olt.gateway.acs.internal-base-url" "Gateway no alcanza /api/acs en el WAR único"
require_present "olt.gateway.acs.api-key" "Gateway→ACS 401 Missing or invalid X-Acs-Key; TR-069 queda PENDING"
require_present "acs.gateway.api-key" "ACS→Gateway Inform 401; no cierra telemetría 360"
require_present "olt.gateway.acs-to-gateway-api-key" "Gateway rechaza Inform ACS (X-Acs-To-Gateway-Key)"
require_url_context "acs.gateway.internal-base-url" "ACS Inform no apunta al WAR único"
require_true "acs.client-enabled" "Core no llama al ACS"
require_url_context "acs.internal-base-url" "cliente Core→ACS apunta a otro context-path"
require_true "genieacs.enabled" "CpeFacadeService devuelve NA (ACS disabled); no hay NBI GenieACS"

if [[ ${#issues[@]} -eq 0 ]]; then
  echo "Preflight módulos ($DEPLOY_ENV): cadena FIBER/TR-069 completa."
  exit 0
fi

echo "ADVERTENCIA: módulos desactivados o mal apuntados que pueden comprometer el deploy ($DEPLOY_ENV):" >&2
echo >&2
for item in "${issues[@]}"; do
  echo "  - $item" >&2
done
echo >&2
echo "Collectors/WhatsApp/UDP apagados a propósito no entran aquí." >&2
echo "Confirma explícitamente si el deploy debe seguir así." >&2

if [[ "$YES" -eq 1 ]]; then
  echo "Continuando por confirmación explícita (--yes o DEPLOY_CONFIRM_DISABLED_MODULES)."
  exit 0
fi

if [[ -t 0 ]]; then
  read -r -p "¿Continuar el deploy de todos modos? [y/N] " answer
  case "$answer" in
    y|Y|yes|S|s)
      echo "Continuando por confirmación explícita."
      exit 0
      ;;
  esac
fi

echo "Abortado. Hace falta confirmación (--yes o DEPLOY_CONFIRM_DISABLED_MODULES=yes)." >&2
exit 1
