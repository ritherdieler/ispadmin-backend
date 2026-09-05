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
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

sort_keys() {
  local raw="$1"
  local -a parts=()
  local p
  IFS=',' read -ra parts <<< "$raw"
  local -a ordered=()
  for p in acs core oltgateway traffic; do
    local item
    for item in "${parts[@]}"; do
      item="$(printf '%s' "$item" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
      if [[ "$item" == "$p" ]]; then
        ordered+=("$p")
        break
      fi
    done
  done
  (IFS=','; echo "${ordered[*]}")
}

if [[ -n "$ONLY" ]]; then
  local_keys=""
  IFS=',' read -ra only_parts <<< "$ONLY"
  for p in "${only_parts[@]}"; do
    p="$(printf '%s' "$p" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    case "$p" in
      core|oltgateway|traffic|acs)
        if [[ -n "$local_keys" ]]; then
          local_keys="${local_keys},"
        fi
        local_keys="${local_keys}${p}"
        ;;
      "")
        ;;
      *)
        echo "Unknown war key: $p (use core,oltgateway,traffic,acs)" >&2
        exit 2
        ;;
    esac
  done
  if [[ -z "$local_keys" ]]; then
    echo "Empty --only. Pass core,oltgateway,traffic,acs" >&2
    exit 2
  fi
  sort_keys "$local_keys"
  exit 0
fi

map_file() {
  local f="${1//\\//}"
  local base
  base="$(basename "$f")"
  case "$f" in
    pom.xml|*/pom.xml)
      echo ALL
      return
      ;;
  esac
  case "$base" in
    application.properties|application-prod.properties)
      echo ALL
      return
      ;;
    application-oltgateway.properties)
      echo oltgateway
      return
      ;;
    application-acs.properties)
      echo acs
      return
      ;;
    application-traffic.properties)
      echo traffic
      return
      ;;
    application-staging.properties)
      echo core
      return
      ;;
  esac
  case "$f" in
    */events/*|*/events)
      echo ALL
      ;;
    */oltgateway/*)
      echo oltgateway
      ;;
    */acs/*)
      echo acs
      ;;
    */traffic/*)
      echo traffic
      ;;
    */wispadmin/wispadmin/*|*/observability/*|*/netdiag/*|*/servicehealth/*)
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
all=0
keys=""
add_key() {
  local k="$1"
  [[ ",$keys," == *",$k,"* ]] && return 0
  if [[ -n "$keys" ]]; then
    keys="${keys},"
  fi
  keys="${keys}${k}"
}

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" ]] && continue
  m="$(map_file "$line")"
  case "$m" in
    skip) ;;
    ALL)
      all=1
      mapped_any=1
      ;;
    *)
      add_key "$m"
      mapped_any=1
      ;;
  esac
done <<< "$files"

if [[ "$all" -eq 1 ]]; then
  echo "acs,core,oltgateway,traffic"
  exit 0
fi

if [[ "$mapped_any" -eq 0 ]]; then
  echo "No WAR-mapped changes. Pass --only core,oltgateway,traffic,acs" >&2
  exit 1
fi

sort_keys "$keys"
