#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: deploy-war-needs-rebuild.sh <war-key> <war-path> [project-dir]" >&2
  echo "  war-key: core (single WAR; other keys are accepted as aliases)" >&2
  exit 2
}

KEY="${1:-}"
WAR_PATH="${2:-}"
PROJECT_DIR="${3:-.}"

[[ -n "$KEY" && -n "$WAR_PATH" ]] || usage
KEY="$(printf '%s' "$KEY" | tr '[:upper:]' '[:lower:]')"

if [[ "${FORCE_WAR_REBUILD:-}" == "1" || "${FORCE_WAR_REBUILD:-}" == "true" ]]; then
  echo "needs-rebuild: FORCE_WAR_REBUILD set ($KEY)"
  exit 0
fi

if [[ ! -f "$WAR_PATH" ]]; then
  echo "needs-rebuild: missing $WAR_PATH ($KEY)"
  exit 0
fi

PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"

paths_for_key() {
  echo "$PROJECT_DIR/shared"
  echo "$PROJECT_DIR/acs"
  echo "$PROJECT_DIR/oltgateway"
  echo "$PROJECT_DIR/traffic"
  echo "$PROJECT_DIR/core"
  echo "$PROJECT_DIR/build-logic"
  echo "$PROJECT_DIR/gradle"
  echo "$PROJECT_DIR/settings.gradle.kts"
  echo "$PROJECT_DIR/build.gradle.kts"
  echo "$PROJECT_DIR/gradlew"
}

newer_than_war() {
  local path="$1"
  if [[ -f "$path" ]]; then
    [[ "$path" -nt "$WAR_PATH" ]] && return 0
    return 1
  fi
  if [[ -d "$path" ]]; then
    local hit
    hit="$(find "$path" -type f -newer "$WAR_PATH" 2>/dev/null | head -n 1 || true)"
    [[ -n "$hit" ]] && return 0
  fi
  return 1
}

while IFS= read -r path || [[ -n "$path" ]]; do
  [[ -z "$path" ]] && continue
  if newer_than_war "$path"; then
    echo "needs-rebuild: stale input $path ($KEY)"
    exit 0
  fi
done < <(paths_for_key)

echo "skip-rebuild: $WAR_PATH is newer than $KEY sources"
exit 1
