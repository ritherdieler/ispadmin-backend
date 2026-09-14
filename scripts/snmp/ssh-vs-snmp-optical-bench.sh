#!/usr/bin/env bash
# Throwaway live bench: SSH bulk (mmi-mode, no More) vs SNMP column walks.
# Does not print community/password. Requires:
#   source /tmp/olt-ssh-env.sh && source /tmp/olt-snmp-env.sh
# Or export OLT_GATEWAY_{HOST,USERNAME,PASSWORD,SNMP_RO_COMMUNITY}
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${BENCH_OUT:-/tmp/ssh-vs-snmp-optical-bench-$(date +%Y%m%d-%H%M%S).log}"
HOST="${OLT_GATEWAY_HOST:-10.11.104.2}"
COMM="${OLT_GATEWAY_SNMP_RO_COMMUNITY:-}"
USER="${OLT_GATEWAY_USERNAME:-oltadmin}"
PASS="${OLT_GATEWAY_PASSWORD:-}"

RX_OID="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.4"
TX_OID="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.5"
OLTRX_OID="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.6"
TEMP_OID="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.1"
BIAS_OID="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.2"
SN_OID="1.3.6.1.4.1.2011.6.128.1.1.2.43.1.3"
RUN_OID="1.3.6.1.4.1.2011.6.128.1.1.2.46.1.15"

# Ports with ONTs (probe 2026-08-26): slot0 0-4,13-14; slot1 0-13,15
PORTS_WITH_ONTS=(
  "0:0" "0:1" "0:2" "0:3" "0:4" "0:13" "0:14"
  "1:0" "1:1" "1:2" "1:3" "1:4" "1:5" "1:6" "1:7" "1:8" "1:9" "1:10" "1:11" "1:12" "1:13" "1:15"
)
DENSE_SLOT=1
DENSE_PORT=6

if [[ -z "$COMM" || -z "$PASS" ]]; then
  echo "Need OLT_GATEWAY_SNMP_RO_COMMUNITY and OLT_GATEWAY_PASSWORD" >&2
  exit 1
fi

: >"$OUT"
exec >>"$OUT" 2>&1
echo "BENCH_START host=$HOST out=$OUT $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "PORTS_WITH_ONTS=${#PORTS_WITH_ONTS[@]} dense=$DENSE_SLOT/$DENSE_PORT"

snmp_walk() {
  local label="$1" oid="$2"
  local tmp
  tmp="$(mktemp)"
  local start end rows valid
  start=$(python3 -c 'import time; print(int(time.time()*1000))')
  snmpbulkwalk -v2c -c "$COMM" -Cr25 -t 15 -r 1 -On -OQ "$HOST" "$oid" >"$tmp" 2>"${tmp}.err" || true
  end=$(python3 -c 'import time; print(int(time.time()*1000))')
  rows=$(grep -c ' = ' "$tmp" || true)
  valid=$(grep -Evc ' = (No Such|NULL|""$)' "$tmp" || true)
  local ms=$((end - start))
  echo "RESULT label=$label kind=snmp_walk oid=$oid rows=$rows approxNonEmpty=$valid durationMs=$ms"
  if [[ -s "${tmp}.err" ]]; then
    echo "SNMP_ERR label=$label $(head -c 200 "${tmp}.err" | tr '\n' ' ')"
  fi
  rm -f "$tmp" "${tmp}.err"
}

echo "== A) SNMP full-table Rx only =="
snmp_walk "A_rx" "$RX_OID"

echo "== B) SNMP full-table 5 DDM columns sequential =="
B_START=$(python3 -c 'import time; print(int(time.time()*1000))')
snmp_walk "B_rx" "$RX_OID"
snmp_walk "B_tx" "$TX_OID"
snmp_walk "B_oltRx" "$OLTRX_OID"
snmp_walk "B_temp" "$TEMP_OID"
snmp_walk "B_bias" "$BIAS_OID"
B_END=$(python3 -c 'import time; print(int(time.time()*1000))')
echo "RESULT label=B_5ddm_total kind=snmp_seq durationMs=$((B_END - B_START))"

echo "== C) SNMP multi-varbind GETBULK probe (5 OIDs one PDU, first page only) =="
C_TMP="$(mktemp)"
C_START=$(python3 -c 'import time; print(int(time.time()*1000))')
# net-snmp: snmpbulkget with multiple OIDs = multi-varbind GETBULK page
snmpbulkget -v2c -c "$COMM" -Cr10 -t 15 -r 1 -On -OQ "$HOST" \
  "$RX_OID" "$TX_OID" "$OLTRX_OID" "$TEMP_OID" "$BIAS_OID" >"$C_TMP" 2>"${C_TMP}.err" || true
C_END=$(python3 -c 'import time; print(int(time.time()*1000))')
C_ROWS=$(grep -c ' = ' "$C_TMP" || true)
echo "RESULT label=C_multivar_first_page kind=snmp_bulkget bindings=$C_ROWS durationMs=$((C_END - C_START))"
echo "NOTE C is first-page only (not full walk); validates agent accepts multi-OID GETBULK"
rm -f "$C_TMP" "${C_TMP}.err"

echo "== INV) SNMP inventory SN + runStatus =="
I_START=$(python3 -c 'import time; print(int(time.time()*1000))')
snmp_walk "INV_sn" "$SN_OID"
snmp_walk "INV_run" "$RUN_OID"
I_END=$(python3 -c 'import time; print(int(time.time()*1000))')
echo "RESULT label=INV_snmp_sn_run_total kind=snmp_seq durationMs=$((I_END - I_START))"

echo "== DENSE SNMP) Rx scoped to ifIndex of slot1/port6 =="
# ifIndex = 0xFA000000 + (slot<<13) + (port<<8)
IFINDEX=$(python3 - <<'PY'
slot, port = 1, 6
print(0xFA000000 + (slot << 13) + (port << 8))
PY
)
snmp_walk "DENSE_snmp_rx" "${RX_OID}.${IFINDEX}"

echo "== D+E) SSH mmi-mode: optical port-all (22 ports) + inventory slot-all + dense port =="
export OLT_GATEWAY_HOST="$HOST"
export OLT_GATEWAY_USERNAME="$USER"
export OLT_GATEWAY_PASSWORD="$PASS"
export BENCH_DENSE_SLOT="$DENSE_SLOT"
export BENCH_DENSE_PORT="$DENSE_PORT"
export BENCH_PORTS="$(IFS=,; echo "${PORTS_WITH_ONTS[*]}")"

expect <<'EOF'
set timeout 300
set host $env(OLT_GATEWAY_HOST)
set user $env(OLT_GATEWAY_USERNAME)
set pass $env(OLT_GATEWAY_PASSWORD)
set dense_slot $env(BENCH_DENSE_SLOT)
set dense_port $env(BENCH_DENSE_PORT)
set ports_csv $env(BENCH_PORTS)

proc now_ms {} {
  return [clock milliseconds]
}

proc wait_prompt {tout} {
  global timeout
  set timeout $tout
  expect {
    -re {---- More \( Press 'Q' to break \) ----} {
      puts "WARN_MORE_SEEN"
      send " "
      exp_continue
    }
    -re {More \( Press 'Q' to break \)} {
      puts "WARN_MORE_SEEN"
      send " "
      exp_continue
    }
    -re {MA5608T\(config-if-gpon-0/[01]\)#} {}
    -re {MA5608T\(config\)#} {}
    -re {MA5608T#} {}
    timeout { puts "TIMEOUT_PROMPT"; return 0 }
  }
  return 1
}

spawn ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o KexAlgorithms=diffie-hellman-group-exchange-sha1 -o HostKeyAlgorithms=ssh-rsa -o PubkeyAcceptedKeyTypes=ssh-rsa -o Ciphers=aes128-cbc -o ConnectTimeout=10 $user@$host
expect {
  -re "(?i)password:" { send "$pass\r" }
  timeout { puts "FAIL_LOGIN"; exit 1 }
}
expect {
  -re {MA5608T>} { send "enable\r" }
  -re {MA5608T#} {}
  timeout { puts "FAIL_PROMPT"; exit 1 }
}
expect {
  -re "(?i)password:" { send "$pass\r"; exp_continue }
  -re {MA5608T#} {}
  -re {MA5608T>} { send "enable\r"; exp_continue }
}
send "config\r"
wait_prompt 30
send "mmi-mode enable\r"
wait_prompt 30
puts "MMI_MODE_OK"

# --- Inventory slot-all (frame 0 slots 0 and 1) ---
set inv_start [now_ms]
set inv_chars 0
set inv_more 0
foreach slot {0 1} {
  set t0 [now_ms]
  send "display ont info 0 $slot all\r"
  expect {
    -re {---- More} { incr inv_more; send " "; exp_continue }
    -re {MA5608T\(config\)#} {
      set chunk $expect_out(buffer)
      set inv_chars [expr {$inv_chars + [string length $chunk]}]
    }
    timeout { puts "INV_SLOT_TIMEOUT slot=$slot" }
  }
  set t1 [now_ms]
  puts "RESULT label=E_inv_slot_$slot kind=ssh_slot_all durationMs=[expr {$t1 - $t0}] chars=[string length $expect_out(buffer)]"
}
set inv_end [now_ms]
puts "RESULT label=E_inv_slot_all_total kind=ssh_slot_all durationMs=[expr {$inv_end - $inv_start}] moreHits=$inv_more chars=$inv_chars"

# --- Dense port optical ---
send "interface gpon 0/$dense_slot\r"
wait_prompt 30
set d0 [now_ms]
send "display ont optical-info $dense_port all\r"
expect {
  -re {---- More} { puts "WARN_MORE_SEEN"; send " "; exp_continue }
  -re {MA5608T\(config-if-gpon-0/$dense_slot\)#} {}
  timeout { puts "DENSE_OPT_TIMEOUT" }
}
set d1 [now_ms]
set dens_buf $expect_out(buffer)
set dens_rows [regexp -all -line {^\s*\d+\s+-?\d} $dens_buf]
set dens_rx [regexp -all -line {^\s*\d+\s+-?\d+\.\d+} $dens_buf]
puts "RESULT label=DENSE_ssh_optical kind=ssh_port_all slot=$dense_slot port=$dense_port durationMs=[expr {$d1 - $d0}] approxRows=$dens_rows approxRxRows=$dens_rx chars=[string length $dens_buf]"
send "quit\r"
wait_prompt 30

# --- Optical all ports with ONTs ---
set opt_start [now_ms]
set total_rows 0
set total_rx 0
set ports_ok 0
set ports_fail 0
set more_hits 0
set last_slot -1
foreach pair [split $ports_csv ","] {
  if {![regexp {^(\d+):(\d+)$} $pair -> slot port]} { continue }
  if {$slot != $last_slot} {
    if {$last_slot >= 0} {
      send "quit\r"
      wait_prompt 30
    }
    send "interface gpon 0/$slot\r"
    wait_prompt 30
    set last_slot $slot
  }
  set t0 [now_ms]
  send "display ont optical-info $port all\r"
  expect {
    -re {---- More} { incr more_hits; send " "; exp_continue }
    -re {MA5608T\(config-if-gpon-0/$slot\)#} {}
    timeout {
      incr ports_fail
      puts "RESULT label=D_port kind=ssh_port_all slot=$slot port=$port durationMs=[expr {[now_ms] - $t0}] status=timeout"
      continue
    }
  }
  set buf $expect_out(buffer)
  set rows [regexp -all -line {^\s*\d+\s+-?\d} $buf]
  set rxrows [regexp -all -line {^\s*\d+\s+-?\d+\.\d+} $buf]
  set total_rows [expr {$total_rows + $rows}]
  set total_rx [expr {$total_rx + $rxrows}]
  incr ports_ok
  puts "RESULT label=D_port kind=ssh_port_all slot=$slot port=$port durationMs=[expr {[now_ms] - $t0}] approxRows=$rows approxRxRows=$rxrows chars=[string length $buf]"
}
if {$last_slot >= 0} {
  send "quit\r"
  wait_prompt 30
}
set opt_end [now_ms]
puts "RESULT label=D_ssh_optical_22ports kind=ssh_port_all durationMs=[expr {$opt_end - $opt_start}] portsOk=$ports_ok portsFail=$ports_fail approxRows=$total_rows approxRxRows=$total_rx moreHits=$more_hits"

send "return\r"
wait_prompt 30
send "quit\r"
expect eof
EOF

echo "BENCH_END $(date -u +%Y-%m-%dT%H:%M:%SZ) log=$OUT"
echo "LOG_PATH=$OUT"
