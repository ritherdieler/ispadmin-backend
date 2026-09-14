#!/usr/bin/env bash
set -euo pipefail

ONLY=""
FILES_SRC=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only)
      ONLY="${2:-}"
      shift 2
      ;;
    --files-from)
      FILES_SRC="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: deploy-select-wars.sh [--only core,oltgateway,traffic,acs] [--files-from FILE|-]" >&2
      echo "Single WAR deploy: any mapped source change selects core." >&2
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -n "$ONLY" ]]; then
  IFS=',' read -ra only_parts <<< "$ONLY"
  for p in "${only_parts[@]}"; do
    p="$(printf '%s' "$p" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    case "$p" in
      core|oltgateway|traffic|acs|app|"")
        ;;
      *)
        echo "Unknown war key: $p (use core,oltgateway,traffic,acs)" >&2
        exit 2
        ;;
    esac
  done
  echo "core"
  exit 0
fi

map_file() {
  local f="${1//\\//}"
  local base
  base="$(basename "$f")"
  case "$f" in
    settings.gradle.kts|build.gradle.kts|gradlew|gradlew.bat|gradle/*|*/build.gradle.kts)
      echo core
      return
      ;;
  esac
  case "$base" in
    application.properties|application-prod.properties|application-staging.properties|application-dev.properties|libs.versions.toml)
      echo core
      return
      ;;
  esac
  case "$f" in
    shared/*|events/*|transport/*|routeros/*|servicehealth/*|acs/*|oltgateway/*|traffic/*|core/*|netdiag/*|observability/*|app/*|build-logic/*)
      echo core
      ;;
    src/*)
      echo core
      ;;
    *)
      echo skip
      ;;
  esac
}

files=""
if [[ -n "$FILES_SRC" ]]; then
  if [[ "$FILES_SRC" == "-" ]]; then
    files="$(cat)"
  else
    files="$(cat "$FILES_SRC")"
  fi
else
  files="$(git diff --name-only HEAD || true)"
fi

mapped_any=0
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" ]] && continue
  m="$(map_file "$line")"
  case "$m" in
    skip) ;;
    *)
      mapped_any=1
      ;;
  esac
done <<< "$files"

if [[ "$mapped_any" -eq 0 ]]; then
  echo "No WAR-mapped changes. Pass --only core,oltgateway,traffic,acs" >&2
  exit 1
fi

echo "core"
