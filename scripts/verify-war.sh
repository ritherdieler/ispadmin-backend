#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WAR="${VERIFY_WAR:-$PROJECT_DIR/core/build/libs/ispadmin.war}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [[ ! -f "$WAR" ]]; then
  fail "No existe $WAR"
fi

WAR_LIST="$(jar tf "$WAR")"

grep -q "WEB-INF/classes/com/dscorp/wispadmin/wispadmin/" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.wispadmin"
grep -q "WEB-INF/classes/com/dscorp/wispadmin/servicehealth/" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.servicehealth"
grep -q "WEB-INF/classes/com/dscorp/wispadmin/netdiag/" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.netdiag"
grep -q "WEB-INF/classes/com/dscorp/wispadmin/observability/" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.observability"
if ! grep -q "WEB-INF/classes/com/dscorp/wispadmin/routeros/" <<< "$WAR_LIST"; then
  grep -q "WEB-INF/lib/shared.jar" <<< "$WAR_LIST" || fail "El WAR unico no contiene shared.jar (routeros)"
  SHARED_JAR="$(mktemp)"
  unzip -p "$WAR" WEB-INF/lib/shared.jar > "$SHARED_JAR"
  if ! jar tf "$SHARED_JAR" | grep -q "com/dscorp/wispadmin/routeros/"; then
    rm -f "$SHARED_JAR"
    fail "El WAR unico no contiene com.dscorp.wispadmin.routeros"
  fi
  rm -f "$SHARED_JAR"
fi
grep -q "WEB-INF/lib/acs.jar" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.acs"
grep -q "WEB-INF/lib/oltgateway.jar" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.oltgateway"
grep -q "WEB-INF/lib/traffic.jar" <<< "$WAR_LIST" || fail "El WAR unico no contiene com.dscorp.wispadmin.traffic"

if grep -q 'application-local-prestaging' <<< "$WAR_LIST"; then
  fail "El WAR contiene application-local-prestaging. Prestaging no se despliega al VPS (solo prod y staging)."
fi

echo "OK: WAR $WAR incluye core, acs, oltgateway y traffic."
