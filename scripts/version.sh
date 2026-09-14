#!/usr/bin/env bash
# Exporta RELEASE_VERSION, RELEASE_SEMVER y RELEASE_SHA a partir de git.
# Formato: {semver}+{shortSha} (ej. 1.4.2+abc1234). Robusto sin tags ni git.
# Uso: source scripts/version.sh

VERSION_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

_release_semver=""
_release_sha=""
_release_dirty=0
if command -v git >/dev/null 2>&1; then
  _release_semver="$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//' || true)"
  _release_sha="$(git rev-parse --short=7 HEAD 2>/dev/null || true)"
  if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
    _release_dirty=1
  fi
fi
_release_semver="${_release_semver:-0.0.0}"
_release_sha="${_release_sha:-unknown}"

export RELEASE_SEMVER="$_release_semver"
export RELEASE_SHA="$_release_sha"
export RELEASE_VERSION="${_release_semver}+${_release_sha}"
export RELEASE_DIRTY="$_release_dirty"

unset _release_semver _release_sha _release_dirty
