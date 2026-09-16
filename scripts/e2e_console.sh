e2e_use_color() {
  [[ -z "${NO_COLOR:-}" ]] || return 1
  if [[ -n "${E2E_FORCE_COLOR:-}" || -n "${FORCE_COLOR:-}" ]]; then
    return 0
  fi
  [[ "${TERM:-dumb}" != "dumb" && -t 2 ]]
}

e2e_paint() {
  local color="$1"
  shift
  if e2e_use_color; then
    printf '%b%s%b' "$color" "$*" '\033[0m'
  else
    printf '%s' "$*"
  fi
}

e2e_phase_color() {
  case "$1" in
    login) printf '%s' '\033[36m' ;;
    alta) printf '%s' '\033[35m' ;;
    acs) printf '%s' '\033[34m' ;;
    http) printf '%s' '\033[33m' ;;
    ok) printf '%s' '\033[32m' ;;
    fail) printf '%s' '\033[31m' ;;
    wait) printf '%s' '\033[33m' ;;
    *) printf '%s' '\033[37m' ;;
  esac
}

e2e_status_tag() {
  case "$1" in
    pass|ok|PASS|OK) printf '%s' 'OK' ;;
    fail|FAIL) printf '%s' 'FAIL' ;;
    wait|WAIT|retry|RETRY) printf '%s' 'WAIT' ;;
    *) printf '%s' "$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')" ;;
  esac
}

e2e_url_path() {
  local url="$1"
  url="${url#*://}"
  url="${url#*/}"
  if [[ "$url" == */* ]]; then
    printf '/%s' "${url#*/}"
  else
    printf '/%s' "$url"
  fi
}

e2e_http_phase() {
  local url="$1"
  case "$url" in
    */users/login*) printf '%s' login ;;
    */registration-progress*|*/service-health*|*/tr069*|*/acs*|*/7557/*) printf '%s' acs ;;
    */onu/*|*/onus/*|*/olt-gateway*|*/smartolt*|*/subscription*|*/place*|*/napbox*|*/plan*|*/networkDevice*) printf '%s' alta ;;
    */rest/ppp*|*/rest/queue*|*/rest/ip/*) printf '%s' http ;;
    *) printf '%s' http ;;
  esac
}

e2e_http_status_color() {
  case "$1" in
    2[0-9][0-9]) printf '%s' '\033[32m' ;;
    3[0-9][0-9]) printf '%s' '\033[36m' ;;
    4[0-9][0-9]) printf '%s' '\033[33m' ;;
    5[0-9][0-9]|000) printf '%s' '\033[31m' ;;
    *) printf '%s' '\033[37m' ;;
  esac
}

e2e_now_ms() {
  python3 -c 'import time; print(int(time.time() * 1000))'
}

e2e_dur_color() {
  local ms="${1:-0}"
  if [[ "$ms" -lt "${E2E_FAST_MS:-2000}" ]]; then
    printf '%s' '\033[32m'
  elif [[ "$ms" -lt "${E2E_SLOW_MS:-10000}" ]]; then
    printf '%s' '\033[33m'
  else
    printf '%s' '\033[31m'
  fi
}

e2e_fmt_dur() {
  local ms="${1:-0}"
  if [[ "$ms" -lt 1000 ]]; then
    printf '%sms' "$ms"
  else
    printf '%d.%03ds' "$((ms / 1000))" "$((ms % 1000))"
  fi
}

e2e_paint_dur() {
  e2e_paint "$(e2e_dur_color "${1:-0}")" "$(e2e_fmt_dur "${1:-0}")"
}

e2e_fields() {
  local out=""
  local body=""
  local http_code=""
  local dur_ms=""
  local pair key value
  for pair in "$@"; do
    [[ "$pair" == *=* ]] || continue
    key="${pair%%=*}"
    value="${pair#*=}"
    [[ -n "$value" && "$value" != "-" && "$value" != "null" ]] || continue
    if [[ "$key" == "body" ]]; then
      body="$value"
      continue
    fi
    if [[ "$key" == "status" ]]; then
      http_code="$value"
      continue
    fi
    if [[ "$key" == "dur" ]]; then
      dur_ms="$value"
      continue
    fi
    if [[ -n "$out" ]]; then
      out="$out  $key=$value"
    else
      out="$key=$value"
    fi
  done
  if [[ -n "$http_code" ]]; then
    local painted
    painted="$(e2e_paint "$(e2e_http_status_color "$http_code")" "$http_code")"
    if [[ -n "$out" ]]; then
      out="$out  status=$painted"
    else
      out="status=$painted"
    fi
  fi
  if [[ -n "$dur_ms" ]]; then
    local painted_dur
    painted_dur="$(e2e_paint_dur "$dur_ms")"
    if [[ -n "$out" ]]; then
      out="$out  dur=$painted_dur"
    else
      out="dur=$painted_dur"
    fi
  fi
  if [[ -n "$out" ]]; then
    printf '  %s\n' "$out" >&2
  fi
  if [[ -n "$body" ]]; then
    printf '  body=\n' >&2
    printf '%s\n' "$body" | sed 's/^/    /' >&2
  fi
}

e2e_hit() {
  local phase="$1"
  local hit_status="$2"
  local title="$3"
  shift 3
  local tag
  tag="$(e2e_status_tag "$hit_status")"
  local color
  if [[ "$tag" == "FAIL" ]]; then
    color="$(e2e_phase_color fail)"
  elif [[ "$tag" == "OK" && "$phase" == "ok" ]]; then
    color="$(e2e_phase_color ok)"
  else
    color="$(e2e_phase_color "$phase")"
  fi
  local phase_u
  phase_u="$(printf '%s' "$phase" | tr '[:lower:]' '[:upper:]')"
  local line
  if [[ "$phase" == "fail" ]]; then
    line="$(printf '[%s] %s' "$tag" "$title")"
  else
    line="$(printf '[%s] %-5s %s' "$tag" "$phase_u" "$title")"
  fi
  printf '%s\n' "$(e2e_paint "$color" "$line")" >&2
  e2e_fields "$@"
}

e2e_clip_body() {
  local raw="$1"
  E2E_CLIP_BODY_IN="$raw" python3 -c '
import json, os, re
raw = os.environ.get("E2E_CLIP_BODY_IN") or ""
limit = int(os.environ.get("E2E_LOG_BODY_LIMIT") or "1200")
secret = re.compile(r"(?i)(accessToken|refreshToken|token|password|authorization|api[_-]?key)")
def redact(obj):
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if secret.search(str(k)):
                out[k] = "<redacted>"
            else:
                out[k] = redact(v)
        return out
    if isinstance(obj, list):
        if len(obj) > 8:
            return [redact(x) for x in obj[:8]] + ["<%d more>" % (len(obj) - 8)]
        return [redact(x) for x in obj]
    if isinstance(obj, str) and len(obj) > 160 and secret.search(obj):
        return "<redacted>"
    return obj
text = raw.strip()
try:
    text = json.dumps(redact(json.loads(text)), ensure_ascii=False, indent=2)
except Exception:
    text = secret.sub(r"\1:<redacted>", text)
    text = re.sub(r"(?i)(Bearer\s+)[A-Za-z0-9._\-]+", r"\1<redacted>", text)
if len(text) > limit:
    text = text[:limit].rstrip() + "\n    ...<clipped>"
print(text)
'
}

e2e_step() {
  e2e_hit "${E2E_PHASE:-http}" wait "$*"
}

e2e_doing() {
  e2e_hit "${E2E_PHASE:-http}" wait "$*"
}

e2e_retry() {
  e2e_hit "${E2E_PHASE:-http}" wait "$*"
}

e2e_http_fail() {
  e2e_hit http fail "$*"
}

e2e_http() {
  local method="$1"
  local url="$2"
  shift 2
  local path
  path="$(e2e_url_path "$url")"
  local phase="${E2E_PHASE:-$(e2e_http_phase "$url")}"
  e2e_hit "$phase" wait "HTTP $method $path" \
    endpoint="$method $path" \
    retry="${E2E_HTTP_RETRY:-}" \
    sn="${E2E_ONU_SN:-}" \
    accessMode="${E2E_ACCESS_MODE:-}" \
    subscription="${E2E_SUB_ID:-}"
  local body=""
  local code="000"
  local curl_ec=0
  local resp=""
  local had_e=0
  local t0 t1 dur_ms
  t0="$(e2e_now_ms)"
  case "$-" in *e*) had_e=1 ;; esac
  set +e
  resp="$(curl -sS -X "$method" -w $'\n%{http_code}' "$@" "$url" 2>&1)"
  curl_ec=$?
  t1="$(e2e_now_ms)"
  if [[ "$had_e" -eq 1 ]]; then set -e; fi
  dur_ms=$((t1 - t0))
  if [[ "$dur_ms" -lt 0 ]]; then
    dur_ms=0
  fi
  E2E_HTTP_CODE="000"
  if [[ "$curl_ec" -ne 0 ]]; then
    e2e_hit fail fail "HTTP $method $path" \
      endpoint="$method $path" \
      status="000" \
      dur="$dur_ms" \
      retry="${E2E_HTTP_RETRY:-}" \
      body="$(e2e_clip_body "$resp")"
    printf '%s' "$resp"
    return "$curl_ec"
  fi
  code="$(printf '%s' "$resp" | tail -n 1)"
  if [[ "$resp" == *$'\n'* ]]; then
    body="$(printf '%s' "$resp" | sed '$d')"
  else
    body=""
  fi
  E2E_HTTP_CODE="$code"
  local clipped
  clipped="$(e2e_clip_body "$body")"
  if [[ "$code" =~ ^[23][0-9][0-9]$ ]]; then
    e2e_hit "$phase" pass "HTTP $method $path" \
      endpoint="$method $path" \
      status="$code" \
      dur="$dur_ms" \
      retry="${E2E_HTTP_RETRY:-}" \
      body="$clipped"
  else
    e2e_hit fail fail "HTTP $method $path" \
      endpoint="$method $path" \
      status="$code" \
      dur="$dur_ms" \
      retry="${E2E_HTTP_RETRY:-}" \
      body="$clipped"
  fi
  printf '%s' "$body"
  return 0
}

e2e_poll_tr069() {
  local base="$1"
  local token="$2"
  local sub_id="$3"
  local attempts="${4:-12}"
  local sleep_s="${5:-5}"
  local i
  local body=""
  local tr069_status=""
  local prev_phase="${E2E_PHASE:-}"
  E2E_PHASE=acs
  E2E_SUB_ID="$sub_id"
  e2e_hit acs wait "poll TR-069 COMPLETE" subscription="$sub_id" retry="0/$attempts"
  for i in $(seq 1 "$attempts"); do
    E2E_HTTP_RETRY="$i/$attempts"
    e2e_hit acs wait "TR-069 poll" subscription="$sub_id" retry="$i/$attempts" \
      endpoint="GET /subscription/$sub_id/registration-progress"
    local had_e=0
    case "$-" in *e*) had_e=1 ;; esac
    set +e
    body="$(e2e_http GET "$base/subscription/$sub_id/registration-progress" \
      -H "Authorization: Bearer $token")"
    if [[ "$had_e" -eq 1 ]]; then set -e; fi
    tr069_status="$(E2E_TR069_BODY="$body" python3 -c '
import json, os
raw = os.environ.get("E2E_TR069_BODY") or ""
try:
    data = json.loads(raw)
except Exception:
    print("")
    raise SystemExit
payload = data.get("data") if isinstance(data, dict) and "data" in data else data
if not isinstance(payload, dict):
    payload = {}
print(payload.get("tr069ProvisionStatus") or payload.get("cpeProvisionStatus") or "")
')"
    if [[ "$tr069_status" == "COMPLETE" ]]; then
      e2e_hit acs pass "TR-069 COMPLETE" subscription="$sub_id" status="$tr069_status" retry="$i/$attempts"
      E2E_PHASE="$prev_phase"
      unset E2E_HTTP_RETRY
      return 0
    fi
    if [[ "$tr069_status" == "FAILED" || "$tr069_status" == "MANUAL_REQUIRED" ]]; then
      e2e_hit acs fail "TR-069 $tr069_status" subscription="$sub_id" status="$tr069_status" retry="$i/$attempts"
      E2E_PHASE="$prev_phase"
      unset E2E_HTTP_RETRY
      return 1
    fi
    e2e_hit acs wait "TR-069 pending" subscription="$sub_id" status="${tr069_status:-?}" retry="$i/$attempts"
    sleep "$sleep_s"
  done
  e2e_hit acs fail "TR-069 poll timeout" subscription="$sub_id" status="${tr069_status:-?}" retry="$attempts/$attempts"
  E2E_PHASE="$prev_phase"
  unset E2E_HTTP_RETRY
  return 1
}

e2e_summary() {
  local summary_status="$1"
  shift
  if [[ "$summary_status" == "pass" || "$summary_status" == "ok" ]]; then
    e2e_hit ok pass "E2E summary" "$@"
  else
    e2e_hit fail fail "E2E summary" "$@"
  fi
}
